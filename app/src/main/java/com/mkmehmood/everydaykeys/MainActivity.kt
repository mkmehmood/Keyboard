package com.mkmehmood.everydaykeys

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : FragmentActivity() {
    private val tick = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SettingsApp(tick.intValue) }
            }
        }
    }
    override fun onResume() { super.onResume(); tick.intValue++ }
}

class Ed(val ctx: Context, val scope: CoroutineScope) {
    fun <T> set(k: Preferences.Key<T>, v: T) { scope.launch { ctx.ds.edit { it[k] = v } } }
    fun reset(vararg ks: Preferences.Key<*>) { scope.launch { ctx.ds.edit { p -> ks.forEach { p.remove(it) } } } }
}

enum class Sec(val title: String, val desc: String, val icon: Int, val kw: String) {
    LANG("Languages and layouts", "Languages, number row, accents", R.drawable.ic_language, "language layout urdu arabic hindi accent number row"),
    NUMPAD("Number pad", "Phone or calculator style, operators", R.drawable.ic_dialpad, "number pad numeric calculator phone operators"),
    CLIP("Clipboard", "History, storage, lock, chunked paste", R.drawable.ic_clipboard, "clipboard history paste chunk biometric sensitive storage"),
    SNIP("Text snippets", "Shortcuts for text you type often", R.drawable.ic_snippets, "snippets shortcut email address phone"),
    SMART("Smart typing", "Autocorrect, suggestions, dictionary", R.drawable.ic_check, "autocorrect suggestions dictionary capitalization space period"),
    LOOK("Appearance", "Theme, sizes, one-handed mode", R.drawable.ic_tune, "theme dark light key height font size one handed"),
    KEYS("Keys and feedback", "Vibration, sound, long-press, spacebar", R.drawable.ic_keyboard, "vibration sound haptic long press delay spacebar cursor"),
    TOOLBAR("Toolbar", "Choose which tools appear", R.drawable.ic_toolbar, "toolbar tools voice emoji undo redo translate"),
    PRIVACY("Privacy and data", "Everything stays on this device", R.drawable.ic_lock, "privacy reset delete data dictionary"),
    BACKUP("Backup and restore", "One file with all your data", R.drawable.ic_backup, "backup restore export import"),
    ABOUT("About", "Version, licenses, feedback", R.drawable.ic_info, "about version license feedback rate")
}

@Composable
fun SettingsApp(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ed = remember { Ed(ctx, scope) }
    val flow = remember { Store.flow(ctx) }
    val p by flow.collectAsState(Prefs())
    var sec by remember { mutableStateOf<Sec?>(null) }
    var q by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    BackHandler(sec != null) { sec = null }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sec != null) {
                IconButton({ sec = null }) { Icon(painterResource(R.drawable.ic_arrow_left), "Back") }
                Text(sec!!.title, style = MaterialTheme.typography.titleLarge)
            } else {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) })
            }
        }
        Box(Modifier.weight(1f)) {
            val s = sec
            if (s == null) Home(tick, q, ctx) { sec = it } else SectionScreen(s, p, ed, tick)
        }
        OutlinedTextField(preview, { preview = it }, Modifier.fillMaxWidth().padding(8.dp), label = { Text("Try typing here") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_keyboard), null) })
    }
}

@Composable
fun Home(tick: Int, q: String, ctx: Context, open: (Sec) -> Unit) {
    val imm = ctx.getSystemService(InputMethodManager::class.java)
    val enabled = remember(tick) { imm.enabledInputMethodList.any { it.packageName == ctx.packageName } }
    val selected = remember(tick) { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(ctx.packageName) == true }
    val list = Sec.values().filter { q.isBlank() || (it.title + " " + it.desc + " " + it.kw).contains(q, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (enabled && selected) R.drawable.ic_check else R.drawable.ic_info), null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (!enabled) "Step 1 of 2: enable Everyday Keys" else if (!selected) "Step 2 of 2: switch to Everyday Keys" else "Everyday Keys is active",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (enabled && selected) "You are ready to type." else "Two quick steps in Android settings.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!(enabled && selected)) Button({
                        if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) else imm.showInputMethodPicker()
                    }) { Icon(painterResource(R.drawable.ic_keyboard), null); Spacer(Modifier.width(6.dp)); Text(if (!enabled) "Enable" else "Switch") }
                }
            }
        }
        items(list) { s ->
            Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { open(s) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(s.icon), null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text(s.title, style = MaterialTheme.typography.titleMedium); Text(s.desc, style = MaterialTheme.typography.bodySmall) }
                    Icon(painterResource(R.drawable.ic_arrow_right), null)
                }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, desc: String? = null, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(v, role = Role.Switch, onValueChange = on).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall) }
        Switch(v, null)
    }
}

