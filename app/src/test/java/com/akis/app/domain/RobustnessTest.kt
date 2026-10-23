package com.akis.app.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class RobustnessTest {
    private fun parse(xml: String) =
        FeedParser.parse(KXmlParser().apply { setInput(ByteArrayInputStream(xml.toByteArray()), null) })

    @Test
    fun garbageFeedsGiveNullInsteadOfCrashing() {
        listOf("", "not xml at all", "<rss><channel><item>", "<html><body>hi</body></html>", "\u0000\u0001<<<>>>").forEach {
            runCatching { parse(it) }.onSuccess { feed -> assertTrue(feed == null || feed.items.isEmpty()) }
        }
    }

    @Test
    fun hugeFeedIsBoundedByMerge() {
        val items = (1..5_000).map { ParsedItem("k$it", "t$it", "https://e.com/$it", "x", null) }
        val merged = FeedRules.merge(emptyList(), "f", items, 1_000L)
        assertTrue(merged.size <= Limits.MAX_STORED_PER_FEED)
    }

    @Test
    fun hugeAndHostileHtmlStaysBounded() {
        val html = "<p>" + "a".repeat(2_000_000) + "</p><script>x</script>" + "<div>".repeat(50_000)
        val text = Html.toText(html)
        assertTrue(text.length <= Limits.MAX_TEXT)
        assertTrue("<" !in text)
    }

    @Test
    fun unknownEntitiesAndBadNumbersSurvive() {
        assertEquals("&nope; x", Html.unescape("&nope; x"))
        assertEquals("&#99999999;", Html.unescape("&#99999999;"))
        assertEquals("&#0;", Html.unescape("&#0;"))
    }

    @Test
    fun badAddressesAreRejected() {
        listOf("", "   ", "http://example.com/feed", "javascript:alert(1)", "file:///etc/passwd", "ftp://x.com/a").forEach {
            assertNull(it, FeedRules.normalizeUrl(it)?.takeIf { url -> !url.startsWith("https://") })
        }
        assertTrue(FeedRules.normalizeUrl("https://" + "a".repeat(5_000) + ".com")?.length ?: 0 <= Limits.MAX_URL)
    }

    @Test
    fun settingsAreClampedAndIdsStable() {
        val s = FeedRules.sanitize(Settings(textStep = 99, theme = -4, dailyLimit = 7, breakMinutes = 1000))
        assertTrue(s.textStep in 0..3 && s.theme in 0..2 && s.dailyLimit in listOf(0, 15, 30, 50) && s.breakMinutes in listOf(0, 15, 30))
        assertEquals(FeedRules.feedId("https://A.com/x"), FeedRules.feedId("https://a.com/x"))
    }

    @Test
    fun removingFeedDuringFetchDoesNotResurrectIt() {
        val data = FeedRules.removeFeed(AppData(feeds = listOf(Feed("a", "https://a.com"))), "a")
        val outcome = FetchOutcome.Failure("x")
        assertTrue(FeedRules.applyFetch(data, "a", outcome, 1L).feeds.isEmpty())
    }
}
