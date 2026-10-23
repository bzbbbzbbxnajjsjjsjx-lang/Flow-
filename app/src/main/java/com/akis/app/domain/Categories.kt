package com.akis.app.domain

import java.util.Locale

/** Turns the labels feeds attach to stories into a short, shared vocabulary; no network, no model. */
object Categories {
    private const val MAX_LENGTH = 24
    private val junk = setOf("uncategorized", "kategorisiz", "genel", "diğer", "other", "blog", "featured", "öne çıkanlar")
    private val aliases: Map<String, String> = buildMap {
        fun group(canonical: String, vararg names: String) = names.forEach { put(it, canonical) }
        group("teknoloji", "tech", "technology", "teknoloji haberleri", "technews", "gadgets", "gadget")
        group("haber", "news", "haberler", "gündem", "son dakika", "breaking news", "headlines")
        group("oyun", "gaming", "games", "game", "oyunlar", "video games", "gaming news")
        group("bilim", "science", "bilim ve teknik", "space", "uzay")
        group("ekonomi", "economy", "business", "finance", "finans", "iş dünyası", "borsa", "markets")
        group("spor", "sports", "sport", "futbol", "football")
        group("dünya", "world", "world news", "uluslararası", "international")
        group("siyaset", "politics", "politika")
        group("sağlık", "health", "yaşam", "lifestyle")
        group("eğlence", "entertainment", "magazin", "sinema", "film", "movies", "tv")
        group("güvenlik", "security", "privacy", "gizlilik", "siber güvenlik", "cybersecurity")
        group("mobil", "mobile", "android", "ios", "telefon", "smartphones", "phones")
        group("yazılım", "software", "programming", "development", "open source", "linux")
        group("yapay zekâ", "ai", "yapay zeka", "artificial intelligence", "machine learning")
    }

    /** The shared name of a label, or null for empty, noisy or too long ones. */
    fun normalize(raw: String): String? {
        val clean = Html.toText(raw, MAX_LENGTH * 2).trim().trim('#', ',', ';', '/').trim()
        // Turkish lower-casing turns "I" into "ı", so English names ("GAMING") are matched with the plain rules first.
        val label = clean.lowercase(FeedRules.TR)
        if (label.isEmpty() || label.length > MAX_LENGTH || label in junk) return null
        return aliases[clean.lowercase(Locale.ROOT)] ?: aliases[label] ?: label
    }

    /** For chips: "teknoloji" → "Teknoloji". */
    fun display(label: String): String = label.replaceFirstChar { it.titlecase(FeedRules.TR) }

    /** The [limit] most used labels in [articles], most used first (ties by name). */
    fun top(articles: List<Article>, limit: Int = Limits.CATEGORY_CHIPS): List<String> =
        articles.flatMap { it.categories }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }

    fun filter(articles: List<Article>, label: String): List<Article> =
        if (label.isEmpty()) articles else articles.filter { label in it.categories }
}
