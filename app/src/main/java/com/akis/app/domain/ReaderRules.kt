package com.akis.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Rules for the calm-reading features: muted words, search, groups, auto-read and the weekly summary. */
@Suppress("TooManyFunctions")
object ReaderRules {
    private val TR = FeedRules.TR
    private const val DAY_MS = 86_400_000L
    private const val WEEK = 7
    private val whitespace = Regex("\\s+")
    private val urlInText = Regex("https?://[^\\s<>\"']+")

    /** A muted word as stored: trimmed, lower-cased the Turkish way, 2 to [Limits.MAX_MUTED_LEN] characters. */
    fun cleanWord(raw: String): String? =
        raw.trim().replace(whitespace, " ").lowercase(TR).takeIf { it.length in 2..Limits.MAX_MUTED_LEN }

    /** Stories whose title or text mention [topic] (a stored word, already lower-cased). */
    fun byTopic(articles: List<Article>, topic: String): List<Article> =
        articles.filter { "${it.title}\n${it.text}".lowercase(TR).contains(topic) }

    /** "Bugün", "Dün", "Bu hafta" or "Daha eski", for the day headers of a list sorted by date. */
    fun dayBucket(millis: Long, today: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val age = today.toEpochDay() - java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        return when {
            age <= 0 -> "Bugün"
            age == 1L -> "Dün"
            age < DAYS_IN_WEEK -> "Bu hafta"
            else -> "Daha eski"
        }
    }

    private const val DAYS_IN_WEEK = 7L

    fun withoutMuted(articles: List<Article>, words: List<String>): List<Article> =
        if (words.isEmpty()) {
            articles
        } else {
            articles.filterNot { a ->
                val haystack = "${a.title}\n${a.text}".lowercase(TR)
                words.any { haystack.contains(it) }
            }
        }

    /** Case-insensitive (tr-TR) search in title, summary and any fetched full text. */
    fun search(articles: List<Article>, query: String): List<Article> {
        val q = query.trim().lowercase(TR)
        return if (q.isEmpty()) articles else articles.filter { a -> listOf(a.title, a.text, a.fullText).any { it.lowercase(TR).contains(q) } }
    }

    /** Puts [query] first in the search history (tr-TR, no duplicates), at most [Limits.MAX_RECENT_SEARCHES]; short ones are ignored. */
    fun pushRecent(recent: List<String>, query: String): List<String> {
        val q = query.trim().replace(whitespace, " ").take(Limits.MAX_GROUP)
        if (q.length < 2) return recent
        return (listOf(q) + recent.filterNot { it.lowercase(TR) == q.lowercase(TR) }).take(Limits.MAX_RECENT_SEARCHES)
    }

