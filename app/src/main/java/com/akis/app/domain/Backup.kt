package com.akis.app.domain

import kotlinx.serialization.json.Json

/** One-file backup of everything the app keeps; restoring never trusts the file beyond the usual bounds. */
object Backup {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    /** Null when the text is not a backup; otherwise data cut down to the same limits as live data. */
    fun restore(text: String): AppData? {
        val raw = text.takeIf { it.length <= Limits.MAX_BACKUP_BYTES }
            ?.let { runCatching { json.decodeFromString(AppData.serializer(), it) }.getOrNull() }
        return raw?.let(::cut)
    }

    private fun cleanLink(l: Link): Link? = FeedRules.normalizeUrl(l.url)?.let { Link(l.label.take(Limits.MAX_LINK_LABEL), it) }

    private fun cut(raw: AppData): AppData {
        val feeds = raw.feeds.mapNotNull { f -> FeedRules.normalizeUrl(f.url)?.let { f.copy(url = it, id = FeedRules.feedId(it)) } }
            .distinctBy { it.id }.take(Limits.MAX_FEEDS)
        val ids = raw.feeds.associate { it.id to FeedRules.normalizeUrl(it.url)?.let(FeedRules::feedId) }
        val known = feeds.map { it.id }.toSet()
        val articles = raw.articles.mapNotNull { a ->
            val feedId = ids[a.feedId]?.takeIf { it in known } ?: return@mapNotNull null
            a.copy(
                feedId = feedId,
                title = a.title.take(Limits.MAX_TITLE),
                text = a.text.take(Limits.MAX_TEXT),
                fullText = a.fullText.take(Limits.MAX_FULL_TEXT),
                note = a.note.take(Limits.MAX_NOTE),
                categories = a.categories.take(Limits.MAX_CATEGORIES),
                link = a.link.take(Limits.MAX_URL),
                resume = a.resume.coerceAtLeast(0),
                links = a.links.mapNotNull(::cleanLink).take(Limits.MAX_LINKS),
            )
        }.distinctBy { it.id }.take(Limits.MAX_ARTICLES)
        return raw.copy(
            feeds = feeds,
            articles = articles,
            settings = FeedRules.sanitize(raw.settings),
            readLog = raw.readLog.takeLast(Limits.MAX_READ_LOG),
            lastRefreshMillis = 0L,
        )
    }
}
