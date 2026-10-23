package com.akis.app.domain

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightTest {
    private val story = listOf(
        "Android yeni sürümünde bellek yönetimini tamamen değiştirdi ve uygulamalar artık sınırlandı.",
        "Bellek sınırı cihazın toplam RAM miktarına göre belirleniyor ve uygulamalar bu sınırı aşamıyor.",
        "Geliştiriciler uygulamalarının bellek kullanımını ölçmek için yeni araçlara ihtiyaç duyacak.",
        "Şirket ayrıca kullanıcıların telefonlarını daha uzun süre kullanabilmesi için pil iyileştirmeleri yaptı.",
        "Hava durumu bugün parçalı bulutlu geçecek ve sıcaklık mevsim normallerinin biraz üzerinde seyredecek.",
        "Kullanıcılar yeni sürümü ilk hafta içinde cihazlarına yükleyebilecek ve bellek ayarlarına bakabilecek.",
        "Spor haberlerinde ise hafta sonu oynanacak maçların programı açıklandı ve biletler satışa çıktı.",
    ).joinToString(" ")

    @Test
    fun summaryPicksRepeatedTopicSentencesInOrder() {
        val gist = Summarizer.top(story)
        assertEquals(3, gist.size)
        assertTrue(gist.all { it in story })
        assertEquals(gist, gist.sortedBy { story.indexOf(it) })
        assertTrue(gist.count { "bellek" in it.lowercase() } >= 2)
        assertTrue(gist.none { "Hava durumu" in it })
    }

    @Test
    fun shortTextsGetNoSummary() {
        assertEquals(emptyList<String>(), Summarizer.top("Kısa bir metin. İki cümle."))
        assertEquals(emptyList<String>(), Summarizer.top(""))
    }

    @Test
    fun topicsMatchTitleAndTextTurkishAware() {
        val list = listOf(
            Article("1", "f", "ANDROİD 17 geldi"),
            Article("2", "f", "Başka", text = "metinde android geçiyor"),
            Article("3", "f", "Alakasız"),
        )
        assertEquals(listOf("1", "2"), ReaderRules.byTopic(list, "android").map { it.id })
        assertEquals(listOf("1"), ReaderRules.byTopic(list, "android 17").map { it.id })
        assertEquals(emptyList<String>(), ReaderRules.byTopic(list, "ios"))
    }

    @Test
    fun topicsAreCleanedAndCapped() {
        val s = FeedRules.sanitize(Settings(topics = listOf(" Android ", "a", "ANDROID") + (1..20).map { "konu$it" }))
        assertEquals("android", s.topics.first())
        assertEquals(Limits.MAX_TOPICS, s.topics.size)
        assertTrue(s.topics.none { it.length < 2 })
    }

    @Test
    fun dayBucketsFollowTheCalendar() {
        val today = LocalDate.of(2026, 10, 23)
        fun at(day: LocalDate) = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("Bugün", ReaderRules.dayBucket(at(today), today, ZoneOffset.UTC))
        assertEquals("Bugün", ReaderRules.dayBucket(at(today.plusDays(1)), today, ZoneOffset.UTC))
        assertEquals("Dün", ReaderRules.dayBucket(at(today.minusDays(1)), today, ZoneOffset.UTC))
        assertEquals("Bu hafta", ReaderRules.dayBucket(at(today.minusDays(5)), today, ZoneOffset.UTC))
        assertEquals("Daha eski", ReaderRules.dayBucket(at(today.minusDays(9)), today, ZoneOffset.UTC))
    }
}

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 23) // a Friday

    @Test
    fun weeklyTotalsAreOldestFirstAndCurrentWeekLast() {
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
        val log = listOf(
            ReadEntry(monday + 1, 10, "a"),
            ReadEntry(monday - 1, 20, "a"),
            ReadEntry(monday - 8, 30, "a"),
            ReadEntry(monday - 30, 99, "a"),
        )
        assertEquals(listOf(0, 30, 20, 10), ReaderRules.weeklyTotals(log, today))
        assertEquals(listOf(0, 0, 0, 0), ReaderRules.weeklyTotals(emptyList(), today))
    }

    private fun story(id: String, feed: String, ageDays: Long) =
        Article(id, feed, id, publishedMillis = today.minusDays(ageDays).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

    @Test
    fun pinnedSourcesLeadUnderTheirOwnHeader() {
        val list = listOf(story("a", "x", 0), story("b", "pin", 2), story("c", "x", 1), story("d", "pin", 0))
        val sections = Sections.build(list, 2, dated = true, pinnedFeeds = setOf("pin"), today = today)
        assertEquals(listOf("Öncelikli", "Bugün", "Dün"), sections.map { it.label })
        assertEquals(listOf("b", "d"), sections[0].rows.flatten().map { it.id })
        assertEquals(listOf("a"), sections[1].rows.flatten().map { it.id })
    }

    @Test
    fun undatedListIsOneRunOfRows() {
        val list = (1..5).map { story("s$it", "x", 0) }
        val sections = Sections.build(list, 2, dated = false, pinnedFeeds = emptySet(), today = today)
        assertEquals(1, sections.size)
        assertEquals(null, sections[0].label)
        assertEquals(listOf(2, 2, 1), sections[0].rows.map { it.size })
        assertEquals(emptyList<Section>(), Sections.build(emptyList(), 2, dated = false, pinnedFeeds = emptySet(), today = today))
    }

    @Test
    fun pinFlagStaysOnTheFeedOnly() {
        val data = AppData(feeds = listOf(Feed("f", "https://f.com"), Feed("g", "https://g.com")))
        val pinned = FeedRules.setPinned(data, "f", true)
        assertEquals(listOf(true, false), pinned.feeds.map { it.pinned })
    }
}

class RateTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun postsPerWeekCountsOnlyTheLastFourWeeks() {
        val list = (1..12).map { Article("a$it", "busy", "t", publishedMillis = now - it * day) } +
            listOf(Article("old", "busy", "t", publishedMillis = now - 40 * day)) +
            listOf(Article("s1", "slow", "t", publishedMillis = now - 2 * day))
        val rates = ReaderRules.postsPerWeek(list, now)
        assertEquals(3, rates["busy"])
        assertEquals(0, rates["slow"])
        assertEquals(null, rates["none"])
    }
}

class ProgressTest {
    @Test
    fun barStartsAtZeroAndEndsAtOne() {
        // 12 items, 6 on screen: the old formula said 50 % before any scrolling.
        assertEquals(0f, ReaderRules.scrollProgress(0, 0, 400, total = 12, visible = 6), 0.001f)
        assertEquals(0.5f, ReaderRules.scrollProgress(3, 0, 400, total = 12, visible = 6), 0.001f)
        assertEquals(1f, ReaderRules.scrollProgress(6, 0, 400, total = 12, visible = 6), 0.001f)
        assertEquals(0.25f, ReaderRules.scrollProgress(1, 200, 400, total = 12, visible = 6), 0.001f)
    }

    @Test
    fun smallStoriesAndEmptyListsAreSafe() {
        assertEquals(1f, ReaderRules.scrollProgress(0, 0, 400, total = 5, visible = 5), 0f)
        assertEquals(0f, ReaderRules.scrollProgress(0, 0, 0, total = 0, visible = 0), 0f)
        assertEquals(1f, ReaderRules.scrollProgress(99, 0, 400, total = 12, visible = 6), 0f)
    }

    @Test
    fun resumeOffersTheNewestUnfinishedStoryOfTheLastDays() {
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        val list = listOf(
            Article("a", "f", "a", resume = 6, openedMillis = now - day),
            Article("b", "f", "b", resume = 9, openedMillis = now - 2 * day),
            Article("old", "f", "old", resume = 9, openedMillis = now - 10 * day),
            Article("done", "f", "done", resume = 0, openedMillis = now),
        )
        assertEquals("a", ReaderRules.resumeCandidate(list, now)?.id)
        assertEquals(null, ReaderRules.resumeCandidate(list.drop(2), now))
    }

