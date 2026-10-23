package com.akis.app.ui.screens

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akis.app.data.ImageStore
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One shape and one spacing for every tile keeps the screens on a strict rectangular grid. */
val TileShape = RoundedCornerShape(16.dp)
val TileSpacing = 8.dp
private val ArticleTileHeight = 216.dp
private val TileImageHeight = 104.dp
private const val IMAGE_TARGET_PX = 720
private const val MENU_LABEL = "Seçenekler"
private const val HERO_RATIO = 16f / 9f
private const val HERO_WIDE = 2.4f

fun Feed?.displayName(): String =
    this?.title?.ifBlank { null } ?: this?.url?.substringAfter("://")?.substringBefore('/')?.removePrefix("www.").orEmpty()

/** The badge tints a source can be given: (background, letter). */
val TintColors = listOf(
    Color(0xFF7A2E2E) to Color(0xFFFFDAD6),
    Color(0xFF7A4A1E) to Color(0xFFFFDDB8),
    Color(0xFF6B5E12) to Color(0xFFF5E6A0),
    Color(0xFF2E6B4F) to Color(0xFFC4EED6),
    Color(0xFF1F5F66) to Color(0xFFBFEAF0),
    Color(0xFF2F3B9F) to Color(0xFFDDE2FF),
    Color(0xFF5E2F7A) to Color(0xFFF0D9FF),
    Color(0xFF7A2F59) to Color(0xFFFFD8EA),
)

/** The icon button every bar and row uses: it morphs its shape while pressed, as Material 3 Expressive asks. */
@Composable
fun ActionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun SourceBadge(feed: Feed?, size: Dp, modifier: Modifier = Modifier) {
    val tint = feed?.tint?.let { TintColors.getOrNull(it - 1) }
    Surface(
        shape = RoundedCornerShape(size / 3),
        color = tint?.first ?: MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                FeedRules.badgeLetter(feed),
                style = MaterialTheme.typography.labelLarge,
                color = tint?.second ?: MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private sealed interface ImageState {
    data object Loading : ImageState
    data object Missing : ImageState
    data class Loaded(val image: ImageBitmap) : ImageState
}

/**
 * A picture from the cache or the network, shown while loading as a wavy progress block.
 * When [enabled] is false no request is ever made and [missing] is drawn instead.
 */
@Composable
fun RemoteImage(
    url: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    hideWhenMissing: Boolean = false,
    missing: @Composable () -> Unit = {},
) {
    val initial = remember(url, enabled) {
        when {
            !enabled || url.isEmpty() -> ImageState.Missing
            else -> ImageStore.peek(url)?.let { ImageState.Loaded(it.asImageBitmap()) } ?: ImageState.Loading
        }
    }
    val state by produceState(initial, url, enabled) {
        if (value is ImageState.Loading) {
            val bitmap = withContext(Dispatchers.IO) { ImageStore.load(url, IMAGE_TARGET_PX) }
            value = bitmap?.let { ImageState.Loaded(it.asImageBitmap()) } ?: ImageState.Missing
        }
    }
    when (val s = state) {
        is ImageState.Loaded -> Image(s.image, null, modifier, contentScale = ContentScale.Crop)
        ImageState.Loading -> Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(Modifier.size(32.dp))
        }
        ImageState.Missing -> if (!hideWhenMissing) Box(modifier) { missing() }
    }
}

/** Stands in for a missing picture with the source's letter, in the same space so tiles keep their size. */
@Composable
fun LetterBlock(feed: Feed?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(FeedRules.badgeLetter(feed), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun VideoBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
            Text("Video", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** The lead story: full width, a taller picture and a bigger title; the grid below stays equal. */
@Composable
fun HeroTile(
    article: Article,
    feed: Feed?,
    now: Long,
    showImages: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    similar: Int = 0,
    onMenu: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMenu, onLongClickLabel = MENU_LABEL),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        RemoteImage(
            article.imageUrl,
            showImages,
            Modifier.fillMaxWidth().aspectRatio(if (wide) HERO_WIDE else HERO_RATIO),
            missing = { LetterBlock(feed, Modifier.fillMaxSize()) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(feed, 24.dp)
                Text(
                    feed.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (similar > 0) Text("+$similar kaynak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(
                article.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = if (article.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                FeedRules.relativeTime(article.publishedMillis, now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ArticleTile(
    article: Article,
    feed: Feed?,
    now: Long,
    calm: Boolean,
    showImages: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    similar: Int = 0,
    onMenu: () -> Unit = {},
) {
    val video = remember(article.link) { FeedRules.isVideo(article) }
    Card(
        modifier = modifier.height(ArticleTileHeight).combinedClickable(onClick = onClick, onLongClick = onMenu, onLongClickLabel = MENU_LABEL),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().height(TileImageHeight)) {
            RemoteImage(article.imageUrl, showImages, Modifier.fillMaxSize(), missing = { LetterBlock(feed, Modifier.fillMaxSize()) })
            if (video) VideoBadge(Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(feed, 20.dp)
                Text(
                    feed.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (article.starred) {
                    Icon(Icons.Filled.Star, "Yıldızlı", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                article.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (article.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    FeedRules.relativeTime(article.publishedMillis, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (similar > 0) {
                    Text(
                        "+$similar kaynak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                // Calm mode shows no unread dots: nothing on the screen nags for attention.
                if (!calm && !article.read) {
                    Box(
                        Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .semantics { contentDescription = "Okunmamış" },
                    )
                }
            }
        }
    }
}
