package com.lockcanvas.app.render

import android.app.WallpaperManager
import android.content.Context
import com.lockcanvas.app.data.Preset
import com.lockcanvas.app.data.PresetStore

/**
 * Renders the current preset and applies the bitmap to the lock screen.
 */
class LockWallpaperApplier(private val context: Context, assets: AssetStore) {

    private val renderer = Renderer(assets)

    fun renderCurrent(preset: Preset, width: Int, height: Int): android.graphics.Bitmap {
        val vars = Variables.current(context)
        return renderer.render(preset, width, height, vars)
    }

    fun targetSize(): Pair<Int, Int> {
        val wm = WallpaperManager.getInstance(context)
        val w = wm.desiredMinimumWidth
        val h = wm.desiredMinimumHeight
        return if (w > 0 && h > 0) w to h else fallbackScreenSize() to fallbackScreenSizeHeight()
    }

    private fun fallbackScreenSize(): Int =
        context.resources.displayMetrics.widthPixels

    private fun fallbackScreenSizeHeight(): Int =
        context.resources.displayMetrics.heightPixels

    /** Applies the preset to the lock screen. Returns false if it failed. */
    fun apply(preset: Preset): Boolean {
        val (w, h) = targetSize()
        val bmp = renderCurrent(preset, w, h)
        return try {
            val wm = WallpaperManager.getInstance(context)
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
            true
        } catch (t: Throwable) {
            false
        }
    }
}

/** Loads the preset from storage, renders it and applies it. Used by the scheduler. */
fun applyPresetFromStorage(context: Context): Boolean {
    val preset = runCatching { PresetStore(context).load() }.getOrNull() ?: Preset()
    return LockWallpaperApplier(context, AssetStore(context)).apply(preset)
}
