package com.lockcanvas.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lockcanvas.app.data.ColorLayer
import com.lockcanvas.app.data.GroupLayer
import com.lockcanvas.app.data.ImageLayer
import com.lockcanvas.app.data.Layer
import com.lockcanvas.app.data.TextLayer

private val swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF101014, 0xFF3DDC84, 0xFF4285F4,
    0xFFFF4081, 0xFFFFC107, 0xFF7C4DFF, 0xFF00BCD4, 0xFF888888,
)

@Composable
fun PropertiesPanel(
    layer: Layer?,
    fonts: List<String>,
    images: List<String>,
    viewModel: EditorViewModel,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layer == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Select a layer to edit its properties",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
    ) {

        var name by remember(layer.id) { mutableStateOf(layer.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; viewModel.renameSelected(it) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        LabeledSlider("X", layer.x, defaultValue = 0.5f, min = 0f, max = 1f) { viewModel.updateTransform(x = it) }
        LabeledSlider("Y", layer.y, defaultValue = 0.5f, min = 0f, max = 1f) { viewModel.updateTransform(y = it) }
        LabeledSlider("Rotation", layer.rotation, defaultValue = 0f, min = -180f, max = 180f) { viewModel.updateTransform(rotation = it) }
        LabeledSlider("Opacity", layer.alpha, defaultValue = 1f, min = 0f, max = 1f) { viewModel.updateTransform(alpha = it) }
        LabeledSlider("Anchor X", layer.pivotX, defaultValue = 0.5f, min = 0f, max = 1f) { viewModel.updateTransform(pivotX = it) }
        LabeledSlider("Anchor Y", layer.pivotY, defaultValue = 0.5f, min = 0f, max = 1f) { viewModel.updateTransform(pivotY = it) }

        when (layer) {
            is TextLayer -> TextProperties(layer, fonts, viewModel)
            is ImageLayer -> ImageProperties(layer, images, viewModel, onPickImage)
            is ColorLayer -> ColorProperties(layer, viewModel)
            is GroupLayer -> {}
        }
    }
}

/** Human-readable name for a font id (builtin or imported). */
fun fontDisplayName(id: String): String =
    id.substringAfterLast('/')
        .removeSuffix(".ttf")
        .removeSuffix("-Regular")

@Composable
private fun TextProperties(layer: TextLayer, fonts: List<String>, viewModel: EditorViewModel) {
    var template by remember(layer.id) { mutableStateOf(layer.template) }
    OutlinedTextField(
        value = template,
        onValueChange = { template = it; viewModel.updateText(template = it) },
        label = { Text("Text template") },
        supportingText = { Text("Variables: \$TIME_HH\$ \$TIME_MM\$ \$TIME_H12\$ \$TIME_AMPM\$ \$DATE_DD\$ \$BATT\$") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
    LabeledSlider("Font size", layer.fontSize, defaultValue = 0.12f, min = 0.01f, max = 0.6f) { viewModel.updateText(fontSize = it) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Font", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(if (layer.font != null) fontDisplayName(layer.font!!) else "Default")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MenuSurface {
                    DropdownMenuItem(text = { Text("Default") }, onClick = { viewModel.assignFont(null); expanded = false })
                    fonts.forEach { f ->
                        DropdownMenuItem(text = { Text(fontDisplayName(f)) }, onClick = { viewModel.assignFont(f); expanded = false })
                    }
                }
            }
        }
    }

    ColorField("Text color", layer.color) { viewModel.updateText(color = it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bold", modifier = Modifier.weight(1f))
        Switch(checked = layer.bold, onCheckedChange = { viewModel.updateText(bold = it) })
    }
}

@Composable
private fun imageDisplayName(images: List<String>, id: String?): String {
    if (id == null) return "None"
    val idx = images.indexOf(id)
    return if (idx >= 0) "Image ${idx + 1}" else "None"
}

@Composable
private fun ImageProperties(
    layer: ImageLayer,
    images: List<String>,
    viewModel: EditorViewModel,
    onPickImage: () -> Unit,
) {
    LabeledSlider("Width", layer.width, defaultValue = 0.5f, min = 0.01f, max = 1.5f) { viewModel.updateImage(width = it) }
    LabeledSlider("Height", layer.height, defaultValue = 0.5f, min = 0.01f, max = 1.5f) { viewModel.updateImage(height = it) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Source", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) { Text(imageDisplayName(images, layer.asset)) }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MenuSurface {
                    DropdownMenuItem(text = { Text("None") }, onClick = { viewModel.assignImage(null); expanded = false })
                    images.forEachIndexed { index, img ->
                        DropdownMenuItem(
                            text = { Text("Image ${index + 1}") },
                            onClick = { viewModel.assignImage(img); expanded = false },
                        )
                    }
                }
            }
        }
    }
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Text("Replace image (photo picker)", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ColorProperties(layer: ColorLayer, viewModel: EditorViewModel) {
    ColorField("Color", layer.color) { viewModel.updateColor(it) }
}

@Composable
private fun ColorField(label: String, color: Long, onPick: (Long) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        swatches.forEach { c ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(c.toInt()), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/**
 * Slider with a numeric text field (value can be typed by keyboard) and a
 * reset button restoring the layer default.
 */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    var text by remember { mutableStateOf("%.3f".format(value)) }
    LaunchedEffect(value) {
        val parsed = text.trim().toDoubleOrNull()
        if (parsed == null || parsed.toFloat() != value) {
            text = "%.3f".format(value)
        }
    }
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw
                    raw.trim().toDoubleOrNull()?.let { parsed ->
                        onChange(parsed.toFloat().coerceIn(min, max))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
                modifier = Modifier
                    .widthIn(min = 72.dp, max = 108.dp)
                    .height(48.dp)
                    .padding(start = 2.dp, end = 2.dp),
            )
            IconButton(
                onClick = { onChange(defaultValue) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = "Reset $label",
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
        )
    }
}
