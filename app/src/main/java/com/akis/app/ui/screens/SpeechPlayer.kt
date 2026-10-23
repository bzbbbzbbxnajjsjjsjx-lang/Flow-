package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akis.app.ui.SpeechController

private val SpeedOptions = listOf("0.8x" to 0.8f, "1x" to 1f, "1.25x" to 1.25f)

/** A quiet player above the reader's toolbar: pause or resume, stop, speed, and paragraph progress. */
@Composable
fun SpeechPlayer(speech: SpeechController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = TileShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val error = speech.error
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Kapat") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActionIconButton(onClick = { if (speech.playing) speech.pause() else speech.resume() }) {
                        Icon(if (speech.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (speech.playing) "Duraklat" else "Devam")
                    }
                    ActionIconButton(onClick = { speech.stop(); onClose() }) { Icon(Icons.Filled.Stop, "Durdur") }
                    ChoiceGroup(SpeedOptions, speech.rate, onSelect = speech::changeRate, modifier = Modifier.weight(1f))
                }
                LinearWavyProgressIndicator(
                    progress = { speech.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = speech::cycleSleep) {
                    Text(if (speech.sleepMinutes == 0) "Uyku zamanlayıcısı: kapalı" else "${speech.sleepMinutes} dk sonra durur")
                }
            }
        }
    }
}
