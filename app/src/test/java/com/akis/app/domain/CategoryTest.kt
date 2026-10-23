package com.akis.app.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class CategoryTest {
    private val hour = 3_600_000L

    private fun a(id: String, feed: String, title: String, ageH: Long = 0, read: Boolean = false, cats: List<String> = emptyList()) =
        Article(id, feed, title, publishedMillis = 1_000 * hour - ageH * hour, read = read, categories = cats)

    @Test
    fun labelsShareOneVocabulary() {
        assertEquals("teknoloji", Categories.normalize("Tech"))
        assertEquals("teknoloji", Categories.normalize(" Technology "))
        assertEquals("oyun", Categories.normalize("GAMING"))
        assertEquals("yapay zekâ", Categories.normalize("AI"))
        assertEquals("özel dosya", Categories.normalize("Özel Dosya"))
        assertNull(Categories.normalize("Uncategorized"))
        assertNull(Categories.normalize("   "))
        assertNull(Categories.normalize("x".repeat(80)))
        assertEquals("İstanbul".lowercase(FeedRules.TR), Categories.normalize("İstanbul"))
        assertEquals("Teknoloji", Categories.display("teknoloji"))
        assertEquals("İş", Categories.display("iş"))
    }

    @Test
    fun parserReadsRssAndAtomCategories() {
        fun parse(xml: String) = FeedParser.parse(KXmlParser().apply { setInput(ByteArrayInputStream(xml.toByteArray()), null) })!!
        val rss = parse(
            "<rss><channel><title>t</title><item><title>x</title><link>https://e.com/1</link>" +
                "<category>Tech</category><category><![CDATA[Android]]></category><category>Tech</category><category>Oyun</category>" +
                "<category>Spor</category></item></channel></rss>",
        )
        assertEquals(listOf("teknoloji", "mobil", "oyun"), rss.items[0].categories)
        val atom = parse(
            "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>t</title><entry><title>y</title><id>1</id>" +
                "<link href=\"https://e.com/2\"/><category term=\"Science\"/></entry></feed>",
        )
        assertEquals(listOf("bilim"), atom.items[0].categories)
    }

    @Test
    fun chipsAndFilter() {
        val list = listOf(a("1", "f", "x", cats = listOf("teknoloji", "oyun")), a("2", "f", "y", cats = listOf("teknoloji")), a("3", "f", "z"))
        assertEquals(listOf("teknoloji", "oyun"), Categories.top(list))
        assertEquals(listOf("1", "2"), Categories.filter(list, "teknoloji").map { it.id })
        assertEquals(3, Categories.filter(list, "").size)
    }

    @Test
    fun sameStoryFromTwoSourcesIsOneTile() {
        val list = listOf(
            a("1", "f1", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı"),
            a("2", "f2", "Android sürümü kullanıcılara dağıtılmaya başladı yeni", ageH = 2),
            a("3", "f3", "Bambaşka bir konu hakkında uzun bir başlık yazısı"),
            a("4", "f1", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı", ageH = 1),
        )
        val (shown, extra) = Similar.collapse(list)
        assertEquals(listOf("1", "3", "4"), shown.map { it.id })
        assertEquals(mapOf("1" to 1), extra)
    }

    @Test
    fun similarTitlesFarApartOrShortStayApart() {
        val far = listOf(
            a("1", "f1", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı"),
            a("2", "f2", "Yeni Android sürümü kullanıcılara dağıtılmaya başladı", ageH = 24 * 5),
            a("3", "f1", "Kısa başlık"),
            a("4", "f2", "Kısa başlık"),
        )
        assertEquals(4, Similar.collapse(far).first.size)
    }

    @Test
    fun refreshKeepsQueueProgressNoteAndLabels() {
        val old = Article("x", "f", "t", later = true, resume = 7, note = "not", categories = listOf("oyun"), read = true, starred = true)
        val id = FeedRules.articleId("f", "k")
        val stored = old.copy(id = id)
        val merged = FeedRules.merge(listOf(stored), "f", listOf(ParsedItem("k", "t", "https://e.com/1", "x", null)), 10_000L).single()
        assertTrue(merged.later && merged.read && merged.starred)
        assertEquals(7, merged.resume)
        assertEquals("not", merged.note)
        assertEquals(listOf("oyun"), merged.categories)
    }

    @Test
    fun backupRoundTripsAndRejectsHostileFiles() {
        val feed = Feed(FeedRules.feedId("https://a.com/feed"), "https://a.com/feed")
        val data = AppData(
            feeds = listOf(feed),
            articles = listOf(Article("1", feed.id, "t", later = true, note = "n", resume = 6)),
            settings = Settings(theme = 1, cleanup = true),
            lastRefreshMillis = 99,
        )
        val back = Backup.restore(Backup.encode(data))!!
        assertEquals(data.feeds, back.feeds)
        assertEquals(data.articles, back.articles)
        assertEquals(1, back.settings.theme)
        assertEquals(0L, back.lastRefreshMillis)
        assertNull(Backup.restore("not json"))
        assertNull(Backup.restore("x".repeat(Limits.MAX_BACKUP_BYTES + 1)))
        val evil = AppData(
            feeds = listOf(Feed("e", "http://insecure.com/f"), Feed("ok", "https://ok.com/f")),
            articles = listOf(Article("1", "e", "orphan"), Article("2", "ok", "t".repeat(9_999), note = "n".repeat(9_999), resume = -5)),
            settings = Settings(textStep = 99),
        )
        val cleaned = Backup.restore(Backup.encode(evil))!!
        assertEquals(1, cleaned.feeds.size)
        assertEquals(listOf("2"), cleaned.articles.map { it.id })
        assertTrue(cleaned.articles[0].title.length <= Limits.MAX_TITLE && cleaned.articles[0].note.length <= Limits.MAX_NOTE)
        assertEquals(0, cleaned.articles[0].resume)
        assertEquals(3, cleaned.settings.textStep)
        assertNotNull(cleaned.settings)
    }

    @Test
    fun notesTextListsOnlyNotedStories() {
        val text = ReaderRules.notesText(listOf(Article("1", "f", "Bir", link = "https://e.com/1", note = "güzel"), Article("2", "f", "İki")))
        assertEquals("Bir\nhttps://e.com/1\ngüzel", text)
    }
}
