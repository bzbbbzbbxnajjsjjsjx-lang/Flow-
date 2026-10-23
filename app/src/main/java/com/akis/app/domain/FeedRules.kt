package com.akis.app.domain

import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Pure, unit-tested reader logic: URLs, ids, merging, limits, filters, labels and OPML. */
@Suppress("TooManyFunctions")
object FeedRules {
    val TR: Locale = Locale.forLanguageTag("tr-TR")
    private const val ID_LENGTH = 24
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val DAY_MS = DAY
    private const val WEEK_DAYS = 7
    private const val HTTP_PREFIX = 7
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM", TR)

    /** Accepts "example.com/feed" and https URLs only; plain http, other schemes and logins are refused. */
    fun normalizeUrl(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty() || raw.length > Limits.MAX_URL) return null
        val withScheme = if ("://" in raw) raw else "https://$raw"
        return try {
            val uri = URI(withScheme)
            if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, uri.path, uri.query, null).toString()
            } else {
                null
            }
        } catch (_: URISyntaxException) {
            null
        }
    }

    sealed interface AddCheck {
        data class Ok(val url: String, val id: String) : AddCheck
        data class Error(val message: String) : AddCheck
    }

    /** Validates a typed address against what is already subscribed. */
    fun checkNewFeed(input: String, existing: List<Feed>): AddCheck {
        val url = normalizeUrl(input)
        val id = url?.let(::feedId)
        return when {
            url == null || id == null -> AddCheck.Error("Geçerli bir HTTPS adresi gir")
            existing.any { it.id == id } -> AddCheck.Error("Bu kaynak zaten ekli")
            existing.size >= Limits.MAX_FEEDS -> AddCheck.Error("En fazla ${Limits.MAX_FEEDS} kaynak eklenebilir")
            else -> AddCheck.Ok(url, id)
        }
    }

    /** Adds imported (title, url) pairs that are not subscribed yet, up to the feed cap; returns the new count too. */
    fun addFeeds(data: AppData, pairs: List<Pair<String, String>>): Pair<AppData, Int> =
        addEntries(data, pairs.map { (title, url) -> OpmlEntry(title, url) })

    /** Adds entries that are not subscribed yet, up to the feed cap, keeping their groups; returns the new count too. */
    fun addEntries(data: AppData, entries: List<OpmlEntry>): Pair<AppData, Int> {
        val known = data.feeds.map { it.id }.toSet()
        val room = (Limits.MAX_FEEDS - data.feeds.size).coerceAtLeast(0)
        val fresh = entries.map { Feed(feedId(it.url), it.url, it.title, group = it.group) }
            .filter { it.id !in known }.distinctBy { it.id }.take(room)
        return data.copy(feeds = data.feeds + fresh) to fresh.size
    }

    fun setRead(data: AppData, articleId: String, read: Boolean): AppData =
        data.copy(articles = data.articles.map { if (it.id == articleId) it.copy(read = read) else it })

    fun toggleStar(data: AppData, articleId: String): AppData =
        data.copy(articles = data.articles.map { if (it.id == articleId) it.copy(starred = !it.starred) else it })

    fun setLater(data: AppData, articleId: String, later: Boolean, now: Long = System.currentTimeMillis()): AppData =
        data.copy(articles = data.articles.map { if (it.id == articleId) it.copy(later = later, laterAt = if (later) now else 0L) else it })

    /** Remembers where reading stopped; the top of the story (before the text starts) is stored as 0. */
    fun setResume(data: AppData, articleId: String, index: Int): AppData {
        val value = if (index >= RESUME_MIN) index else 0
        return data.copy(articles = data.articles.map { if (it.id == articleId && it.resume != value) it.copy(resume = value) else it })
    }

    fun setFeedOptions(data: AppData, feedId: String, quiet: Boolean, autoFull: Boolean): AppData =
        data.copy(feeds = data.feeds.map { if (it.id == feedId) it.copy(quiet = quiet, autoFull = autoFull) else it })

    fun unreadIds(data: AppData): Set<String> = data.articles.filter { !it.read }.map { it.id }.toSet()

    fun setPinned(data: AppData, feedId: String, pinned: Boolean): AppData =
        data.copy(feeds = data.feeds.map { if (it.id == feedId) it.copy(pinned = pinned) else it })

    fun setTint(data: AppData, feedId: String, tint: Int): AppData =
        data.copy(feeds = data.feeds.map { if (it.id == feedId) it.copy(tint = tint.coerceIn(0, Limits.TINTS)) else it })

    /** Mutes a source's unread stories for [days] days from [now]; 0 days lifts the mute. */
    fun snooze(data: AppData, feedId: String, days: Int, now: Long): AppData =
        data.copy(feeds = data.feeds.map { if (it.id == feedId) it.copy(mutedUntil = if (days > 0) now + days * DAY_MS else 0L) else it })

    fun setOpened(data: AppData, articleId: String, now: Long): AppData =
        data.copy(articles = data.articles.map { if (it.id == articleId) it.copy(openedMillis = now) else it })

    /** Whole months since a source's newest story when that is at least three; 0 while it is still active or unknown. */
    fun staleMonths(newestMillis: Long?, now: Long): Int {
        val months = if (newestMillis == null || newestMillis <= 0L) 0 else ((now - newestMillis) / MONTH_MS).toInt()
        return if (months >= STALE_AFTER_MONTHS) months else 0
    }

    fun setNote(data: AppData, articleId: String, note: String): AppData =
        data.copy(articles = data.articles.map { if (it.id == articleId) it.copy(note = note.take(Limits.MAX_NOTE)) else it })

    fun markUnread(data: AppData, ids: Set<String>): AppData =
        data.copy(articles = data.articles.map { if (it.id in ids) it.copy(read = false) else it })

    private const val RESUME_MIN = 4

    fun markAllRead(data: AppData): AppData = data.copy(articles = data.articles.map { it.copy(read = true) })

    private val fullFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR)

    fun fullDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(fullFormat)

    /** Images must be https; a plain http address is upgraded, relative or odd ones are dropped. */
    fun normalizeImage(raw: String): String? = upgradeAndNormalize(raw)

    /** Same rule for any address found inside a page: http becomes https, anything without a scheme is dropped. */
    fun upgradeAndNormalize(raw: String): String? {
        val t = raw.trim().let { if (it.startsWith("http://", ignoreCase = true)) "https://" + it.substring(HTTP_PREFIX) else it }
        return if ("://" in t) normalizeUrl(t) else null
    }

    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    /** The YouTube video id of a link (watch, youtu.be, shorts, embed), or null. */
    fun videoId(link: String): String? = try {
        val uri = URI(link)
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
        val candidate = when (host) {
            "youtu.be" -> uri.path.orEmpty().trim('/')
            "youtube.com", "youtube-nocookie.com" -> youtubePath(uri)
            else -> ""
        }
        candidate.takeIf { videoIdPattern.matches(it) }
    } catch (_: URISyntaxException) {
        null
    }

    private fun youtubePath(uri: URI): String {
        val path = uri.path.orEmpty()
        val fromQuery = uri.query.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        return fromQuery ?: listOf("/shorts/", "/embed/", "/live/").firstOrNull { path.startsWith(it) }?.let { path.removePrefix(it) }.orEmpty()
    }

    fun isVideo(article: Article): Boolean = videoId(article.link) != null

    /** Whole minutes to read [text] at an average pace; 0 for no words. */
    fun readingMinutes(text: String): Int {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return (words + Limits.WORDS_PER_MINUTE - 1) / Limits.WORDS_PER_MINUTE
    }

    /** Keeps the text on the article, then drops the oldest unstarred texts beyond [Limits.MAX_FULL_TEXTS]. */
    fun setFullText(data: AppData, articleId: String, text: String): AppData {
        val withText = data.articles.map { if (it.id == articleId) it.copy(fullText = text.take(Limits.MAX_FULL_TEXT)) else it }
        val keep = withText.filter { it.fullText.isNotEmpty() }
            .sortedByDescending { it.publishedMillis }.take(Limits.MAX_FULL_TEXTS).map { it.id }.toSet()
        return data.copy(articles = withText.map { if (it.fullText.isNotEmpty() && it.id !in keep && !it.starred) it.copy(fullText = "") else it })
    }

    private val dailyLimits = setOf(0, 15, 30, 50)
    private val breakChoices = setOf(0, 15, 30)
    private const val MAX_TEXT_STEP = 3
    private const val MAX_THEME = 2
    private const val MAX_PALETTE = 2
    private const val STALE_AFTER_MONTHS = 3
    private const val MONTH_MS = 30 * 24 * 60 * 60 * 1000L

    /** Clamps every choice to a value the screens offer, so a bad file cannot break the UI. */
    fun sanitize(s: Settings): Settings = s.copy(
        textStep = s.textStep.coerceIn(0, MAX_TEXT_STEP),
        theme = s.theme.coerceIn(0, MAX_THEME),
        palette = s.palette.coerceIn(0, MAX_PALETTE),
        spacing = s.spacing.coerceIn(0, MAX_PALETTE),
        margin = s.margin.coerceIn(0, MAX_PALETTE),
        quiet = s.quiet.coerceIn(0, MAX_PALETTE),
        mutedWords = s.mutedWords.mapNotNull(ReaderRules::cleanWord).distinct().take(Limits.MAX_MUTED),
        topics = s.topics.mapNotNull(ReaderRules::cleanWord).distinct().take(Limits.MAX_TOPICS),
        recentSearches = s.recentSearches.map { it.trim().take(Limits.MAX_GROUP) }.filter { it.length >= 2 }.take(Limits.MAX_RECENT_SEARCHES),
        dailyLimit = if (s.dailyLimit in dailyLimits) s.dailyLimit else Settings().dailyLimit,
        breakMinutes = if (s.breakMinutes in breakChoices) s.breakMinutes else Settings().breakMinutes,
    )

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun feedId(url: String): String = sha(url.lowercase()).take(ID_LENGTH)

    fun articleId(feedId: String, key: String): String = sha("$feedId|$key").take(ID_LENGTH)

    /** Applies one fetch result: feed metadata, merged articles, per-feed and global caps. */
    fun applyFetch(data: AppData, feedId: String, outcome: FetchOutcome, now: Long): AppData {
        val feed = data.feeds.firstOrNull { it.id == feedId } ?: return data
        val updated = when (outcome) {
            FetchOutcome.NotModified -> feed.copy(lastFetchedMillis = now, error = "")
            is FetchOutcome.Failure -> feed.copy(error = outcome.reason)
            is FetchOutcome.Success -> feed.copy(
                title = outcome.feed.title.ifBlank { feed.title },
                siteUrl = outcome.feed.siteUrl.ifBlank { feed.siteUrl },
                etag = outcome.etag,
                lastModified = outcome.lastModified,
                lastFetchedMillis = now,
                error = "",
            )
        }
        val articles = if (outcome is FetchOutcome.Success) merge(data.articles, feedId, outcome.feed.items, now) else data.articles
        return data.copy(feeds = data.feeds.map { if (it.id == feedId) updated else it }, articles = articles)
    }

    /** Existing read/starred flags and dates survive; new items are unread; caps drop the oldest unstarred first. */
    fun merge(existing: List<Article>, feedId: String, items: List<ParsedItem>, now: Long): List<Article> {
        val known = existing.associateBy { it.id }
        val incoming = items.map { item ->
            val id = articleId(feedId, item.key)
            val old = known[id]
            Article(
                id = id,
                feedId = feedId,
                title = item.title.take(Limits.MAX_TITLE),
                link = item.link,
                text = item.text,
                publishedMillis = old?.publishedMillis ?: item.publishedMillis?.coerceAtMost(now) ?: now,
                read = old?.read ?: false,
                starred = old?.starred ?: false,
                imageUrl = item.imageUrl.ifBlank { old?.imageUrl.orEmpty() },
                fullText = old?.fullText.orEmpty(),
                later = old?.later ?: false,
                laterAt = old?.laterAt ?: 0L,
                links = item.links.ifEmpty { old?.links.orEmpty() },
                resume = old?.resume ?: 0,
                note = old?.note.orEmpty(),
                openedMillis = old?.openedMillis ?: 0L,
                categories = item.categories.ifEmpty { old?.categories.orEmpty() },
            )
        }
        val incomingIds = incoming.map { it.id }.toSet()
        val others = existing.filter { it.id !in incomingIds }
        return cap(newestFirst(others + incoming))
    }

    fun newestFirst(articles: List<Article>): List<Article> =
        articles.sortedWith(compareByDescending<Article> { it.publishedMillis }.thenBy { it.id })

    /** Stories the user marked (star, read-later, own note) are never dropped by the size caps. */
    private fun keeps(a: Article) = a.starred || a.later || a.note.isNotEmpty()

    private fun cap(sorted: List<Article>): List<Article> {
        val perFeed = mutableMapOf<String, Int>()
        val withinFeed = sorted.filter { a ->
            val n = perFeed.merge(a.feedId, 1, Int::plus) ?: 1
            keeps(a) || n <= Limits.MAX_STORED_PER_FEED
        }
        if (withinFeed.size <= Limits.MAX_ARTICLES) return withinFeed
        var excess = withinFeed.size - Limits.MAX_ARTICLES
        return withinFeed.asReversed().filter { a -> if (!keeps(a) && excess > 0) { excess--; false } else true }.asReversed()
    }

    fun removeFeed(data: AppData, feedId: String): AppData =
        data.copy(feeds = data.feeds.filterNot { it.id == feedId }, articles = data.articles.filterNot { it.feedId == feedId })

    fun filter(articles: List<Article>, filter: Filter): List<Article> = when (filter) {
        Filter.ALL -> articles
        Filter.UNREAD -> articles.filter { !it.read }
        Filter.READ -> articles.filter { it.read }.sortedByDescending { it.openedMillis }
        Filter.LATER -> articles.filter { it.later }
        Filter.STARRED -> articles.filter { it.starred }
    }

    fun unreadCount(articles: List<Article>, feedId: String): Int = articles.count { it.feedId == feedId && !it.read }

    fun badgeLetter(feed: Feed?): String {
        val source = feed?.title?.ifBlank { null } ?: feed?.url?.substringAfter("://")?.removePrefix("www.").orEmpty()
        return source.firstOrNull { it.isLetterOrDigit() }?.uppercase(TR) ?: "?"
    }

    fun relativeTime(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val diff = (now - then).coerceAtLeast(0)
        return when {
            diff < MINUTE -> "az önce"
            diff < HOUR -> "${diff / MINUTE} dk önce"
            diff < DAY -> "${diff / HOUR} sa önce"
            diff < 2 * DAY -> "dün"
            diff < WEEK_DAYS * DAY -> "${diff / DAY} gün önce"
            else -> Instant.ofEpochMilli(then).atZone(zone).format(dayFormat)
        }
    }

    /** Reads (title, https url) pairs from OPML; anything not https, duplicated or over the cap is dropped. */
    fun parseOpml(parser: XmlPullParser): List<Pair<String, String>> = parseOpmlEntries(parser).map { it.title to it.url }

    /** Reads every feed of an OPML file with the folder it sits in; folders beyond [Limits.MAX_GROUPS] are dropped. */
    fun parseOpmlEntries(parser: XmlPullParser): List<OpmlEntry> {
        val reader = OpmlReader()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && reader.found.size < Limits.MAX_FEEDS) {
                if (parser.name.equals("outline", ignoreCase = true)) {
                    if (event == XmlPullParser.START_TAG) reader.start(parser) else if (event == XmlPullParser.END_TAG) reader.end()
                }
                event = parser.next()
            }
        } catch (_: XmlPullParserException) {
            return emptyList()
        }
        return reader.found.values.toList()
    }

    /** Ungrouped feeds first, then one folder per group, so other readers keep the folders. */
    fun toOpml(feeds: List<Feed>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<opml version="2.0"><head><title>Akış</title></head><body>""")
        fun line(f: Feed) {
            val title = escape(f.title.ifBlank { f.url })
            appendLine("""<outline type="rss" text="$title" title="$title" xmlUrl="${escape(f.url)}"/>""")
        }
        feeds.filter { it.group.isEmpty() }.forEach(::line)
        feeds.filter { it.group.isNotEmpty() }.groupBy { it.group }.forEach { (group, members) ->
            appendLine("""<outline text="${escape(group)}" title="${escape(group)}">""")
            members.forEach(::line)
            appendLine("</outline>")
        }
        appendLine("</body></opml>")
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
