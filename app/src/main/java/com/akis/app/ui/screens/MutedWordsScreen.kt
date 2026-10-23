package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Limits
import com.akis.app.domain.ReaderRules

/** Words that hide a story everywhere: in the feed, in search results and in the counts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MutedWordsScreen(
    words: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    text: WordsText = MutedText,
    max: Int = Limits.MAX_MUTED,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val clean = ReaderRules.cleanWord(typed)
    val full = words.size >= max
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text.title) },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(text.label) },
                singleLine = true,
                enabled = !full,
                supportingText = { Text(if (full) "En fazla $max kelime eklenebilir" else "En az 2 harf") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    clean?.let(onAdd)
                    typed = ""
                },
                enabled = clean != null && !full,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ekle") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { word ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(word) },
                        label = { Text(word) },
                        trailingIcon = { Icon(Icons.Filled.Close, "$word kelimesini kaldır", Modifier.size(18.dp)) },
                    )
                }
            }
            if (words.isEmpty()) {
                Text(text.empty, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
