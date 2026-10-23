package com.akis.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test
    fun shortParagraphStaysWhole() {
        assertEquals(listOf("Merhaba dünya. Nasılsın?"), SpeechChunks.split(listOf("Merhaba dünya. Nasılsın?")))
    }

    @Test
    fun longParagraphBreaksAtSentencesAndKeepsAllWords() {
        val text = (1..60).joinToString(" ") { "Bu $it numaralı cümle biraz uzun bir cümledir." }
        val pieces = SpeechChunks.split(listOf(text), max = 200)
        assertTrue(pieces.size > 5)
        assertTrue(pieces.all { it.length <= 200 && it.endsWith(".") })
        assertEquals(text, pieces.joinToString(" "))
    }

    @Test
    fun oneHugeSentenceIsCutAtSpaces() {
        val text = (1..500).joinToString(" ") { "kelime$it" }
        val pieces = SpeechChunks.split(listOf(text), max = 100)
        assertTrue(pieces.all { it.length <= 100 && it.isNotBlank() })
        assertEquals(text, pieces.joinToString(" "))
    }

    @Test
    fun blankInputGivesNothing() {
        assertEquals(emptyList<String>(), SpeechChunks.split(listOf("", "   ")))
    }
}
