package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Adaptive
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules

/** Every stored story of one source, read or not: the place to find something that left the home list. */
@Composable
fun SourceArticlesScreen(
    feed: Feed,
    articles: List<Article>,
    calm: Boolean,
    showImages: Boolean,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember(articles) { System.currentTimeMillis() }
    val columns = Adaptive.columns(windowWidthDp())
    val rows = remember(articles, feed.id, columns) { FeedRules.newestFirst(articles.filter { it.feedId == feed.id }).chunked(columns) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(feed.displayName(), maxLines = 1) },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Bu kaynakta henüz yazı yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rows, key = { it.first().id }) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                    pair.forEach { ArticleTile(it, feed, now, calm, showImages, { onOpen(it.id) }, Modifier.weight(1f)) }
                    repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
