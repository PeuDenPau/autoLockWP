package com.lockcanvas.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.lockcanvas.app.data.Preset
import com.lockcanvas.app.render.AssetStore
import com.lockcanvas.app.render.Variables

/**
 * Live preview of the preset. The selected layer can be dragged directly.
 * Re-renders every second so the clock variables are visibly ticking.
 */
@Composable
fun PreviewCanvas(
    preset: Preset,
    selectedLayerId: String?,
    onMoveLayer: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier,
    showGuides: Boolean = true,
) {
    val context = LocalContext.current
    val assets = remember { AssetStore(context) }
    val renderer = remember { com.lockcanvas.app.render.Renderer(assets) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val width = 720
    val height = 1600
    LaunchedEffect(preset, tick) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val vars = Variables.current(context)
            bitmap = renderer.render(preset, width, height, vars)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(720f / 1600f)
                .pointerInput(selectedLayerId) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        onMoveLayer(dragAmount.x / w, dragAmount.y / h)
                    }
                }
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                )
            }
            if (showGuides) {
                LockScreenGuides(Modifier.matchParentSize())
            }
        }
    }
}

/**
 * Overlay showing where the lock screen UI elements roughly sit, so layers
 * can be positioned around them: clock zone (top), notifications (middle),
 * shortcuts (bottom).
 */
@Composable
fun LockScreenGuides(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
        val stroke = Stroke(width = 2.dp.toPx(), pathEffect = dash)

        fun zone(top: Float, bottom: Float, hMargin: Float) {
            drawRect(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                topLeft = Offset(w * hMargin, h * top),
                size = Size(w * (1f - 2f * hMargin), h * (bottom - top)),
                style = stroke,
            )
        }

        zone(0.06f, 0.26f, 0.08f)
        zone(0.30f, 0.74f, 0.06f)
        zone(0.88f, 0.97f, 0.10f)

        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(200, 255, 255, 255)
                textSize = 12.dp.toPx()
                isAntiAlias = true
            }
            drawText("Clock zone", w * 0.08f + 4, h * 0.06f + 22f, paint)
            drawText("Notifications", w * 0.06f + 4, h * 0.30f + 22f, paint)
            drawText("Shortcuts", w * 0.10f + 4, h * 0.88f + 22f, paint)
        }
    }
}
