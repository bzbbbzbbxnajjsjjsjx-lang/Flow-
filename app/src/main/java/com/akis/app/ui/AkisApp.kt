package com.akis.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akis.app.data.ImageStore
import com.akis.app.domain.AddResult
import com.akis.app.domain.Adaptive
import com.akis.app.domain.Limits
import com.akis.app.domain.ReaderRules
import com.akis.app.domain.Settings
import com.akis.app.ui.screens.AddFeedSheet
import com.akis.app.ui.screens.ArticleActions
import com.akis.app.ui.screens.HomeScreen
import com.akis.app.ui.screens.DiscoverScreen
import com.akis.app.ui.screens.MutedWordsScreen
import com.akis.app.ui.screens.TopicText
import com.akis.app.ui.screens.ReaderScreen
import com.akis.app.ui.screens.SettingsScreen
import com.akis.app.ui.screens.SourceArticlesScreen
import com.akis.app.ui.screens.SourcesScreen
import com.akis.app.ui.screens.SummaryScreen
import com.akis.app.ui.screens.TileShape
import com.akis.app.ui.screens.displayName
import com.akis.app.ui.theme.AkisTheme
import com.akis.app.ui.theme.PALETTE_BLACK
import com.akis.app.ui.theme.PALETTE_SEPIA
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_HOME = "home"
private const val ROUTE_SOURCES = "sources"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_MUTED = "muted"
private const val ROUTE_TOPICS = "topics"
private const val ROUTE_DISCOVER = "discover"
private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_SOURCE = "source"
private const val MINUTE_MS = 60_000L
private const val BREAK_CHECK_MS = 30_000L
private const val REFRESH_CHECK_MS = 5 * MINUTE_MS
private const val AWAY_MS = 5 * MINUTE_MS
private const val THEME_DARK = 0
private const val THEME_LIGHT = 1

