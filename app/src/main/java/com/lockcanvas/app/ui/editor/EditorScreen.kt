package com.lockcanvas.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockcanvas.app.schedule.RenderScheduler

/** Opaque, bordered container for dropdown menu content. */
@Composable
fun MenuSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importImage(it) } }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFont(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AutoLockWp") },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.undoDepth > 0) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.redoDepth > 0) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    var addExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { addExpanded = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add layer")
                    }
                    DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                        MenuSurface {
                            DropdownMenuItem(
                                text = { Text("Text") },
                                leadingIcon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
                                onClick = { viewModel.addLayer(LayerType.TEXT); addExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Image") },
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    addExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Image (existing asset)") },
                                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                                onClick = { viewModel.addLayer(LayerType.IMAGE); addExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Color") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = { viewModel.addLayer(LayerType.COLOR); addExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Group") },
                                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                onClick = { viewModel.addLayer(LayerType.GROUP); addExpanded = false },
                            )
                        }
                    }
                    var settingsExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { settingsExpanded = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    DropdownMenu(expanded = settingsExpanded, onDismissRequest = { settingsExpanded = false }) {
                        MenuSurface {
                            DropdownMenuItem(
                                text = { Text("Import font (TTF)") },
                                leadingIcon = { Icon(Icons.Filled.TextFormat, contentDescription = null) },
                                onClick = {
                                    fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                                    settingsExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Apply now") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = { viewModel.applyOnce(); settingsExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Request exact alarm permission") },
                                leadingIcon = { Icon(Icons.Filled.Alarm, contentDescription = null) },
                                onClick = { RenderScheduler.requestExactPermission(context); settingsExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Lock screen guides " + if (state.showGuides) "ON" else "OFF") },
                                leadingIcon = {
                                    Icon(
                                        if (state.showGuides) Icons.Filled.GridOn else Icons.Filled.GridOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = viewModel::toggleGuides,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            DropdownMenuItem(
                                text = { Text(if (state.enabled) "Lock screen updates ON" else "Lock screen updates OFF") },
                                trailingIcon = {
                                    Switch(
                                        checked = state.enabled,
                                        onCheckedChange = { enabled ->
                                            if (enabled && !RenderScheduler.canScheduleExact(context)) {
                                                RenderScheduler.requestExactPermission(context)
                                            }
                                            viewModel.setEnabled(enabled)
                                        },
                                    )
                                },
                                onClick = {
                                    val enabled = !state.enabled
                                    if (enabled && !RenderScheduler.canScheduleExact(context)) {
                                        RenderScheduler.requestExactPermission(context)
                                    }
                                    viewModel.setEnabled(enabled)
                                },
                            )
                            Text(
                                "Update interval",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                            )
                            RenderScheduler.INTERVAL_CHOICES_MINUTES.forEach { m ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (m >= 60) "${m / 60} h" else "$m min") +
                                                if (m == state.intervalMinutes) "  •" else ""
                                        )
                                    },
                                    onClick = { viewModel.setInterval(m) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
        ) {
        Column(Modifier.fillMaxSize()) {
            PreviewCanvas(
                preset = state.preset,
                selectedLayerId = state.selectedLayerId,
                showGuides = state.showGuides,
                onMoveLayer = { dx, dy ->
                    val sel = viewModel.selectedLayer() ?: return@PreviewCanvas
                    viewModel.updateTransform(
                        x = (sel.x + dx).coerceIn(-0.5f, 1.5f),
                        y = (sel.y + dy).coerceIn(-0.5f, 1.5f),
                    )
                },
                modifier = Modifier.weight(0.65f),
            )
            HorizontalDivider()
            Row(Modifier.weight(0.35f)) {
                LayerPanel(
                    preset = state.preset,
                    selectedLayerId = state.selectedLayerId,
                    onSelect = viewModel::selectLayer,
                    onToggleVisible = viewModel::toggleVisible,
                    onDelete = viewModel::deleteLayer,
                    onMove = viewModel::moveLayer,
                    modifier = Modifier.weight(0.42f),
                )
                VerticalDivider()
                PropertiesPanel(
                    layer = viewModel.selectedLayer(),
                    fonts = state.fonts,
                    images = state.images,
                    viewModel = viewModel,
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                )
            }
        }
        }
    }

    if (state.importFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportFailed,
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportFailed) { Text("OK") }
            },
            title = { Text("Import failed") },
            text = { Text("Could not import the selected file. Try a different one.") },
        )
    }
}
