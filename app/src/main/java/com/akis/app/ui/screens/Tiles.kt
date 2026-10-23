package com.akis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Connected tiles shared by Ayarlar, the reader's "Aa" sheet, Keşfet and the menus: big corners outside,
// small ones between. This is the Ayarlar look the user approved in 1.15.0; do not change its structure:
// a row is icon, text and a control; a choice puts its buttons under the title across the whole row.
private val TileHeight = 72.dp
private val OuterCorner = 28.dp
private val InnerCorner = 4.dp

/** The shape of tile [i] of [count] in a connected group: big corners outside, small ones between. */
private fun groupShape(i: Int, count: Int): Shape {
    val top = if (i == 0) OuterCorner else InnerCorner
    val bottom = if (i == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** One section: its tiles form a connected group. */
@Composable
internal fun Group(vararg tiles: @Composable (Shape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tiles.forEachIndexed { i, tile -> tile(groupShape(i, tiles.size)) }
    }
}

/** A connected group of [count] tiles built from their index, for lists whose length is not fixed. */
@Composable
internal fun GroupOf(count: Int, tile: @Composable (index: Int, shape: Shape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until count) tile(i, groupShape(i, count))
    }
}

@Composable
internal fun tileColors(): CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

@Composable
internal fun TileCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), shape = shape, colors = tileColors()) { content() }
}

@Composable
internal fun TileText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    shape: Shape,
    onChange: (Boolean) -> Unit,
) {
    TileCard(shape) {
        Row(Modifier.heightIn(min = TileHeight).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.padding(end = 16.dp))
            SwitchRow(title, subtitle, checked, onChange, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun <T> ChoiceTile(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    shape: Shape,
    onSelect: (T) -> Unit,
) {
    TileCard(shape) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.padding(end = 16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge)
            }
            ChoiceGroup(options, selected, onSelect)
        }
    }
}

@Composable
internal fun LinkTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    onClick: () -> Unit,
    chevron: Boolean = true,
) {
    Card(onClick, Modifier.fillMaxWidth(), shape = shape, colors = tileColors()) {
        Row(Modifier.heightIn(min = TileHeight).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.padding(end = 16.dp))
            TileText(title, subtitle, Modifier.weight(1f))
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
