package com.akis.app.ui.screens

import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Explore
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akis.app.domain.AppData
import com.akis.app.domain.FeedRules
import com.akis.app.domain.Adaptive
import com.akis.app.domain.Article
import com.akis.app.domain.Categories
import com.akis.app.domain.Filter
import com.akis.app.domain.Limits
import com.akis.app.domain.ReaderRules
import com.akis.app.domain.Sections
import com.akis.app.domain.Similar

@Composable
fun HomeScreen(
    data: AppData,
    refreshing: Boolean,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onSources: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onMarkAllRead: () -> Unit,
    actionsFor: (Article) -> ArticleActions,
    onDiscover: () -> Unit,
    onImport: (Uri) -> Unit,
    onExport: (Uri) -> Unit,
    onSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = data.settings
    var filter by rememberSaveable { mutableStateOf(Filter.UNREAD) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var shortOnly by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }
    // How many more than the daily limit the reader chose to see; a deliberate tap each time.
    var extra by rememberSaveable { mutableIntStateOf(0) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fabColor = MaterialTheme.colorScheme.tertiaryContainer
    BackHandler(enabled = fabOpen) { fabOpen = false }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }

    val visible = remember(data.articles, settings.mutedWords) { ReaderRules.withoutMuted(data.articles, settings.mutedWords) }
    val groups = remember(data.feeds) { ReaderRules.groups(data.feeds) }
    val activeGroup = if (group in groups) group else ""
    val quiet = remember(data.feeds) {
        val now = System.currentTimeMillis()
        data.feeds.filter { it.quiet || it.mutedUntil > now }.map { it.id }.toSet()
    }
    val homeBase = remember(visible, quiet, filter) {
        if (filter == Filter.UNREAD && quiet.isNotEmpty()) visible.filter { it.feedId !in quiet } else visible
    }
    val inGroup = remember(homeBase, data.feeds, activeGroup) {
        if (activeGroup.isEmpty()) homeBase else ReaderRules.inGroup(homeBase, data.feeds, activeGroup)
    }
    // A category named like a group would only repeat its chip (Keşfet gives sources their category as group).
    val categories = remember(inGroup, groups) {
        val named = groups.map { it.lowercase(FeedRules.TR) }.toSet()
        Categories.top(inGroup, Limits.CATEGORY_CHIPS + named.size).filter { it !in named }.take(Limits.CATEGORY_CHIPS)
    }
    val activeCategory = if (category in categories) category else ""
    val activeTopic = if (topic in settings.topics) topic else ""
    val narrowed = remember(inGroup, activeCategory, shortOnly, activeTopic) {
        Categories.filter(inGroup, activeCategory)
            .let { list -> if (activeTopic.isEmpty()) list else ReaderRules.byTopic(list, activeTopic) }
            .let { list -> if (shortOnly) list.filter { FeedRules.readingMinutes(it.text) <= Limits.SHORT_MINUTES } else list }
    }
    val filtered = remember(narrowed, filter) { FeedRules.filter(narrowed, filter) }
    val collapsed = remember(filtered, settings.mergeSimilar, filter) {
        if (settings.mergeSimilar && filter == Filter.UNREAD) Similar.groups(filtered) else filtered to emptyMap()
    }
    val searchActive = searching && query.isNotBlank()
    val results = remember(visible, query, searchActive) { if (searchActive) ReaderRules.search(visible, query) else emptyList() }
    var quietOverride by rememberSaveable { mutableStateOf(false) }
    val quietBlocked = filter == Filter.UNREAD && !searching && !quietOverride &&
        ReaderRules.inQuietHours(settings.quiet, java.time.LocalTime.now().hour)
    val source = when {
        quietBlocked -> emptyList()
        searchActive -> results
        else -> collapsed.first
    }
    val todayMinutes = remember(data.readLog) {
        ReaderRules.weekSummary(data.readLog, java.time.LocalDate.now()).let { it.perDay.getOrElse(it.todayIndex) { 0 } }
    }
    val limited = !searchActive && settings.calm && settings.dailyLimit > 0
    val shown = remember(source, limited, settings.dailyLimit, extra) {
        if (limited) source.take(settings.dailyLimit + extra) else source
    }
    val hidden = source.size - shown.size
    val columns = Adaptive.columns(LocalConfiguration.current.screenWidthDp)
    // The newest story with a picture leads the list as one wide tile; the rest fill the equal grid.
    val resume = remember(data.articles, filter, searching) {
        if (filter == Filter.UNREAD && !searching) ReaderRules.resumeCandidate(data.articles, System.currentTimeMillis()) else null
    }
    val hero = remember(shown, settings.loadImages, searching) {
        if (searching || !settings.loadImages) null else shown.firstOrNull { it.imageUrl.isNotEmpty() }
    }
    // Day headers only where the list is sorted by date; the read list is sorted by when it was opened.
    val dated = !searchActive && filter != Filter.READ
    val pinnedFeeds = remember(data.feeds, filter) {
        if (filter == Filter.UNREAD) data.feeds.filter { it.pinned }.map { it.id }.toSet() else emptySet()
    }
    val entries = remember(shown, columns, hero, dated, pinnedFeeds, searching) {
        if (searching) emptyList() else listEntries(shown.filter { it.id != hero?.id }, columns, dated, pinnedFeeds)
    }
    val minutes = remember(shown) { shown.sumOf { FeedRules.readingMinutes(it.text) } }
    val allRead = remember(homeBase) { homeBase.isNotEmpty() && homeBase.none { !it.read } }
    val allDone = settings.calm && allRead
    val feeds = remember(data.feeds) { data.feeds.associateBy { it.id } }
    val now = remember(data.articles) { System.currentTimeMillis() }

    val barBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val subtitle = when {
        searchActive -> "${shown.size} yazı bulundu"
        shown.isNotEmpty() -> "${shown.size} yazı • yaklaşık $minutes dk okuma"
        else -> null
    }
    Scaffold(
        modifier = modifier.nestedScroll(barBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                SearchField(query, onQuery = { query = it }, onSubmit = { onSearch(query) }, onClose = { searching = false; query = "" })
            } else LargeFlexibleTopAppBar(
                title = { Text("Akış") },
                subtitle = subtitle?.let { text -> { Text(text) } },
                scrollBehavior = barBehavior,
                actions = {
                    ActionIconButton(onClick = { searching = true }, enabled = data.articles.isNotEmpty()) { Icon(Icons.Filled.Search, "Ara") }
                    ActionIconButton(onClick = onRefresh, enabled = !refreshing && data.feeds.isNotEmpty()) {
                        Icon(Icons.Filled.Refresh, "Yenile")
                    }
                    ActionIconButton(onClick = onSources) { Icon(Icons.Filled.RssFeed, "Kaynaklar") }
                    ActionIconButton(onClick = onSettings) { Icon(Icons.Filled.Tune, "Ayarlar") }
                },
            )
        },
        floatingActionButton = {
            if (data.feeds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = fabColor,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(Icons.Filled.Add, null)
                    Text("Kaynak ekle", Modifier.padding(start = 8.dp))
                }
            } else {
                FloatingActionButtonMenu(
                    expanded = fabOpen,
                    button = {
                        ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }, containerColor = { fabColor }) {
                            val icon by remember { derivedStateOf { if (checkedProgress > FAB_SWAP) Icons.Filled.Close else Icons.Filled.Add } }
                            Icon(icon, if (fabOpen) "Menüyü kapat" else "Menüyü aç", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    },
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; onAdd() },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Kaynak ekle") },
                    )
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; importLauncher.launch(arrayOf("*/*")) },
                        icon = { Icon(Icons.Filled.Download, null) },
                        text = { Text("OPML içe aktar") },
                    )
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; exportLauncher.launch("akis-kaynaklar.opml") },
                        icon = { Icon(Icons.Filled.Upload, null) },
                        text = { Text("OPML dışa aktar") },
                    )
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; onMarkAllRead() },
                        icon = { Icon(Icons.Filled.DoneAll, null) },
                        text = { Text("Tümünü okundu yap") },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Refreshing, by pull or by the button, shows one thing: the expressive morphing shape. No wavy bar.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding),
            state = pullState,
            enabled = data.feeds.isNotEmpty() && !searching,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Column {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                if (!searching) item { Box(inset) { FilterButtons(filter, onSelect = { filter = it; extra = 0 }) } }
                if (groups.isNotEmpty() && !searching) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = activeGroup.isEmpty(), onClick = { group = ""; extra = 0 }, label = { Text("Tümü") }) }
                            items(groups) { g -> FilterChip(selected = activeGroup == g, onClick = { group = g; extra = 0 }, label = { Text(g) }) }
                        }
                    }
                }
                if (!searching) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = shortOnly,
                                    onClick = { shortOnly = !shortOnly; extra = 0 },
                                    label = { Text("Kısa (≤${Limits.SHORT_MINUTES} dk)") },
                                )
                            }
                            items(settings.topics) { t ->
                                FilterChip(
                                    selected = activeTopic == t,
                                    onClick = { topic = if (activeTopic == t) "" else t; extra = 0 },
                                    label = { Text("# $t") },
                                )
                            }
                            items(categories) { c ->
                                FilterChip(
                                    selected = activeCategory == c,
                                    onClick = { category = if (activeCategory == c) "" else c; extra = 0 },
                                    label = { Text(Categories.display(c)) },
                                )
                            }
                        }
                    }
                }
                when {
                    data.feeds.isEmpty() -> item {
                        Box(inset) { EmptyFeeds(onImport = { importLauncher.launch(arrayOf("*/*")) }, onDiscover = onDiscover) }
                    }
                    searching && !searchActive -> item {
                        Box(inset) { RecentSearches(settings.recentSearches, onPick = { query = it; onSearch(it) }, onClear = onClearRecent) }
                    }
                    searchActive && results.isEmpty() -> item { Box(inset) { NoResults(query) } }
                    quietBlocked -> item { Box(inset) { QuietCard(onShow = { quietOverride = true }) } }
                    entries.isEmpty() && hero == null -> item {
                        Text(
                            if (data.articles.isEmpty()) "Henüz yazı yok. Yenilemek için üstteki simgeye dokun." else "Bu filtrede yazı yok.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (allDone && filter == Filter.UNREAD && !searching) item { Box(inset) { AllDone(todayMinutes, onSources) } }
                resume?.let { a -> item(key = "resume-${a.id}") { Box(inset) { ResumeCard(a, onClick = { onOpen(a.id) }) } } }
                hero?.let { top ->
                    item(key = "hero-${top.id}") {
                        HeroTile(
                            top, feeds[top.feedId], now, settings.loadImages, wide = columns > 2,
                            onClick = { onOpen(top.id) },
                            modifier = inset,
                            similar = collapsed.second[top.id]?.size ?: 0,
                            onMenu = { menuId = top.id },
                        )
                    }
                }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is ListEntry.Header -> Text(
                            entry.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp).semantics { heading() },
                        )
                        is ListEntry.Tiles -> Row(inset, horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                            entry.items.forEach {
                                ArticleTile(
                                    it, feeds[it.feedId], now, settings.calm, settings.loadImages, { onOpen(it.id) },
                                    Modifier.weight(1f), similar = collapsed.second[it.id]?.size ?: 0, onMenu = { menuId = it.id },
                                )
                            }
                            repeat(columns - entry.items.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (searchActive) {
                    items(results, key = { "found-${it.id}" }) { found ->
                        SearchResultRow(
                            found, feeds[found.feedId], now, settings.loadImages, query,
                            onClick = { onSearch(query); onOpen(found.id) },
                            modifier = inset,
                        )
                    }
                }
                if (hidden > 0) item { Box(inset) { EndCard(hidden, onMore = { extra += Limits.MORE_STEP }) } }
            }
        }
        }
    }
    data.articles.firstOrNull { it.id == menuId }?.let { story ->
        val others = collapsed.second[story.id].orEmpty().map { feeds[it.feedId].displayName() to it }
        ArticleMenu(story, actionsFor(story), others, onOpenOther = { id -> menuId = null; onOpen(id) }, onDismiss = { menuId = null })
    }
}

