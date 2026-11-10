package com.mkmehmood.everydaykeys.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore by preferencesDataStore(name = "everyday_keys_settings")

class Pref<T>(val key: Preferences.Key<T>, val default: T)

object P {
    private fun b(n: String, d: Boolean) = Pref(booleanPreferencesKey(n), d)
    private fun s(n: String, d: String) = Pref(stringPreferencesKey(n), d)
    private fun f(n: String, d: Float) = Pref(floatPreferencesKey(n), d)

    // Languages and layouts
    val layouts = s("layouts", "en_qwerty,ur,ar,hi")
    val numberRow = b("number_row", false)
    val accentPopup = b("accent_popup", true)
    val langKey = b("lang_key", true)
    // Number pad
    val numpadStyle = s("numpad_style", "phone")
    val mathRow = b("math_row", true)
    val autoNumpad = b("auto_numpad", true)
    // Clipboard
    val clipEnabled = b("clip_enabled", true)
    val clipRetention = s("clip_retention", "forever")
    val clipSkipSensitive = b("clip_skip_sensitive", true)
    val clipBiometric = b("clip_biometric", false)
    val pasteSpeed = s("paste_speed", "balanced")
    val pasteProgress = b("paste_progress", true)
    // Smart typing
    val autocorrect = s("autocorrect", "mild")
    val nextWord = b("next_word", true)
    val glide = b("glide", true)
    val autoCap = b("auto_cap", true)
    val doubleSpace = b("double_space", true)
    // Appearance
    val theme = s("theme", "system")
    val accent = s("accent", "0F766E")
    val customBg = s("custom_bg", "D6DCE4")
    val keyHeight = f("key_height", 52f)
    val fontSize = f("font_size", 20f)
    val keyBorder = b("key_border", false)
    val keyPreview = b("key_preview", true)
    val oneHanded = s("one_handed", "off")
    val floating = b("floating", false)
    // Keys and feedback
    val vibrate = b("vibrate", true)
    val vibIntensity = f("vib_intensity", 0.5f)
    val sound = b("sound", false)
    val soundVolume = f("sound_volume", 0.5f)
    val longPress = f("long_press", 350f)
    val cursorSens = f("cursor_sens", 0.5f)
    // Toolbar
    val toolbar = s("toolbar", "clipboard,snippets,voice,translate,undo,redo,cursor,search,emoji,settings")
    val toolbarCollapsed = b("toolbar_collapsed", false)

    val groups: Map<String, List<Pref<*>>> = mapOf(
        "languages" to listOf(layouts, numberRow, accentPopup, langKey),
        "numpad" to listOf(numpadStyle, mathRow, autoNumpad),
        "clipboard" to listOf(clipEnabled, clipRetention, clipSkipSensitive, clipBiometric, pasteSpeed, pasteProgress),
        "smart" to listOf(autocorrect, nextWord, glide, autoCap, doubleSpace),
        "appearance" to listOf(theme, accent, customBg, keyHeight, fontSize, keyBorder, keyPreview, oneHanded, floating),
        "feedback" to listOf(vibrate, vibIntensity, sound, soundVolume, longPress, cursorSens),
        "toolbar" to listOf(toolbar, toolbarCollapsed),
    )
}

class Snap(val p: Preferences) {
    operator fun <T> get(pr: Pref<T>): T = p[pr.key] ?: pr.default
}

fun Context.snapFlow(): Flow<Snap> = dataStore.data.map { Snap(it) }
fun Context.snapNow(): Snap = runBlocking { Snap(dataStore.data.first()) }
suspend fun <T> Context.setPref(pr: Pref<T>, v: T) { dataStore.edit { it[pr.key] = v } }

@Suppress("UNCHECKED_CAST")
suspend fun Context.resetPrefs(list: List<Pref<*>>) {
    dataStore.edit { e -> list.forEach { e.remove(it.key as Preferences.Key<Any>) } }
}
