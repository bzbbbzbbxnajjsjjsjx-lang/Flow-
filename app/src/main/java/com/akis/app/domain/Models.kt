package com.akis.app.domain

import kotlinx.serialization.Serializable

/** Every bound in one place: the app never holds or parses more than this. */
object Limits {
    const val MAX_FEEDS = 200
    const val MAX_ITEMS_PER_FETCH = 100
    const val MAX_STORED_PER_FEED = 200
    const val MAX_ARTICLES = 1_000
    const val MAX_BODY_BYTES = 2 * 1024 * 1024
    const val MAX_OPML_BYTES = 1024 * 1024
    const val MAX_TITLE = 300
    const val MAX_TEXT = 5_000
    const val MAX_URL = 2_048
    const val MAX_FULL_TEXT = 30_000
    const val MAX_FULL_TEXTS = 50
    const val IMAGE_CACHE_BYTES = 40L * 1024 * 1024
    const val WORDS_PER_MINUTE = 200
    const val MORE_STEP = 10
    const val MAX_MUTED = 50
    const val MAX_MUTED_LEN = 40
    const val AUTO_READ_DAYS = 7
    const val CLEANUP_DAYS = 30
    const val MAX_NOTE = 500
    const val MAX_CATEGORIES = 3
    const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    const val SHORT_MINUTES = 3
    const val CATEGORY_CHIPS = 10
    const val MAX_TOPICS = 8
    const val SUMMARY_SENTENCES = 3
    const val PREFETCH = 5
    const val HISTORY_WEEKS = 4
    const val TINTS = 8
    const val RESUME_DAYS = 3
    const val MAX_LINKS = 5
    const val MAX_LINK_LABEL = 60
    const val LATER_DAYS = 30
    const val READ_LOG_DAYS = 60
    const val MAX_READ_LOG = 2_000
    const val MAX_GROUP = 24
    const val MAX_GROUPS = 20
    const val MAX_FOUND_FEEDS = 5
}

@Serializable
data class Feed(
    val id: String,
    val url: String,
    val title: String = "",
    val siteUrl: String = "",
    val etag: String = "",
    val lastModified: String = "",
    val lastFetchedMillis: Long = 0L,
    val error: String = "",
    /** Name of the group this source belongs to; empty for none. */
    val group: String = "",
    /** Its unread stories stay out of the home list; they show on the source's own page, under the star and in search. */
    val quiet: Boolean = false,
    /** Fetch the article page's full text as soon as one of its stories is opened. */
    val autoFull: Boolean = false,
    /** Its unread stories lead the home list under their own header. */
    val pinned: Boolean = false,
    /** 0 the default badge colour, 1 to 8 one of the badge tints. */
    val tint: Int = 0,
    /** Until when its unread stories stay out of the home list; 0 or a past time means not muted. */
    val mutedUntil: Long = 0L,
)

/** A link found in a story's own markup, kept so the reader can offer it. */
@Serializable
data class Link(val label: String, val url: String)

@Serializable
data class Article(
    val id: String,
    val feedId: String,
    val title: String,
    val link: String = "",
    val text: String = "",
    val publishedMillis: Long = 0L,
    val read: Boolean = false,
    val starred: Boolean = false,
    val imageUrl: String = "",
    /** The article page's main text once fetched on demand; empty until then. */
    val fullText: String = "",
    /** In the read-later queue; opening the story takes it out. */
    val later: Boolean = false,
    /** Index of the first reader item that was on screen when the story was left; 0 means from the top. */
    val resume: Int = 0,
    /** The user's own note on the story. */
    val note: String = "",
    /** When the story was last opened; the read list shows the newest first. 0 for never. */
    val openedMillis: Long = 0L,
    /** When the story joined the read-later list; 0 when it is not there (or got there before this was kept). */
    val laterAt: Long = 0L,
    /** Up to [Limits.MAX_LINKS] https links from the story's text. */
    val links: List<Link> = emptyList(),
    /** Up to [Limits.MAX_CATEGORIES] normalized labels the feed gave the story. */
    val categories: List<String> = emptyList(),
)

