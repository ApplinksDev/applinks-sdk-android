package com.applinks.android.attribution

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
import org.junit.jupiter.api.Assertions.assertNull
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
    ) = AttributionManager(
        apiClient = AppLinksApiClient(serverUrl = serverUrl, apiKey = apiKey),
        store = store,
        app = AttributionManager.AppInfo("app.sweepy.sweepy", appVersion, osVersion),
        installAttributionEnabled = installAttributionEnabled,
        apiKey = apiKey,
        scope = scope,
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
    fun `keeps the attribution of an install already reported (200)`() {
        server.enqueue(created(status = 200))
        val sdk = manager()

        sdk.start(freshInstall = true).await()
        sdk.onDeferredLookupFinished(referrer).await()

        assertEquals(attributionId, sdk.attributionId)
        manager().start(freshInstall = false).await()
        assertEquals(1, server.requestCount)
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
        assertEquals(buildJsonObject { put("ads_consent", true) }, server.takeRequest().json())

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

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 422])
    fun `treats a refused update as sent`(status: Int) {
        val sdk = reportInstall()

        server.enqueue(error(status))
        sdk.setAdvertisingId("not-a-gaid").await()
        nextRequest()

        sdk.start(freshInstall = false).await()
        assertNull(nextRequest())

        server.enqueue(created(status = 200))
        sdk.updateConsent(adsConsent = true).await()
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

    private class InMemoryStore : KeyValueStore {
        private val values = ConcurrentHashMap<String, Any>()

        override fun getString(key: String): String? = values[key] as String?

        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }

        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as Boolean? ?: default

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
