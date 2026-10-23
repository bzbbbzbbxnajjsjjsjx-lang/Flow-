package com.akis.app.domain

/** Finds the links in a story's own markup: https only, labelled, no repeats, nothing that points back at the story. */
object Links {
    private const val MAX_RAW = 100_000
    private val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val anchor = Regex("<a\\b[^>]*?\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a\\s*>", flags)
    private const val MIN_LABEL = 2

    fun extract(html: String, storyLink: String): List<Link> {
        val own = FeedRules.normalizeUrl(storyLink)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Link>()
        for (m in anchor.findAll(html.take(MAX_RAW))) {
            val url = FeedRules.normalizeUrl(Html.unescape(m.groupValues[1]))
            val label = Html.toText(m.groupValues[2], Limits.MAX_LINK_LABEL).replace('\n', ' ').trim()
            val usable = url != null && url != own && label.length >= MIN_LABEL
            if (usable && seen.add(url!!)) out += Link(label, url)
            if (out.size >= Limits.MAX_LINKS) break
        }
        return out
    }
}