@Composable
fun AkisApp(
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
    viewModel: AkisViewModel = viewModel(),
    onUseSharedUrl: () -> Unit = {},
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val fullTexts by viewModel.fullText.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    var articleId by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var addInitial by rememberSaveable { mutableStateOf("") }
    var sessionStart by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var breakDue by rememberSaveable { mutableStateOf(false) }
    var lastStop by remember { mutableLongStateOf(0L) }
    var cacheTick by remember { mutableIntStateOf(0) }
    // Home leaves the composition while a story, the sources or the settings are open; this keeps its
    // scroll position, filter and "show more" choice so coming back lands exactly where you were.
    val stateHolder = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = data
    val settings = current?.settings ?: Settings()
    // Held here so the Ayarlar list keeps its place however the screen is rebuilt after a change.
    val settingsList = rememberLazyListState()
    val systemDark = isSystemInDarkTheme()
    val dark = when {
        settings.palette == PALETTE_SEPIA -> false
        settings.palette == PALETTE_BLACK -> true
        settings.theme == THEME_DARK -> true
        settings.theme == THEME_LIGHT -> false
        else -> systemDark
    }

    fun say(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    // Light theme needs dark status and navigation icons, so the bars follow the theme.
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        (context.findActivity() as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    val useSharedUrl by rememberUpdatedState(onUseSharedUrl)
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            addInitial = sharedUrl
            addOpen = true
            useSharedUrl()
        }
    }
    // A reading session starts when the app comes back after a real absence, not after a glance away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lastStop = System.currentTimeMillis() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (lastStop != 0L && System.currentTimeMillis() - lastStop > AWAY_MS) {
            sessionStart = System.currentTimeMillis()
            breakDue = false
        }
    }
    // Automatic refresh while the app is on screen: once when it becomes visible, then every few minutes.
    val lifecycleOwner = LocalLifecycleOwner.current
    val ready = current != null
    LaunchedEffect(settings.autoRefresh, ready) {
        if (settings.autoRefresh && ready) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshIfStale()
                    delay(REFRESH_CHECK_MS)
                }
            }
        }
    }
    LaunchedEffect(settings.breakMinutes, sessionStart, breakDue) {
        val limitMs = settings.breakMinutes * MINUTE_MS
        while (limitMs > 0 && !breakDue) {
            delay(BREAK_CHECK_MS)
            if (System.currentTimeMillis() - sessionStart >= limitMs) breakDue = true
        }
    }
    val cacheBytes by produceState(0L, route, cacheTick) { value = withContext(Dispatchers.IO) { ImageStore.sizeBytes() } }

    val openArticle = current?.articles?.firstOrNull { it.id == articleId }
    AkisTheme(darkTheme = dark, palette = settings.palette) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Text screens keep a comfortable line length on tablets and unfolded phones.
            val readingWidth = Modifier.align(Alignment.TopCenter).widthIn(max = Adaptive.READING_MAX_DP.dp).fillMaxSize()
            if (current == null) {
                CircularWavyProgressIndicator(Modifier.align(Alignment.Center))
            } else if (openArticle != null) {
                BackHandler { articleId = null }
                ReaderScreen(
                    modifier = readingWidth,
                    article = openArticle,
                    feed = current.feeds.firstOrNull { it.id == openArticle.feedId },
                    settings = settings,
                    fullState = fullTexts[openArticle.id],
                    onBack = { articleId = null },
                    onStar = { viewModel.toggleStar(openArticle.id) },
                    onLater = { viewModel.toggleLater(openArticle.id) },
                    onNote = { viewModel.saveNote(openArticle.id, it) },
                    onProgress = { index, finished -> viewModel.saveProgress(openArticle.id, index, finished) },
                    onToggleRead = { viewModel.setRead(openArticle.id, !openArticle.read) },
                    onFetchFull = { viewModel.fetchFullText(openArticle.id) },
                    onSettings = viewModel::changeSettings,
                )
            } else when (route) {
                ROUTE_SOURCES -> {
                    BackHandler { route = ROUTE_HOME }
                    SourcesScreen(
                        modifier = readingWidth,
                        feeds = current.feeds,
                        articles = current.articles,
                        onBack = { route = ROUTE_HOME },
                        onAdd = { addOpen = true },
                        onOpenSource = { sourceId = it; route = ROUTE_SOURCE },
                        onDelete = viewModel::removeFeed,
                        onSetGroup = viewModel::setGroup,
                        onSetOptions = viewModel::setFeedOptions,
                        onPin = viewModel::setPinned,
                        onSnooze = viewModel::snooze,
                        onDiscover = { route = ROUTE_DISCOVER },
                        onTint = viewModel::setTint,
                        calm = settings.calm,
                    )
                }
                ROUTE_SOURCE -> {
                    BackHandler { route = ROUTE_SOURCES }
                    val feed = current.feeds.firstOrNull { it.id == sourceId }
                    if (feed == null) {
                        LaunchedEffect(Unit) { route = ROUTE_SOURCES }
                    } else {
                        SourceArticlesScreen(
                            feed = feed,
                            articles = current.articles,
                            calm = settings.calm,
                            showImages = settings.loadImages,
                            onOpen = {
                                viewModel.open(it)
                                articleId = it
                            },
                            onBack = { route = ROUTE_SOURCES },
                        )
                    }
                }
                ROUTE_SETTINGS -> {
                    BackHandler { route = ROUTE_HOME }
                    SettingsScreen(
                        modifier = readingWidth,
                        settings = settings,
                        cacheBytes = cacheBytes,
                        onChange = viewModel::changeSettings,
                        onClearCache = { viewModel.clearImages { cacheTick++ } },
                        onMutedWords = { route = ROUTE_MUTED },
                        onTopics = { route = ROUTE_TOPICS },
                        onSummary = { route = ROUTE_SUMMARY },
                        listState = settingsList,
                        noteCount = current.articles.count { it.note.isNotBlank() },
                        onShareNotes = {
                            val text = viewModel.notesText()
                            if (text.isBlank()) say("Henüz notun yok") else shareText(context, text)
                        },
                        onShareList = {
                            val text = viewModel.listText()
                            if (text.isBlank()) say("Yıldızlı ya da sonra okunacak yazı yok") else shareText(context, text)
                        },
                        onBackup = { uri -> viewModel.backup(uri) { ok -> say(if (ok) "Yedek kaydedildi" else "Yedek yazılamadı") } },
                        onRestore = { uri -> viewModel.restore(uri) { ok -> say(if (ok) "Yedek geri yüklendi" else "Dosya bir Akış yedeği değil") } },
                        onBack = { route = ROUTE_HOME },
                    )
                }
                ROUTE_DISCOVER -> {
                    BackHandler { route = ROUTE_SOURCES }
                    DiscoverScreen(
                        modifier = readingWidth,
                        subscribed = remember(current.feeds) { current.feeds.map { it.id }.toSet() },
                        onAdd = { entries ->
                            viewModel.addCatalog(entries) { added -> say(if (added == 0) "Hepsi zaten ekli" else "$added kaynak eklendi") }
                        },
                        onBack = { route = ROUTE_SOURCES },
                    )
                }
                ROUTE_TOPICS -> {
                    BackHandler { route = ROUTE_SETTINGS }
                    MutedWordsScreen(
                        modifier = readingWidth,
                        words = settings.topics,
                        onAdd = { word -> viewModel.changeSettings { it.copy(topics = it.topics + word) } },
                        onRemove = { word -> viewModel.changeSettings { it.copy(topics = it.topics - word) } },
                        onBack = { route = ROUTE_SETTINGS },
                        text = TopicText,
                        max = Limits.MAX_TOPICS,
                    )
                }
                ROUTE_MUTED -> {
                    BackHandler { route = ROUTE_SETTINGS }
                    MutedWordsScreen(
                        modifier = readingWidth,
                        words = settings.mutedWords,
                        onAdd = { word -> viewModel.changeSettings { it.copy(mutedWords = it.mutedWords + word) } },
                        onRemove = { word -> viewModel.changeSettings { it.copy(mutedWords = it.mutedWords - word) } },
                        onBack = { route = ROUTE_SETTINGS },
                    )
                }
                ROUTE_SUMMARY -> {
                    BackHandler { route = ROUTE_SETTINGS }
                    val summary = remember(current.readLog) { ReaderRules.weekSummary(current.readLog, LocalDate.now()) }
                    SummaryScreen(
                        modifier = readingWidth,
                        summary = summary,
                        weeks = remember(current.readLog) { ReaderRules.weeklyTotals(current.readLog, LocalDate.now()) },
                        topName = current.feeds.firstOrNull { it.id == summary.topFeedId }.displayName(),
                        onBack = { route = ROUTE_SETTINGS },
                    )
                }
                else -> {
                    stateHolder.SaveableStateProvider(ROUTE_HOME) {
                        HomeScreen(
                            data = current,
                            refreshing = refreshing,
                            snackbar = snackbar,
                            onRefresh = viewModel::refreshAll,
                            onOpen = {
                                viewModel.open(it)
                                articleId = it
                            },
                            onSources = { route = ROUTE_SOURCES },
                            onSettings = { route = ROUTE_SETTINGS },
                            onAdd = { addOpen = true },
                            onDiscover = { route = ROUTE_DISCOVER },
                            onSearch = { q -> viewModel.changeSettings { it.copy(recentSearches = ReaderRules.pushRecent(it.recentSearches, q)) } },
                            onClearRecent = { viewModel.changeSettings { it.copy(recentSearches = emptyList()) } },
                            actionsFor = { a ->
                                ArticleActions(
                                    later = { viewModel.toggleLater(a.id) },
                                    star = { viewModel.toggleStar(a.id) },
                                    toggleRead = { viewModel.setRead(a.id, !a.read) },
                                    share = { shareText(context, "${a.title}\n${a.link}", "Paylaş") },
                                )
                            },
                            onMarkAllRead = {
                                val ids = viewModel.markAllRead()
                                scope.launch {
                                    val result = snackbar.showSnackbar("Hepsi okundu", actionLabel = "Geri al")
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreUnread(ids)
                                }
                            },
                            onImport = { uri ->
                                viewModel.importOpml(uri) { added ->
                                    say(if (added == null) "OPML dosyası okunamadı" else "$added kaynak eklendi")
                                }
                            },
                            onExport = { uri ->
                                viewModel.exportOpml(uri) { ok -> say(if (ok) "OPML kaydedildi" else "OPML yazılamadı") }
                            },
                        )
                    }
                }
            }
            if (breakDue) {
                BreakCard(
                    minutes = settings.breakMinutes,
                    onContinue = {
                        breakDue = false
                        sessionStart = System.currentTimeMillis()
                    },
                    onFinish = { context.findActivity()?.finish() },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                )
            }
        }

        if (addOpen && current != null) {
            AddFeedSheet(
                initialUrl = addInitial,
                onSubmit = { url, done ->
                    viewModel.addFeed(url) { result ->
                        done(result)
                        if (result is AddResult.Added) {
                            addOpen = false
                            addInitial = ""
                        }
                    }
                },
                onDismiss = {
                    addOpen = false
                    addInitial = ""
                },
            )
        }
    }
}

private fun shareText(context: Context, text: String, title: String = "Notlarım") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** A quiet nudge that never blocks reading: continue, or close the app. */
@Composable
private fun BreakCard(minutes: Int, onContinue: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "$minutes dakikadır okuyorsun. Kısa bir mola iyi gelir.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onContinue) { Text("Devam et") }
                OutlinedButton(onClick = onFinish) { Text("Bitir") }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
