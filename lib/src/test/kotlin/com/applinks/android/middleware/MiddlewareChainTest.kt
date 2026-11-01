package com.applinks.android.middleware

import android.content.ContextWrapper
import android.net.TestUri
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiddlewareChainTest {
    private val ck = "9b2e4c1a-7f3d-4e8b-a2c6-5d1f0e9b8a7c"

    private fun handle(url: String): LinkHandlingResult {
        val chain = MiddlewareChain(ContextWrapper(null))
        chain.addMiddleware(LoggingMiddleware())
        chain.addMiddleware(SchemeMiddleware(supportedSchemes = setOf("app.sweepy.sweepy")))
        return runBlocking { chain.processLink(TestUri(url), LinkHandlingContext()) }
    }

    @Test
    fun `removes the click ID from the params of the install referrer's link`() {
        val result = handle("app.sweepy.sweepy://promo/42?ref=ad&ck=$ck")

        assertEquals("/promo/42", result.path)
        assertEquals(mapOf("ref" to "ad"), result.params)
    }

    @Test
    fun `removes an uppercase click ID`() {
        assertEquals(emptyMap<String, String>(), handle("app.sweepy.sweepy://promo?ck=${ck.uppercase()}").params)
    }

    @Test
    fun `keeps a ck param that isn't a click ID`() {
        assertEquals(mapOf("ck" to "summer", "ref" to "ad"), handle("app.sweepy.sweepy://promo?ck=summer&ref=ad").params)
    }
}
