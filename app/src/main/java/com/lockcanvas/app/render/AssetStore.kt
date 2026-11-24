package com.lockcanvas.app.render

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores imported binary assets (images, fonts) in app-private storage.
 * Asset ids are stable across restarts, so presets can reference them.
 */
class AssetStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "assets").apply { mkdirs() }

    private val bitmapCache = ConcurrentHashMap<String, android.graphics.Bitmap>()
    private val fontCache = ConcurrentHashMap<String, Typeface>()

    fun availableAssets(): List<String> = dir.listFiles()?.map { it.name } ?: emptyList()

    suspend fun import(uri: Uri, kind: Kind): String? = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            val bytes = input.use { it.readBytes() }
            if (bytes.isEmpty()) return@runCatching null
            val ext = when (kind) {
                Kind.IMAGE -> "img"
                Kind.FONT -> "ttf"
            }
            val id = "${System.nanoTime().toString(36)}.$ext"
            File(dir, id).writeBytes(bytes)
            id
        }.getOrNull()
    }

    fun openBitmap(id: String): android.graphics.Bitmap? {
        bitmapCache[id]?.let { return it }
        val file = File(dir, id)
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        bitmapCache[id] = bmp
        return bmp
    }

    fun openFont(id: String): Typeface? {
        fontCache[id]?.let { return it }
        val tf = if (id.startsWith(BUILTIN_FONT_PREFIX)) {
            runCatching {
                Typeface.createFromAsset(context.assets, id.removePrefix(BUILTIN_FONT_PREFIX))
            }.getOrNull()
        } else {
            val file = File(dir, id)
            if (!file.exists()) null else {
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            }
        } ?: return null
        fontCache[id] = tf
        return tf
    }

    /** Ids of the FOSS fonts bundled in assets/fonts (SIL OFL licensed). */
    fun builtinFonts(): List<String> =
        context.assets.list("fonts")
            ?.map { "$BUILTIN_FONT_PREFIX$it" }
            .orEmpty()
            .sorted()

    companion object {
        const val BUILTIN_FONT_PREFIX = "fonts/"
    }

    fun bitmapOrNull(id: String?): android.graphics.Bitmap? =
        id?.let { openBitmap(it) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        bitmapCache.remove(id)
        fontCache.remove(id)
        File(dir, id).delete()
    }

    enum class Kind { IMAGE, FONT }
}