/** User choices; every field has a default so older saved files still load. */
@Serializable
data class Settings(
    val textStep: Int = 1,
    val fastRead: Boolean = false,
    val calm: Boolean = true,
    /** 0 means no limit. */
    val dailyLimit: Int = 30,
    /** 0 means no break reminder. */
    val breakMinutes: Int = 30,
    val loadImages: Boolean = true,
    /** 0 dark, 1 light, 2 follow the system. */
    val theme: Int = 0,
    /** 0 the navy palette, 1 sepia (paper), 2 pure black (OLED); 1 and 2 decide light or dark on their own. */
    val palette: Int = 0,
    /** Reader line spacing: 0 tight, 1 normal, 2 airy. */
    val spacing: Int = 1,
    /** Reader side margin: 0 narrow, 1 normal, 2 wide. */
    val margin: Int = 1,
    /** 0 off, 1 the list rests 22:00-07:00, 2 it rests 00:00-08:00. */
    val quiet: Int = 0,
    val serif: Boolean = false,
    /** Marks unread stories older than [Limits.AUTO_READ_DAYS] days as read. */
    val autoRead: Boolean = false,
    /** Deletes read stories that are not starred or queued once they are older than [Limits.CLEANUP_DAYS] days. */
    val cleanup: Boolean = false,
    /** Shows one tile for a story several sources report. */
    val mergeSimilar: Boolean = true,
    /** While the app is open, refreshes stale sources on its own; nothing runs in the background. */
    val autoRefresh: Boolean = true,
    /** Words whose stories get a chip of their own on the home screen. */
    val topics: List<String> = emptyList(),
    /** Shows the three most telling sentences of a long story above its text. */
    val summary: Boolean = true,
    /** After a refresh, fetches the full text of the read-later stories so they open without a network. */
    val prefetchLater: Boolean = false,
    /** Takes stories out of the read-later list after [Limits.LATER_DAYS] days there; the stories stay stored. */
    val laterCleanup: Boolean = false,
    /** Lower-cased (tr-TR) words; stories containing one are hidden everywhere. */
    val mutedWords: List<String> = emptyList(),
)

/** One story opened for reading: only the day, the estimated minutes and the source are kept. */
@Serializable
data class ReadEntry(val epochDay: Long, val minutes: Int, val feedId: String)

@Serializable
data class AppData(
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
    val lastRefreshMillis: Long = 0L,
    val settings: Settings = Settings(),
    val readLog: List<ReadEntry> = emptyList(),
    /** Bumped when a one-time repair of stored data has run. */
    val dataVersion: Int = 0,
)

enum class Filter(val label: String) { ALL("Tümü"), UNREAD("Okunmamış"), READ("Okunan"), LATER("Sonra"), STARRED("Yıldızlı") }

data class ParsedItem(
    val key: String,
    val title: String,
    val link: String,
    val text: String,
    /** Null when the feed gives no usable date. */
    val publishedMillis: Long?,
    val imageUrl: String = "",
    val categories: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
)

data class ParsedFeed(val title: String, val siteUrl: String, val items: List<ParsedItem>)

/** What one network round trip for a feed produced. */
sealed interface FetchOutcome {
    data object NotModified : FetchOutcome
    data class Success(val feed: ParsedFeed, val etag: String, val lastModified: String) : FetchOutcome
    data class Failure(val reason: String) : FetchOutcome
}

/** Progress of an on-demand full text fetch; absent from the map when idle or done. */
sealed interface FullTextState {
    data object Loading : FullTextState
    data class Failed(val reason: String) : FullTextState
}

/** One source of an OPML file: its name, address and the folder (group) it sits in, empty for none. */
data class OpmlEntry(val title: String, val url: String, val group: String = "")

data class DiscoveredFeed(val title: String, val url: String)

/** What adding a source by address led to. */
sealed interface AddResult {
    data object Added : AddResult
    data class Error(val message: String) : AddResult
    data class Candidates(val feeds: List<DiscoveredFeed>) : AddResult
}

/** Minutes read per weekday (Monday first) of the week that contains today. */
data class WeekSummary(
    val totalMinutes: Int,
    val articles: Int,
    val topFeedId: String?,
    val perDay: List<Int>,
    val todayIndex: Int,
)
