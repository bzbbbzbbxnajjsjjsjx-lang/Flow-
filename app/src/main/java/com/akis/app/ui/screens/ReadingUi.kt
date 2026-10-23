package com.akis.app.ui.screens

import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akis.app.domain.FastRead
import com.akis.app.domain.FeedRules
import com.akis.app.domain.Settings

private val TextSizes = listOf(15.sp, 17.sp, 20.sp, 24.sp)
private const val LINE_HEIGHT_FACTOR = 1.75f
private const val PREVIEW = "Kısa bir paragraf burada görünür; seçtiğin boyutu ve hızlı okumayı hemen buradan görebilirsin."

val TextSizeOptions = listOf("Küçük" to 0, "Normal" to 1, "Büyük" to 2, "Çok büyük" to 3)

fun textSizeFor(step: Int): TextUnit = TextSizes[step.coerceIn(0, TextSizes.lastIndex)]

private val LineFactors = listOf(1.5f, LINE_HEIGHT_FACTOR, 2.05f)
private val Margins = listOf(12.dp, 20.dp, 32.dp)

fun lineFactorFor(step: Int): Float = LineFactors[step.coerceIn(0, LineFactors.lastIndex)]

fun marginFor(step: Int): Dp = Margins[step.coerceIn(0, Margins.lastIndex)]

private val FontChoices = listOf("Sade" to false, "Serif" to true)
private val PaletteChoices = listOf("Normal" to 0, "Sepya" to 1, "Siyah" to 2)
val SpacingOptions = listOf("Sıkı" to 0, "Normal" to 1, "Ferah" to 2)
val MarginOptions = listOf("Dar" to 0, "Normal" to 1, "Geniş" to 2)

/** A connected button group where exactly one option is selected. */
@Composable
fun <T> ChoiceGroup(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** A switch whose thumb carries a check mark while it is on. */
@Composable
fun CheckSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        thumbContent = if (checked) {
            { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CheckSwitch(checked, onChange)
    }
}

/** One paragraph of reading text; with [fastRead] the first letters of each word are bold. */
@Composable
fun ReaderParagraph(
    text: String,
    fastRead: Boolean,
    size: TextUnit,
    serif: Boolean,
    modifier: Modifier = Modifier,
    highlight: String = "",
    lineFactor: Float = LINE_HEIGHT_FACTOR,
) {
    val mark = MaterialTheme.colorScheme.tertiaryContainer
    val shown = remember(text, fastRead, highlight, mark) {
        val base = if (fastRead) {
            buildAnnotatedString {
                FastRead.segments(text).forEach { (piece, bold) ->
                    if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(piece) } else append(piece)
                }
            }
        } else {
            AnnotatedString(text)
        }
        marked(base, highlight, mark)
    }
    Text(
        shown,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        fontSize = size,
        lineHeight = size * lineFactor,
        fontFamily = if (serif) FontFamily.Serif else null,
    )
}

/** Puts a background behind every occurrence of [query] (tr-TR, case-insensitive). */
internal fun marked(text: AnnotatedString, query: String, color: Color): AnnotatedString {
    val q = query.trim().lowercase(FeedRules.TR)
    val plain = text.text.lowercase(FeedRules.TR)
    return if (q.length < 2 || plain.length != text.text.length) text else buildAnnotatedString {
        append(text)
        var at = plain.indexOf(q)
        while (at >= 0) {
            addStyle(SpanStyle(background = color), at, at + q.length)
            at = plain.indexOf(q, at + q.length)
        }
    }
}

/** The "Aa" sheet: live preview, then the reading choices as one connected group of tiles, like Ayarlar. */
@Composable
fun ReadingSheet(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) { ReadingSheetContent(settings, onChange) }
}

@Composable
internal fun ReadingSheetContent(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, modifier: Modifier = Modifier) {
    run {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Okuma ayarları",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                ReaderParagraph(
                    PREVIEW, settings.fastRead, textSizeFor(settings.textStep), settings.serif, Modifier.padding(20.dp),
                    lineFactor = lineFactorFor(settings.spacing),
                )
            }
            Group(
                {
                    ChoiceTile(Icons.Filled.TextFields, "Yazı boyutu", TextSizeOptions, settings.textStep, it) { v ->
                        onChange { s -> s.copy(textStep = v) }
                    }
                },
                {
                    ChoiceTile(Icons.Filled.FormatLineSpacing, "Satır aralığı", SpacingOptions, settings.spacing, it) { v ->
                        onChange { s -> s.copy(spacing = v) }
                    }
                },
                {
                    ChoiceTile(Icons.Filled.SpaceBar, "Kenar boşluğu", MarginOptions, settings.margin, it) { v ->
                        onChange { s -> s.copy(margin = v) }
                    }
                },
                {
                    ChoiceTile(Icons.Filled.FontDownload, "Yazı tipi", FontChoices, settings.serif, it) { v ->
                        onChange { s -> s.copy(serif = v) }
                    }
                },
                {
                    SwitchTile(Icons.Filled.FlashOn, "Hızlı okuma", "Kelime başlarını kalın yapar", settings.fastRead, it) { on ->
                        onChange { s -> s.copy(fastRead = on) }
                    }
                },
                {
                    ChoiceTile(Icons.Filled.Brightness6, "Okuma rengi", PaletteChoices, settings.palette, it) { v ->
                        onChange { s -> s.copy(palette = v) }
                    }
                },
            )
            Text(
                "Seçimler hatırlanır",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
