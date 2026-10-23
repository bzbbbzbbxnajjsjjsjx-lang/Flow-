package com.akis.app.domain

import java.time.LocalDate

/** A run of tile rows under an optional header. */
data class Section(val label: String?, val rows: List<List<Article>>)

object Sections {
    private const val PINNED = "Öncelikli"

    /**
     * Rows of [columns] tiles. When [dated], pinned sources' stories come first under "Öncelikli" and the rest
     * follow under "Bugün", "Dün"..., in the order given; otherwise one header-less run.
     */
    fun build(articles: List<Article>, columns: Int, dated: Boolean, pinnedFeeds: Set<String>, today: LocalDate): List<Section> {
        if (!dated) return if (articles.isEmpty()) emptyList() else listOf(Section(null, articles.chunked(columns)))
        val (lead, rest) = articles.partition { it.feedId in pinnedFeeds }
        val led = if (lead.isEmpty()) emptyList() else listOf(Section(PINNED, lead.chunked(columns)))
        val days = rest.groupBy { ReaderRules.dayBucket(it.publishedMillis, today) }.map { (label, items) ->
            Section(label, items.chunked(columns))
        }
        return led + days
    }
}
