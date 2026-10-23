package com.akis.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    private fun article(id: String, read: Boolean = false, starred: Boolean = false, later: Boolean = false, ageDays: Int = 0) =
        Article(id, "f", id, publishedMillis = now - ageDays * day, read = read, starred = starred, later = later)

    private fun data(vararg a: Article, cleanup: Boolean = true) =
        AppData(feeds = listOf(Feed("f", "https://f.com")), articles = a.toList(), settings = Settings(cleanup = cleanup))

    @Test
    fun cleanupKeepsAnythingTheUserMightWant() {
        val d = data(
            article("old-read", read = true, ageDays = 40),
            article("old-starred", read = true, starred = true, ageDays = 40),
            article("old-later", read = true, later = true, ageDays = 40),
            article("old-unread", ageDays = 40),
            article("new-read", read = true, ageDays = 3),
        )
        assertEquals(listOf("old-starred", "old-later", "old-unread", "new-read"), ReaderRules.cleanup(d, now).articles.map { it.id })
        val off = d.copy(settings = Settings(cleanup = false))
        assertEquals(off, ReaderRules.cleanup(off, now))
    }

    @Test
    fun readFilterListsOnlyReadStories() {
        val d = data(article("a"), article("b", read = true))
        assertEquals(listOf("b"), FeedRules.filter(d.articles, Filter.READ).map { it.id })
    }

    @Test
    fun laterFilterAndToggle() {
        val d = FeedRules.setLater(data(article("a"), article("b")), "b", true)
        assertEquals(listOf("b"), FeedRules.filter(d.articles, Filter.LATER).map { it.id })
        assertTrue(FeedRules.setLater(d, "b", false).articles.none { it.later })
    }

    @Test
    fun undoMarkAllReadRestoresOnlyWhatWasUnread() {
        val d = data(article("a"), article("b", read = true))
        val ids = FeedRules.unreadIds(d)
        val marked = FeedRules.markAllRead(d)
        assertTrue(marked.articles.all { it.read })
        val restored = FeedRules.markUnread(marked, ids)
        assertFalse(restored.articles.first { it.id == "a" }.read)
        assertTrue(restored.articles.first { it.id == "b" }.read)
    }

    @Test
    fun resumeIgnoresTheHeaderAndFeedOptionsStick() {
        val d = data(article("a"))
        assertEquals(0, FeedRules.setResume(d, "a", 2).articles[0].resume)
        assertEquals(9, FeedRules.setResume(d, "a", 9).articles[0].resume)
        val f = FeedRules.setFeedOptions(d, "f", quiet = true, autoFull = true).feeds[0]
        assertTrue(f.quiet && f.autoFull)
    }
}

class ComfortTest {
    private val month = 30 * 24 * 60 * 60 * 1000L

    @Test
    fun staleSourcesAreFlaggedAfterThreeMonths() {
        val now = 10 * month
        assertEquals(0, FeedRules.staleMonths(null, now))
        assertEquals(0, FeedRules.staleMonths(now - 2 * month, now))
        assertEquals(3, FeedRules.staleMonths(now - 3 * month - 1, now))
        assertEquals(0, FeedRules.staleMonths(0L, now))
    }

    @Test
    fun readListShowsLastOpenedFirstAndSurvivesRefresh() {
        val list = listOf(
            Article("a", "f", "a", read = true, openedMillis = 5),
            Article("b", "f", "b", read = true, openedMillis = 9),
            Article("c", "f", "c"),
        )
        assertEquals(listOf("b", "a"), FeedRules.filter(list, Filter.READ).map { it.id })
        val id = FeedRules.articleId("f", "k")
        val item = ParsedItem("k", "t", "https://e.com", "x", null)
        val merged = FeedRules.merge(listOf(Article(id, "f", "t", openedMillis = 42)), "f", listOf(item), 1L)
        assertEquals(42L, merged.single().openedMillis)
    }

    @Test
    fun findParagraphsIsTurkishAwareAndNeedsTwoLetters() {
        val parts = listOf("Işık hızı", "Hiçbir şey", "ışıkla ilgili")
        assertEquals(listOf(0, 2), ReaderRules.findParagraphs(parts, "ışık"))
        assertEquals(emptyList<Int>(), ReaderRules.findParagraphs(parts, "ı"))
        assertEquals(emptyList<Int>(), ReaderRules.findParagraphs(parts, "  "))
    }

    @Test
    fun paletteIsClamped() {
        assertEquals(2, FeedRules.sanitize(Settings(palette = 9)).palette)
        assertEquals(0, FeedRules.sanitize(Settings(palette = -3)).palette)
    }
}

class RestTest {
    @Test
    fun quietHoursWrapAroundMidnight() {
        assertTrue(ReaderRules.inQuietHours(1, 23) && ReaderRules.inQuietHours(1, 3) && !ReaderRules.inQuietHours(1, 7))
        assertTrue(ReaderRules.inQuietHours(2, 0) && !ReaderRules.inQuietHours(2, 8) && !ReaderRules.inQuietHours(2, 23))
        assertFalse(ReaderRules.inQuietHours(0, 3))
    }

    @Test
    fun listTextHasStarredThenQueuedWithNotes() {
        val list = listOf(
            Article("1", "f", "Bir", link = "https://e.com/1", starred = true, note = "güzel\nyazı"),
            Article("2", "f", "İki", later = true),
            Article("3", "f", "Üç"),
        )
        assertEquals(
            "## Yıldızlı\n\n- [Bir](https://e.com/1) — güzel yazı\n\n## Sonra okunacak\n\n- İki",
            ReaderRules.listText(list),
        )
        assertEquals("", ReaderRules.listText(listOf(Article("3", "f", "Üç"))))
    }
}
