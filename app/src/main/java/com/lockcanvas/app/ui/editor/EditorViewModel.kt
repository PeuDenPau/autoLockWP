package com.lockcanvas.app.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockcanvas.app.data.*
import com.lockcanvas.app.render.AssetStore
import com.lockcanvas.app.schedule.RenderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LayerType { IMAGE, COLOR, TEXT, GROUP }

data class EditorUiState(
    val loading: Boolean = true,
    val preset: Preset = Preset(),
    val selectedLayerId: String? = null,
    val enabled: Boolean = false,
    val intervalMinutes: Int = 1,
    val fonts: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val importFailed: Boolean = false,
    val undoDepth: Int = 0,
    val redoDepth: Int = 0,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PresetStore(app)
    private val assets = AssetStore(app)

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<Preset>()
    private val redoStack = ArrayDeque<Preset>()

    init {
        viewModelScope.launch {
            val preset = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrNull() ?: Preset() }
            _state.value = EditorUiState(
                loading = false,
                preset = preset,
                selectedLayerId = preset.root.children.lastOrNull()?.id,
                enabled = store.enabled,
                intervalMinutes = store.intervalMinutes,
                fonts = assets.builtinFonts() + assets.availableAssets().filter { it.endsWith("ttf") },
                images = assets.availableAssets().filter { it.endsWith("img") },
            )
        }
    }

    fun selectedLayer(): Layer? {
        val s = _state.value
        return s.preset.root.children.firstOrNull { it.id == s.selectedLayerId }
            ?: findRecursive(s.preset.root.children) { it.id == s.selectedLayerId }
    }

    private fun findRecursive(layers: List<Layer>, pred: (Layer) -> Boolean): Layer? {
        for (l in layers) {
            if (pred(l)) return l
            if (l is GroupLayer) {
                findRecursive(l.children, pred)?.let { return it }
            }
        }
        return null
    }

    private fun mutate(transform: (Layer) -> Layer) {
        val s = _state.value
        val targetId = s.selectedLayerId ?: return
        updatePreset(s.preset.copy(root = replaceInTree(s.preset.root, targetId, transform)))
    }

    /** Returns a new tree with the layer matching id replaced by its transform. */
    private fun replaceInTree(root: GroupLayer, id: String, transform: (Layer) -> Layer): GroupLayer {
        fun walk(current: Layer): Layer {
            if (current.id == id) return transform(current)
            if (current is GroupLayer) return current.copy(children = current.children.map(::walk))
            return current
        }
        return walk(root) as GroupLayer
    }

    fun selectLayer(id: String?) {
        _state.value = _state.value.copy(selectedLayerId = id)
    }

    fun updateSelected(transform: (Layer) -> Layer) {
        mutate(transform)
    }

    fun addLayer(type: LayerType) {
        val s = _state.value
        val layer: Layer = when (type) {
            LayerType.COLOR -> ColorLayer(id = newLayerId(), name = "Color")
            LayerType.IMAGE -> ImageLayer(id = newLayerId(), name = "Image")
            LayerType.TEXT -> TextLayer(id = newLayerId(), name = "Text")
            LayerType.GROUP -> GroupLayer(id = newLayerId(), name = "Group")
        }
        val targetGroup: GroupLayer? = selectedLayer() as? GroupLayer
        val newRoot: GroupLayer = if (targetGroup != null) {
            replaceInTree(s.preset.root, targetGroup.id) { g ->
                (g as GroupLayer).copy(children = g.children + layer)
            }
        } else {
            s.preset.root.copy(children = s.preset.root.children + layer)
        }
        updatePreset(s.preset.copy(root = newRoot))
        _state.value = _state.value.copy(selectedLayerId = layer.id)
    }

    fun addIntoGroup(groupId: String, layer: Layer) {
        val s = _state.value
        val newRoot = replaceInTree(s.preset.root, groupId) {
            (it as GroupLayer).copy(children = it.children + layer)
        }
        updatePreset(s.preset.copy(root = newRoot))
        _state.value = _state.value.copy(selectedLayerId = layer.id)
    }

    fun deleteLayer(id: String) {
        val s = _state.value
        val newRoot = deleteRecursive(s.preset.root, id) ?: return
        updatePreset(s.preset.copy(root = newRoot))
        if (s.selectedLayerId == id) _state.value = _state.value.copy(selectedLayerId = null)
    }

    private fun deleteRecursive(group: GroupLayer, id: String): GroupLayer? {
        val kept = group.children.filter { it.id != id }
        if (kept.size != group.children.size) return group.copy(children = kept)
        var changed = false
        val newKids = group.children.map { child ->
            if (child is GroupLayer) {
                val r = deleteRecursive(child, id)
                if (r != null) changed = true
                r ?: child
            } else child
        }
        return if (changed) group.copy(children = newKids) else null
    }

    fun moveLayer(id: String, delta: Int) {
        val s = _state.value
        val newRoot = moveInGroup(s.preset.root, id, delta) ?: return
        updatePreset(s.preset.copy(root = newRoot))
    }

    private fun moveInGroup(group: GroupLayer, id: String, delta: Int): GroupLayer? {
        val idx = group.children.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val newIdx = (idx + delta).coerceIn(0, group.children.size - 1)
            if (newIdx == idx) return null
            val kids = group.children.toMutableList()
            val item = kids.removeAt(idx)
            kids.add(newIdx, item)
            return group.copy(children = kids)
        }
        var changed = false
        val newKids = group.children.map { child ->
            if (child is GroupLayer) {
                val r = moveInGroup(child, id, delta)
                if (r != null) changed = true
                r ?: child
            } else child
        }
        return if (changed) group.copy(children = newKids) else null
    }

    fun importImage(uri: Uri) = import(uri, AssetStore.Kind.IMAGE)
    fun importFont(uri: Uri) = import(uri, AssetStore.Kind.FONT)

    private fun import(uri: Uri, kind: AssetStore.Kind) {
        viewModelScope.launch {
            val id = assets.import(uri, kind)
            if (id == null) {
                _state.value = _state.value.copy(importFailed = true)
                return@launch
            }
            val list = assets.availableAssets()
            _state.value = _state.value.copy(
                importFailed = false,
                fonts = (assets.builtinFonts() + list.filter { it.endsWith("ttf") }),
                images = list.filter { it.endsWith("img") },
            )
            if (kind == AssetStore.Kind.IMAGE) {
                val s = _state.value
                if (selectedLayer() is ImageLayer) {
                    mutate { l -> when (l) {
                        is ImageLayer -> l.copy(asset = id)
                        else -> l
                    } }
                } else {
                    addIntoGroup(s.preset.root.id, ImageLayer(id = newLayerId(), name = "Image", asset = id))
                }
            } else {
                if (selectedLayer() is TextLayer) {
                    mutate { l -> when (l) {
                        is TextLayer -> l.copy(font = id)
                        else -> l
                    } }
                }
            }
        }
    }

    /** Assigns an already imported asset to the selected layer. */
    fun assignImage(assetId: String?) {
        mutate { l -> when (l) {
            is ImageLayer -> l.copy(asset = assetId)
            else -> l
        } }
    }

    fun assignFont(assetId: String?) {
        mutate { l -> when (l) {
            is TextLayer -> l.copy(font = assetId)
            else -> l
        } }
    }

    fun updateText(template: String? = null, fontSize: Float? = null,
                   bold: Boolean? = null, color: Long? = null) {
        mutate { l -> when (l) {
            is TextLayer -> l.copy(
                template = template ?: l.template,
                fontSize = fontSize ?: l.fontSize,
                bold = bold ?: l.bold,
                color = color ?: l.color,
            )
            else -> l
        } }
    }

    fun updateTransform(x: Float? = null, y: Float? = null,
                        pivotX: Float? = null, pivotY: Float? = null,
                        rotation: Float? = null, alpha: Float? = null) {
        mutate { l -> l.withCommon(
            x = x ?: l.x,
            y = y ?: l.y,
            pivotX = pivotX ?: l.pivotX,
            pivotY = pivotY ?: l.pivotY,
            rotation = rotation ?: l.rotation,
            alpha = alpha ?: l.alpha,
        ) }
    }

    fun updateImage(width: Float? = null, height: Float? = null, fit: ImageLayer.Fit? = null) {
        mutate { l -> when (l) {
            is ImageLayer -> l.copy(
                width = width ?: l.width,
                height = height ?: l.height,
                fit = fit ?: l.fit,
            )
            else -> l
        } }
    }

    fun updateColor(color: Long) {
        mutate { l -> when (l) {
            is ColorLayer -> l.copy(color = color)
            else -> l
        } }
    }

    fun renameSelected(name: String) {
        mutate { l -> l.withCommon(name = name) }
    }

    fun toggleVisible(id: String) {
        val s = _state.value
        updatePreset(s.preset.copy(root = replaceInTree(s.preset.root, id) { l -> l.withCommon(visible = !l.visible) }))
    }

    private fun updatePreset(preset: Preset, pushUndo: Boolean = true) {
        val previous = _state.value.preset
        if (pushUndo && previous != preset) {
            undoStack.addLast(previous)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        _state.value = _state.value.copy(
            preset = preset,
            undoDepth = undoStack.size,
            redoDepth = redoStack.size,
        )
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.save(preset) } }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(_state.value.preset)
        _state.value = _state.value.copy(
            preset = previous,
            undoDepth = undoStack.size,
            redoDepth = redoStack.size,
        )
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.save(previous) } }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_state.value.preset)
        _state.value = _state.value.copy(
            preset = next,
            undoDepth = undoStack.size,
            redoDepth = redoStack.size,
        )
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.save(next) } }
    }

    private companion object {
        const val MAX_HISTORY = 50
    }

    fun setInterval(minutes: Int) {
        store.intervalMinutes = minutes
        _state.value = _state.value.copy(intervalMinutes = minutes)
        if (store.enabled) RenderScheduler.scheduleNext(getApplication(), minutes)
    }

    fun setEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) RenderScheduler.start(context) else RenderScheduler.stop(context)
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun applyOnce() {
        val context = getApplication<Application>()
        Thread { com.lockcanvas.app.render.applyPresetFromStorage(context) }.start()
    }

    fun dismissImportFailed() {
        _state.value = _state.value.copy(importFailed = false)
    }
}
