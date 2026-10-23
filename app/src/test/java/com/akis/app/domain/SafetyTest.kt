package com.akis.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun cleanupKeepsStoriesWithANote() {
        val old = Article("n", "f", "n", publishedMillis = now - 40 * day, read = true, note = "remember")
        val gone = Article("g", "f", "g", publishedMillis = now - 40 * day, read = true)
        val d = AppData(articles = listOf(old, gone), settings = Settings(cleanup = true))
        assertEquals(listOf("n"), ReaderRules.cleanup(d, now).articles.map { it.id })
    }

    @Test
    fun recentSearchesAreNewestFirstUniqueAndCapped() {
        var recent = emptyList<String>()
        (1..12).forEach { recent = ReaderRules.pushRecent(recent, "arama $it") }
        assertEquals(Limits.MAX_RECENT_SEARCHES, recent.size)
        assertEquals("arama 12", recent.first())
        recent = ReaderRules.pushRecent(recent, "  ARAMA 9 ")
        assertEquals("ARAMA 9", recent.first())
        assertEquals(1, recent.count { it.lowercase(FeedRules.TR) == "arama 9" })
        assertEquals(recent, ReaderRules.pushRecent(recent, "a"))
        val clamped = FeedRules.sanitize(Settings(recentSearches = List(20) { "kelime $it" }))
        assertTrue(clamped.recentSearches.size <= Limits.MAX_RECENT_SEARCHES)
    }

    @Test
    fun sizeCapsNeverDropQueuedOrNotedStories() {
        val over = Limits.MAX_STORED_PER_FEED + 5
        val existing = (1..over).map {
            Article("a$it", "f", "t$it", publishedMillis = it * 1000L, later = it % 2 == 0, note = if (it % 2 == 1) "n" else "")
        }
        val merged = FeedRules.merge(existing, "f", emptyList(), now)
        assertEquals(over, merged.size)
    }
}
