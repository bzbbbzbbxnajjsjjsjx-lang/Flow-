package com.akis.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Article
import com.akis.app.domain.Feed
import com.akis.app.domain.FeedRules

private val Thumb = 64.dp

/** The search field: a back button and one rounded pill that holds the query. */
@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionIconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Aramadan çık") }
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Yazılarda ara") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) ActionIconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Aramayı temizle") }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).padding(end = 12.dp).focusRequester(focus),
        )
    }
}

/** Before anything is typed: the last searches, or a short hint when there are none. */
@Composable
fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (recent.isEmpty()) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(24.dp))
                    Text(
                        "Başlıkta, özette ve açtığın tam metinlerde arar.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Son aramalar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).semantics { heading() },
                )
                TextButton(onClick = onClear) { Text("Temizle") }
            }
            recent.forEach { term ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(term) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(term, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** One result: a picture (or the source letter), the title with the match marked, and where and when. */
@Composable
fun SearchResultRow(
    article: Article,
    feed: Feed?,
    now: Long,
    showImages: Boolean,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mark = MaterialTheme.colorScheme.tertiaryContainer
    val title = remember(article.title, query, mark) { marked(AnnotatedString(article.title), query, mark) }
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(Thumb), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            RemoteImage(article.imageUrl, showImages, Modifier.fillMaxSize(), missing = { LetterBlock(feed, Modifier.fillMaxSize()) })
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${feed.displayName()} • ${FeedRules.relativeTime(article.publishedMillis, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
