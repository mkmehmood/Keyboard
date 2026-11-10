package com.mkmehmood.everydaykeys.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkmehmood.everydaykeys.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.store: DataStore<Preferences> by preferencesDataStore("everyday_keys")

/** Every setting is stored as a string; defaults come from [Defs]. */
class Prefs(private val p: Preferences) {
    fun str(k: String): String {
        val raw = p[stringPreferencesKey(k)] ?: Defs.default(k)
        return if (Secrets.isSecretKey(k)) Secrets.decrypt(raw) else raw
    }
    fun bool(k: String): Boolean = str(k) == "true"
    fun int(k: String): Int = str(k).toIntOrNull() ?: Defs.default(k).toIntOrNull() ?: 0
}

object SettingsRepo {
    fun flow(ctx: Context): Flow<Prefs> = ctx.store.data.map { Prefs(it) }
    suspend fun set(ctx: Context, key: String, value: String) {
        val stored = if (Secrets.isSecretKey(key)) Secrets.encrypt(value) else value
        ctx.store.edit { it[stringPreferencesKey(key)] = stored }
        if (key == "hideLauncher") Launcher.apply(ctx, value == "true")
    }
    suspend fun reset(ctx: Context, keys: List<String>) {
        ctx.store.edit { e -> keys.forEach { e.remove(stringPreferencesKey(it)) } }
        if ("hideLauncher" in keys) Launcher.apply(ctx, false)
    }
}

sealed class Item(val key: String, val title: String, val default: String)
class Toggle(key: String, title: String, d: Boolean) : Item(key, title, d.toString())
class Choice(key: String, title: String, val options: List<Pair<String, String>>, d: String) : Item(key, title, d)
class Multi(key: String, title: String, val options: List<Pair<String, String>>, d: String) : Item(key, title, d)
class Slide(key: String, title: String, val min: Int, val max: Int, d: Int) : Item(key, title, d.toString())
class Input(key: String, title: String, val secret: Boolean = false, val hint: String = "") : Item(key, title, "")
class Action(val id: String, title: String, val desc: String = "", val url: String? = null) : Item("action_$id", title, "")
class Section(val id: String, val title: String, val desc: String, val icon: Int, val items: List<Item>)

object Defs {
    private val tools = listOf(
        "clip" to "Clipboard", "snip" to "Snippets", "voice" to "Voice typing", "translate" to "Translate",
        "style" to "Switch layout style", "undo" to "Undo", "redo" to "Redo", "cursor" to "Cursor and edit keys", "convert" to "Unit converter", "settings" to "Settings"
    )

