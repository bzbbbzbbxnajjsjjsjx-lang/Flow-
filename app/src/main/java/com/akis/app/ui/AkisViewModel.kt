package com.akis.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akis.app.data.AppStore
import com.akis.app.data.ArticlePage
import com.akis.app.data.FeedLoader
import com.akis.app.data.ImageStore
import com.akis.app.data.PageResult
import com.akis.app.data.readUpTo
import com.akis.app.domain.AddResult
import com.akis.app.domain.Addresses
import com.akis.app.domain.AppData
import com.akis.app.domain.Backup
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules
import com.akis.app.domain.FetchOutcome
import com.akis.app.domain.FullTextState
import com.akis.app.domain.Limits
import com.akis.app.domain.OpmlEntry
import com.akis.app.domain.ReaderRules
import com.akis.app.domain.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
class AkisViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AppStore(app, viewModelScope)
    private val busy = MutableStateFlow(false)
    private val gate = Semaphore(MAX_PARALLEL)

    private val fullTexts = MutableStateFlow<Map<String, FullTextState>>(emptyMap())

    val data: StateFlow<AppData?> = store.data
    val refreshing: StateFlow<Boolean> = busy
    val fullText: StateFlow<Map<String, FullTextState>> = fullTexts

    init {
        ImageStore.setup(app.cacheDir)
        viewModelScope.launch(Dispatchers.IO) { ImageStore.trim() }
        // Refresh once at start, but only when the last refresh is stale: no background work at all.
        viewModelScope.launch {
            val first = store.data.filterNotNull().first()
            store.update { ReaderRules.tidy(ReaderRules.repairEntities(it), System.currentTimeMillis()) }
            if (first.feeds.isNotEmpty() && System.currentTimeMillis() - first.lastRefreshMillis > STALE_MS) refreshAll()
        }
    }

    /** With the setting on, fetches the full text of a few read-later stories so they open without a network. */
    private fun prefetchLater() {
        val d = store.data.value ?: return
        if (!d.settings.prefetchLater) return
        d.articles.filter { it.later && it.fullText.isEmpty() && !FeedRules.isVideo(it) }.take(Limits.PREFETCH).forEach { fetchFullText(it.id) }
    }

    /** Refreshes only when the last refresh is older than [STALE_MS]; safe to call as often as the screen is visible. */
    fun refreshIfStale() {
        val d = store.data.value ?: return
        if (d.feeds.isNotEmpty() && System.currentTimeMillis() - d.lastRefreshMillis > STALE_MS) refreshAll()
    }

    /** Fetches every feed, at most [MAX_PARALLEL] at a time; each result is applied as soon as it arrives. */
    fun refreshAll() {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val feeds = store.data.value?.feeds.orEmpty()
                coroutineScope {
                    feeds.map { feed ->
                        async(Dispatchers.IO) {
                            val outcome = gate.withPermit { FeedLoader.load(feed) }
                            store.update { FeedRules.applyFetch(it, feed.id, outcome, System.currentTimeMillis()) }
                        }
                    }.awaitAll()
                }
                val now = System.currentTimeMillis()
                store.update { ReaderRules.tidy(it.copy(lastRefreshMillis = now), now) }
                prefetchLater()
            } finally {
                busy.value = false
            }
        }
    }

    /**
     * Adds a source by address. A feed address is added directly; an ordinary site address is searched
     * for the feeds it advertises, which the caller then offers to the user.
     */
    fun addFeed(input: String, onResult: (AddResult) -> Unit) {
        val existing = store.data.value?.feeds.orEmpty()
        val converted = Addresses.convert(input)
        if (converted is Addresses.Result.Unsupported) {
            onResult(AddResult.Error(converted.reason))
            return
        }
        val address = (converted as? Addresses.Result.Converted)?.url ?: input
        when (val check = FeedRules.checkNewFeed(address, existing)) {
            is FeedRules.AddCheck.Error -> onResult(AddResult.Error(check.message))
            is FeedRules.AddCheck.Ok -> viewModelScope.launch {
                val feed = Feed(check.id, check.url)
                when (val probe = withContext(Dispatchers.IO) { FeedLoader.probe(check.url) }) {
                    is FeedLoader.Probe.IsFeed -> {
                        val now = System.currentTimeMillis()
                        store.update { FeedRules.applyFetch(it.copy(feeds = it.feeds + feed), feed.id, probe.success, now) }
                        onResult(AddResult.Added)
                    }
                    is FeedLoader.Probe.Candidates -> {
                        val known = existing.map { it.id }.toSet()
                        val fresh = probe.feeds.filter { FeedRules.feedId(it.url) !in known }
                        onResult(if (fresh.isEmpty()) AddResult.Error("Bu sayfadaki akışlar zaten ekli") else AddResult.Candidates(fresh))
                    }
                    is FeedLoader.Probe.Failure -> onResult(AddResult.Error(probe.reason))
                }
            }
        }
    }

    fun changeSettings(transform: (Settings) -> Settings) = store.update {
        ReaderRules.tidy(it.copy(settings = FeedRules.sanitize(transform(it.settings))), System.currentTimeMillis())
    }

    /** Opening a story for the first time is logged for the weekly summary, then it counts as read. */
    fun open(articleId: String) {
        val data = store.data.value ?: return
        val article = data.articles.firstOrNull { it.id == articleId } ?: return
        store.update { d ->
            val read = FeedRules.setRead(ReaderRules.logRead(d, article, LocalDate.now()), articleId, true)
            FeedRules.setOpened(FeedRules.setLater(read, articleId, false), articleId, System.currentTimeMillis())
        }
        val autoFull = data.feeds.firstOrNull { it.id == article.feedId }?.autoFull == true
        if (autoFull && article.fullText.isEmpty() && !FeedRules.isVideo(article)) fetchFullText(articleId)
    }

    fun setGroup(feedId: String, group: String) = store.update { ReaderRules.setGroup(it, feedId, group) }

    /** Fetches the article's own page and keeps its main text; progress and errors show in [fullText]. */
    fun fetchFullText(articleId: String) {
        val article = store.data.value?.articles?.firstOrNull { it.id == articleId } ?: return
        if (fullTexts.value[articleId] is FullTextState.Loading) return
        fullTexts.update { it + (articleId to FullTextState.Loading) }
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { ArticlePage.load(article.link) }) {
                is PageResult.Text -> {
                    store.update { FeedRules.setFullText(it, articleId, result.text) }
                    fullTexts.update { it - articleId }
                }
                is PageResult.Failure -> fullTexts.update { it + (articleId to FullTextState.Failed(result.reason)) }
            }
        }
    }

    fun clearImages(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ImageStore.clear() }
            onDone()
        }
    }

    fun removeFeed(id: String) = store.update { FeedRules.removeFeed(it, id) }

    fun setRead(articleId: String, read: Boolean) = store.update { FeedRules.setRead(it, articleId, read) }

    fun toggleStar(articleId: String) = store.update { FeedRules.toggleStar(it, articleId) }

    /** Returns the ids that were unread, so the caller can offer to undo. */
    fun markAllRead(): Set<String> {
        val ids = store.data.value?.let(FeedRules::unreadIds).orEmpty()
        store.update(FeedRules::markAllRead)
        return ids
    }

    fun restoreUnread(ids: Set<String>) = store.update { FeedRules.markUnread(it, ids) }

    fun toggleLater(articleId: String) = store.update { d ->
        FeedRules.setLater(d, articleId, d.articles.firstOrNull { it.id == articleId }?.later != true)
    }

    /** Adds the catalog's verified feeds right away (no probing); they keep their category as group. */
    fun addCatalog(entries: List<OpmlEntry>, onDone: (Int) -> Unit) {
        var added = 0
        store.update { current -> FeedRules.addEntries(current, entries).also { added = it.second }.first }
        onDone(added)
        if (added > 0) refreshAll()
    }

    fun saveNote(articleId: String, note: String) = store.update { FeedRules.setNote(it, articleId, note) }

    fun listText(): String = ReaderRules.listText(store.data.value?.articles.orEmpty())

    fun notesText(): String = ReaderRules.notesText(store.data.value?.articles.orEmpty())

    fun backup(uri: Uri, onDone: (Boolean) -> Unit) {
        val text = store.data.value?.let(Backup::encode)
        viewModelScope.launch {
            val ok = text != null && withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    /** Replaces everything with the backup's content, cut down to the usual limits; false when it is no backup. */
    fun restore(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readUpTo(Limits.MAX_BACKUP_BYTES) }
                }.getOrNull()?.decodeToString()?.let(Backup::restore)
            }
            if (restored != null) store.update { restored }
            onDone(restored != null)
            if (restored != null) refreshAll()
        }
    }

    /** A story read to its end is stored as finished, so it is not offered for resuming. */
    fun saveProgress(articleId: String, index: Int, finished: Boolean) =
        store.update { FeedRules.setResume(it, articleId, if (finished) 0 else index) }

    fun setTint(feedId: String, tint: Int) = store.update { FeedRules.setTint(it, feedId, tint) }

    fun snooze(feedId: String, days: Int) = store.update { FeedRules.snooze(it, feedId, days, System.currentTimeMillis()) }

    fun setPinned(feedId: String, pinned: Boolean) = store.update { FeedRules.setPinned(it, feedId, pinned) }

    fun setFeedOptions(feedId: String, quiet: Boolean, autoFull: Boolean) =
        store.update { FeedRules.setFeedOptions(it, feedId, quiet, autoFull) }

    fun importOpml(uri: Uri, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readUpTo(Limits.MAX_OPML_BYTES) }
                }.getOrNull()
            }
            val entries = bytes?.let { FeedLoader.parser(it) }?.let(FeedRules::parseOpmlEntries).orEmpty()
            if (entries.isEmpty()) {
                onDone(null)
            } else {
                var added = 0
                store.update { current -> FeedRules.addEntries(current, entries).also { added = it.second }.first }
                onDone(added)
                if (added > 0) refreshAll()
            }
        }
    }

    fun exportOpml(uri: Uri, onDone: (Boolean) -> Unit) {
        val text = FeedRules.toOpml(store.data.value?.feeds.orEmpty())
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(text.toByteArray()) } ?: error("no stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    private companion object {
        const val MAX_PARALLEL = 4
        const val STALE_MS = 15 * 60_000L
    }
}
