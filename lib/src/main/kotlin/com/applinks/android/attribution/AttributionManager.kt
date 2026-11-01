package com.applinks.android.attribution

import android.util.Log
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder

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
) {
    /** What the install report says about the app */
    data class AppInfo(
        val bundleId: String,
        val appVersion: String?,
        val osVersion: String?,
    )

    private data class State(
        val adsConsent: Boolean,
        val limitedDataUse: Boolean,
        val advertisingId: String?,
        val appVersion: String?,
        val osVersion: String?,
    )

    companion object {
        private const val TAG = "AppLinksAttribution"

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

    /**
     * Called when the SDK initializes. On a fresh install, the install report
     * becomes due; it's sent after the first deferred deep link lookup.
     */
    fun start(freshInstall: Boolean): Job? {
        if (!enabled) return null
        if (freshInstall && attributionId == null) {
            store.putBoolean(KEY_REPORT_DUE, true)
        }
        return launchSync()
    }

    /**
     * Called after each deferred deep link lookup with the install referrer, or
     * null when there was none. The first lookup of a fresh install sends the
     * install report, with the click ID of the referrer when it has one.
     */
    fun onDeferredLookupFinished(referrer: String?): Job? {
        if (!enabled) return null
        synchronized(this) {
            if (!store.getBoolean(KEY_REPORT_DUE, false) || store.getBoolean(KEY_LOOKUP_DONE, false)) return null
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
        store.putString(KEY_ADVERTISING_ID, advertisingId?.takeIf { it.isNotBlank() })
        return launchSync()
    }

    private fun launchSync(): Job? = if (enabled) scope.launch { sync() } else null

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
                Log.d(TAG, "Install reported (matched: ${result.data.matched})")
                store.putString(KEY_ATTRIBUTION_ID, result.data.attributionId)
                saveSent(state)
                clearPendingReport()
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
        val body = buildJsonObject {
            if (state.adsConsent != sent.adsConsent) put("ads_consent", state.adsConsent)
            if (state.limitedDataUse != sent.limitedDataUse) put("limited_data_use", state.limitedDataUse)
            if (state.advertisingId != sent.advertisingId) put("advertising_id", state.advertisingId)
            if (state.appVersion != null && state.appVersion != sent.appVersion) put("app_version", state.appVersion)
            if (state.osVersion != null && state.osVersion != sent.osVersion) put("os_version", state.osVersion)
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
        appVersion = app.appVersion,
        osVersion = app.osVersion,
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
