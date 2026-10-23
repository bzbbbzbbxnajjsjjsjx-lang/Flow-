package com.akis.app.ui.screens

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules
import com.akis.app.domain.ReaderRules

private val SourceRowHeight = 72.dp

@Composable
fun SourcesScreen(
    feeds: List<Feed>,
    articles: List<Article>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenSource: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSetGroup: (String, String) -> Unit,
    onSetOptions: (String, Boolean, Boolean) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onTint: (String, Int) -> Unit,
    onDiscover: () -> Unit,
    onSnooze: (String, Int) -> Unit,
    calm: Boolean,
    modifier: Modifier = Modifier,
) {
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = remember(feeds) { System.currentTimeMillis() }
    val newest = remember(articles) { articles.groupBy { it.feedId }.mapValues { (_, list) -> list.maxOf { it.publishedMillis } } }
    val perWeek = remember(articles) { ReaderRules.postsPerWeek(articles, System.currentTimeMillis()) }
    val unread = remember(articles) { articles.filter { !it.read }.groupingBy { it.feedId }.eachCount() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Kaynaklar") },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { ActionIconButton(onClick = onDiscover) { Icon(Icons.Filled.Explore, "Kaynakları keşfet") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(Icons.Filled.Add, null)
                Text("Kaynak ekle", Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            if (feeds.isEmpty()) {
                item {
                    Text(
                        "Henüz kaynak yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(feeds, key = { it.id }) { feed ->
                SourceRow(
                    feed,
                    if (calm) 0 else unread[feed.id] ?: 0,
                    now,
                    stats = SourceStats(FeedRules.staleMonths(newest[feed.id], now), perWeek[feed.id] ?: 0, feed.mutedUntil > now),
                    onOpen = { onOpenSource(feed.id) },
                    onGroup = { groupId = feed.id },
                    onDelete = { deleteId = feed.id },
                )
            }
        }
    }

    feeds.firstOrNull { it.id == deleteId }?.let { feed ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Kaynağı sil") },
            text = { Text("“${feed.displayName()}” ve bu kaynaktaki tüm yazılar silinecek.") },
            confirmButton = { TextButton(onClick = { onDelete(feed.id); deleteId = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Vazgeç") } },
        )
    }
    feeds.firstOrNull { it.id == groupId }?.let { feed ->
        FeedSheet(
            feed,
            ReaderRules.groups(feeds),
            FeedActions(
                pick = { onSetGroup(feed.id, it); groupId = null },
                options = { quiet, full -> onSetOptions(feed.id, quiet, full) },
                pin = { onPin(feed.id, it) },
                tint = { onTint(feed.id, it) },
                snooze = { onSnooze(feed.id, it) },
            ),
            onDismiss = { groupId = null },
        )
    }
}

/** What the stored stories say about a source: months without a story (0 while active) and stories per week. */
private data class SourceStats(val staleMonths: Int, val perWeek: Int, val muted: Boolean)

@Composable
private fun SourceRow(
    feed: Feed,
    unread: Int,
    now: Long,
    stats: SourceStats,
    onOpen: () -> Unit,
    onGroup: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().heightIn(min = SourceRowHeight),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(feed, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(feed.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val failed = feed.error.isNotEmpty()
                val status = when {
                    failed -> "Son yenileme başarısız: ${feed.error}"
                    stats.staleMonths > 0 -> "${stats.staleMonths} aydır yeni yazı yok — silmeyi düşünebilirsin"
                    feed.lastFetchedMillis == 0L -> "Henüz yenilenmedi"
                    else -> "Güncellendi ${FeedRules.relativeTime(feed.lastFetchedMillis, now)}"
                }
                val muted = if (stats.muted) " · sessizde" else ""
                val rate = if (!failed && stats.staleMonths == 0 && stats.perWeek > 0) " · haftada ~${stats.perWeek} yazı" else ""
                Text(
                    (if (feed.group.isNotEmpty() && !failed) "${feed.group} · $status" else status) + rate + muted,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        failed -> MaterialTheme.colorScheme.error
                        stats.staleMonths > 0 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unread > 0) Text("$unread", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            ActionIconButton(onClick = onGroup) { Icon(Icons.Filled.Tune, "${feed.displayName()} ayarları") }
            ActionIconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "${feed.displayName()} kaynağını sil") }
        }
    }
}

private val SnoozeOptions = listOf("Kapalı" to 0, "1 gün" to 1, "1 hafta" to SNOOZE_WEEK)
private const val SNOOZE_WEEK = 7
private const val SNOOZE_DAY_MS = 24 * 60 * 60 * 1000L

/** Which snooze choice matches the time a source is muted until: off, one day or one week. */
private fun snoozeChoice(mutedUntil: Long, now: Long): Int = when {
    mutedUntil <= now -> 0
    mutedUntil - now <= SNOOZE_DAY_MS -> 1
    else -> SNOOZE_WEEK
}

/** The badge colour as one tile in the Ayarlar style: the title row, the choices under it across the row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TintTile(selected: Int, onSelect: (Int) -> Unit, shape: Shape) {
    TileCard(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, null, Modifier.padding(end = 16.dp))
                Text("Rozet rengi", style = MaterialTheme.typography.bodyLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (tint in 0..TintColors.size) {
                    val color = TintColors.getOrNull(tint - 1)?.first ?: MaterialTheme.colorScheme.secondaryContainer
                    Surface(
                        onClick = { onSelect(tint) },
                        shape = CircleShape,
                        color = color,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = if (tint == 0) "Varsayılan renk" else "Renk $tint" },
                    ) {
                        if (tint == selected) Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null) }
                    }
                }
            }
        }
    }
}

/** Per-source choices as one connected group of tiles, then the group: pick one, type a new one, or leave it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedSheet(
    feed: Feed,
    groups: List<String>,
    actions: FeedActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) { FeedSheetContent(feed, groups, actions) }
}

/** What the source's settings can change. */
internal class FeedActions(
    val pick: (String) -> Unit,
    val options: (Boolean, Boolean) -> Unit,
    val pin: (Boolean) -> Unit,
    val tint: (Int) -> Unit,
    val snooze: (Int) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedSheetContent(feed: Feed, groups: List<String>, actions: FeedActions, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            feed.displayName(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Group(
            { TintTile(feed.tint, actions.tint, it) },
            {
                SwitchTile(
                    Icons.Filled.PushPin, "Öncelikli kaynak", "Okunmamış yazıları ana listenin başında ayrı bir başlıkla çıkar",
                    feed.pinned, it, actions.pin,
                )
            },
            {
                SwitchTile(
                    Icons.Filled.VisibilityOff, "Ana akışta gösterme", "Okunmamışlar yalnızca bu kaynağın sayfasında, yıldızda ve aramada görünür",
                    feed.quiet, it,
                ) { actions.options(it, feed.autoFull) }
            },
            {
                SwitchTile(
                    Icons.AutoMirrored.Filled.Article, "Tam metni otomatik getir", "Bir yazıyı açınca sayfanın tamamı kendiliğinden gelir",
                    feed.autoFull, it,
                ) { actions.options(feed.quiet, it) }
            },
            {
                val chosen = snoozeChoice(feed.mutedUntil, System.currentTimeMillis())
                ChoiceTile(Icons.Filled.Snooze, "Sustur", SnoozeOptions, chosen, it, actions.snooze)
            },
        )
        Text(
            "Grup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
        )
        if (groups.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g -> FilterChip(selected = feed.group == g, onClick = { actions.pick(g) }, label = { Text(g) }) }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Yeni grup adı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { actions.pick(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Kaydet") }
        if (feed.group.isNotEmpty()) TextButton(onClick = { actions.pick("") }) { Text("Gruptan çıkar") }
    }
}
