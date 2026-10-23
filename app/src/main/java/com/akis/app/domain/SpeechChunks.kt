package com.akis.app.domain

/** Cuts text for the speech engine: short pieces that end at a sentence, so a long paragraph cannot stall it. */
object SpeechChunks {
    private const val MAX = 280
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+|\\n+")

    fun split(paragraphs: List<String>, max: Int = MAX): List<String> = paragraphs.flatMap { piece(it, max) }

    private fun piece(text: String, max: Int): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in text.trim().split(sentenceEnd).filter { it.isNotBlank() }) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > max) {
                out += current.toString()
                current = StringBuilder()
            }
            current.append(if (current.isEmpty()) "" else " ").append(sentence.trim())
            while (current.length > max) {
                val cut = current.lastIndexOf(" ", max).takeIf { it > 0 } ?: max
                out += current.substring(0, cut).trim()
                current = StringBuilder(current.substring(cut).trim())
            }
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }
}
