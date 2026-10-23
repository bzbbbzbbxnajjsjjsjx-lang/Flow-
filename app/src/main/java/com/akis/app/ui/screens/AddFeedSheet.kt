package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akis.app.domain.AddResult
import com.akis.app.domain.DiscoveredFeed

/**
 * Takes a feed address or an ordinary site address. [onSubmit] reports what it led to: the source was
 * added, an error, or a list of feeds the page advertises, from which one is chosen and added.
 */
@Composable
fun AddFeedSheet(
    initialUrl: String,
    onSubmit: (String, (AddResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<DiscoveredFeed>>(emptyList()) }
    var chosen by remember { mutableStateOf("") }

    fun submit(address: String) {
        busy = true
        error = null
        onSubmit(address) { result ->
            busy = false
            when (result) {
                AddResult.Added -> Unit
                is AddResult.Error -> error = result.message
                is AddResult.Candidates -> {
                    found = result.feeds
                    chosen = result.feeds.first().url
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kaynak ekle", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                    found = emptyList()
                },
                label = { Text("Akış ya da site adresi") },
                placeholder = { Text("example.com") },
                singleLine = true,
                enabled = !busy,
                isError = error != null,
                supportingText = { Text(error ?: "Yalnızca HTTPS kabul edilir; site adresinde RSS bağlantısı aranır") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            if (found.isNotEmpty()) {
                FoundFeeds(found, chosen, onChoose = { chosen = it })
                Button(onClick = { submit(chosen) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ekle") }
            } else {
                Button(onClick = { submit(url) }, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Doğrula ve ekle")
                }
            }
        }
    }
}

@Composable
private fun FoundFeeds(feeds: List<DiscoveredFeed>, chosen: String, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("${feeds.size} akış bulundu", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
            feeds.forEach { feed ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = chosen == feed.url, onClick = { onChoose(feed.url) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == feed.url, onClick = null)
                    Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(feed.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            feed.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
