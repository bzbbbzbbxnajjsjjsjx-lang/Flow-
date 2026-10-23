package com.akis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akis.app.domain.WeekSummary

private val ShortDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val LongDays = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
private val ChartHeight = 120.dp
private val StatHeight = 88.dp
private const val BAR_WIDTH = 0.6f

/** This week's reading in plain numbers: no streaks, no badges, no goals. */
@Composable
fun SummaryScreen(summary: WeekSummary, weeks: List<Int>, topName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Okuma özeti") },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                    Stat("${summary.totalMinutes}", "Toplam dakika", Modifier.weight(1f))
                    Stat("${summary.articles}", "Yazı sayısı", Modifier.weight(1f))
                    Stat(topName.ifEmpty { "–" }, "En çok okunan kaynak", Modifier.weight(1f))
                }
            }
            item { Chart(summary) }
            item { History(weeks) }
            item {
                Text(
                    if (summary.totalMinutes == 0) "Bu hafta henüz okuma yok" else "Bu hafta ${summary.totalMinutes} dk okudun",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics { heading() },
                )
            }
        }
    }
}

/** The last weeks side by side, oldest first; the current week is highlighted. */
@Composable
private fun History(weeks: List<Int>, modifier: Modifier = Modifier) {
    val peak = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)
    val names = listOf("3 hf önce", "2 hf önce", "Geçen hf", "Bu hafta")
    Surface(modifier.fillMaxWidth(), shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Text("Son ${weeks.size} hafta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                weeks.forEachIndexed { i, minutes ->
                    val name = names.getOrElse(i + names.size - weeks.size) { "" }
                    Column(
                        Modifier.weight(1f).semantics { contentDescription = "$name, $minutes dakika" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$minutes dk", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.fillMaxWidth().height(ChartHeight), contentAlignment = Alignment.BottomCenter) {
                            val color = if (i == weeks.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            Box(
                                Modifier.fillMaxWidth(BAR_WIDTH).height((ChartHeight * minutes / peak).coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color),
                            )
                        }
                        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier.height(StatHeight), shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Chart(summary: WeekSummary, modifier: Modifier = Modifier) {
    val peak = (summary.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    Surface(modifier.fillMaxWidth(), shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            summary.perDay.forEachIndexed { i, minutes ->
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${LongDays[i]}, $minutes dakika" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val color = if (i == summary.todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                    Box(Modifier.fillMaxWidth().height(ChartHeight), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth(BAR_WIDTH).height((ChartHeight * minutes / peak).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(color),
                        )
                    }
                    Text(ShortDays[i], style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
