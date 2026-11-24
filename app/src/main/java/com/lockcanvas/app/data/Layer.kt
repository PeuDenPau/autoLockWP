package com.lockcanvas.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A layer of the wallpaper preset. All coordinates are normalized (0..1)
 * relative to the canvas, so presets are resolution independent.
 * x, y locate the layer anchor (pivot) on the canvas.
 * For text layers, width/height are ignored and the box wraps its content.
 */
@Serializable
sealed class Layer {
    abstract val id: String
    abstract val name: String
    abstract val visible: Boolean
    abstract val x: Float
    abstract val y: Float
    /** Anchor point inside the layer box (0..1, 0.5 = center). */
    abstract val pivotX: Float
    abstract val pivotY: Float
    abstract val rotation: Float
    abstract val alpha: Float

    abstract fun withCommon(
        id: String = this.id,
        name: String = this.name,
        visible: Boolean = this.visible,
        x: Float = this.x,
        y: Float = this.y,
        pivotX: Float = this.pivotX,
        pivotY: Float = this.pivotY,
        rotation: Float = this.rotation,
        alpha: Float = this.alpha,
    ): Layer
}

@Serializable
@SerialName("image")
data class ImageLayer(
    override val id: String,
    override val name: String = "Image",
    override val visible: Boolean = true,
    override val x: Float = 0.5f,
    override val y: Float = 0.5f,
    override val pivotX: Float = 0.5f,
    override val pivotY: Float = 0.5f,
    override val rotation: Float = 0f,
    override val alpha: Float = 1f,
    val asset: String? = null,
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val fit: Fit = Fit.FILL,
) : Layer() {
    @Serializable
    enum class Fit { FILL, FIT, STRETCH }

    override fun withCommon(
        id: String,
        name: String,
        visible: Boolean,
        x: Float,
        y: Float,
        pivotX: Float,
        pivotY: Float,
        rotation: Float,
        alpha: Float,
    ) = copy(
        id = id, name = name, visible = visible, x = x, y = y,
        pivotX = pivotX, pivotY = pivotY, rotation = rotation, alpha = alpha,
    )
}

@Serializable
@SerialName("color")
data class ColorLayer(
    override val id: String,
    override val name: String = "Color",
    override val visible: Boolean = true,
    override val x: Float = 0.5f,
    override val y: Float = 0.5f,
    override val pivotX: Float = 0.5f,
    override val pivotY: Float = 0.5f,
    override val rotation: Float = 0f,
    override val alpha: Float = 1f,
    val color: Long = 0xFF0E0E12,
    val width: Float = 1f,
    val height: Float = 1f,
) : Layer() {
    override fun withCommon(
        id: String,
        name: String,
        visible: Boolean,
        x: Float,
        y: Float,
        pivotX: Float,
        pivotY: Float,
        rotation: Float,
        alpha: Float,
    ) = copy(
        id = id, name = name, visible = visible, x = x, y = y,
        pivotX = pivotX, pivotY = pivotY, rotation = rotation, alpha = alpha,
    )
}

@Serializable
@SerialName("text")
data class TextLayer(
    override val id: String,
    override val name: String = "Text",
    override val visible: Boolean = true,
    override val x: Float = 0.5f,
    override val y: Float = 0.5f,
    override val pivotX: Float = 0.5f,
    override val pivotY: Float = 0.5f,
    override val rotation: Float = 0f,
    override val alpha: Float = 1f,
    /** Text template, supports variables like $TIME_HH$:$TIME_MM$. */
    val template: String = "\$TIME_HH\$:\$TIME_MM\$",
    /** Font asset id, null = system default. */
    val font: String? = null,
    /** Font size as a fraction of the canvas height. */
    val fontSize: Float = 0.12f,
    val color: Long = 0xFFFFFFFF,
    val letterSpacing: Float = 0f,
    val bold: Boolean = false,
) : Layer() {
    fun withTextProps(
        template: String = this.template,
        font: String? = this.font,
        fontSize: Float = this.fontSize,
        color: Long = this.color,
        letterSpacing: Float = this.letterSpacing,
        bold: Boolean = this.bold,
    ) = copy(
        template = template, font = font, fontSize = fontSize,
        color = color, letterSpacing = letterSpacing, bold = bold,
    )

    override fun withCommon(
        id: String,
        name: String,
        visible: Boolean,
        x: Float,
        y: Float,
        pivotX: Float,
        pivotY: Float,
        rotation: Float,
        alpha: Float,
    ) = copy(
        id = id, name = name, visible = visible, x = x, y = y,
        pivotX = pivotX, pivotY = pivotY, rotation = rotation, alpha = alpha,
    )
}

@Serializable
@SerialName("group")
data class GroupLayer(
    override val id: String,
    override val name: String = "Group",
    override val visible: Boolean = true,
    override val x: Float = 0f,
    override val y: Float = 0f,
    override val pivotX: Float = 0f,
    override val pivotY: Float = 0f,
    override val rotation: Float = 0f,
    override val alpha: Float = 1f,
    val children: List<Layer> = emptyList(),
) : Layer() {
    override fun withCommon(
        id: String,
        name: String,
        visible: Boolean,
        x: Float,
        y: Float,
        pivotX: Float,
        pivotY: Float,
        rotation: Float,
        alpha: Float,
    ) = copy(
        id = id, name = name, visible = visible, x = x, y = y,
        pivotX = pivotX, pivotY = pivotY, rotation = rotation, alpha = alpha,
    )
}

fun newLayerId(): String =
    java.util.UUID.randomUUID().toString().take(8)

@Serializable
data class Preset(
    val root: GroupLayer = GroupLayer(
        id = newLayerId(),
        name = "Scene",
        children = listOf(
            ColorLayer(id = newLayerId(), name = "Background"),
            TextLayer(
                id = newLayerId(),
                name = "Clock",
                template = "\$TIME_HH\$:\$TIME_MM\$",
                fontSize = 0.18f,
            ),
        ),
    ),
)

fun defaultPresetJson(): String = Json.encodeToString(Preset.serializer(), Preset())

fun loadPreset(json: String): Preset = Json.decodeFromString(Preset.serializer(), json)

fun presetToJson(preset: Preset): String =
    Json.encodeToString(Preset.serializer(), preset)
