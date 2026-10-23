package com.akis.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akis.app.domain.Settings

private val LimitOptions = listOf("15" to 15, "30" to 30, "50" to 50, "Sınırsız" to 0)
private val BreakOptions = listOf("Kapalı" to 0, "15 dk" to 15, "30 dk" to 30)
private val ThemeOptions = listOf("Koyu" to 0, "Açık" to 1, "Sistem" to 2)
private val PaletteOptions = listOf("Normal" to 0, "Sepya" to 1, "Siyah" to 2)
private val QuietOptions = listOf("Kapalı" to 0, "22–07" to 1, "00–08" to 2)
private val FontOptions = listOf("Sade" to false, "Serif" to true)
private const val MB = 1024 * 1024

@Composable
fun SettingsScreen(
    settings: Settings,
    cacheBytes: Long,
    onChange: ((Settings) -> Settings) -> Unit,
    onClearCache: () -> Unit,
    onMutedWords: () -> Unit,
    onTopics: () -> Unit,
    onSummary: () -> Unit,
    listState: LazyListState,
    noteCount: Int,
    onShareNotes: () -> Unit,
    onShareList: () -> Unit,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { ActionIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        pendingRestore?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Yedekten geri yükle") },
                text = { Text("Şu anki kaynaklar, yazılar ve ayarlar yedektekilerle değiştirilecek.") },
                confirmButton = { TextButton(onClick = { onRestore(uri); pendingRestore = null }) { Text("Geri yükle") } },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Vazgeç") } },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            item { Section("Sakin akış") }
            item {
                Group(
                    {
                        SwitchTile(
                            Icons.Filled.Spa, "Sakin mod", "Okunmamış noktalarını gizler ve akışı günlük sınırla bitirir",
                            settings.calm, it,
                        ) { on -> onChange { s -> s.copy(calm = on) } }
                    },
                    {
                        ChoiceTile(Icons.Filled.Timer, "Günlük sınır", LimitOptions, settings.dailyLimit, it) { v ->
                            onChange { s -> s.copy(dailyLimit = v) }
                        }
                    },
                    {
                        ChoiceTile(Icons.Filled.NotificationsActive, "Mola hatırlatıcı", BreakOptions, settings.breakMinutes, it) { v ->
                            onChange { s -> s.copy(breakMinutes = v) }
                        }
                    },
                )
            }
            item { Section("Filtreler ve akış") }
            item {
                Group(
                    {
                        LinkTile(
                            Icons.Filled.Block, "Sessiz kelimeler (${settings.mutedWords.size})",
                            "Bu kelimeler geçen yazılar gösterilmez", it, onMutedWords,
                        )
                    },
                    {
                        LinkTile(
                            Icons.Filled.Tag, "Konu takibi (${settings.topics.size})",
                            "Bu kelimeleri içeren yazılar ana ekranda kendi çipinde toplanır", it, onTopics,
                        )
                    },
                    {
                        SwitchTile(
                            Icons.Filled.DoneAll, "7 günden eski okunmamışları otomatik okundu yap",
                            "Biriken yazılar kendiliğinden kapanır", settings.autoRead, it,
                        ) { on -> onChange { s -> s.copy(autoRead = on) } }
                    },
                    {
                        ChoiceTile(Icons.Filled.Bedtime, "Sessiz saatler", QuietOptions, settings.quiet, it) { v ->
                            onChange { s -> s.copy(quiet = v) }
                        }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.PlaylistRemove, "Sonra listesini temizle",
                            "30 günden uzun süredir Sonra'da bekleyen yazılar listeden çıkar; yazılar silinmez",
                            settings.laterCleanup, it,
                        ) { on -> onChange { s -> s.copy(laterCleanup = on) } }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.CloudDownload, "Sonra listesini hazırla",
                            "Yenilemeden sonra Sonra'daki yazıların tam metni indirilir; ağ olmadan açılırlar",
                            settings.prefetchLater, it,
                        ) { on -> onChange { s -> s.copy(prefetchLater = on) } }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.Sync, "Kendiliğinden yenile",
                            "Uygulama açıkken kaynaklar 15 dakikada bir kendiliğinden yenilenir; arka planda çalışmaz",
                            settings.autoRefresh, it,
                        ) { on -> onChange { s -> s.copy(autoRefresh = on) } }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.Merge, "Benzer haberleri birleştir",
                            "Birkaç kaynağın verdiği aynı haber tek kutuda görünür", settings.mergeSimilar, it,
                        ) { on -> onChange { s -> s.copy(mergeSimilar = on) } }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.AutoDelete, "Eski yazıları temizle",
                            "Okunmuş, yıldızsız ve sonraya eklenmemiş yazılar 30 günden sonra silinir", settings.cleanup, it,
                        ) { on -> onChange { s -> s.copy(cleanup = on) } }
                    },
                )
            }
            item { Section("Okuma ve görünüm") }
            item {
                Group(
                    {
                        SwitchTile(
                            Icons.Filled.FlashOn, "Hızlı okuma", "Kelime başlarını kalın yapar", settings.fastRead, it,
                        ) { on -> onChange { s -> s.copy(fastRead = on) } }
                    },
                    {
                        SwitchTile(
                            Icons.Filled.Summarize, "Kısa özet", "Uzun yazıların en önemli 3 cümlesini yazının üstünde gösterir",
                            settings.summary, it,
                        ) { on -> onChange { s -> s.copy(summary = on) } }
                    },
                    {
                        ChoiceTile(Icons.Filled.Palette, "Tema", ThemeOptions, settings.theme, it) { v ->
                            onChange { s -> s.copy(theme = v) }
                        }
                    },
                    {
                        ChoiceTile(Icons.Filled.Brightness6, "Okuma rengi", PaletteOptions, settings.palette, it) { v ->
                            onChange { s -> s.copy(palette = v) }
                        }
                    },
                    {
                        ChoiceTile(Icons.Filled.TextFields, "Yazı tipi", FontOptions, settings.serif, it) { v ->
                            onChange { s -> s.copy(serif = v) }
                        }
                    },
                    { LinkTile(Icons.Filled.BarChart, "Haftalık okuma özeti", "Bu hafta ne kadar okudun", it, onSummary) },
                )
            }
            item { Section("Veri") }
            item {
                Group(
                    {
                        LinkTile(
                            Icons.Filled.Upload, "Yedekle", "Her şeyi tek bir dosyaya kaydeder", it,
                            { backupLauncher.launch("akis-yedek.json") },
                        )
                    },
                    {
                        LinkTile(
                            Icons.Filled.Download, "Yedekten geri yükle", "Mevcut verinin yerine geçer", it,
                            { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        )
                    },
                    { LinkTile(Icons.Filled.Bookmarks, "Okuma listesini paylaş", "Yıldızlı ve sonra okunacaklar, Markdown olarak", it, onShareList) },
                    { LinkTile(Icons.Filled.EditNote, "Notlarımı paylaş", "$noteCount yazıda notun var", it, onShareNotes) },
                )
            }
            item { Section("Görseller") }
            item {
                Group(
                    {
                        SwitchTile(
                            Icons.Filled.Image, "Görselleri yükle",
                            "Kaynak sitelerden indirilir; kapalıyken hiçbir görsel isteği yapılmaz", settings.loadImages, it,
                        ) { on -> onChange { s -> s.copy(loadImages = on) } }
                    },
                    {
                        LinkTile(
                            Icons.Filled.DeleteSweep, "Önbelleği temizle", "${cacheBytes / MB} MB kullanılıyor", it, onClearCache,
                            chevron = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, top = 12.dp).semantics { heading() },
    )
}