    @Test
    fun tintsStayInRange() {
        val data = AppData(feeds = listOf(Feed("f", "https://f.com")))
        assertEquals(Limits.TINTS, FeedRules.setTint(data, "f", 99).feeds[0].tint)
        assertEquals(0, FeedRules.setTint(data, "f", -4).feeds[0].tint)
        assertEquals(3, FeedRules.setTint(data, "f", 3).feeds[0].tint)
    }
}

class LinksSnoozeTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun linksKeepOnlyUsefulHttpsOnes() {
        val html = """<p>Bkz. <a href="https://example.com/a?x=1&amp;y=2">Ayrıntılar <b>burada</b></a>, ve
            <a href="http://insecure.com/x">güvensiz</a> <a href="https://example.com/a?x=1&y=2">aynı yine</a>
            <a href="https://own.com/story">kendisi</a> <a href="mailto:a@b.c">posta</a>
            <a href="https://img.com/p"><img src="x.png"></a> <a href="/göreli">göreli</a>
            <a href="https://b.com">B sitesi</a></p>"""
        val links = Links.extract(html, "https://own.com/story")
        assertEquals(listOf("https://example.com/a?x=1&y=2", "https://b.com"), links.map { it.url })
        assertEquals("Ayrıntılar burada", links[0].label)
    }

    @Test
    fun linksAreCappedAndParsedFromFeeds() {
        val many = (1..20).joinToString(" ") { "<a href=\"https://s$it.com\">site $it</a>" }
        assertEquals(Limits.MAX_LINKS, Links.extract(many, "").size)
        val xml = "<rss><channel><title>t</title><item><title>x</title><link>https://e.com/1</link>" +
            "<description><![CDATA[<p>oku <a href=\"https://kaynak.com/h\">haber</a></p>]]></description></item></channel></rss>"
        val feed = FeedParser.parse(org.kxml2.io.KXmlParser().apply { setInput(java.io.ByteArrayInputStream(xml.toByteArray()), null) })!!
        assertEquals(listOf(Link("haber", "https://kaynak.com/h")), feed.items[0].links)
        val merged = FeedRules.merge(emptyList(), "f", feed.items, 1_000L).single()
        assertEquals(1, merged.links.size)
    }

    @Test
    fun snoozeMutesForDaysAndLiftsAgain() {
        val data = AppData(feeds = listOf(Feed("f", "https://f.com")))
        val muted = FeedRules.snooze(data, "f", 7, 10 * day)
        assertEquals(17 * day, muted.feeds[0].mutedUntil)
        assertEquals(0L, FeedRules.snooze(muted, "f", 0, 11 * day).feeds[0].mutedUntil)
    }

    @Test
    fun laterCleanupOnlyExpiresOldQueuedStoriesWhenOn() {
        val now = 100 * day
        val list = listOf(
            Article("old", "f", "old", later = true, laterAt = now - 40 * day),
            Article("new", "f", "new", later = true, laterAt = now - 5 * day),
            Article("legacy", "f", "legacy", later = true, laterAt = 0),
        )
        val on = AppData(articles = list, settings = Settings(laterCleanup = true))
        val cleaned = ReaderRules.cleanLater(on, now).articles
        assertEquals(listOf(false, true, true), cleaned.map { it.later })
        assertEquals(3, cleaned.size)
        assertEquals(on.copy(settings = Settings()), ReaderRules.cleanLater(on.copy(settings = Settings()), now))
    }

    @Test
    fun queuedAtIsSetAndKeptAcrossRefresh() {
        val data = AppData(articles = listOf(Article(FeedRules.articleId("f", "k"), "f", "t")))
        val queued = FeedRules.setLater(data, data.articles[0].id, true, now = 777L)
        assertEquals(777L, queued.articles[0].laterAt)
        val item = ParsedItem("k", "t", "https://e.com", "x", null)
        assertEquals(777L, FeedRules.merge(queued.articles, "f", listOf(item), 1L).single().laterAt)
        assertEquals(0L, FeedRules.setLater(queued, data.articles[0].id, false, now = 9L).articles[0].laterAt)
    }
}

