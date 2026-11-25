package com.lockcanvas.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lockcanvas.app.data.GroupLayer
import com.lockcanvas.app.data.Layer

/** Flattened (layer, depth) rows for the layer tree. */
private data class Row(val layer: Layer, val depth: Int)

private fun flatten(layers: List<Layer>, depth: Int = 0): List<Row> =
    layers.flatMap { listOf(Row(it, depth)) + if (it is GroupLayer) flatten(it.children, depth + 1) else emptyList() }

@Composable
fun LayerPanel(
    preset: com.lockcanvas.app.data.Preset,
    selectedLayerId: String?,
    onSelect: (String?) -> Unit,
    onToggleVisible: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(preset) { flatten(preset.root.children) }
    LazyColumn(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        items(rows) { row ->
            val selected = row.layer.id == selectedLayerId
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(row.layer.id) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = (12 + row.depth * 16).dp, end = 4.dp, top = 2.dp, bottom = 2.dp
                    ),
                ) {
                    Icon(
                        imageVector = when (row.layer) {
                            is com.lockcanvas.app.data.TextLayer -> Icons.Filled.TextFields
                            is com.lockcanvas.app.data.ImageLayer -> Icons.Filled.Image
                            is com.lockcanvas.app.data.ColorLayer -> Icons.Filled.Circle
                            is GroupLayer -> Icons.Filled.Folder
                        },
                        contentDescription = null,
                        tint = if (row.layer.visible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = row.layer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.layer.visible) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { onToggleVisible(row.layer.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (row.layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle visibility",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = { onMove(row.layer.id, -1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMove(row.layer.id, 1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(row.layer.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
