package com.akis.app.domain

/** Spots one story reported by several sources, by comparing title words; the first (newest) one stays. */
object Similar {
    private const val MIN_WORDS = 4
    private const val MIN_WORD_LENGTH = 3
    private const val THRESHOLD_PERCENT = 60
    private const val PERCENT = 100
    private const val WINDOW_DAYS = 3
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val WINDOW_MS = WINDOW_DAYS * DAY_MS
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    private class Rep(val article: Article, val words: Set<String>) {
        val others = mutableListOf<Article>()
    }

    private fun words(title: String): Set<String> =
        title.lowercase(FeedRules.TR).split(splitter).filter { it.length >= MIN_WORD_LENGTH }.toSet()

    private fun similar(a: Set<String>, b: Set<String>): Boolean {
        val common = a.count { it in b }
        return common * PERCENT >= THRESHOLD_PERCENT * (a.size + b.size - common)
    }

    /** The stories to show and, per kept story id, how many other sources reported the same one. */
    fun collapse(articles: List<Article>): Pair<List<Article>, Map<String, Int>> =
        groups(articles).let { (shown, others) -> shown to others.mapValues { it.value.size } }

    /** The stories to show and, per kept story id, the other sources' versions of the same story. */
    fun groups(articles: List<Article>): Pair<List<Article>, Map<String, List<Article>>> {
        val reps = mutableListOf<Rep>()
        val index = HashMap<String, MutableList<Int>>()
        for (article in articles) {
            val words = words(article.title)
            val match = if (words.size < MIN_WORDS) null else candidates(words, index).map { reps[it] }.firstOrNull {
                it.article.feedId != article.feedId &&
                    kotlin.math.abs(it.article.publishedMillis - article.publishedMillis) <= WINDOW_MS && similar(words, it.words)
            }
            if (match != null) {
                match.others += article
            } else {
                reps += Rep(article, words)
                words.forEach { index.getOrPut(it) { mutableListOf() }.add(reps.lastIndex) }
            }
        }
        return reps.map { it.article } to reps.filter { it.others.isNotEmpty() }.associate { it.article.id to it.others.toList() }
    }

    private fun candidates(words: Set<String>, index: Map<String, List<Int>>): Set<Int> =
        words.flatMapTo(LinkedHashSet()) { index[it].orEmpty() }
}
