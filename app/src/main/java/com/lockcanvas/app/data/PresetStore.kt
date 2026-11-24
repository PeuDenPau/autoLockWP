package com.lockcanvas.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists the preset and app settings in app-private storage.
 */
class PresetStore(private val context: Context) {

    private val presetFile: File
        get() = File(context.filesDir, "preset.json")

    private val prefs
        get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun load(): Preset {
        if (!presetFile.exists()) return Preset()
        val text = presetFile.readText()
        return kotlinx.serialization.json.Json.decodeFromString(
            Preset.serializer(), text
        )
    }

    suspend fun save(preset: Preset) = withContext(Dispatchers.IO) {
        presetFile.writeText(json.encodeToString(Preset.serializer(), preset))
    }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 1)
        set(value) = prefs.edit().putInt("interval_minutes", value).apply()

    var lastAppliedAt: Long
        get() = prefs.getLong("last_applied_at", 0L)
        set(value) = prefs.edit().putLong("last_applied_at", value).apply()
}
