package com.applinks.android.attribution

import android.util.Log
import com.applinks.android.InstallReferrerManager
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.storage.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports the app's install to Applinks once, so it's credited to the link
 * click that sent the user to the Play Store, and keeps the attribution's
 * consent, advertising ID and versions up to date afterwards.
 *
 * Everything is sent by [sync], one call at a time: it compares the state the
 * app wants (stored consent, advertising ID, current versions) with the state
 * last sent successfully, and does the install report (POST) or an update
 * with the fields that changed (PATCH).
 */
internal class AttributionManager(
    private val apiClient: AppLinksApiClient,
    private val store: KeyValueStore,
    private val app: AppInfo,
    installAttributionEnabled: Boolean,
    apiKey: String?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What the install report says about the app */
    data class AppInfo(
        val bundleId: String,
        val appVersion: String?,
        val osVersion: String?,
        // PackageInfo's, in milliseconds: null when they couldn't be read
        val firstInstallTime: Long? = null,
        val lastUpdateTime: Long? = null,
    ) {
        /**
         * Whether the app was installed with the version that runs, or
         * recently. An app that adds the SDK in an update has no SDK state,
         * like a fresh install: its existing users installed long ago.
         */
        fun isNewInstall(now: Long): Boolean {
            val installed = firstInstallTime ?: return true
            return installed == lastUpdateTime || now - installed < NEW_INSTALL_WINDOW_MS
        }
    }

    private data class State(
        val adsConsent: Boolean,
        val limitedDataUse: Boolean,
        val advertisingId: String?,
        val appVersion: String?,
        val osVersion: String?,
    )

    companion object {
        private const val TAG = "AppLinksAttribution"

        // An app updated before its first launch is still a new install
        private val NEW_INSTALL_WINDOW_MS = TimeUnit.DAYS.toMillis(30)

        // The longest app or OS version Applinks keeps (Attribution::VERSION_LENGTH)
        private const val VERSION_LENGTH = 64

        // Shared by every instance: initializing the SDK twice must not report twice
        private val syncMutex = Mutex()

        private const val KEY_REPORT_DUE = "attribution_install_report_due"
        private const val KEY_LOOKUP_DONE = "attribution_lookup_done"
        private const val KEY_CLICK_ID = "attribution_click_id"
        private const val KEY_ATTRIBUTION_ID = "attribution_id"
        private const val KEY_ADS_CONSENT = "attribution_ads_consent"
        private const val KEY_LIMITED_DATA_USE = "attribution_limited_data_use"
        private const val KEY_ADVERTISING_ID = "attribution_advertising_id"
        private const val KEY_SENT_ADS_CONSENT = "attribution_sent_ads_consent"
        private const val KEY_SENT_LIMITED_DATA_USE = "attribution_sent_limited_data_use"
        private const val KEY_SENT_ADVERTISING_ID = "attribution_sent_advertising_id"
        private const val KEY_SENT_APP_VERSION = "attribution_sent_app_version"
        private const val KEY_SENT_OS_VERSION = "attribution_sent_os_version"
        // Set after a 200: Applinks kept only stricter consent and none of the
        // advertising ID and versions, so every field is sent in an update
        private const val KEY_SENT_UNKNOWN = "attribution_sent_unknown"

        private val ALL_KEYS = listOf(
            KEY_REPORT_DUE, KEY_LOOKUP_DONE, KEY_CLICK_ID, KEY_ATTRIBUTION_ID,
            KEY_ADS_CONSENT, KEY_LIMITED_DATA_USE, KEY_ADVERTISING_ID,
            KEY_SENT_ADS_CONSENT, KEY_SENT_LIMITED_DATA_USE, KEY_SENT_ADVERTISING_ID,
            KEY_SENT_APP_VERSION, KEY_SENT_OS_VERSION, KEY_SENT_UNKNOWN,
        )

        private const val ZERO_ADVERTISING_ID = "00000000-0000-0000-0000-000000000000"
        private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }

    // Nothing is stored as due nor sent without an API key
    private val enabled = installAttributionEnabled && !apiKey.isNullOrEmpty()

    /** The ID of the reported install, or null until the install was reported */
    val attributionId: String?
        get() = store.getString(KEY_ATTRIBUTION_ID)

    // Set when the install report failed in a way worth retrying: it's retried
    // at the next SDK initialization, not at every trigger of this session.
    // Only read and written while holding syncMutex.
    private var reportFailedThisSession = false

    // Deferred deep link lookups started and not finished yet, guarded by this
    private var lookupsInFlight = 0

    /**
     * Called when the SDK initializes, before [start], when its preferences
     * were restored from another device or an earlier install (Auto Backup,
     * device transfer): that install's attribution, consent and advertising ID
     * aren't this one's, so they're forgotten.
     */
    fun forgetRestoredState() {
        if (attributionId != null || store.getBoolean(KEY_REPORT_DUE, false)) {
            Log.i(TAG, "Attribution state restored from a backup, forgotten")
        }
        ALL_KEYS.forEach { store.remove(it) }
    }

    /**
     * Called when the SDK initializes. On a fresh install, the install report
     * becomes due; it's sent after the first deferred deep link lookup.
     */
    fun start(freshInstall: Boolean): Job? {
        if (!enabled) return null
        if (freshInstall && attributionId == null) {
            if (app.isNewInstall(clock())) {
                store.putBoolean(KEY_REPORT_DUE, true)
            } else {
                Log.d(TAG, "App installed before it had the SDK: not reported as a new install")
            }
        }
        return launchSync()
    }

    /**
     * Wraps the callback of a deferred deep link lookup: the app's callback
     * runs first, then the lookup's referrer goes to [onDeferredLookupFinished].
     * Call it when the lookup starts.
     */
    fun trackLookup(callback: InstallReferrerManager.InstallReferrerCallback): InstallReferrerManager.InstallReferrerCallback {
        synchronized(this) { lookupsInFlight++ }
        val finished = AtomicBoolean(false)
        fun finish(referrer: String?) {
            if (finished.compareAndSet(false, true)) onDeferredLookupFinished(referrer)
        }
        return object : InstallReferrerManager.InstallReferrerCallback {
            override fun onReferrerRetrieved(referrerData: InstallReferrerManager.ReferrerData) {
                try {
                    callback.onReferrerRetrieved(referrerData)
                } finally {
                    finish(referrerData.installReferrer)
                }
            }

            override fun onError(error: String) {
                try {
                    callback.onError(error)
                } finally {
                    // Reported as an install without a click
                    finish(null)
                }
            }
        }
    }

    /**
     * Called after each deferred deep link lookup with the install referrer, or
     * null when there was none. The first lookup of a fresh install sends the
     * install report, with the click ID of the referrer when it has one.
     */
    fun onDeferredLookupFinished(referrer: String?): Job? {
        if (!enabled) return null
        synchronized(this) {
            lookupsInFlight = (lookupsInFlight - 1).coerceAtLeast(0)
            if (!store.getBoolean(KEY_REPORT_DUE, false) || store.getBoolean(KEY_LOOKUP_DONE, false)) return null
            // A failed lookup waits for the ones still running (the app's
            // manual check alongside the automatic one): they can still read
            // the referrer, and the report is sent with the first referrer found
            if (referrer == null && lookupsInFlight > 0) return null
            // Only the click ID is kept and sent, never the referrer itself
            store.putString(KEY_CLICK_ID, referrer?.let { ClickIds.fromReferrer(it) })
            store.putBoolean(KEY_LOOKUP_DONE, true)
        }
        return launchSync()
    }

    fun updateConsent(adsConsent: Boolean, limitedDataUse: Boolean = false): Job? {
        store.putBoolean(KEY_ADS_CONSENT, adsConsent)
        store.putBoolean(KEY_LIMITED_DATA_USE, limitedDataUse)
        return launchSync()
    }

    fun setAdvertisingId(advertisingId: String?): Job? {
        store.putString(KEY_ADVERTISING_ID, validAdvertisingId(advertisingId))
        return launchSync()
    }

    // Applinks refuses a report or update with an advertising ID that isn't a
    // UUID, so one that isn't is dropped; the zeroed ID is no ID at all
    private fun validAdvertisingId(advertisingId: String?): String? {
        val id = advertisingId?.trim()?.takeIf { it.isNotEmpty() && it != ZERO_ADVERTISING_ID } ?: return null
        if (!UUID_REGEX.matches(id)) {
            Log.w(TAG, "Ignored an advertising ID that isn't a UUID: pass the GAID as AdvertisingIdClient returns it")
            return null
        }
        return id
    }

    private fun launchSync(): Job? = if (enabled) scope.launch {
        // The app must never crash because of an attribution
        try {
            sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Attribution sync failed", e)
        }
    } else null

    /**
     * Sends what changed since the last successful send. Calls run one at a
     * time, and each reads the state when it starts, so a change made while a
     * request is in flight is sent by the call that follows.
     */
    suspend fun sync() {
        if (!enabled) return
        syncMutex.withLock {
            val desired = desiredState()
            val id = attributionId
            if (id != null) {
                update(id, desired)
            } else if (isReportReady() && !reportFailedThisSession) {
                reportInstall(desired)
            }
        }
    }

    // A fresh install whose first deferred deep link lookup is done
    private fun isReportReady(): Boolean =
        store.getBoolean(KEY_REPORT_DUE, false) && store.getBoolean(KEY_LOOKUP_DONE, false)

    private fun reportInstall(state: State) {
        val clickId = store.getString(KEY_CLICK_ID)
        val body = buildJsonObject {
            put("platform", "android")
            put("bundle_id", app.bundleId)
            clickId?.let { put("click_id", it) }
            put("ads_consent", state.adsConsent)
            put("limited_data_use", state.limitedDataUse)
            state.advertisingId?.let { put("advertising_id", it) }
            state.appVersion?.let { put("app_version", it) }
            state.osVersion?.let { put("os_version", it) }
        }

        when (val result = apiClient.reportInstall(body)) {
            is AppLinksApiClient.Result.Success -> {
                val attribution = result.data.attribution
                Log.d(TAG, "Install reported (matched: ${attribution.matched}, new: ${result.data.created})")
                // On disk before the report is cleared: a process killed in
                // between must not report the install twice
                store.putString(KEY_ATTRIBUTION_ID, attribution.attributionId, durable = true)
                if (result.data.created) {
                    saveSent(state)
                } else {
                    // Already reported (a lost answer, or a reinstall with the
                    // same referrer): Applinks kept its stored consent where it's
                    // stricter, and ignored the advertising ID and versions, so
                    // they're all sent in an update
                    store.putBoolean(KEY_SENT_UNKNOWN, true)
                }
                clearPendingReport()
                if (!result.data.created) update(attribution.attributionId, state)
            }
            is AppLinksApiClient.Result.Error -> {
                if (isRetryable(result.code)) {
                    Log.w(TAG, "Install report failed, retrying at the next launch: ${result.message}")
                    reportFailedThisSession = true
                } else {
                    Log.e(TAG, "Install report refused, not retrying: ${describe(result, installReport = true)}")
                    clearPendingReport()
                }
            }
        }
    }

    private fun update(attributionId: String, state: State) {
        val sent = sentState()
        val unknown = store.getBoolean(KEY_SENT_UNKNOWN, false)
        // Applinks keeps the advertising ID only while ads_consent is true: it
        // dropped the one sent without consent, or when consent was withdrawn,
        // so it's sent again when consent is given
        val consentGiven = state.adsConsent && !sent.adsConsent
        val body = buildJsonObject {
            if (unknown || state.adsConsent != sent.adsConsent) put("ads_consent", state.adsConsent)
            if (unknown || state.limitedDataUse != sent.limitedDataUse) put("limited_data_use", state.limitedDataUse)
            if (unknown || state.advertisingId != sent.advertisingId || (consentGiven && state.advertisingId != null)) {
                put("advertising_id", state.advertisingId)
            }
            if (state.appVersion != null && (unknown || state.appVersion != sent.appVersion)) put("app_version", state.appVersion)
            if (state.osVersion != null && (unknown || state.osVersion != sent.osVersion)) put("os_version", state.osVersion)
        }
        if (body.isEmpty()) return

        when (val result = apiClient.updateAttribution(attributionId, body)) {
            is AppLinksApiClient.Result.Success -> saveSent(state)
            is AppLinksApiClient.Result.Error -> {
                if (isRetryable(result.code)) {
                    Log.w(TAG, "Attribution update failed, retrying later: ${result.message}")
                } else {
                    // Treated as sent, so the same refused update isn't sent again and again
                    Log.e(TAG, "Attribution update refused: ${describe(result, installReport = false)}")
                    saveSent(state)
                }
            }
        }
    }

    // A network error (no code), a timeout, a server error or rate limiting
    private fun isRetryable(code: Int?): Boolean =
        code == null || code >= 500 || code == 408 || code == 429

    private fun describe(error: AppLinksApiClient.Result.Error, installReport: Boolean): String = when {
        error.code == 404 && installReport ->
            "App not found (404): no Applinks project has ${app.bundleId} as its Android bundle ID"
        error.code == 401 ->
            "Unauthorized (401): check the public API key (pk_) passed to apiKey"
        else -> "${error.message} (${error.code})"
    }

    private fun desiredState() = State(
        adsConsent = store.getBoolean(KEY_ADS_CONSENT, false),
        limitedDataUse = store.getBoolean(KEY_LIMITED_DATA_USE, false),
        advertisingId = store.getString(KEY_ADVERTISING_ID),
        // Applinks refuses a longer one, and with it the whole report
        appVersion = app.appVersion?.take(VERSION_LENGTH),
        osVersion = app.osVersion?.take(VERSION_LENGTH),
    )

    private fun sentState() = State(
        adsConsent = store.getBoolean(KEY_SENT_ADS_CONSENT, false),
        limitedDataUse = store.getBoolean(KEY_SENT_LIMITED_DATA_USE, false),
        advertisingId = store.getString(KEY_SENT_ADVERTISING_ID),
        appVersion = store.getString(KEY_SENT_APP_VERSION),
        osVersion = store.getString(KEY_SENT_OS_VERSION),
    )

    private fun saveSent(state: State) {
        store.putBoolean(KEY_SENT_ADS_CONSENT, state.adsConsent)
        store.putBoolean(KEY_SENT_LIMITED_DATA_USE, state.limitedDataUse)
        store.putString(KEY_SENT_ADVERTISING_ID, state.advertisingId)
        store.putString(KEY_SENT_APP_VERSION, state.appVersion)
        store.putString(KEY_SENT_OS_VERSION, state.osVersion)
        store.remove(KEY_SENT_UNKNOWN)
    }

    private fun clearPendingReport() {
        store.putBoolean(KEY_REPORT_DUE, false)
        store.putBoolean(KEY_LOOKUP_DONE, false)
        store.putString(KEY_CLICK_ID, null)
    }
}

/**
 * The click ID ("ck") Applinks adds to the URL it hands the app: the Play
 * install referrer (bundleid://path?ck=<uuid>) or the clipboard's link.
 */
internal object ClickIds {
    private const val UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // The same as the server's CK_PARAM. The URL isn't parsed: the deep link
    // path can hold spaces or accented letters a strict parser refuses.
    private val CK_PARAM = Regex("[?&]ck=($UUID)(?=[&#]|\\z)")
    private val UUID_REGEX = Regex(UUID)

    /** The lowercased ck UUID of the URL, or null */
    fun extract(url: String): String? =
        CK_PARAM.find(url.trim())?.groupValues?.get(1)?.lowercase()

    /** The click ID of the install referrer, also when it's still percent-encoded */
    fun fromReferrer(referrer: String): String? =
        extract(referrer) ?: decode(referrer)?.let { extract(it) }

    /** The params without "ck" when it's a click ID; other params are kept */
    fun strip(params: Map<String, String>): Map<String, String> {
        val ck = params["ck"] ?: return params
        return if (UUID_REGEX.matches(ck)) params - "ck" else params
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }
}
