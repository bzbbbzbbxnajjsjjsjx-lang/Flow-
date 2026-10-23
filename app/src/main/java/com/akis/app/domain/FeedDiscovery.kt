package com.akis.app.domain

import java.net.URI
import java.net.URISyntaxException

/** Finds the RSS/Atom links a normal web page advertises in its head, without running anything. */
object FeedDiscovery {
    private const val MAX_SCAN = 200_000
    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([A-Za-z][A-Za-z0-9:_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val feedTypes = setOf("application/rss+xml", "application/atom+xml")

    /** Where sites commonly serve a feed; tried (at most these four requests) only when a page names none. */
    val commonPaths = listOf("/feed", "/rss", "/rss.xml", "/atom.xml")

    /** "https://host" of an address, or null when it is not a usable https address. */
    fun origin(url: String): String? {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            null
        }
        return if (uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.authority.isNullOrBlank()) {
            "https://${uri.authority}"
        } else {
            null
        }
    }

    fun find(html: String, baseUrl: String): List<DiscoveredFeed> {
        val base = try {
            URI(baseUrl)
        } catch (_: URISyntaxException) {
            null
        } ?: return emptyList()
        val found = linkedMapOf<String, String>()
        for (tag in linkTag.findAll(html.take(MAX_SCAN))) {
            val attrs = attribute.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] } }
            val advertised = "alternate" in attrs["rel"].orEmpty().lowercase().split(' ') && attrs["type"].orEmpty().lowercase() in feedTypes
            val url = if (advertised) attrs["href"]?.let { resolve(base, Html.unescape(it)) } else null
            if (url != null) found.putIfAbsent(url, Html.unescape(attrs["title"].orEmpty()).trim().take(Limits.MAX_TITLE))
            if (found.size >= Limits.MAX_FOUND_FEEDS) break
        }
        return found.map { (url, title) -> DiscoveredFeed(title.ifBlank { URI(url).host.orEmpty() }, url) }
    }

    private fun resolve(base: URI, href: String): String? = try {
        FeedRules.upgradeAndNormalize(base.resolve(href.trim()).toString())
    } catch (_: IllegalArgumentException) {
        null
    }
}
