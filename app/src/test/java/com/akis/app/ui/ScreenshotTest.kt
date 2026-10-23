package com.akis.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.akis.app.domain.AppData
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.Link
import com.akis.app.domain.ReaderRules
import com.akis.app.domain.Settings
import com.akis.app.ui.screens.ArticleActions
import com.akis.app.ui.screens.DiscoverScreen
import com.akis.app.ui.screens.FeedActions
import com.akis.app.ui.screens.FeedSheetContent
import com.akis.app.ui.screens.HomeScreen
import com.akis.app.ui.screens.ReaderScreen
import com.akis.app.ui.screens.ReadingSheetContent
import com.akis.app.ui.screens.RecentSearches
import com.akis.app.ui.screens.SearchField
import com.akis.app.ui.screens.SearchResultRow
import com.akis.app.ui.screens.SettingsScreen
import com.akis.app.ui.screens.SourcesScreen
import com.akis.app.ui.screens.SummaryScreen
import com.akis.app.ui.theme.AkisTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens into `app/src/test/screenshots/` so a change can be looked at before it ships.
 * Record with `./gradlew recordRoborazziDebug`, check with `./gradlew verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val minute = 60_000L
    private val now = 1_792_000_000_000L
    private val feeds = listOf(
        Feed("f1", "https://webtekno.com/rss", "Webtekno", group = "Teknoloji", pinned = true),
        Feed("f2", "https://bbc.com/rss", "BBC Türkçe", group = "Haber", tint = 3),
        Feed("f3", "https://xkcd.com/atom.xml", "xkcd", lastFetchedMillis = now - 5 * minute),
    )
    private val body = (1..14).joinToString("\n\n") {
        "Bu, $it. paragraf. Akış sakin bir okuma deneyimi sunar; yazılar tek tek açılır ve gün sonunda biter. " +
            "Okuyucu metni düz yazı olarak gösterir, bağlantılar ve görseller ayrıca listelenir."
    }
    private val articles = listOf(
        article(
            "a1", "f1", "Android 17 için uygulama bellek sınırları cihazın RAM miktarına göre belirlenecek", 12, true,
            listOf("teknoloji", "mobil"),
        ),
        article("a2", "f2", "Merkez Bankası faiz kararını açıkladı: piyasalar nasıl tepki verdi?", 40, false, listOf("ekonomi")),
        article("a3", "f1", "Yeni nesil işlemciler enerji verimliliğinde çıtayı yükseltiyor", 95, false, listOf("teknoloji")),
        article("a4", "f3", "Hava durumu", 300, false, emptyList()),
        article("a5", "f2", "Kış lastiği zorunluluğu için tarih belli oldu", 600, false, listOf("haber")),
        article("a6", "f1", "Samsung Galaxy Fit 4 ortaya çıktı", 900, false, listOf("teknoloji")),
    )
    private val data = AppData(
        feeds = feeds,
        articles = articles,
        lastRefreshMillis = now,
        settings = Settings(topics = listOf("android")),
    )

    private fun article(id: String, feed: String, title: String, ageMin: Long, image: Boolean, cats: List<String>) = Article(
        id = id, feedId = feed, title = title, link = "https://example.com/$id", text = body.take(1_200),
        publishedMillis = now - ageMin * minute, imageUrl = if (image) "https://example.com/$id.jpg" else "",
        categories = cats,
    )

    private val noActions = { _: Article -> ArticleActions({}, {}, {}, {}) }

    @Composable
    private fun Home(d: AppData) = HomeScreen(
        data = d, refreshing = false, snackbar = SnackbarHostState(), onRefresh = {}, onOpen = {}, onSources = {},
        onSettings = {}, onAdd = {}, onMarkAllRead = {}, actionsFor = noActions, onDiscover = {}, onImport = {}, onExport = {},
        onSearch = {}, onClearRecent = {},
    )

    @Test fun home() = capture("home") { Home(data) }

    @Test fun homeLight() = capture("home_light", dark = false) { Home(data.copy(settings = data.settings.copy(theme = 1))) }

    @Test fun homeSepia() = capture("home_sepia", palette = 1, dark = false) { Home(data.copy(settings = data.settings.copy(palette = 1))) }

    @Test fun homeBlack() = capture("home_black", palette = 2) { Home(data.copy(settings = data.settings.copy(palette = 2))) }

    @Test fun homeEmpty() = capture("home_empty") { Home(AppData()) }

    @Test fun searchEmpty() = capture("search_empty") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
            SearchField("", {}, {}, {})
            RecentSearches(listOf("yapay zeka", "uzay", "bisiklet"), {}, {})
        }
    }

    @Test fun searchResults() = capture("search_results") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(horizontal = 16.dp)) {
            SearchField(articles[0].title.take(5), {}, {}, {})
            articles.take(4).forEach { SearchResultRow(it, feeds.first(), now, false, articles[0].title.take(5), {}) }
        }
    }

    @Test fun settings() = capture("settings") { Settings(0) }

    @Test fun settingsMiddle() = capture("settings_2") { Settings(4) }

    @Test fun settingsEnd() = capture("settings_3") { Settings(8) }

    @Composable
    private fun Settings(firstItem: Int) = SettingsScreen(
        settings = data.settings, cacheBytes = 12L * 1024 * 1024, onChange = {}, onClearCache = {}, onMutedWords = {}, onTopics = {},
        onSummary = {}, listState = rememberLazyListState(initialFirstVisibleItemIndex = firstItem), noteCount = 2, onShareNotes = {},
        onShareList = {}, onBackup = {}, onRestore = {}, onBack = {},
    )

    @Test fun reader() = capture("reader") {
        ReaderScreen(
            article = articles[0].copy(
                text = body, links = listOf(Link("Ayrıntılar", "https://example.com/a"), Link("Kaynak", "https://kaynak.com/h")),
                note = "Sonra tekrar bak.",
            ),
            feed = feeds[0], settings = data.settings, fullState = null, onBack = {}, onStar = {},
            onLater = {}, onNote = {}, onProgress = { _, _ -> }, onToggleRead = {}, onFetchFull = {}, onSettings = {},
        )
    }

    @Test fun sources() = capture("sources") {
        SourcesScreen(
            feeds = feeds, articles = articles, onBack = {}, onAdd = {}, onOpenSource = {}, onDelete = {}, onSetGroup = { _, _ -> },
            onSetOptions = { _, _, _ -> }, onPin = { _, _ -> }, onTint = { _, _ -> }, onSnooze = { _, _ -> }, onDiscover = {}, calm = true,
        )
    }

    @Test fun sourceSettings() = capture("source_settings") {
        FeedSheetContent(
            feed = feeds[0], groups = listOf("Haber", "Teknoloji"),
            actions = FeedActions({}, { _, _ -> }, {}, {}, {}),
        )
    }

    @Test fun readingSheet() = capture("reading_sheet") { ReadingSheetContent(data.settings, onChange = {}) }

    @Test fun discover() = capture("discover") { DiscoverScreen(subscribed = setOf(feeds[0].id), onAdd = {}, onBack = {}) }

    @Test fun summary() = capture("summary") {
        val log = listOf(
            com.akis.app.domain.ReadEntry(LocalDate.of(2026, 10, 19).toEpochDay(), 12, "f1"),
            com.akis.app.domain.ReadEntry(LocalDate.of(2026, 10, 21).toEpochDay(), 30, "f2"),
            com.akis.app.domain.ReadEntry(LocalDate.of(2026, 10, 22).toEpochDay(), 8, "f1"),
        )
        SummaryScreen(
            summary = ReaderRules.weekSummary(log, LocalDate.of(2026, 10, 22)),
            weeks = listOf(40, 95, 70, 50), topName = "Webtekno", onBack = {},
        )
    }

    private fun capture(name: String, dark: Boolean = true, palette: Int = 0, screen: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AkisTheme(darkTheme = dark, palette = palette) { screen() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
