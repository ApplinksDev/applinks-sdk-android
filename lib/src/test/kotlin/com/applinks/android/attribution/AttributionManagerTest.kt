package com.applinks.android.attribution

import com.applinks.android.InstallReferrerManager
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AttributionManagerTest {
    private val ck = "9b2e4c1a-7f3d-4e8b-a2c6-5d1f0e9b8a7c"
    private val attributionId = "c41f5e2a-9d8b-4c7e-b6a1-3f2e1d0c9b8a"
    private val referrer = "app.sweepy.sweepy://promo/42?ref=ad&ck=$ck"
    private val gaid = "38400000-8cf0-11bd-b23e-10b96e40000d"
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L

    private lateinit var server: MockWebServer
    private val store = InMemoryStore()
    private val scope = CoroutineScope(Dispatchers.IO)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // A manager as the SDK creates it at initialization: a new one simulates a restart
    private fun manager(
        appVersion: String? = "2.3.1",
        osVersion: String? = "14",
        installAttributionEnabled: Boolean = true,
        apiKey: String? = "pk_test",
        serverUrl: String = server.url("/").toString().removeSuffix("/"),
        firstInstallTime: Long? = now - 1000,
        lastUpdateTime: Long? = now - 1000,
    ) = AttributionManager(
        apiClient = AppLinksApiClient(serverUrl = serverUrl, apiKey = apiKey),
        store = store,
        app = AttributionManager.AppInfo("app.sweepy.sweepy", appVersion, osVersion, firstInstallTime, lastUpdateTime),
        installAttributionEnabled = installAttributionEnabled,
        apiKey = apiKey,
        scope = scope,
        clock = { now },
    )

    private fun created(status: Int = 201) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"attribution_id":"$attributionId","matched":true,"link":{"id":"3d6f","title":"Promo","deep_link_params":{}}}""")

    // With Retry-After, OkHttp doesn't retry a 408 itself, so the SDK gets it
    private fun error(status: Int) = MockResponse()
        .setResponseCode(status)
        .setHeader("Retry-After", "1")
        .setBody("""{"error":{"status":"error","code":$status,"message":"Refused"}}""")

    private fun Job?.await() = runBlocking { this@await?.join() }

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body.readUtf8()).jsonObject

    private fun nextRequest(): RecordedRequest? = server.takeRequest(200, TimeUnit.MILLISECONDS)

    private fun installBody(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("platform", "android")
        put("bundle_id", "app.sweepy.sweepy")
        put("ads_consent", false)
        put("limited_data_use", false)
        put("app_version", "2.3.1")
        put("os_version", "14")
        builder()
    }

    // Reports the install of a fresh install and takes its POST
    private fun reportInstall(sdk: AttributionManager = manager()): AttributionManager {
        server.enqueue(created())
        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        assertEquals("POST", nextRequest()?.method)
        return sdk
    }

    @Test
    fun `reports a fresh install after the first lookup, with the click ID`() {
        server.enqueue(created())
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        assertEquals(0, server.requestCount)

        sdk.onDeferredLookupFinished(referrer).await()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/attributions", request.path)
        assertEquals("Bearer pk_test", request.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals(installBody { put("click_id", ck) }, request.json())
        assertEquals(attributionId, sdk.attributionId)
        // On disk before the report is cleared, so it isn't sent twice
        assertTrue("attribution_id" in store.durableKeys)
    }

    @Test
    fun `reports an install without a referrer as unmatched`() {
        server.enqueue(created())
        val sdk = manager(appVersion = null, osVersion = null)

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(null).await()

        val expected = buildJsonObject {
            put("platform", "android")
            put("bundle_id", "app.sweepy.sweepy")
            put("ads_consent", false)
            put("limited_data_use", false)
        }
        assertEquals(expected, server.takeRequest().json())
        assertEquals(attributionId, sdk.attributionId)
    }

    @Test
    fun `sends no click ID when the referrer has none`() {
        server.enqueue(created())
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished("utm_source=google-play&utm_medium=organic").await()

        assertEquals(installBody(), server.takeRequest().json())
    }

    @Test
    fun `finds the click ID of a percent-encoded referrer`() {
        server.enqueue(created())
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished("app.sweepy.sweepy%3A%2F%2Fpromo%3Fck%3D${ck.uppercase()}").await()

        assertEquals(installBody { put("click_id", ck) }, server.takeRequest().json())
    }

    @Test
    fun `sends the consent and advertising ID set before the report`() {
        server.enqueue(created())
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.updateConsent(adsConsent = true, limitedDataUse = true).await()
        sdk.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d").await()
        assertEquals(0, server.requestCount)

        sdk.onDeferredLookupFinished(referrer).await()

        val expected = installBody {
            put("click_id", ck)
            put("ads_consent", true)
            put("limited_data_use", true)
            put("advertising_id", "38400000-8cf0-11bd-b23e-10b96e40000d")
        }
        assertEquals(expected, server.takeRequest().json())
    }

    @Test
    fun `reports only after the first lookup`() {
        server.enqueue(created())
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        nextRequest()

        assertNull(sdk.onDeferredLookupFinished("app.sweepy.sweepy://other?ck=$ck"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `never reports an app that wasn't a fresh install`() {
        val sdk = manager()

        sdk.start(freshInstall = false).await()
        sdk.onDeferredLookupFinished(referrer).await()
        sdk.updateConsent(adsConsent = true).await()

        assertEquals(0, server.requestCount)
        assertNull(sdk.attributionId)
    }

    @Test
    fun `sends nothing when install attribution is disabled`() {
        val sdk = manager(installAttributionEnabled = false)

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        sdk.updateConsent(adsConsent = true).await()

        assertEquals(0, server.requestCount)
        // And it isn't due when attribution is enabled in a later version
        manager().start(freshInstall = false).await()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sends nothing without an API key`() {
        listOf(null, "").forEach { apiKey ->
            val sdk = manager(apiKey = apiKey)

            sdk.start(freshInstall = true).await()
            sdk.onDeferredLookupFinished(referrer).await()
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `keeps the attribution of an install already reported (200), and updates all its fields`() {
        server.enqueue(created(status = 200))
        server.enqueue(created(status = 200))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.updateConsent(adsConsent = true).await()
        sdk.setAdvertisingId(gaid).await()
        sdk.onDeferredLookupFinished(referrer).await()

        assertEquals("POST", server.takeRequest().method)
        assertEquals(attributionId, sdk.attributionId)
        // Applinks kept the first report's consent where it's stricter, and
        // none of the repeat's advertising ID and versions
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/attributions/$attributionId", update.path)
        val expected = buildJsonObject {
            put("ads_consent", true)
            put("limited_data_use", false)
            put("advertising_id", gaid)
            put("app_version", "2.3.1")
            put("os_version", "14")
        }
        assertEquals(expected, update.json())

        manager().start(freshInstall = false).await()
        assertNull(nextRequest())
    }

    @Test
    fun `sends a removed advertising ID as null after a 200`() {
        server.enqueue(created(status = 200))
        server.enqueue(created(status = 200))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        server.takeRequest()

        val expected = buildJsonObject {
            put("ads_consent", false)
            put("limited_data_use", false)
            put("advertising_id", JsonNull)
            put("app_version", "2.3.1")
            put("os_version", "14")
        }
        assertEquals(expected, server.takeRequest().json())
    }

    @Test
    fun `retries the update that follows a 200 at the next trigger`() {
        server.enqueue(created(status = 200))
        server.enqueue(error(503))
        server.enqueue(created(status = 200))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.updateConsent(adsConsent = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        server.takeRequest()
        server.takeRequest()

        manager().start(freshInstall = false).await()
        val expected = buildJsonObject {
            put("ads_consent", true)
            put("limited_data_use", false)
            put("advertising_id", JsonNull)
            put("app_version", "2.3.1")
            put("os_version", "14")
        }
        assertEquals(expected, server.takeRequest().json())

        manager().start(freshInstall = false).await()
        assertNull(nextRequest())
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 503, 408, 429])
    fun `retries the report at the next launch, with the click ID`(status: Int) {
        server.enqueue(error(status))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        nextRequest()
        assertNull(sdk.attributionId)

        // Not again in this session
        sdk.updateConsent(adsConsent = true).await()
        assertEquals(1, server.requestCount)

        server.enqueue(created())
        val restarted = manager()
        restarted.start(freshInstall = false).await()

        assertEquals(installBody { put("click_id", ck); put("ads_consent", true) }, server.takeRequest().json())
        assertEquals(attributionId, restarted.attributionId)
    }

    @Test
    fun `retries the report at the next launch after a network error`() {
        val offline = MockWebServer().apply { start() }
        val offlineUrl = offline.url("/").toString().removeSuffix("/")
        offline.shutdown()

        val sdk = manager(serverUrl = offlineUrl)
        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        assertNull(sdk.attributionId)

        server.enqueue(created())
        val restarted = manager()
        restarted.start(freshInstall = false).await()

        assertEquals(installBody { put("click_id", ck) }, server.takeRequest().json())
        assertEquals(attributionId, restarted.attributionId)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 422])
    fun `stops reporting when the report is refused`(status: Int) {
        server.enqueue(error(status))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        nextRequest()

        sdk.updateConsent(adsConsent = true).await()
        manager().start(freshInstall = false).await()
        manager().start(freshInstall = true).await()

        assertEquals(1, server.requestCount)
        assertNull(sdk.attributionId)
    }

    @Test
    fun `reports once when triggers run concurrently`() {
        server.enqueue(created().setBodyDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(created())
        val sdk = manager()
        val otherInstance = manager()
        sdk.start(freshInstall = true).await()

        runBlocking {
            val jobs = (1..20).map { index ->
                scope.launch {
                    val instance = if (index % 2 == 0) sdk else otherInstance
                    instance.onDeferredLookupFinished(referrer)?.join()
                    instance.sync()
                    instance.start(freshInstall = true)?.join()
                }
            }
            jobs.joinAll()
        }

        assertEquals("POST", nextRequest()?.method)
        assertNull(nextRequest())
        assertEquals(attributionId, sdk.attributionId)
    }

    @Test
    fun `sends a change made while the report is in flight afterwards`() {
        server.enqueue(created().setBodyDelay(500, TimeUnit.MILLISECONDS))
        server.enqueue(created(status = 200))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        val report = sdk.onDeferredLookupFinished(referrer)
        assertEquals(installBody { put("click_id", ck) }, server.takeRequest().json())

        val change = sdk.updateConsent(adsConsent = true)
        runBlocking { listOfNotNull(report, change).joinAll() }

        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/attributions/$attributionId", update.path)
        assertEquals(buildJsonObject { put("ads_consent", true) }, update.json())
    }

    @Test
    fun `updates only the fields that changed`() {
        val sdk = reportInstall()

        server.enqueue(created(status = 200))
        sdk.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d").await()
        val first = server.takeRequest()
        assertEquals("PATCH", first.method)
        assertEquals("/api/v1/attributions/$attributionId", first.path)
        assertEquals("Bearer pk_test", first.getHeader("Authorization"))
        assertEquals(buildJsonObject { put("advertising_id", "38400000-8cf0-11bd-b23e-10b96e40000d") }, first.json())

        // Nothing changed
        sdk.updateConsent(adsConsent = false, limitedDataUse = false).await()
        sdk.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d").await()
        assertNull(nextRequest())

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()
        val consentGiven = buildJsonObject {
            put("ads_consent", true)
            // Sent again: Applinks dropped it, as it came without consent
            put("advertising_id", "38400000-8cf0-11bd-b23e-10b96e40000d")
        }
        assertEquals(consentGiven, server.takeRequest().json())

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = false, limitedDataUse = true).await()
        val consent = buildJsonObject {
            put("ads_consent", false)
            put("limited_data_use", true)
        }
        assertEquals(consent, server.takeRequest().json())

        server.enqueue(created(status = 200))
        sdk.setAdvertisingId(null).await()
        assertEquals(JsonObject(mapOf("advertising_id" to JsonNull)), server.takeRequest().json())
    }

    @Test
    fun `sends the advertising ID again when consent is given again`() {
        val gaid = "38400000-8cf0-11bd-b23e-10b96e40000d"
        val sdk = reportInstall()

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()
        assertEquals(buildJsonObject { put("ads_consent", true) }, server.takeRequest().json())

        server.enqueue(created(status = 200))
        sdk.setAdvertisingId(gaid).await()
        assertEquals(buildJsonObject { put("advertising_id", gaid) }, server.takeRequest().json())

        // Withdrawing consent removes the stored ID on the server
        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = false).await()
        assertEquals(buildJsonObject { put("ads_consent", false) }, server.takeRequest().json())

        server.enqueue(created(status = 200))
        manager().updateConsent(adsConsent = true).await() // after a restart
        val expected = buildJsonObject {
            put("ads_consent", true)
            put("advertising_id", gaid)
        }
        assertEquals(expected, server.takeRequest().json())

        // Withdrawn without an ID: none to send again
        server.enqueue(created(status = 200))
        sdk.setAdvertisingId(null).await()
        assertEquals(JsonObject(mapOf("advertising_id" to JsonNull)), server.takeRequest().json())
        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = false).await()
        server.takeRequest()
        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()
        assertEquals(buildJsonObject { put("ads_consent", true) }, server.takeRequest().json())
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 408, 429])
    fun `retries a failed update at the next trigger`(status: Int) {
        val sdk = reportInstall()

        server.enqueue(error(status))
        sdk.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d").await()
        nextRequest()

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()

        val expected = buildJsonObject {
            put("ads_consent", true)
            put("advertising_id", "38400000-8cf0-11bd-b23e-10b96e40000d")
        }
        assertEquals(expected, server.takeRequest().json())
    }

    @Test
    fun `retries an update that failed with a network error at the next trigger`() {
        reportInstall()
        val offline = MockWebServer().apply { start() }
        val offlineUrl = offline.url("/").toString().removeSuffix("/")
        offline.shutdown()

        manager(serverUrl = offlineUrl).setAdvertisingId(gaid).await()

        server.enqueue(created(status = 200))
        manager().start(freshInstall = false).await()
        assertEquals(buildJsonObject { put("advertising_id", gaid) }, server.takeRequest().json())
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 422])
    fun `treats a refused update as sent`(status: Int) {
        val sdk = reportInstall()

        server.enqueue(error(status))
        sdk.updateConsent(adsConsent = false, limitedDataUse = true).await()
        nextRequest()

        sdk.start(freshInstall = false).await()
        assertNull(nextRequest())

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true, limitedDataUse = true).await()
        assertEquals(buildJsonObject { put("ads_consent", true) }, server.takeRequest().json())
    }

    @Test
    fun `sends the new app and OS versions at the launch after an update`() {
        reportInstall()

        // Same versions: nothing to send
        manager().start(freshInstall = false).await()
        assertNull(nextRequest())

        server.enqueue(created(status = 200))
        manager(appVersion = "2.4.0").start(freshInstall = false).await()
        assertEquals(buildJsonObject { put("app_version", "2.4.0") }, server.takeRequest().json())

        server.enqueue(created(status = 200))
        manager(appVersion = "2.4.0", osVersion = "15").start(freshInstall = false).await()
        assertEquals(buildJsonObject { put("os_version", "15") }, server.takeRequest().json())

        manager(appVersion = "2.4.0", osVersion = "15").start(freshInstall = false).await()
        assertNull(nextRequest())
    }

    @Test
    fun `keeps the attribution and consent across restarts`() {
        val sdk = reportInstall()
        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true, limitedDataUse = true).await()
        nextRequest()

        val restarted = manager()
        restarted.start(freshInstall = false).await()

        assertEquals(attributionId, restarted.attributionId)
        assertNull(nextRequest())

        server.enqueue(created(status = 200))
        restarted.updateConsent(adsConsent = true).await()
        assertEquals(buildJsonObject { put("limited_data_use", false) }, server.takeRequest().json())
    }

    @Test
    fun `cuts app and OS versions to the 64 characters Applinks keeps`() {
        val long = "2.3.1-beta.7+20261101.ci.4821.sha.9b2e4c1a7f3d4e8ba2c65d1f0e9b8a7c6d5e"
        assertEquals(70, long.length)
        server.enqueue(created())
        val sdk = manager(appVersion = long, osVersion = "14$long")

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()

        val expected = installBody {
            put("click_id", ck)
            put("app_version", long.take(64))
            put("os_version", "14$long".take(64))
        }
        assertEquals(expected, server.takeRequest().json())

        // And it isn't seen as a new version at the next launch
        manager(appVersion = long, osVersion = "14$long").start(freshInstall = false).await()
        assertNull(nextRequest())
    }

    @Test
    fun `sends only an advertising ID that is a UUID, trimmed`() {
        server.enqueue(created())
        val sdk = manager()
        sdk.start(freshInstall = true).await()

        sdk.setAdvertisingId(" $gaid\n").await()
        sdk.onDeferredLookupFinished(referrer).await()
        assertEquals(installBody { put("click_id", ck); put("advertising_id", gaid) }, server.takeRequest().json())

        // Each is dropped, which removes the ID set before
        listOf("not-a-uuid", "a1b2c3", "00000000-0000-0000-0000-000000000000").forEach { invalid ->
            server.enqueue(created(status = 200))
            sdk.setAdvertisingId(invalid).await()
            assertEquals(JsonObject(mapOf("advertising_id" to JsonNull)), server.takeRequest().json(), invalid)

            server.enqueue(created(status = 200))
            sdk.setAdvertisingId(gaid).await()
            assertEquals(buildJsonObject { put("advertising_id", gaid) }, server.takeRequest().json())
        }
    }

    @Test
    fun `stops reporting without crashing when the request can't be built`() {
        listOf(
            manager(serverUrl = "applinks.com"),     // no http(s)
            manager(apiKey = "pk_te\nst"),          // a header can't hold it
        ).forEach { sdk ->
            sdk.start(freshInstall = true).await()
            val report = sdk.onDeferredLookupFinished(referrer)
            report.await()

            // An exception would have failed the job, and crashed the app
            assertFalse(report!!.isCancelled)
            assertNull(sdk.attributionId)
        }
        // Refused, so not sent again with a valid configuration either
        manager().start(freshInstall = false).await()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `reports the referrer of a lookup that ran alongside one that failed`() {
        server.enqueue(created())
        val sdk = manager()
        sdk.start(freshInstall = true).await()
        val received = mutableListOf<String>()
        val app = object : InstallReferrerManager.InstallReferrerCallback {
            override fun onReferrerRetrieved(referrerData: InstallReferrerManager.ReferrerData) {
                received += referrerData.installReferrer
            }

            override fun onError(error: String) {
                received += error
            }
        }

        val automatic = sdk.trackLookup(app)
        val manual = sdk.trackLookup(app)
        automatic.onError("Service not connected")
        assertNull(nextRequest())

        manual.onReferrerRetrieved(InstallReferrerManager.ReferrerData(referrer, 0, 0, false))
        assertEquals(installBody { put("click_id", ck) }, server.takeRequest(5, TimeUnit.SECONDS)?.json())
        assertEquals(listOf("Service not connected", referrer), received)
    }

    @Test
    fun `reports an unmatched install once every lookup failed`() {
        server.enqueue(created())
        val sdk = manager()
        sdk.start(freshInstall = true).await()
        val app = object : InstallReferrerManager.InstallReferrerCallback {
            override fun onReferrerRetrieved(referrerData: InstallReferrerManager.ReferrerData) {}
            override fun onError(error: String) {}
        }

        val first = sdk.trackLookup(app)
        val second = sdk.trackLookup(app)
        first.onError("Install referrer service unavailable")
        // A callback that runs twice finishes its lookup once
        first.onError("Install referrer service unavailable")
        assertNull(nextRequest())

        second.onError("Install referrer not supported")
        assertEquals(installBody(), server.takeRequest(5, TimeUnit.SECONDS)?.json())
    }

    @Test
    fun `reports after the app's callback, even when it throws`() {
        server.enqueue(created())
        val sdk = manager()
        sdk.start(freshInstall = true).await()
        val app = object : InstallReferrerManager.InstallReferrerCallback {
            override fun onReferrerRetrieved(referrerData: InstallReferrerManager.ReferrerData) {
                throw IllegalStateException("app bug")
            }

            override fun onError(error: String) {}
        }

        runCatching { sdk.trackLookup(app).onReferrerRetrieved(InstallReferrerManager.ReferrerData(referrer, 0, 0, false)) }

        assertEquals(installBody { put("click_id", ck) }, server.takeRequest(5, TimeUnit.SECONDS)?.json())
    }

    @Test
    fun `doesn't report existing users of an app that adds the SDK in an update`() {
        // Installed long ago, updated to the version with the SDK
        val sdk = manager(firstInstallTime = now - 400 * day, lastUpdateTime = now - day)
        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `reports a new install updated before its first launch`() {
        listOf(
            manager(firstInstallTime = now - 400 * day, lastUpdateTime = now - 400 * day), // never updated
            manager(firstInstallTime = now - 10 * day, lastUpdateTime = now - day),        // installed recently
            manager(firstInstallTime = null, lastUpdateTime = null),                       // unknown
        ).forEach { sdk ->
            server.enqueue(created())
            store.clear()
            sdk.start(freshInstall = true).await()
            sdk.onDeferredLookupFinished(referrer).await()
            assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS)?.method)
        }
    }

    @Test
    fun `forgets the attribution restored from a backup`() {
        val sdk = reportInstall()
        server.enqueue(created(status = 200))
        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()
        sdk.setAdvertisingId(gaid).await()
        server.takeRequest()
        server.takeRequest()

        // The new device's SDK starts with the old device's preferences
        val restored = manager(osVersion = "15")
        restored.forgetRestoredState()
        restored.start(freshInstall = false).await()
        restored.setAdvertisingId("6f1c2d3e-4b5a-4c7d-8e9f-0a1b2c3d4e5f").await()

        assertNull(restored.attributionId)
        assertNull(nextRequest())
    }

    @Test
    fun `doesn't send the pending report restored from a backup`() {
        server.enqueue(error(503))
        val sdk = manager()
        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()
        server.takeRequest()

        val restored = manager()
        restored.forgetRestoredState()
        restored.start(freshInstall = false).await()

        assertNull(nextRequest())
    }

    private class InMemoryStore : KeyValueStore {
        private val values = ConcurrentHashMap<String, Any>()
        val durableKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

        override fun getString(key: String): String? = values[key] as String?

        override fun putString(key: String, value: String?, durable: Boolean) {
            if (value == null) values.remove(key) else values[key] = value
            if (durable) durableKeys += key
        }

        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as Boolean? ?: default

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        fun clear() = values.clear()
    }
}
