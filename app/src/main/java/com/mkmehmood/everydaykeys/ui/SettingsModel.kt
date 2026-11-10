package com.mkmehmood.everydaykeys.ui

import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.P
import com.mkmehmood.everydaykeys.data.Pref
import com.mkmehmood.everydaykeys.ime.Palettes

sealed class Item(val title: String, val desc: String)
class Toggle(val p: Pref<Boolean>, title: String, desc: String = "") : Item(title, desc)
class Slide(val p: Pref<Float>, val min: Float, val max: Float, title: String, desc: String = "", val unit: String = "") : Item(title, desc)
class Choice(val p: Pref<String>, val options: List<Pair<String, String>>, title: String, desc: String = "") : Item(title, desc)
class Link(val route: String, title: String, desc: String = "") : Item(title, desc)
class Action(val id: String, title: String, desc: String = "") : Item(title, desc)
class Note(title: String, desc: String = "") : Item(title, desc)
class Meter(title: String) : Item(title, "")

class Section(val id: String, val title: String, val sub: String, val icon: Int, val prefs: List<Pref<*>>, val items: List<Item>)

object Sections {
    val all: List<Section> = listOf(
        Section("languages", "Languages and layouts", "English, Urdu, QWERTY", R.drawable.ic_globe, P.groups["languages"]!!, listOf(
            Link("languages", "Languages and layouts", "Add, remove and reorder. Pick the layout for each language."),
            Toggle(P.numberRow, "Number row", "Show a row of digits above the letters"),
            Toggle(P.accentPopup, "Long-press accents", "Hold a key for accents and alternate characters"),
            Toggle(P.langKey, "Language switch key", "Show the globe key when more than one language is on"),
        )),
        Section("numpad", "Number pad", "Phone style, operators row", R.drawable.ic_grid, P.groups["numpad"]!!, listOf(
            Choice(P.numpadStyle, listOf("phone" to "Phone style (1-2-3 on top)", "calc" to "Calculator style (7-8-9 on top)"), "Default layout"),
            Toggle(P.mathRow, "Operators row", "Show + − × ÷ = % and the unit converter key"),
            Toggle(P.autoNumpad, "Auto-switch in number fields", "Open the number pad in number and phone fields"),
        )),
        Section("clipboard", "Clipboard", "Unlimited size, history on", R.drawable.ic_clipboard, P.groups["clipboard"]!!, listOf(
            Toggle(P.clipEnabled, "Clipboard history", "Save everything you copy"),
            Choice(P.clipRetention, listOf("forever" to "Keep forever", "day" to "Delete after 1 day", "week" to "Delete after 1 week", "month" to "Delete after 1 month"), "Storage", "Pinned items are never deleted."),
            Meter("Storage used"),
            Action("clip_clear", "Clear all", "Remove every unpinned item and its files"),
            Toggle(P.clipSkipSensitive, "Skip sensitive fields", "Passwords and sensitive fields never reach history"),
            Toggle(P.clipBiometric, "Biometric lock", "Ask for fingerprint, face or screen lock before showing history"),
            Choice(P.pasteSpeed, listOf("fast" to "Fast (100 KB chunks)", "balanced" to "Balanced (75 KB chunks)", "safe" to "Safe (50 KB chunks)"), "Chunked paste speed", "Large text is pasted in small pieces so nothing is cut off."),
            Toggle(P.pasteProgress, "Show paste progress", "Progress bar while pasting large text"),
            Action("clip_export", "Export history", "Save clipboard history to a file"),
            Action("clip_import", "Import history", "Load clipboard history from a file"),
        )),
        Section("snippets", "Text snippets", "Shortcuts such as ;addr", R.drawable.ic_doc, emptyList(), listOf(
            Link("snippets", "Manage snippets", "Add, edit, delete and assign shortcuts"),
            Action("snip_export", "Export snippets", "Save snippets to a file"),
            Action("snip_import", "Import snippets", "Load snippets from a file"),
        )),
        Section("smart", "Smart typing", "Autocorrect, glide, on-device", R.drawable.ic_sparkle, P.groups["smart"]!!, listOf(
            Choice(P.autocorrect, listOf("off" to "Off", "mild" to "Mild", "strong" to "Strong"), "Autocorrect strength"),
            Toggle(P.nextWord, "Next-word suggestions"),
            Toggle(P.glide, "Glide typing", "Swipe across letters to type a word"),
            Toggle(P.autoCap, "Auto-capitalization"),
            Toggle(P.doubleSpace, "Double-space for a period"),
            Link("dictionary", "Personal dictionary", "Add, edit, delete and import words"),
            Note("Everything stays on this phone", "All typing, learning and suggestions are processed locally. No keystrokes are sent to a server."),
        )),
        Section("appearance", "Appearance", "Theme, key height, font size", R.drawable.ic_settings, P.groups["appearance"]!!, listOf(
            Choice(P.theme, listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark", "custom" to "Custom colors"), "Theme"),
            Choice(P.accent, Palettes.accents.map { it.second to it.first }, "Accent color"),
            Choice(P.customBg, Palettes.backgrounds.map { it.second to it.first }, "Custom background", "Used when the theme is set to Custom colors."),
            Slide(P.keyHeight, 40f, 70f, "Key height", unit = " dp"),
            Slide(P.fontSize, 14f, 28f, "Font size", unit = " sp"),
            Toggle(P.keyBorder, "Key borders"),
            Toggle(P.keyPreview, "Key preview popup"),
            Choice(P.oneHanded, listOf("off" to "Off", "left" to "Dock left", "right" to "Dock right"), "One-handed mode"),
            Toggle(P.floating, "Floating mode", "Drag the keyboard anywhere on screen"),
        )),
        Section("feedback", "Keys and feedback", "Vibration, sound, long-press", R.drawable.ic_vibrate, P.groups["feedback"]!!, listOf(
            Toggle(P.vibrate, "Vibration on key press"),
            Slide(P.vibIntensity, 0.05f, 1f, "Vibration intensity"),
            Toggle(P.sound, "Key sound"),
            Slide(P.soundVolume, 0f, 1f, "Sound volume"),
            Slide(P.longPress, 200f, 700f, "Long-press delay", unit = " ms"),
            Slide(P.cursorSens, 0f, 1f, "Spacebar cursor drag sensitivity"),
        )),
        Section("toolbar", "Toolbar", "Choose and reorder tools", R.drawable.ic_keyboard, P.groups["toolbar"]!!, listOf(
            Link("toolbar", "Tools", "Choose which tools appear and reorder them"),
            Toggle(P.toolbarCollapsed, "Collapse to a single row", "Hide the suggestion strip and keep only the toolbar"),
        )),
        Section("privacy", "Privacy and data", "Nothing leaves your phone", R.drawable.ic_lock, emptyList(), listOf(
            Note("Private by default", "No keystrokes leave your device and no account is needed. Password and sensitive fields are never saved to clipboard history."),
            Action("dict_reset", "Reset learned dictionary", "Forget learned words and word pairs (your own words stay)"),
            Action("data_delete", "Delete all app data", "Settings, snippets, dictionary and clipboard history"),
        )),
        Section("backup", "Backup and restore", "One file for everything", R.drawable.ic_download, emptyList(), listOf(
            Action("backup", "Back up", "Settings, snippets, dictionary and clipboard in one file"),
            Action("restore", "Restore", "Load a backup file"),
        )),
        Section("about", "About", "Licenses, feedback, privacy policy", R.drawable.ic_info, emptyList(), listOf(
            Note("Version"),
            Action("licenses", "Open-source licenses"),
            Action("privacy_policy", "Privacy policy"),
            Action("feedback", "Feedback and bug report"),
            Action("rate", "Rate us", "Star the project on GitHub"),
        )),
    )

    fun byId(id: String) = all.first { it.id == id }
}

fun actionIcon(id: String) = when (id) {
    "clip_clear", "data_delete" -> R.drawable.ic_trash
    "clip_export", "snip_export", "backup" -> R.drawable.ic_upload
    "clip_import", "snip_import", "restore" -> R.drawable.ic_download
    "dict_reset" -> R.drawable.ic_reset
    "feedback" -> R.drawable.ic_chat
    "rate" -> R.drawable.ic_star
    else -> R.drawable.ic_info
}