private val inset = Modifier.padding(horizontal = 16.dp)

/** One line that takes the reader back to the story left half-read. */
@Composable
private fun ResumeCard(article: Article, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Okumaya devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private sealed interface ListEntry {
    val key: String

    data class Header(val label: String) : ListEntry {
        override val key get() = "day-$label"
    }

    data class Tiles(val items: List<Article>) : ListEntry {
        override val key get() = items.first().id
    }
}

/** The grouped rows of [Sections] as flat list entries: a header, then its rows of tiles. */
private fun listEntries(articles: List<Article>, columns: Int, dated: Boolean, pinned: Set<String>): List<ListEntry> =
    Sections.build(articles, columns, dated, pinned, java.time.LocalDate.now()).flatMap { section ->
        listOfNotNull(section.label?.let { ListEntry.Header(it) }) + section.rows.map { ListEntry.Tiles(it) }
    }

/** The four lists as one connected group of toggle buttons; the selected one morphs to a pill. */
@Composable
private fun FilterButtons(selected: Filter, onSelect: (Filter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        HomeFilters.forEachIndexed { i, f ->
            ToggleButton(
                checked = selected == f,
                onCheckedChange = { onSelect(f) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    HomeFilters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text(f.label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Read stories leave the home list; they stay reachable from their source and, when starred, under the star. */
private val HomeFilters = listOf(Filter.UNREAD, Filter.READ, Filter.LATER, Filter.STARRED)

@Composable
private fun NoResults(query: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(DONE_BLOCK), shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.SearchOff, null, Modifier.size(48.dp)) }
        }
        Text(
            "Eşleşen yazı yok",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        Text(
            "\"${query.trim()}\" için başlıkta, özette veya metinde bir şey bulunamadı.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
    }
}

/** The feed ends here on purpose: scrolling is not the goal. */
@Composable
private fun EndCard(hidden: Int, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Bugünlük bu kadar 🌿", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "Kalan $hidden yazı bekliyor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FilledTonalButton(onClick = onMore) { Text("Daha fazla göster") }
        }
    }
}

@Composable
private fun AllDone(todayMinutes: Int, onSources: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            Modifier.size(DONE_BLOCK),
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Text("🌿", fontSize = DONE_EMOJI_SIZE) }
        }
        Text(
            "Hepsini okudun",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        Text(
            if (todayMinutes > 0) "Bugün $todayMinutes dk okudun. Şimdi telefonu bırakabilirsin" else "Şimdi telefonu bırakabilirsin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        androidx.compose.material3.TextButton(onClick = onSources) { Text("Kaynakları yönet") }
    }
}

@Composable
private fun QuietCard(onShow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌙", fontSize = DONE_EMOJI_SIZE)
        Text(
            "Dinlenme saati",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        Text(
            "Akış şimdilik kapalı. Sonra ve Yıldızlı sekmeleri açık.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        androidx.compose.material3.TextButton(onClick = onShow) { Text("Yine de göster") }
    }
}

@Composable
private fun EmptyFeeds(onImport: () -> Unit, onDiscover: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(EMPTY_BLOCK), shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(contentAlignment = Alignment.Center) { Text("📡", fontSize = EMPTY_EMOJI_SIZE) }
        }
        Text(
            "Henüz kaynak yok",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
        )
        Text(
            "İlk kaynağını ekleyerek başla",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDiscover, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Filled.Explore, null)
            Text("Kaynakları keşfet", Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Download, null)
            Text("OPML içe aktar", Modifier.padding(start = 8.dp))
        }
    }
}

private const val FAB_SWAP = 0.5f
private val EMPTY_BLOCK = 160.dp
private val EMPTY_EMOJI_SIZE = 72.sp
private val DONE_BLOCK = 120.dp
private val DONE_EMOJI_SIZE = 56.sp
