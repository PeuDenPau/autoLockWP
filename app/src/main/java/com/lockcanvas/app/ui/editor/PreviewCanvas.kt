package com.lockcanvas.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
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
        modifier = modifier
            .fillMaxWidth()
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
    }
}
