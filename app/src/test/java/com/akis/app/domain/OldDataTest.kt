package com.akis.app.domain

import java.io.ByteArrayInputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file written by an older Akış must still load, with every newer field at its default. */
@OptIn(ExperimentalSerializationApi::class)
class OldDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    // The shape 1.3.x wrote: no groups, notes, later flags, links, tints or newer settings.
    private val oldFile = """
        {"feeds":[{"id":"a1","url":"https://a.example/feed","title":"A","etag":"x","lastFetchedMillis":5}],
         "articles":[{"id":"s1","feedId":"a1","title":"Eski yazı","link":"https://a.example/1","text":"metin",
                      "publishedMillis":10,"read":true,"starred":true}],
         "lastRefreshMillis":7,
         "settings":{"textStep":2,"calm":false,"theme":1,"mutedWords":["spor"]},
         "readLog":[{"epochDay":20000,"minutes":4,"feedId":"a1"}],"dataVersion":1,
         "someFutureField":123}
    """.trimIndent()

    @Test
    fun oldFileLoadsThroughTheStreamingReaderWithNothingLost() {
        val data = json.decodeFromStream(AppData.serializer(), ByteArrayInputStream(oldFile.toByteArray()))
        assertEquals(listOf("A"), data.feeds.map { it.title })
        assertEquals("", data.feeds[0].group)
        assertFalse(data.feeds[0].pinned || data.feeds[0].quiet)
        val story = data.articles.single()
        assertTrue(story.read && story.starred && !story.later && story.links.isEmpty() && story.note.isEmpty())
        assertEquals(2, data.settings.textStep)
        assertEquals(listOf("spor"), data.settings.mutedWords)
        assertTrue(data.settings.summary && data.settings.mergeSimilar && data.settings.autoRefresh)
        assertEquals(1, data.readLog.size)
    }

    @Test
    fun theBackupReaderAcceptsTheSameOldFile() {
        val restored = Backup.restore(oldFile)!!
        assertEquals(1, restored.feeds.size)
        assertEquals(1, restored.articles.size)
    }
}
