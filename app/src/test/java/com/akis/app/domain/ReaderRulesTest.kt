package com.akis.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRulesTest {
    private fun article(id: String, title: String = "T", text: String = "", feed: String = "f", at: Long = 0L, read: Boolean = false) =
        Article(id, feed, title, text = text, publishedMillis = at, read = read)

    @Test
    fun discoveryFindsAdvertisedFeeds() {
        val html = """<html><head>
            <link rel="stylesheet" href="/a.css">
            <link rel="alternate" type="application/rss+xml" title="Ana &amp; Akış" href="/feed.xml">
            <link href='http://other.example/atom' type='application/atom+xml' rel='alternate' title=''>
            <link rel="alternate" type="application/rss+xml" href="/feed.xml">
            <link rel="alternate" type="text/html" href="/tr">
            <link rel="alternate" type="application/json" href="/feed.json">
            </head></html>"""
        val found = FeedDiscovery.find(html, "https://site.example/blog/")
        assertEquals(
            listOf(
                DiscoveredFeed("Ana & Akış", "https://site.example/feed.xml"),
                DiscoveredFeed("other.example", "https://other.example/atom"),
            ),
            found,
        )
        assertTrue(FeedDiscovery.find("<html><body>yok</body></html>", "https://site.example/").isEmpty())
        assertTrue(FeedDiscovery.find("<link rel=alternate>", "::bozuk::").isEmpty())
    }

    @Test
    fun originAndCommonPaths() {
        assertEquals("https://a.example", FeedDiscovery.origin("https://a.example/x/y?q=1"))
        assertEquals("https://a.example:8443", FeedDiscovery.origin("https://a.example:8443/p"))
        assertNull(FeedDiscovery.origin("http://a.example/x"))
        assertNull(FeedDiscovery.origin("::bozuk::"))
        assertEquals(4, FeedDiscovery.commonPaths.size)
    }

    @Test
    fun discoveryIsBounded() {
        val many = (1..20).joinToString("") { """<link rel="alternate" type="application/rss+xml" href="/f$it.xml">""" }
        assertEquals(Limits.MAX_FOUND_FEEDS, FeedDiscovery.find(many, "https://s.example/").size)
    }

    @Test
    fun mutedWordsAreTurkishCaseInsensitive() {
        val words = listOf("burç", "ısparta")
        val list = listOf(article("1", "BURÇ yorumları"), article("2", text = "ISPARTA gezisi"), article("3", "Teknoloji"))
        assertEquals(listOf("3"), ReaderRules.withoutMuted(list, words).map { it.id })
        assertSame(list, ReaderRules.withoutMuted(list, emptyList()))
        assertEquals("ısparta", ReaderRules.cleanWord("  ISPARTA "))
        assertNull(ReaderRules.cleanWord("a"))
        assertNull(ReaderRules.cleanWord("x".repeat(41)))
        val clean = FeedRules.sanitize(Settings(mutedWords = listOf("Burç", "burç", "b", "Magazin")))
        assertEquals(listOf("burç", "magazin"), clean.mutedWords)
    }

    @Test
    fun searchLooksInTitleSummaryAndFullText() {
        val list = listOf(article("1", "Steam Linux"), article("2", text = "özet"), article("3").copy(fullText = "Gizli ÇAY tarifi"))
        assertEquals(listOf("1"), ReaderRules.search(list, "linux").map { it.id })
        assertEquals(listOf("2"), ReaderRules.search(list, " ÖZET ").map { it.id })
        assertEquals(listOf("3"), ReaderRules.search(list, "çay").map { it.id })
        assertEquals(3, ReaderRules.search(list, "  ").size)
        assertTrue(ReaderRules.search(list, "yok").isEmpty())
    }

    @Test
    fun groupsAreCleanedAndBounded() {
        val feeds = listOf(Feed("1", "https://a.example/f", group = "Haber"), Feed("2", "https://b.example/f"))
        val data = AppData(feeds = feeds)
        assertEquals(listOf("Haber"), ReaderRules.groups(feeds))
        assertEquals("Teknoloji", ReaderRules.setGroup(data, "2", "  Teknoloji  ").feeds[1].group)
        assertEquals("", ReaderRules.setGroup(data, "1", " ").feeds[0].group)
        assertEquals(Limits.MAX_GROUP, ReaderRules.cleanGroup("g".repeat(60)).length)
        val grouped = (1..Limits.MAX_GROUPS).map { Feed("$it", "https://x$it.example/f", group = "G$it") }
        val full = AppData(feeds = grouped + Feed("z", "https://z.example/f"))
        assertSame(full, ReaderRules.setGroup(full, "z", "Yeni"))
        assertEquals("G3", ReaderRules.setGroup(full, "z", "G3").feeds.last().group)
        val articles = listOf(article("a", feed = "1"), article("b", feed = "2"))
        assertEquals(listOf("a"), ReaderRules.inGroup(articles, feeds, "Haber").map { it.id })
    }

    @Test
    fun autoReadClosesOnlyOldUnreadStories() {
        val day = 86_400_000L
        val now = 100 * day
        val data = AppData(
            articles = listOf(article("old", at = now - 8 * day), article("new", at = now - 2 * day), article("oldRead", at = 0, read = true)),
            settings = Settings(autoRead = true),
        )
        val result = ReaderRules.autoRead(data, now)
        assertEquals(listOf(true, false, true), result.articles.map { it.read })
        assertSame(result, ReaderRules.autoRead(result, now))
        val off = data.copy(settings = Settings(autoRead = false))
        assertSame(off, ReaderRules.autoRead(off, now))
    }

    @Test
    fun readingIsLoggedOnceAndAgesOut() {
        val today = LocalDate.of(2026, 10, 22)
        val a = article("1", text = "kelime ".repeat(450), feed = "f1")
        val once = ReaderRules.logRead(AppData(), a, today)
        assertEquals(listOf(ReadEntry(today.toEpochDay(), 3, "f1")), once.readLog)
        assertSame(once, ReaderRules.logRead(once, a.copy(read = true), today))
        val old = AppData(readLog = listOf(ReadEntry(today.toEpochDay() - 61, 5, "f1")))
        assertEquals(1, ReaderRules.logRead(old, a, today).readLog.size)
        val short = ReaderRules.logRead(AppData(), article("2", text = ""), today)
        assertEquals(1, short.readLog.single().minutes)
    }

    @Test
    fun weekSummaryCountsTheCurrentWeekFromMonday() {
        val thursday = LocalDate.of(2026, 10, 22)
        val monday = thursday.minusDays(3).toEpochDay()
        val log = listOf(
            ReadEntry(monday, 10, "a"), ReadEntry(monday, 5, "b"), ReadEntry(monday + 3, 20, "b"),
            ReadEntry(monday - 1, 99, "z"), ReadEntry(monday + 7, 99, "z"),
        )
        val s = ReaderRules.weekSummary(log, thursday)
        assertEquals(listOf(15, 0, 0, 20, 0, 0, 0), s.perDay)
        assertEquals(35, s.totalMinutes)
        assertEquals(3, s.articles)
        assertEquals("b", s.topFeedId)
        assertEquals(3, s.todayIndex)
        val empty = ReaderRules.weekSummary(emptyList(), thursday)
        assertEquals(0, empty.totalMinutes)
        assertNull(empty.topFeedId)
    }

    @Test
    fun sharedTextYieldsAnHttpsAddress() {
        assertEquals("https://a.example/x?y=1", ReaderRules.sharedUrl("Bak: https://a.example/x?y=1 harika!"))
        assertEquals("https://a.example/p", ReaderRules.sharedUrl("(http://a.example/p)."))
        assertNull(ReaderRules.sharedUrl("adres yok"))
        assertNull(ReaderRules.sharedUrl("ftp://a.example/x"))
    }

    @Test
    fun storedEntitiesAreRepairedOnce() {
        val broken = article("1", "Steam &uuml;st", "s&uuml;r&uuml;m &amp;amp; AT&T").copy(fullText = "&ouml;nemli &ccedil;al&imath;&scedil;ma")
        val fixed = ReaderRules.repairEntities(AppData(articles = listOf(broken)))
        val a = fixed.articles.single()
        assertEquals("Steam üst", a.title)
        assertEquals("sürüm &amp; AT&T", a.text)
        assertEquals("önemli çalışma", a.fullText)
        assertEquals(1, fixed.dataVersion)
        assertSame(fixed, ReaderRules.repairEntities(fixed))
    }

    @Test
    fun newSettingsAreClamped() {
        assertEquals(2, FeedRules.sanitize(Settings(theme = 9)).theme)
        assertEquals(0, FeedRules.sanitize(Settings(theme = -1)).theme)
        val many = FeedRules.sanitize(Settings(mutedWords = (1..80).map { "kelime$it" }))
        assertEquals(Limits.MAX_MUTED, many.mutedWords.size)
    }
}
