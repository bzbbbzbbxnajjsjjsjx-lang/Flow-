package com.akis.app.ui.screens

import androidx.compose.ui.graphics.Shape
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.akis.app.domain.ReaderRules
import com.akis.app.domain.Summarizer
import kotlinx.coroutines.launch
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.akis.app.domain.Limits
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules
import com.akis.app.domain.FullTextState
import com.akis.app.domain.Settings
import com.akis.app.ui.SpeechController

private const val WIDE = 16f / 9f

private const val FINISHED = 0.95f

@Composable
fun ReaderScreen(
    article: Article,
    feed: Feed?,
    settings: Settings,
    fullState: FullTextState?,
    onBack: () -> Unit,
    onStar: () -> Unit,
    onLater: () -> Unit,
    onNote: (String) -> Unit,
    onProgress: (Int, Boolean) -> Unit,
    onToggleRead: () -> Unit,
    onFetchFull: () -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val video = remember(article.link) { FeedRules.isVideo(article) }
    val canOpen = article.link.startsWith("https://")
    var wantFull by rememberSaveable(article.id) { mutableStateOf(article.fullText.isNotEmpty() || feed?.autoFull == true) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var speech by remember { mutableStateOf<SpeechController?>(null) }
    DisposableEffect(Unit) { onDispose { speech?.shutdown() } }
    val body = if (wantFull && article.fullText.isNotEmpty()) article.fullText else article.text
    val paragraphs = remember(body) { body.split("\n\n").filter { it.isNotBlank() } }
    val gist = remember(body, settings.summary) { if (settings.summary && !video) Summarizer.top(body) else emptyList() }
    val minutes = remember(body) { FeedRules.readingMinutes(body).coerceAtLeast(1) }
    val size = textSizeFor(settings.textStep)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = article.resume)
    val saveProgress by rememberUpdatedState(onProgress)
    val readProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            ReaderRules.scrollProgress(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, first?.size ?: 0,
                info.totalItemsCount, info.visibleItemsInfo.size,
            )
        }
    }
    DisposableEffect(article.id) { onDispose { saveProgress(listState.firstVisibleItemIndex, readProgress >= FINISHED) } }
    var finding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hit by rememberSaveable { mutableIntStateOf(0) }
    val matches = remember(paragraphs, query, finding) { if (finding) ReaderRules.findParagraphs(paragraphs, query) else emptyList() }
    val scope = rememberCoroutineScope()
    // The paragraphs are the last list items before the note field, so their first index follows from the total.
    val goTo = { n: Int ->
        if (matches.isNotEmpty()) {
            hit = (n + matches.size) % matches.size
            val first = listState.layoutInfo.totalItemsCount - paragraphs.size - 1
            scope.launch { listState.animateScrollToItem(first + matches[hit]) }
        }
    }
    LaunchedEffect(matches) { if (matches.isNotEmpty()) goTo(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(feed.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    ActionIconButton(onClick = { finding = !finding; query = "" }) { Icon(Icons.Filled.Search, "Yazıda ara") }
                    ActionIconButton(onClick = onLater) {
                        Icon(
                            if (article.later) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            if (article.later) "Sonra okumadan çıkar" else "Sonra oku",
                        )
                    }
                    ActionIconButton(onClick = { sheet = true }) { Icon(Icons.Filled.TextFields, "Yazı boyutu ve okuma ayarları") }
                },
            )
        },
        bottomBar = {
            Column {
                speech?.let { SpeechPlayer(it, onClose = { speech?.shutdown(); speech = null }) }
                BottomAppBar(
                actions = {
                    ActionIconButton(onClick = {
                        val current = speech
                        if (current == null) {
                            speech = SpeechController(context).also { it.start(listOf(article.title) + paragraphs) }
                        } else if (current.playing) {
                            current.pause()
                        } else {
                            current.resume()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Sesli oku") }
                    ActionIconButton(onClick = onStar) {
                        Icon(
                            if (article.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (article.starred) "Yıldızı kaldır" else "Yıldızla",
                        )
                    }
                    ActionIconButton(onClick = { share(context, article) }, enabled = article.link.isNotBlank()) {
                        Icon(Icons.Filled.Share, "Paylaş")
                    }
                    ActionIconButton(onClick = { openLink(context, article.link) }, enabled = canOpen) {
                        Icon(Icons.Filled.OpenInBrowser, "Tarayıcıda aç")
                    }
                    ActionIconButton(onClick = onToggleRead) {
                        Icon(
                            if (article.read) Icons.Filled.MarkEmailUnread else Icons.Filled.MarkEmailRead,
                            if (article.read) "Okunmadı yap" else "Okundu yap",
                        )
                    }
                },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (finding) {
                FindBar(
                    query = query,
                    onQuery = { query = it },
                    count = matches.size,
                    position = hit,
                    onPrev = { goTo(hit - 1) },
                    onNext = { goTo(hit + 1) },
                    onClose = { finding = false; query = "" },
                )
            }
            // Reserved height, so the text does not jump when the progress indicator appears.
            Box(Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 20.dp)) {
                if (fullState is FullTextState.Loading) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(progress = { readProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = marginFor(settings.margin), vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (video) {
                    item { VideoHero(article, settings.loadImages, canOpen, onOpen = { openLink(context, article.link) }) }
                } else if (settings.loadImages && article.imageUrl.isNotEmpty()) {
                    item {
                        RemoteImage(
                            article.imageUrl,
                            enabled = true,
                            modifier = Modifier.fillMaxWidth().aspectRatio(WIDE).clip(TileShape),
                            hideWhenMissing = true,
                        )
                    }
                }
                item {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = if (settings.serif) FontFamily.Serif else null,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${feed.displayName()} · ${FeedRules.fullDate(article.publishedMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "$minutes dk okuma",
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (video) {
                    item {
                        Button(onClick = { openLink(context, article.link) }, enabled = canOpen, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text("Videoyu uygulamada aç", Modifier.padding(start = 8.dp))
                        }
                    }
                    item {
                        Text(
                            "Video Akış içinde oynatılmaz; kurulu bir uygulamada (YouTube, NewPipe) açılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        ChoiceGroup(
                            options = listOf("Akıştaki metin" to false, (if (article.fullText.isEmpty()) "Tam metni getir" else "Tam metin") to true),
                            selected = wantFull,
                            onSelect = { full ->
                                wantFull = full
                                if (full && article.fullText.isEmpty() && fullState !is FullTextState.Loading) onFetchFull()
                            },
                        )
                    }
                    if (gist.isNotEmpty()) item { SummaryCard(gist) }
                    if (wantFull && fullState is FullTextState.Loading) {
                        item { Text("Tam metin getiriliyor…", style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (wantFull && fullState is FullTextState.Failed) {
                        item { FullTextError(fullState.reason, canOpen, onRetry = onFetchFull, onOpen = { openLink(context, article.link) }) }
                    }
                }
                if (paragraphs.isEmpty()) {
                    item {
                        Text(
                            "Bu yazının metni akışta yok. Tam metni getirmeyi ya da tarayıcıda açmayı dene.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(paragraphs) {
                    ReaderParagraph(
                        it, settings.fastRead, size, settings.serif,
                        highlight = query, lineFactor = lineFactorFor(settings.spacing),
                    )
                }
                if (article.links.isNotEmpty()) {
                    item {
                        Text(
                            "Yazıdaki bağlantılar",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        )
                    }
                    item {
                        GroupOf(article.links.size) { i, shapes ->
                            val link = article.links[i]
                            LinkTile(
                                Icons.Filled.OpenInBrowser, link.label, linkHost(link.url), shapes,
                                { openLink(context, link.url) }, chevron = false,
                            )
                        }
                    }
                }
                item {
                    var note by rememberSaveable(article.id) { mutableStateOf(article.note) }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(Limits.MAX_NOTE); onNote(note) },
                        label = { Text("Notum") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (sheet) ReadingSheet(settings, onSettings, onDismiss = { sheet = false })
}

/** The story's three most telling sentences, picked on the phone. */
@Composable
private fun SummaryCard(sentences: List<String>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Kısa özet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            sentences.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    position: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Yazıda ara") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        val label = when {
            query.trim().length < 2 -> ""
            count == 0 -> "Yok"
            else -> "${position + 1}/$count"
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        ActionIconButton(onClick = onPrev, enabled = count > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Önceki sonuç") }
        ActionIconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Filled.KeyboardArrowDown, "Sonraki sonuç") }
        ActionIconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Aramayı kapat") }
    }
}

@Composable
private fun VideoHero(article: Article, showImages: Boolean, canOpen: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(WIDE).clickable(enabled = canOpen, onClick = onOpen),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(WIDE), contentAlignment = Alignment.Center) {
            RemoteImage(article.imageUrl, showImages, Modifier.matchParentSize(), missing = { LetterBlock(null, Modifier.matchParentSize()) })
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "YouTube videosu", Modifier.size(36.dp)) }
            }
        }
    }
}

@Composable
private fun FullTextError(reason: String, canOpen: Boolean, onRetry: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tam metin alınamadı: $reason", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRetry) { Text("Tekrar dene") }
                OutlinedButton(onClick = onOpen, enabled = canOpen) { Text("Tarayıcıda aç") }
            }
        }
    }
}

private fun share(context: android.content.Context, article: Article) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
    context.startActivity(Intent.createChooser(send, "Paylaş"))
}

private fun linkHost(url: String): String = url.removePrefix("https://").substringBefore('/').removePrefix("www.")

/** Hands an https link to whatever app handles it (a browser, or YouTube/NewPipe for videos). */
private fun openLink(context: android.content.Context, link: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No app can open it: nothing to do.
    }
}
