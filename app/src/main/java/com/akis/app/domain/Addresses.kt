package com.akis.app.domain

import java.net.URI

/**
 * Turns the address of a profile or channel into its feed address, offline, for the sites whose feeds follow a
 * fixed pattern; names the sites that have no feed at all. Anything else is left to the normal discovery.
 */
object Addresses {
    sealed interface Result {
        data class Converted(val url: String, val what: String) : Result
        data class Unsupported(val reason: String) : Result
    }

    private val noFeed = mapOf(
        "x.com" to "X",
        "twitter.com" to "X",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "tiktok.com" to "TikTok",
        "threads.net" to "Threads",
        "t.me" to "Telegram",
        "linkedin.com" to "LinkedIn",
    )
    private val reddit = setOf("r", "user", "u")
    private val githubFeeds = setOf("releases", "tags")

    /** Null when the address needs no conversion (a feed, a blog, a page the discovery can read). */
    fun convert(input: String): Result? {
        val uri = FeedRules.normalizeUrl(input)?.let(::URI) ?: return null
        val host = uri.host.removePrefix("www.").removePrefix("m.")
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return noFeed[host]?.let { Result.Unsupported("$it$NO_FEED") } ?: bySite(host, path, uri.query.orEmpty())
    }

    private fun bySite(host: String, path: List<String>, query: String): Result? = when {
        host == "youtube.com" -> youtube(path, query)
        host == "reddit.com" || host == "old.reddit.com" -> redditFeed(path)
        host == "github.com" -> github(path)
        host == "bsky.app" -> bluesky(path)
        host == "medium.com" -> medium(path)
        host.endsWith(".substack.com") -> substack(host, path)
        else -> mastodon(host, path)
    }

    private fun bluesky(path: List<String>): Result? =
        if (path.size == PAIR && path[0] == "profile") Result.Converted("https://bsky.app/profile/${path[1]}/rss", "Bluesky hesabı") else null

    private fun medium(path: List<String>): Result? =
        if (path.size == 1) Result.Converted("https://medium.com/feed/${path[0]}", "Medium yazarı") else null

    private fun substack(host: String, path: List<String>): Result? =
        if (path.isEmpty()) Result.Converted("https://$host/feed", "Substack bülteni") else null

    /** A single "/@name" path is the Mastodon profile pattern; the feed is the same address plus ".rss". */
    private fun mastodon(host: String, path: List<String>): Result? =
        if (path.size == 1 && path[0].startsWith("@") && path[0].length > 1) {
            Result.Converted("https://$host/${path[0]}.rss", "Mastodon hesabı")
        } else {
            null
        }

    private fun youtube(path: List<String>, query: String): Result? = when {
        path.size == PAIR && path[0] == "channel" ->
            Result.Converted("https://www.youtube.com/feeds/videos.xml?channel_id=${path[1]}", "YouTube kanalı")
        path == listOf("playlist") ->
            param(query, "list")?.let { Result.Converted("https://www.youtube.com/feeds/videos.xml?playlist_id=$it", "YouTube listesi") }
        else -> null
    }

    private fun param(query: String, name: String): String? =
        query.split('&').firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }

    private fun redditFeed(path: List<String>): Result? =
        if (path.size >= PAIR && path[0] in reddit && !path.last().endsWith(".rss")) {
            Result.Converted("https://www.reddit.com/${path[0]}/${path[1]}/.rss", "Reddit")
        } else {
            null
        }

    private fun github(path: List<String>): Result? = when {
        path.size == PAIR -> Result.Converted("https://github.com/${path[0]}/${path[1]}/releases.atom", "GitHub sürümleri")
        path.size == TRIPLE && path[2] in githubFeeds ->
            Result.Converted("https://github.com/${path[0]}/${path[1]}/${path[2]}.atom", "GitHub ${path[2]}")
        else -> null
    }

    private const val PAIR = 2
    private const val TRIPLE = 3
    private const val NO_FEED = " hesaplarının RSS akışı yok; aynı içeriği yayınlayan bir site ya da Bluesky/Mastodon hesabı ara"
}