    val sections: List<Section> = listOf(
        Section("lang", "Languages and layouts", "English, Urdu, QWERTY", R.drawable.ic_globe, listOf(
            Multi("languages", "Languages (arrows reorder; globe key cycles)", listOf("en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi"), "en"),
            Toggle("numberRow", "Number row on letters", true),
            Toggle("accentPopup", "Long-press accents and alternates", true),
            Toggle("langKey", "Language switch key", true)
        )),
        Section("numpad", "Number pad", "Layout and auto-switch", R.drawable.ic_numpad, listOf(
            Toggle("autoNumPad", "Switch to number pad in number and phone fields", true)
        )),
        Section("clip", "Clipboard", "Unlimited size, history on", R.drawable.ic_clipboard, listOf(
            Toggle("clipEnabled", "Save clipboard history", true),
            Toggle("clipPreview", "Tap a clip to preview it before pasting", true),
            Choice("retention", "Keep history", listOf("forever" to "Forever", "1" to "1 day", "7" to "1 week", "30" to "1 month"), "forever"),
            Toggle("skipSensitive", "Skip passwords and sensitive fields", true),
            Toggle("clipLock", "Lock history with fingerprint or screen lock", false),
            Choice("pasteSpeed", "Large paste speed", listOf("fast" to "Fast", "balanced" to "Balanced", "safe" to "Safe"), "balanced"),
            Toggle("showProgress", "Show paste progress", true),
            Action("clearClips", "Clear clipboard history", "Pinned items are kept")
        )),
        Section("snippets", "Text snippets", "Shortcuts such as ;addr", R.drawable.ic_snippet, listOf(
            Action("snippets", "Manage snippets", "Add, edit, delete, set shortcuts")
        )),
        Section("smart", "Smart typing", "Autocorrect, smart suggestions, on-device", R.drawable.ic_sparkle, listOf(
            Choice("autocorrect", "Autocorrect", listOf("off" to "Off", "mild" to "Mild", "strong" to "Strong"), "mild"),
            Toggle("suggestions", "Word suggestions", true),
            Choice("suggestCount", "Suggestions shown", listOf("2" to "2", "3" to "3", "4" to "4"), "3"),
            Toggle("contextRank", "Rank by the previous word", true),
            Toggle("typoSuggest", "Suggest fixes for typos", true),
            Toggle("learnWords", "Learn words I use often", true),
            Toggle("nextWord", "Next-word suggestions (learned on this phone)", true),
            Toggle("onlineSuggestions", "Live online suggestions", false),
            Toggle("autoCap", "Auto-capitalization", true),
            Toggle("doubleSpace", "Double-space for a period", true),
            Action("dictionary", "Personal dictionary", "Add or remove your own words"),
            Action("localNote", "Local by default", "Online suggestions are optional and send only the partial word you are typing")
        )),
        Section("translate", "Translation", "Translate selected or typed text", R.drawable.ic_translate, listOf(
            Choice("translateProvider", "Provider", listOf("mymemory" to "MyMemory (free, no key)", "google" to "Google Cloud (your key)", "deepl" to "DeepL Free (your key)"), "mymemory"),
            Choice("translateTarget", "Translate into", listOf("en" to "English", "ur" to "Urdu", "ar" to "Arabic", "hi" to "Hindi", "es" to "Spanish", "fr" to "French", "de" to "German", "pt" to "Portuguese", "ru" to "Russian", "tr" to "Turkish", "it" to "Italian", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "id" to "Indonesian", "fa" to "Persian", "bn" to "Bengali"), "en"),
            Input("googleApiKey", "Google Cloud Translation API key", secret = true),
            Input("deeplApiKey", "DeepL API Free key", secret = true),
            Input("translateEmail", "Email for a higher free MyMemory limit (optional)"),
            Action("translateNote", "Sent only when you tap Translate", "The text you translate goes to the provider you chose. Nothing is sent automatically.")
        )),
        Section("keystyle", "Key size, shape and font", "Resize, reshape, restyle, font", R.drawable.ic_text, listOf(
            Slide("keyScale", "Keyboard size (%)", 60, 100, 100),
            Choice("keyShape", "Key shape", listOf("square" to "Square corners", "slight" to "Slightly rounded", "rounded" to "Rounded", "soft" to "Very rounded", "round" to "Round"), "rounded"),
            Choice("keyStyle", "Key style", listOf("raised" to "Raised", "flat" to "Flat", "outline" to "Outline"), "raised"),
            Choice("keyFont", "Key font", listOf("sans" to "Sans-serif", "serif" to "Serif", "mono" to "Monospace", "cursive" to "Cursive"), "sans"),
            Choice("keyWeight", "Font weight", listOf("normal" to "Normal", "medium" to "Medium", "bold" to "Bold"), "medium"),
            Slide("fontSize", "Key font size", 12, 28, 20)
        )),
        Section("appearance", "Appearance", "Theme, accent, background", R.drawable.ic_sun, listOf(
            Choice("layoutStyle", "Layout style", listOf("current" to "Current (uses your own settings)", "raised" to "Design 1: dark, raised keys", "flat" to "Design 2: light, flat and airy", "pro" to "Design 3: dark, high contrast, symbol hints"), "current"),
            Choice("numpadLayout", "Number pad layout", listOf("auto" to "Follow layout style", "classic" to "Classic", "top" to "Operators on top (Design 1)", "left" to "Operators on the left (Design 2)", "circles" to "Round keys (Design 3)"), "auto"),
            Toggle("keyHints", "Symbol hints on letter keys (long-press types them)", false),
            Action("layoutNote", "Layout styles override theme, key shape and key style", "Choose Current to use your own settings. Add the Switch layout style tool in Toolbar to change style while typing."),
            Choice("theme", "Theme", listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark"), "system"),
            Choice("accent", "Accent color", listOf("teal" to "Teal", "blue" to "Blue", "purple" to "Purple", "orange" to "Orange"), "teal"),
            Toggle("borders", "Key borders", false),
            Action("bgEdit", "Background image", "Choose a photo, crop, zoom, dim, blur"),
            Toggle("bgOn", "Show background image", false),
            Slide("bgDim", "Image dimming (%)", 0, 90, 35),
            Slide("bgBlur", "Image blur (Android 12+)", 0, 25, 0),
            Slide("bgKeyAlpha", "Key opacity over image (%)", 30, 100, 85),
            Choice("oneHanded", "One-handed mode", listOf("off" to "Off", "left" to "Left", "right" to "Right"), "off")
        )),
        Section("keys", "Keys and feedback", "Vibration, sound, long-press", R.drawable.ic_vibration, listOf(
            Toggle("keyPreview", "Key preview popup", true),
            Toggle("vibrate", "Vibration", true),
            Slide("vibMs", "Vibration strength (ms)", 5, 50, 15),
            Toggle("sound", "Key sound", false),
            Slide("soundVol", "Sound volume", 0, 100, 50),
            Slide("longPressMs", "Long-press delay (ms)", 200, 600, 350),
            Slide("dragSens", "Spacebar cursor drag sensitivity", 1, 10, 5)
        )),
        Section("toolbar", "Toolbar", "Choose and order tools", R.drawable.ic_keyboard, listOf(
            Toggle("showToolbar", "Toolbox button and toolbar", true),
            Multi("toolbarItems", "Tools (use the arrows to reorder)", tools, "clip,snip,voice,translate,undo,redo,cursor,settings")
        )),
        Section("privacy", "Privacy and data", "On this phone unless you use online tools", R.drawable.ic_lock, listOf(
            Toggle("hideLauncher", "Hide app icon from the launcher", false),
            Action("privacyNote", "Typing stays on your phone", "No account, no analytics. The app goes online only for Translate (when you tap it) and live suggestions (off by default). API keys are encrypted on this phone."),
            Action("resetDict", "Reset learned dictionary", "Removes your personal words and learned word pairs"),
            Action("wipeAll", "Delete all app data", "Clipboard, snippets, dictionary, settings")
        )),
        Section("backup", "Backup and restore", "One file for everything", R.drawable.ic_download, listOf(
            Action("export", "Back up to a file", "Settings, snippets, dictionary, clipboard"),
            Action("import", "Restore from a file", "Merges with what is on this phone")
        )),
        Section("about", "About", "Licenses, feedback, privacy policy", R.drawable.ic_info, listOf(
            Action("version", "Version"),
            Action("link", "Privacy policy", url = "https://github.com/mkmehmood/keyboard/blob/main/PRIVACY.md"),
            Action("link", "Feedback and bug report", url = "https://github.com/mkmehmood/keyboard/issues"),
            Action("link", "Rate us / star on GitHub", url = "https://github.com/mkmehmood/keyboard"),
            Action("link", "Open-source licenses", url = "https://github.com/mkmehmood/keyboard/blob/main/README.md#licenses")
        ))
    )

    private val all: Map<String, Item> = sections.flatMap { it.items }.associateBy { it.key }
    fun default(k: String): String = all[k]?.default ?: ""
}
