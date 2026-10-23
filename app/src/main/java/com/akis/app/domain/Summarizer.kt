package com.akis.app.domain

import kotlin.math.sqrt

/** Picks the sentences that carry the story's most repeated words; plain counting, no model, nothing leaves the phone. */
object Summarizer {
    private const val MIN_SENTENCE = 30
    private const val MAX_SENTENCE = 300
    private const val MIN_SENTENCES = 6
    private const val MIN_WORD = 4
    private const val EARLY = 3
    private const val EARLY_BONUS = 1.2
    private val splitter = Regex("(?<=[.!?…])\\s+|\\n+")
    private val wordSplitter = Regex("[^\\p{L}\\p{N}]+")
    private val stop = setOf(
        "ancak", "fakat", "için", "gibi", "kadar", "daha", "olarak", "bunun", "olan", "veya", "göre", "sonra",
        "çok", "böylece", "ayrıca", "şimdi", "bunu", "ise", "onun", "diğer", "yani", "ayrıca", "tarafından",
    )

    private class Scored(val index: Int, val text: String, val score: Double)

    private fun words(sentence: String): List<String> =
        sentence.lowercase(FeedRules.TR).split(wordSplitter).filter { it.length >= MIN_WORD && it !in stop }

    /** The [count] best sentences in reading order, or none when the text is too short to need a summary. */
    fun top(text: String, count: Int = Limits.SUMMARY_SENTENCES): List<String> {
        val sentences = text.split(splitter).map { it.trim() }.filter { it.length in MIN_SENTENCE..MAX_SENTENCE }.distinct()
        if (sentences.size < MIN_SENTENCES) return emptyList()
        val frequency = sentences.flatMap { words(it) }.groupingBy { it }.eachCount()
        return sentences.mapIndexed { i, s ->
            val own = words(s).distinct()
            val base = if (own.isEmpty()) 0.0 else own.sumOf { frequency.getValue(it) } / sqrt(own.size.toDouble())
            Scored(i, s, if (i < EARLY) base * EARLY_BONUS else base)
        }.sortedByDescending { it.score }.take(count).sortedBy { it.index }.map { it.text }
    }
}