class CatalogOpmlTest {
    private fun parser(xml: String) = org.kxml2.io.KXmlParser().apply { setInput(java.io.ByteArrayInputStream(xml.toByteArray()), null) }

    @Test
    fun catalogIsCleanAndUnique() {
        val ids = Catalog.feeds.map { FeedRules.feedId(it.url) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Catalog.feeds.all { FeedRules.normalizeUrl(it.url) == it.url || FeedRules.normalizeUrl(it.url) != null })
        assertTrue(Catalog.feeds.all { it.url.startsWith("https://") && it.name.isNotBlank() })
        assertTrue(Catalog.byCategory.size >= 8)
        assertEquals(Catalog.feeds.count { it.category == "Haber" }, Catalog.entries("Haber").size)
    }

    @Test
    fun opmlKeepsFoldersBothWays() {
        val feeds = listOf(
            Feed("a", "https://a.com/f", "A"),
            Feed("b", "https://b.com/f", "B", group = "Haber"),
            Feed("c", "https://c.com/f", "C", group = "Haber"),
            Feed("d", "https://d.com/f", "D", group = "Teknoloji"),
        )
        val back = FeedRules.parseOpmlEntries(parser(FeedRules.toOpml(feeds)))
        assertEquals(
            listOf("A" to "", "B" to "Haber", "C" to "Haber", "D" to "Teknoloji"),
            back.map { it.title to it.group },
        )
        assertEquals(feeds.map { "https://" + it.url.removePrefix("https://") }, back.map { it.url })
    }

    @Test
    fun foreignOpmlWithNestedFoldersIsRead() {
        val xml = """<opml><body>
            <outline text="Teknoloji"><outline text="Alt"><outline type="rss" text="Derin" xmlUrl="https://d.com/f"/></outline>
            <outline type="rss" text="Üst" xmlUrl="https://u.com/f"/></outline>
            <outline type="rss" text="Köksüz" xmlUrl="https://k.com/f"/></body></opml>"""
        val entries = FeedRules.parseOpmlEntries(parser(xml))
        assertEquals(listOf("Alt", "Teknoloji", ""), entries.map { it.group })
        val (data, added) = FeedRules.addEntries(AppData(), entries)
        assertEquals(3, added)
        assertEquals(listOf("Alt", "Teknoloji", ""), data.feeds.map { it.group })
    }

    @Test
    fun sameStoryKeepsEveryOtherSourcesVersion() {
        val day = 24 * 60 * 60 * 1000L
        val list = listOf(
            Article("1", "f1", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı", publishedMillis = 10 * day),
            Article("2", "f2", "Android sürümü kullanıcılara dağıtılmaya başladı yeni", publishedMillis = 10 * day - 1000),
            Article("3", "f3", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı bugün", publishedMillis = 10 * day - 2000),
        )
        val (shown, others) = Similar.groups(list)
        assertEquals(listOf("1"), shown.map { it.id })
        assertEquals(listOf("2", "3"), others.getValue("1").map { it.id })
        assertEquals(mapOf("1" to 2), Similar.collapse(list).second)
    }
}

class SummaryDuplicateTest {
    @Test
    fun repeatedSentencesAppearOnceInTheSummary() {
        val line = "Bu cümle yazının her paragrafında aynen tekrar ediyor ve özette bir kez görünmeli."
        val other = (1..4).joinToString(" ") { "Başka bir konu hakkında uzun ve farklı bir $it numaralı cümle yazıldı." }
        val text = (1..8).joinToString(" ") { line } + " " + other
        val gist = Summarizer.top(text)
        assertEquals(gist.size, gist.toSet().size)
    }
}
