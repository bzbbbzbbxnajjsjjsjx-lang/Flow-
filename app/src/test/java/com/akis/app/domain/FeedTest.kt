package com.akis.app.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class FeedTest {
    private fun parser(xml: String): XmlPullParser =
        KXmlParser().apply { setInput(ByteArrayInputStream(xml.toByteArray()), null) }

    private fun parse(xml: String) = FeedParser.parse(parser(xml))

    private val rss = """<?xml version="1.0"?><rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel><title>Örnek Blog</title><link>https://example.com/</link>
        <item><title>Birinci</title><link>https://example.com/1</link><guid>g1</guid>
        <pubDate>Wed, 21 Oct 2026 07:28:00 GMT</pubDate><description><![CDATA[<p>Merhaba <b>dünya</b></p><script>alert(1)</script>]]></description></item>
        <item><title>İkinci</title><link>https://example.com/2</link><description>Kısa</description></item>
        </channel></rss>"""

    @Test
    fun parsesRssAndStripsHtml() {
        val feed = parse(rss)!!
        assertEquals("Örnek Blog", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        assertEquals(2, feed.items.size)
        assertEquals("Merhaba dünya", feed.items[0].text)
        assertEquals("g1", feed.items[0].key)
        assertEquals(1_792_567_680_000L, feed.items[0].publishedMillis)
        assertNull(feed.items[1].publishedMillis)
        assertEquals("https://example.com/2", feed.items[1].key)
    }

    @Test
    fun parsesAtomLinksAndContent() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Atom Akışı</title>
            <link rel="self" href="https://a.example/feed"/><link href="https://a.example/"/>
            <entry><title>Giriş</title><id>tag:a,1</id><link rel="alternate" href="https://a.example/1"/>
            <link rel="enclosure" href="https://a.example/x.mp3"/><updated>2026-10-21T07:28:00Z</updated>
            <content type="html">&lt;p&gt;Metin&lt;/p&gt;</content></entry></feed>"""
        val feed = parse(atom)!!
        assertEquals("Atom Akışı", feed.title)
        assertEquals("https://a.example/", feed.siteUrl)
        val item = feed.items.single()
        assertEquals("https://a.example/1", item.link)
        assertEquals("Metin", item.text)
        assertEquals(1_792_567_680_000L, item.publishedMillis)
    }

    @Test
    fun rejectsNonFeedsAndBrokenXml() {
        assertNull(parse("<html><body>hi</body></html>"))
        assertNull(parse("<rss><channel><item><title>x</title>"))
        assertNull(parse("not xml at all"))
    }

    @Test
    fun capsItemsPerFetch() {
        val items = (1..150).joinToString("") { "<item><title>t$it</title><guid>$it</guid></item>" }
        assertEquals(Limits.MAX_ITEMS_PER_FETCH, parse("<rss><channel><title>x</title>$items</channel></rss>")!!.items.size)
    }

    @Test
    fun htmlToText() {
        assertEquals("a & b < c", Html.toText("a &amp; b &lt; c"))
        assertEquals("Satır 1\nSatır 2", Html.toText("Satır 1<br>Satır 2"))
        assertEquals("• bir\n• iki", Html.toText("<ul><li>bir</li><li>iki</li></ul>"))
        assertEquals("é€", Html.toText("&#233;&#x20AC;"))
        assertEquals("&bilinmeyen;", Html.toText("&bilinmeyen;"))
        assertEquals("x", Html.toText("<style>p{}</style><!-- gizli -->x"))
        assertEquals("abc…", Html.toText("abcdefgh", limit = 4))
    }

    @Test
    fun datesParseBothFormats() {
        assertEquals(1_792_567_680_000L, FeedDates.parse("Wed, 21 Oct 2026 07:28:00 +0000"))
        assertEquals(1_792_567_680_000L, FeedDates.parse("2026-10-21T10:28:00+03:00"))
        assertNull(FeedDates.parse("dün akşam"))
        assertNull(FeedDates.parse(""))
    }

    @Test
    fun urlsMustBeHttps() {
        assertEquals("https://example.com/feed", FeedRules.normalizeUrl(" example.com/feed "))
        assertEquals("https://example.com/rss?x=1", FeedRules.normalizeUrl("HTTPS://Example.com/rss?x=1#top"))
        assertNull(FeedRules.normalizeUrl("http://example.com/feed"))
        assertNull(FeedRules.normalizeUrl("ftp://example.com/feed"))
        assertNull(FeedRules.normalizeUrl("https://user:pw@example.com/"))
        assertNull(FeedRules.normalizeUrl("https://"))
        assertNull(FeedRules.normalizeUrl(""))
    }

    private val feed = Feed(FeedRules.feedId("https://a.example/f"), "https://a.example/f")

    private fun item(key: String, millis: Long?) = ParsedItem(key, "T$key", "https://a.example/$key", "metin", millis)

    @Test
    fun mergeKeepsFlagsAndDeduplicates() {
        val first = FeedRules.merge(emptyList(), feed.id, listOf(item("1", 100), item("2", 200)), now = 1_000)
        val marked = first.map { if (it.title == "T1") it.copy(read = true, starred = true) else it }
        val second = FeedRules.merge(marked, feed.id, listOf(item("1", 999), item("3", 300)), now = 1_000)
        assertEquals(3, second.size)
        assertEquals(listOf("T3", "T2", "T1"), second.map { it.title })
        val one = second.first { it.title == "T1" }
        assertTrue(one.read && one.starred)
        assertEquals(100L, one.publishedMillis)
    }

    @Test
    fun futureAndMissingDatesAreClampedToNow() {
        val merged = FeedRules.merge(emptyList(), feed.id, listOf(item("1", 9_999_999), item("2", null)), now = 5_000)
        assertTrue(merged.all { it.publishedMillis == 5_000L })
    }

    @Test
    fun capsDropOldestButKeepStarred() {
        val many = (1..300).map { item("$it", it.toLong()) }
        var merged = FeedRules.merge(emptyList(), feed.id, many.take(100), now = 1_000_000)
        merged = FeedRules.merge(merged, feed.id, many.drop(100).take(100), now = 1_000_000)
        merged = FeedRules.merge(merged, feed.id, many.drop(200), now = 1_000_000)
        assertEquals(Limits.MAX_STORED_PER_FEED, merged.size)
        assertEquals("T300", merged.first().title)
        val withStar = merged.map { if (it.title == "T101") it.copy(starred = true) else it }
        val again = FeedRules.merge(withStar, feed.id, listOf(item("301", 301)), now = 1_000_000)
        assertTrue(again.any { it.title == "T101" })
    }

    @Test
    fun applyFetchUpdatesFeedAndHandlesFailures() {
        val data = AppData(feeds = listOf(feed))
        val ok = FeedRules.applyFetch(
            data, feed.id, FetchOutcome.Success(ParsedFeed("Başlık", "https://a.example/", listOf(item("1", 10))), "e1", "lm"), now = 50,
        )
        assertEquals("Başlık", ok.feeds.single().title)
        assertEquals("e1", ok.feeds.single().etag)
        assertEquals(1, ok.articles.size)
        val failed = FeedRules.applyFetch(ok, feed.id, FetchOutcome.Failure("Zaman aşımı"), now = 60)
        assertEquals("Zaman aşımı", failed.feeds.single().error)
        assertEquals(1, failed.articles.size)
        val same = FeedRules.applyFetch(failed, feed.id, FetchOutcome.NotModified, now = 70)
        assertEquals("", same.feeds.single().error)
        assertEquals(70L, same.feeds.single().lastFetchedMillis)
        assertEquals(data, FeedRules.applyFetch(data, "yok", FetchOutcome.NotModified, now = 1))
    }

    @Test
    fun removeFeedDropsItsArticles() {
        val merged = FeedRules.merge(emptyList(), feed.id, listOf(item("1", 1)), now = 10)
        val data = FeedRules.removeFeed(AppData(listOf(feed), merged), feed.id)
        assertTrue(data.feeds.isEmpty() && data.articles.isEmpty())
    }

    @Test
    fun filtersAndCounts() {
        val a = FeedRules.merge(emptyList(), feed.id, listOf(item("1", 1), item("2", 2)), now = 10)
        val marked = a.map { if (it.title == "T1") it.copy(read = true) else it.copy(starred = true) }
        assertEquals(2, FeedRules.filter(marked, Filter.ALL).size)
        assertEquals("T2", FeedRules.filter(marked, Filter.UNREAD).single().title)
        assertEquals("T2", FeedRules.filter(marked, Filter.STARRED).single().title)
        assertEquals(1, FeedRules.unreadCount(marked, feed.id))
    }

    @Test
    fun relativeTimeInTurkish() {
        val now = 10 * 86_400_000L
        assertEquals("az önce", FeedRules.relativeTime(now - 30_000, now))
        assertEquals("5 dk önce", FeedRules.relativeTime(now - 5 * 60_000, now))
        assertEquals("3 sa önce", FeedRules.relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("dün", FeedRules.relativeTime(now - 30 * 3_600_000L, now))
        assertEquals("4 gün önce", FeedRules.relativeTime(now - 4 * 86_400_000L, now))
        assertEquals("az önce", FeedRules.relativeTime(now + 1_000, now))
    }

    @Test
    fun opmlRoundTripAndFiltering() {
        val feeds = listOf(feed.copy(title = "A & B"), Feed("x", "https://b.example/rss", "Bee"))
        val opml = FeedRules.toOpml(feeds)
        val back = FeedRules.parseOpml(parser(opml))
        assertEquals(listOf("A & B" to "https://a.example/f", "Bee" to "https://b.example/rss"), back)
        val mixed = """<opml><body><outline text="http" xmlUrl="http://x.example/f"/>
            <outline text="ok" xmlUrl="https://ok.example/f"/><outline text="again" xmlUrl="https://ok.example/f"/></body></opml>"""
        assertEquals(listOf("ok" to "https://ok.example/f"), FeedRules.parseOpml(parser(mixed)))
        assertTrue(FeedRules.parseOpml(parser("<opml><body><outline")).isEmpty())
    }

    @Test
    fun badgeLetter() {
        assertEquals("Ö", FeedRules.badgeLetter(Feed("1", "https://x.example", "özel")))
        assertEquals("E", FeedRules.badgeLetter(Feed("1", "https://www.example.com/rss")))
        assertEquals("?", FeedRules.badgeLetter(null))
    }
}