@Composable
fun SliderRow(title: String, v: Int, range: IntRange, unit: String = "", on: (Int) -> Unit) {
    Column(Modifier.padding(16.dp, 8.dp)) {
        Text("$title: $v$unit")
        Slider(v.toFloat(), { on(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
fun Choice(title: String, opts: List<String>, sel: Int, on: (Int) -> Unit) {
    Column(Modifier.padding(16.dp, 8.dp).selectableGroup()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        opts.forEachIndexed { i, o ->
            Row(Modifier.fillMaxWidth().selectable(sel == i, role = Role.RadioButton, onClick = { on(i) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(sel == i, null); Spacer(Modifier.width(12.dp)); Text(o)
            }
        }
    }
}

@Composable
fun ActionButton(icon: Int, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.padding(16.dp, 4.dp)) { Icon(painterResource(icon), null); Spacer(Modifier.width(8.dp)); Text(label) }
}

@Composable
fun SectionScreen(sec: Sec, p: Prefs, ed: Ed, tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when (sec) {
            Sec.LANG -> {
                val cur = p.langs.split(",").filter { it.isNotBlank() }
                Layouts.ALL.forEach { l ->
                    SwitchRow(l.name, l.code, l.code in cur) { on ->
                        val n = Layouts.ALL.map { it.code }.filter { c -> if (c == l.code) on else c in cur }
                        if (n.isNotEmpty()) ed.set(K.langs, n.joinToString(","))
                    }
                }
                SwitchRow("Number row", "Show digits above the letters", p.numRow) { ed.set(K.numRow, it) }
                SwitchRow("Long-press accents", "Popup with alternate characters", p.accents) { ed.set(K.accents, it) }
                SwitchRow("Language switch key", null, p.langKey) { ed.set(K.langKey, it) }
                ResetButton { ed.reset(K.langs, K.numRow, K.accents, K.langKey) }
            }
            Sec.NUMPAD -> {
                Choice("Default layout", listOf("Phone style (1-2-3 on top)", "Calculator style (7-8-9 on top)"), p.numpadStyle) { ed.set(K.numpadStyle, it) }
                SwitchRow("Math operators", "+ - × ÷ = % keys", p.ops) { ed.set(K.ops, it) }
                SwitchRow("Auto-switch in number fields", null, p.autoNum) { ed.set(K.autoNum, it) }
                ResetButton { ed.reset(K.numpadStyle, K.ops, K.autoNum) }
            }
            Sec.CLIP -> {
                var usage by remember { mutableLongStateOf(0L) }
                LaunchedEffect(tick) { usage = ClipStore.usage(ctx) }
                SwitchRow("Clipboard history", "Save everything you copy", p.clipOn) { ed.set(K.clipOn, it) }
                Choice("Storage", listOf("Keep forever", "Delete after 1 day", "Delete after 1 week", "Delete after 1 month"),
                    listOf(0, 1, 7, 30).indexOf(p.retention).coerceAtLeast(0)) { ed.set(K.retention, listOf(0, 1, 7, 30)[it]) }
                Text("Storage used: about ${"%.2f".format(usage / 1_000_000.0)} million characters", Modifier.padding(16.dp, 4.dp))
                ActionButton(R.drawable.ic_delete, "Clear all history") { scope.launch { ClipStore.clearAll(ctx, false); usage = ClipStore.usage(ctx); toast("History cleared") } }
                SwitchRow("Skip sensitive fields", "Passwords are never saved", p.skipSensitive) { ed.set(K.skipSensitive, it) }
                SwitchRow("Biometric lock", "Require unlock to open history", p.bioLock) { ed.set(K.bioLock, it) }
                Choice("Chunked paste speed", listOf("Fast (100 KB pieces)", "Balanced (75 KB pieces)", "Safe (50 KB pieces)"), p.chunkSpeed) { ed.set(K.chunkSpeed, it) }
                SwitchRow("Show paste progress", null, p.showProgress) { ed.set(K.showProgress, it) }
                Text("Export and import of history is in Backup and restore.", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall)
                ResetButton { ed.reset(K.clipOn, K.retention, K.skipSensitive, K.bioLock, K.chunkSpeed, K.showProgress) }
            }
            Sec.SNIP -> SnippetsScreen()
            Sec.SMART -> {
                Choice("Autocorrect", listOf("Off", "Mild", "Strong"), p.autocorrect) { ed.set(K.autocorrect, it) }
                SwitchRow("Suggestions", "Word completion while you type", p.suggest) { ed.set(K.suggest, it) }
                SwitchRow("Auto-capitalization", null, p.autoCap) { ed.set(K.autoCap, it) }
                SwitchRow("Double-space for a period", null, p.dblSpace) { ed.set(K.dblSpace, it) }
                Text("All typing intelligence runs on this device. No keystrokes are sent anywhere.", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall)
                DictionaryScreen()
                ResetButton { ed.reset(K.autocorrect, K.suggest, K.autoCap, K.dblSpace) }
            }
            Sec.LOOK -> {
                Choice("Theme", listOf("Follow system", "Light", "Dark"), p.theme) { ed.set(K.theme, it) }
                SliderRow("Key height", p.keyHeight, 38..70, " dp") { ed.set(K.keyHeight, it) }
                SliderRow("Font size", p.fontSp, 14..28, " sp") { ed.set(K.fontSp, it) }
                Choice("One-handed mode", listOf("Off", "Dock left", "Dock right"), p.oneHanded) { ed.set(K.oneHanded, it) }
                ResetButton { ed.reset(K.theme, K.keyHeight, K.fontSp, K.oneHanded) }
            }
            Sec.KEYS -> {
                SwitchRow("Vibration", null, p.vibrate) { ed.set(K.vibrate, it) }
                SliderRow("Vibration strength", p.vibMs, 5..60, " ms") { ed.set(K.vibMs, it) }
                SwitchRow("Key sound", null, p.sound) { ed.set(K.sound, it) }
                SliderRow("Sound volume", p.volume, 0..100, "%") { ed.set(K.volume, it) }
                SliderRow("Long-press delay", p.longMs, 150..600, " ms") { ed.set(K.longMs, it) }
                SliderRow("Spacebar cursor drag step", p.dragSens, 8..40, " dp") { ed.set(K.dragSens, it) }
                ResetButton { ed.reset(K.vibrate, K.vibMs, K.sound, K.volume, K.longMs, K.dragSens) }
            }
            Sec.TOOLBAR -> {
                val cur = p.tools.split(",").filter { it.isNotBlank() }
                Layouts.TOOLS.forEach { t ->
                    Row(Modifier.fillMaxWidth().toggleable(t.id in cur, role = Role.Switch, onValueChange = { on ->
                        ed.set(K.tools, Layouts.TOOLS.map { it.id }.filter { id -> if (id == t.id) on else id in cur }.joinToString(","))
                    }).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(t.icon), null); Spacer(Modifier.width(16.dp)); Text(t.label, Modifier.weight(1f)); Switch(t.id in cur, null)
                    }
                }
                ResetButton { ed.reset(K.tools) }
            }
            Sec.PRIVACY -> {
                Text("No keystroke ever leaves your device. No account is needed. Clipboard history, snippets and your dictionary are stored only on this phone. " +
                        "Voice typing, search and translate use the apps you already have installed, and only when you tap them.", Modifier.padding(16.dp))
                ActionButton(R.drawable.ic_edit, "Reset learned dictionary") { scope.launch { Graph.db(ctx).words().wipe(); Dict.reset(); toast("Dictionary reset") } }
                ActionButton(R.drawable.ic_delete, "Delete all app data") { scope.launch { Backup.wipeAll(ctx); toast("All data deleted") } }
            }
            Sec.BACKUP -> {
                val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
                    if (uri != null) scope.launch { try { Backup.export(ctx, uri); toast("Backup saved") } catch (e: Exception) { toast("Backup failed") } }
                }
                val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) scope.launch { try { Backup.import(ctx, uri); toast("Restored") } catch (e: Exception) { toast("Restore failed") } }
                }
                Text("Settings, snippets, dictionary and clipboard history go into one JSON file. Keep it somewhere private: it contains your clipboard text.", Modifier.padding(16.dp))
                ActionButton(R.drawable.ic_backup, "Back up to a file") { exp.launch("everyday-keys-backup.json") }
                ActionButton(R.drawable.ic_add, "Restore from a file") { imp.launch(arrayOf("application/json", "*/*")) }
            }
            Sec.ABOUT -> {
                val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" } }
                Text("Everyday Keys $ver", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                Text("Open source under the MIT license, plus AndroidX, Jetpack Compose, Room and Kotlin coroutines (Apache 2.0).", Modifier.padding(16.dp, 0.dp))
                fun web(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionButton(R.drawable.ic_lock, "Privacy policy") { web("https://github.com/mkmehmood/keyboard/blob/main/PRIVACY.md") }
                ActionButton(R.drawable.ic_edit, "Feedback and bug reports") { web("https://github.com/mkmehmood/keyboard/issues") }
                ActionButton(R.drawable.ic_check, "Rate us") { web("https://github.com/mkmehmood/keyboard") }
            }
        }
    }
}

@Composable
fun ResetButton(onClick: () -> Unit) = ActionButton(R.drawable.ic_undo, "Reset to defaults", onClick)

@Composable
fun SnippetsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by remember { Graph.db(ctx).snippets().flow() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Snippet?>(null) }
    ActionButton(R.drawable.ic_add, "Add snippet") { editing = Snippet(title = "", shortcut = ";", body = "") }
    list.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${s.title}   ${s.shortcut}"); Text(s.body.take(60), style = MaterialTheme.typography.bodySmall) }
            IconButton({ editing = s }) { Icon(painterResource(R.drawable.ic_edit), "Edit ${s.title}") }
            IconButton({ scope.launch { Graph.db(ctx).snippets().delete(s) } }) { Icon(painterResource(R.drawable.ic_delete), "Delete ${s.title}") }
        }
    }
    editing?.let { e ->
        var t by remember(e) { mutableStateOf(e.title) }
        var sc by remember(e) { mutableStateOf(e.shortcut) }
        var b by remember(e) { mutableStateOf(e.body) }
        AlertDialog(onDismissRequest = { editing = null },
            confirmButton = {
                TextButton({
                    scope.launch { Graph.db(ctx).snippets().put(e.copy(title = t, shortcut = sc, body = b)) }; editing = null
                }) { Icon(painterResource(R.drawable.ic_check), null); Spacer(Modifier.width(6.dp)); Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Icon(painterResource(R.drawable.ic_close), null); Spacer(Modifier.width(6.dp)); Text("Cancel") } },
            title = { Text("Snippet") },
            text = {
                Column {
                    OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(sc, { sc = it }, label = { Text("Shortcut, e.g. ;addr") }, singleLine = true)
                    OutlinedTextField(b, { b = it }, label = { Text("Text") })
                }
            })
    }
}

@Composable
fun DictionaryScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val words by remember { Graph.db(ctx).words().flow() }.collectAsState(emptyList())
    var nw by remember { mutableStateOf("") }
    Text("Personal dictionary", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(nw, { nw = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add a word") })
        IconButton({
            val w = nw.trim().lowercase()
            if (w.isNotEmpty()) { scope.launch { Graph.db(ctx).words().put(Word(w, 5)) }; Dict.learned[w] = 5; nw = "" }
        }) { Icon(painterResource(R.drawable.ic_add), "Add word") }
    }
    words.take(100).forEach { w ->
        Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(w.word, Modifier.weight(1f))
            IconButton({ scope.launch { Graph.db(ctx).words().delete(w) }; Dict.learned.remove(w.word) }) { Icon(painterResource(R.drawable.ic_delete), "Delete ${w.word}") }
        }
    }
}
