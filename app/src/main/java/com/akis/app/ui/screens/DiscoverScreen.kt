package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Catalog
import com.akis.app.domain.CatalogFeed
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules
import com.akis.app.domain.OpmlEntry

/** A short list of well-known feeds by topic, built into the app: nothing is fetched until one is added. */
@Composable
fun DiscoverScreen(
    subscribed: Set<String>,
    onAdd: (List<OpmlEntry>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Keşfet") },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            item {
                Text(
                    "Elle seçilmiş, hepsi denenmiş kaynaklar. Eklediğin kaynaklar kategorisiyle grup olur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            Catalog.byCategory.forEach { (category, feeds) ->
                val missing = feeds.filter { FeedRules.feedId(it.url) !in subscribed }
                item(key = "head-$category") {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        TextButton(onClick = { onAdd(Catalog.entries(category)) }, enabled = missing.isNotEmpty()) {
                            Text(if (missing.isEmpty()) "Hepsi ekli" else "Hepsini ekle (${missing.size})")
                        }
                    }
                }
                item(key = "group-$category") {
                    GroupOf(feeds.size) { i, shape ->
                        val feed = feeds[i]
                        CatalogTile(
                            feed,
                            FeedRules.feedId(feed.url) in subscribed,
                            shape,
                            onAdd = { onAdd(listOf(OpmlEntry(feed.name, feed.url, feed.category))) },
                        )
                    }
                }
            }
        }
    }
}

private fun host(url: String): String = url.removePrefix("https://").substringBefore('/').removePrefix("www.")

@Composable
private fun CatalogTile(feed: CatalogFeed, added: Boolean, shape: Shape, onAdd: () -> Unit) {
    Card(onClick = onAdd, enabled = !added, modifier = Modifier.fillMaxWidth(), shape = shape, colors = tileColors()) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(Feed(FeedRules.feedId(feed.url), feed.url, feed.name), 40.dp)
            TileText(feed.name, host(feed.url), Modifier.weight(1f).padding(horizontal = 16.dp))
            ActionIconButton(onClick = onAdd, enabled = !added) {
                if (added) {
                    Icon(Icons.Filled.Check, "${feed.name} ekli", tint = MaterialTheme.colorScheme.tertiary)
                } else {
                    Icon(Icons.Filled.Add, "${feed.name} ekle")
                }
            }
        }
    }
}
