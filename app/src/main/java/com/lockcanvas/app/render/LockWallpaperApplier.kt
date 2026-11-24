package com.lockcanvas.app.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
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

    /**
     * The lock screen shows the wallpaper at the real display size without scaling,
     * so we render exactly at the display bounds. Using desiredMinimumWidth/Height
     * (which include panning/multi-display padding) results in a wallpaper that
     * appears small and shifted on the lock screen.
     */
    fun targetSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(dm)
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            return dm.widthPixels to dm.heightPixels
        }
        return context.resources.displayMetrics.widthPixels to
            context.resources.displayMetrics.heightPixels
    }

    /** Applies the preset to the lock screen. Returns false if it failed. */
    fun apply(preset: Preset): Boolean {
        val (w, h) = targetSize()
        val bmp = renderCurrent(preset, w, h)
        return try {
            val wm = WallpaperManager.getInstance(context)
            val fullCrop = Rect(0, 0, bmp.width, bmp.height)
            wm.setBitmap(bmp, fullCrop, true, WallpaperManager.FLAG_LOCK)
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