    fun groups(feeds: List<Feed>): List<String> =
        feeds.map { it.group }.filter { it.isNotEmpty() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun cleanGroup(raw: String): String = raw.trim().replace(whitespace, " ").take(Limits.MAX_GROUP)

    /** Puts a source in a group (empty name removes it); a new group beyond [Limits.MAX_GROUPS] is refused. */
    fun setGroup(data: AppData, feedId: String, raw: String): AppData {
        val name = cleanGroup(raw)
        val known = groups(data.feeds)
        return if (name.isNotEmpty() && name !in known && known.size >= Limits.MAX_GROUPS) {
            data
        } else {
            data.copy(feeds = data.feeds.map { if (it.id == feedId) it.copy(group = name) else it })
        }
    }

    fun inGroup(articles: List<Article>, feeds: List<Feed>, group: String): List<Article> {
        val ids = feeds.filter { it.group == group }.map { it.id }.toSet()
        return articles.filter { it.feedId in ids }
    }

    /** Marks unread stories older than [Limits.AUTO_READ_DAYS] days as read; returns the same data when nothing changes. */
    fun autoRead(data: AppData, now: Long): AppData {
        val cutoff = now - Limits.AUTO_READ_DAYS * DAY_MS
        val stale = { a: Article -> !a.read && a.publishedMillis < cutoff }
        return if (!data.settings.autoRead || data.articles.none(stale)) {
            data
        } else {
            data.copy(articles = data.articles.map { if (stale(it)) it.copy(read = true) else it })
        }
    }

    /** True while the home list should rest: mode 1 is 22:00-07:00, mode 2 is 00:00-08:00. */
    fun inQuietHours(mode: Int, hour: Int): Boolean = when (mode) {
        1 -> hour >= QUIET_NIGHT_START || hour < QUIET_MORNING_END
        2 -> hour < QUIET_LATE_END
        else -> false
    }

    private const val QUIET_NIGHT_START = 22
    private const val QUIET_MORNING_END = 7
    private const val QUIET_LATE_END = 8

    /** Starred and read-later stories with their notes, as a Markdown list for sharing or keeping. */
    fun listText(articles: List<Article>): String {
        fun block(title: String, items: List<Article>) = if (items.isEmpty()) "" else buildString {
            append("## ").append(title).append("\n\n")
            items.forEach { a ->
                append("- ").append(if (a.link.isBlank()) a.title else "[${a.title}](${a.link})")
                if (a.note.isNotBlank()) append(" — ").append(a.note.replace('\n', ' '))
                append('\n')
            }
            append('\n')
        }
        val starred = articles.filter { it.starred }
        val later = articles.filter { it.later && !it.starred }
        return (block("Yıldızlı", starred) + block("Sonra okunacak", later)).trim()
    }

    /** Indexes of the paragraphs that contain [query] (tr-TR, at least two characters), in reading order. */
    fun findParagraphs(paragraphs: List<String>, query: String): List<Int> {
        val q = query.trim().lowercase(TR)
        return if (q.length < 2) emptyList() else paragraphs.indices.filter { paragraphs[it].lowercase(TR).contains(q) }
    }

    /** Every note the user wrote, as plain text for sharing: title, address, note. */
    fun notesText(articles: List<Article>): String =
        articles.filter { it.note.isNotBlank() }.joinToString("\n\n") { a ->
            listOf(a.title, a.link, a.note).filter { it.isNotBlank() }.joinToString("\n")
        }

    /** Deletes read stories that nobody asked to keep once they are old; returns the same data when nothing changes. */
    fun cleanup(data: AppData, now: Long): AppData {
        val cutoff = now - Limits.CLEANUP_DAYS * DAY_MS
        val expired = { a: Article -> a.read && !a.starred && !a.later && a.note.isEmpty() && a.publishedMillis in 1 until cutoff }
        return if (!data.settings.cleanup || data.articles.none(expired)) data else data.copy(articles = data.articles.filterNot(expired))
    }

    /** Takes stories out of the read-later list once they have been there [Limits.LATER_DAYS] days; the stories stay. */
    fun cleanLater(data: AppData, now: Long): AppData {
        val cutoff = now - Limits.LATER_DAYS * DAY_MS
        val expired = { a: Article -> a.later && a.laterAt in 1 until cutoff }
        return if (!data.settings.laterCleanup || data.articles.none(expired)) {
            data
        } else {
            data.copy(articles = data.articles.map { if (expired(it)) it.copy(later = false, laterAt = 0L) else it })
        }
    }

    /** All housekeeping passes in one call. */
    fun tidy(data: AppData, now: Long): AppData = cleanup(autoRead(cleanLater(data, now), now), now)

    /** Remembers that a story was opened for the first time (day, minutes, source); older entries age out. */
    fun logRead(data: AppData, article: Article, today: LocalDate): AppData {
        if (article.read) return data
        val minutes = FeedRules.readingMinutes(article.fullText.ifEmpty { article.text }).coerceAtLeast(1)
        val cutoff = today.toEpochDay() - Limits.READ_LOG_DAYS
        val log = (data.readLog + ReadEntry(today.toEpochDay(), minutes, article.feedId))
            .filter { it.epochDay > cutoff }.takeLast(Limits.MAX_READ_LOG)
        return data.copy(readLog = log)
    }

    fun weekSummary(log: List<ReadEntry>, today: LocalDate): WeekSummary {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        val week = log.filter { it.epochDay in start until start + WEEK }
        val perDay = List(WEEK) { i -> week.filter { it.epochDay == start + i }.sumOf { it.minutes } }
        val top = week.groupBy { it.feedId }
            .maxWithOrNull(compareBy<Map.Entry<String, List<ReadEntry>>>({ it.value.sumOf { e -> e.minutes } }, { it.value.size }))?.key
        return WeekSummary(perDay.sum(), week.size, top, perDay, today.dayOfWeek.value - 1)
    }

    /**
     * How far down a story the reader has scrolled, 0 to 1, from the first visible list item: its index and how far it
     * has scrolled out, over the number of items that can scroll away. A story that fits on the screen counts as read.
     */
    fun scrollProgress(firstIndex: Int, firstOffsetPx: Int, firstSizePx: Int, total: Int, visible: Int): Float {
        val scrollable = total - visible
        val fraction = if (firstSizePx > 0) (firstOffsetPx.toFloat() / firstSizePx).coerceIn(0f, 1f) else 0f
        return when {
            total <= 0 -> 0f
            scrollable <= 0 -> 1f
            else -> ((firstIndex + fraction) / scrollable).coerceIn(0f, 1f)
        }
    }

    /** The story left half-read most recently within [Limits.RESUME_DAYS] days, if any. */
    fun resumeCandidate(articles: List<Article>, now: Long): Article? {
        val since = now - Limits.RESUME_DAYS * DAY_MS
        return articles.filter { it.resume > 0 && it.openedMillis >= since }.maxByOrNull { it.openedMillis }
    }

    /** Stories per week each source has published lately, from the last four weeks of stored stories (rounded). */
    fun postsPerWeek(articles: List<Article>, now: Long): Map<String, Int> {
        val since = now - RATE_WEEKS * WEEK * DAY_MS
        return articles.filter { it.publishedMillis >= since }.groupingBy { it.feedId }.eachCount()
            .mapValues { (_, count) -> (count + RATE_WEEKS / 2) / RATE_WEEKS }
    }

    private const val RATE_WEEKS = 4

    /** Minutes read in each of the last [weeks] weeks, oldest first; the last one is the current week. */
    fun weeklyTotals(log: List<ReadEntry>, today: LocalDate, weeks: Int = Limits.HISTORY_WEEKS): List<Int> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        return (weeks - 1 downTo 0).map { back ->
            val start = thisMonday - back * WEEK
            log.filter { it.epochDay in start until start + WEEK }.sumOf { it.minutes }
        }
    }

    private const val REPAIRED_VERSION = 1

    /**
     * One-time repair: stories saved before every HTML name was understood still hold text such as
     * "s&uuml;r&uuml;m". Runs once (guarded by [AppData.dataVersion]) so decoded text is never decoded twice.
     */
    fun repairEntities(data: AppData): AppData {
        val fix = { s: String -> if ('&' in s) Html.unescape(s) else s }
        return if (data.dataVersion >= REPAIRED_VERSION) {
            data
        } else {
            data.copy(
                articles = data.articles.map { it.copy(title = fix(it.title), text = fix(it.text), fullText = fix(it.fullText)) },
                dataVersion = REPAIRED_VERSION,
            )
        }
    }

    /** The first web address in shared text, upgraded to https; null when there is none. */
    fun sharedUrl(text: String): String? =
        urlInText.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';')?.let(FeedRules::upgradeAndNormalize)
}
