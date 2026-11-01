package com.applinks.android.attribution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URLEncoder

class ClickIdsTest {
    private val ck = "9b2e4c1a-7f3d-4e8b-a2c6-5d1f0e9b8a7c"

    @Test
    fun `finds ck as the only query param`() {
        assertEquals(ck, ClickIds.extract("app.sweepy.sweepy://promo/42?ck=$ck"))
    }

    @Test
    fun `finds ck after and before other params`() {
        assertEquals(ck, ClickIds.extract("app.sweepy.sweepy://promo/42?ref=ad&ck=$ck"))
        assertEquals(ck, ClickIds.extract("app.sweepy.sweepy://promo/42?ck=$ck&ref=ad"))
    }

    @Test
    fun `finds ck before a fragment`() {
        assertEquals(ck, ClickIds.extract("https://go.acme.com/promo?ck=$ck#top"))
    }

    @Test
    fun `finds ck after a path with spaces or accented letters`() {
        assertEquals(ck, ClickIds.extract("app.sweepy.sweepy://produits/crème brûlée?ck=$ck"))
    }

    @Test
    fun `finds ck in a percent-encoded referrer`() {
        val referrer = URLEncoder.encode("app.sweepy.sweepy://promo/42?ref=ad&ck=$ck", "UTF-8")

        assertNull(ClickIds.extract(referrer))
        assertEquals(ck, ClickIds.fromReferrer(referrer))
    }

    @Test
    fun `lowercases uppercase hex`() {
        assertEquals(ck, ClickIds.extract("app.sweepy.sweepy://promo?ck=${ck.uppercase()}"))
    }

    @Test
    fun `ignores a ck that isn't a UUID`() {
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo?ck=promo"))
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo?ck=${ck}0"))
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo?ck=${ck.dropLast(1)}"))
        assertNull(ClickIds.fromReferrer("app.sweepy.sweepy://promo?ck=promo"))
    }

    @Test
    fun `ignores ck as the end of another param name`() {
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo?back=$ck"))
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo?ref=ad&back=$ck"))
        assertNull(ClickIds.fromReferrer("app.sweepy.sweepy://promo?back=$ck"))
    }

    @Test
    fun `finds nothing without ck`() {
        assertNull(ClickIds.extract("app.sweepy.sweepy://promo/42?ref=ad"))
        assertNull(ClickIds.extract("app.sweepy.sweepy://ck=$ck"))
        assertNull(ClickIds.fromReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(ClickIds.fromReferrer("100% off"))
        assertNull(ClickIds.fromReferrer(""))
    }

    @Test
    fun `strips ck from the params when it's a click ID`() {
        assertEquals(mapOf("ref" to "ad"), ClickIds.strip(mapOf("ref" to "ad", "ck" to ck)))
        assertEquals(mapOf("ref" to "ad"), ClickIds.strip(mapOf("ref" to "ad", "ck" to ck.uppercase())))
        assertEquals(emptyMap<String, String>(), ClickIds.strip(mapOf("ck" to ck)))
    }

    @Test
    fun `keeps the params when ck isn't a click ID`() {
        val params = mapOf("ref" to "ad", "ck" to "promo")

        assertEquals(params, ClickIds.strip(params))
        assertEquals(mapOf("back" to ck), ClickIds.strip(mapOf("back" to ck)))
    }
}
