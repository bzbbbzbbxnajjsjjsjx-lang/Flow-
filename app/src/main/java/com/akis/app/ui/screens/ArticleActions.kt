package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Article

/** What a long press on a story offers, so a list can be sorted without opening anything. */
class ArticleActions(
    val later: () -> Unit,
    val star: () -> Unit,
    val toggleRead: () -> Unit,
    val share: () -> Unit,
)

@Composable
fun ArticleMenu(
    article: Article,
    actions: ArticleActions,
    others: List<Pair<String, Article>>,
    onOpenOther: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(
                article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp).semantics { heading() },
            )
            Group(
                {
                    LinkTile(
                        if (article.later) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        if (article.later) "Sonra okumadan çıkar" else "Sonra oku", "", it, { actions.later(); onDismiss() }, chevron = false,
                    )
                },
                {
                    LinkTile(
                        if (article.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (article.starred) "Yıldızı kaldır" else "Yıldızla", "", it, { actions.star(); onDismiss() }, chevron = false,
                    )
                },
                {
                    LinkTile(
                        if (article.read) Icons.Filled.MarkEmailUnread else Icons.Filled.MarkEmailRead,
                        if (article.read) "Okunmadı yap" else "Okundu yap", "", it, { actions.toggleRead(); onDismiss() }, chevron = false,
                    )
                },
                { LinkTile(Icons.Filled.Share, "Paylaş", "", it, { actions.share(); onDismiss() }, chevron = false) },
            )
            if (others.isNotEmpty()) {
                Text(
                    "Aynı haber, diğer kaynaklar (${others.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                )
                GroupOf(others.size) { i, shapes ->
                    val (source, story) = others[i]
                    LinkTile(Icons.AutoMirrored.Filled.Article, story.title, source, shapes, { onOpenOther(story.id); onDismiss() }, chevron = false)
                }
            }
        }
    }
}
