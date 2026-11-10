package com.mkmehmood.everydaykeys.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppNav() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF042F2E),
        primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
        background = Color(0xFF0F151B), surface = Color(0xFF18212A),
        onBackground = Color(0xFFE8ECF1), onSurface = Color(0xFFE8ECF1), onSurfaceVariant = Color(0xFF9AA7B6),
        outline = Color(0xFF4A5766), outlineVariant = Color(0xFF2E3946)
    ) else lightColorScheme(
        primary = Color(0xFF0E7C7B), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF1F0), onPrimaryContainer = Color(0xFF0B3D39),
        background = Color(0xFFF7F9FB), surface = Color.White,
        onBackground = Color(0xFF101C28), onSurface = Color(0xFF101C28), onSurfaceVariant = Color(0xFF4A5A69),
        outline = Color(0xFFB7C2CC), outlineVariant = Color(0xFFD3DBE2)
    )
    MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content) }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, "home") {
        composable("home") { HomeScreen(nav) }
        composable("s/{id}") { SectionScreen(it.arguments?.getString("id") ?: "lang", nav) }
        composable("snippets") { SnippetsScreen(nav) }
        composable("background") { BackgroundScreen(nav) }
        composable("dictionary") { DictScreen(nav) }
    }
}

@Composable
private fun rememberPrefs(): Prefs {
    val ctx = LocalContext.current
    val p by remember { SettingsRepo.flow(ctx) }.collectAsState(initial = Prefs(emptyPreferences()))
    return p
}

private fun imeStatus(ctx: Context): Pair<Boolean, Boolean> {
    val imm = ctx.getSystemService(InputMethodManager::class.java)
    val enabled = imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
    val sel = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(ctx.packageName + "/") == true
    return enabled to sel
}

/** Writes a setting. Hiding the launcher icon is refused until the keyboard is enabled, so the user can never lock themselves out. */
private fun commitSetting(ctx: Context, scope: CoroutineScope, k: String, v: String) {
    if (k == "hideLauncher" && v == "true") {
        if (!imeStatus(ctx).first) {
            Toast.makeText(ctx, "Enable Everyday Keys in Android settings first, so you can still reach these settings.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(ctx, "Icon hidden. Open settings from the keyboard's gear icon or Android's keyboard settings.", Toast.LENGTH_LONG).show()
    }
    scope.launch { SettingsRepo.set(ctx, k, v) }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(nav: NavController) {
    val ctx = LocalContext.current
    val owner = ctx as ComponentActivity
    val prefs = rememberPrefs()
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val status = remember(tick) { imeStatus(ctx) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        SettingsTopBar("Settings") { owner.finish() }
        SearchBox(q, { q = it }, "Search settings")
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (q.isBlank()) {
                item { StatusCard(ctx, status.first, status.second) }
                item {
                    GroupCard {
                        Defs.sections.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate("s/${s.id}") }.padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Tile(s.icon)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(s.desc, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                                }
                                Ic(R.drawable.ic_chevron, tint = cs.onSurfaceVariant, size = 20.dp)
                            }
                        }
                    }
                }
            } else {
                val hits = Defs.sections.flatMap { s -> s.items.filter { it !is Action && (it.title.contains(q, true) || s.title.contains(q, true)) }.map { s to it } }
                item {
                    if (hits.isEmpty()) Text("No settings match \"$q\"", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    else GroupCard {
                        hits.forEachIndexed { i, (s, it) ->
                            if (i > 0) RowDivider()
                            Text(s.title, fontSize = 12.sp, color = cs.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
                            ItemRow(it, prefs, { k, v -> commitSetting(ctx, scope, k, v) }, ctx) {}
                        }
                    }
                }
            }
        }
        PreviewField(Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun StatusCard(ctx: Context, enabled: Boolean, selected: Boolean) {
    val ok = enabled && selected
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(cs.primaryContainer).border(1.dp, cs.primary.copy(alpha = 0.25f), CardShape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
            Ic(if (ok) R.drawable.ic_check else R.drawable.ic_keyboard, null, cs.onPrimary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (ok) "Everyday Keys is on" else if (!enabled) "Step 1 of 2: enable Everyday Keys" else "Step 2 of 2: switch to Everyday Keys",
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = cs.onPrimaryContainer
            )
            Text(
                if (ok) "Enabled and selected as your keyboard" else "Two quick steps from Android",
                fontSize = 12.5.sp, color = cs.onPrimaryContainer
            )
        }
        if (!ok) PrimaryButton(if (!enabled) "Enable" else "Switch") {
            if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            else ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
    }
}

// ---------------------------------------------------------------- rows

private val RowPad = Modifier.padding(horizontal = 14.dp)

@Composable
private fun ItemRow(item: Item, prefs: Prefs, set: (String, String) -> Unit, ctx: Context, onAction: (Action) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sub = cs.onSurfaceVariant
    when (item) {
        is Toggle -> {
            val on = prefs.bool(item.key)
            Row(
                Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { set(item.key, it.toString()) }
                    .padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, Modifier.weight(1f).padding(end = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Switch(on, null, colors = settingsSwitchColors())
            }
        }
        is Choice -> {
            var open by remember { mutableStateOf(false) }
            val cur = item.options.firstOrNull { it.first == prefs.str(item.key) }?.second ?: ""
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, Modifier.weight(1f).padding(end = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(cur, color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Ic(R.drawable.ic_down, null, sub, 20.dp)
                }
                DropdownMenu(open, { open = false }) {
                    item.options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { set(item.key, v); open = false }) }
                }
            }
        }
        is Multi -> if (item.key == "toolbarItems" || item.key == "languages") OrderedMulti(item, prefs, set) else Column(Modifier.padding(vertical = 8.dp)) {
            Text(item.title, RowPad, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            val cur = prefs.str(item.key).split(",").filter { it.isNotBlank() }
            item.options.forEach { (v, l) ->
                val on = v in cur
                Row(
                    Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { set(item.key, (if (v in cur) cur - v else cur + v).joinToString(",")) }
                        .padding(horizontal = 14.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline))
                    Text(l, Modifier.padding(start = 12.dp), fontSize = 14.5.sp)
                }
            }
        }
        is Input -> {
            var v by remember(prefs.str(item.key)) { mutableStateOf(prefs.str(item.key)) }
            OutlinedTextField(
                v, { v = it; set(item.key, it.trim()) }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), singleLine = true,
                shape = FieldShape, label = { Text(item.title) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = cs.primary, focusedLabelColor = cs.primary, unfocusedBorderColor = cs.outlineVariant),
                visualTransformation = if (item.secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            )
        }
        is Slide -> {
            var v by remember(prefs.int(item.key)) { mutableStateOf(prefs.int(item.key).toFloat()) }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(v.toInt().toString(), color = cs.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Slider(
                    v, { v = it }, valueRange = item.min.toFloat()..item.max.toFloat(),
                    colors = settingsSliderColors(), onValueChangeFinished = { set(item.key, v.toInt().toString()) }
                )
            }
        }
        is Action -> {
            val desc = if (item.id == "version")
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
            else item.desc
            val note = item.id == "version" || item.id == "privacyNote" || item.id == "localNote" || item.id == "translateNote"
            Row(
                Modifier.fillMaxWidth().then(if (note) Modifier else Modifier.clickable { onAction(item) })
                    .padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (item.id == "wipeAll") cs.error else cs.onSurface)
                    if (desc.isNotBlank()) Text(desc, fontSize = 12.5.sp, color = sub)
                }
                if (!note) Ic(R.drawable.ic_chevron, null, sub, 20.dp)
            }
        }
    }
}

/** A list whose order matters: enabled items first with move up, move down and remove buttons, then the rest to add. */
@Composable
private fun OrderedMulti(item: Multi, prefs: Prefs, set: (String, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = prefs.str(item.key).split(",").filter { id -> id.isNotBlank() && item.options.any { it.first == id } }
    val labels = item.options.toMap()
    fun save(l: List<String>) = set(item.key, l.joinToString(","))
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(item.title, RowPad, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        cur.forEachIndexed { i, id ->
            val name = labels[id] ?: id
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RowShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                }
                Text(name, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 14.5.sp)
                IconButton(onClick = { save(cur.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0) { Ic(R.drawable.ic_up, "Move $name up") }
                IconButton(onClick = { save(cur.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < cur.lastIndex) { Ic(R.drawable.ic_down, "Move $name down") }
                IconButton(onClick = { save(cur - id) }, enabled = cur.size > 1) { Ic(R.drawable.ic_close, "Remove $name") }
            }
        }
        item.options.filter { it.first !in cur }.forEach { (id, name) ->
            Row(
                Modifier.fillMaxWidth().clickable { save(cur + id) }.padding(horizontal = 14.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_add, "Add $name", cs.primary, 20.dp)
                Text(name, Modifier.padding(start = 12.dp), fontSize = 14.5.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

private val RowShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

private class Confirm(val text: String, val run: () -> Unit)

@Composable
fun SectionScreen(id: String, nav: NavController) {
    val s = Defs.sections.first { it.id == id }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = rememberPrefs()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var usage by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { usage = ClipRepo(ctx).usageBytes() }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.export(ctx, uri) }.onSuccess { toast("Backup saved") }.onFailure { toast("Backup failed") } }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.import(ctx, uri) }.onSuccess { toast("Restored") }.onFailure { toast("Restore failed") } }
    }

    fun onAction(a: Action) {
        when (a.id) {
            "bgEdit" -> nav.navigate("background")
            "snippets" -> nav.navigate("snippets")
            "dictionary" -> nav.navigate("dictionary")
            "clearClips" -> confirm = Confirm("Clear all unpinned clipboard history?") {
                scope.launch { ClipRepo(ctx).clearUnpinned(); usage = ClipRepo(ctx).usageBytes() }
            }
            "resetDict" -> confirm = Confirm("Remove your personal words and learned word pairs?") {
                scope.launch { AppDb.get(ctx).words().clear(); Bigrams.clear(ctx); toast("Dictionary reset") }
            }
            "wipeAll" -> confirm = Confirm("Delete clipboard, snippets, dictionary and settings? This cannot be undone.") {
                scope.launch {
                    KbBackground.delete(ctx); ClipRepo(ctx).clearAll(); AppDb.get(ctx).snippets().clear(); AppDb.get(ctx).words().clear(); Bigrams.clear(ctx)
                    ctx.store.edit { it.clear() }; Launcher.apply(ctx, false); toast("All data deleted")
                }
            }
            "export" -> exportL.launch("everyday-keys-backup.json")
            "import" -> importL.launch(arrayOf("application/json", "text/plain"))
            "link" -> a.url?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        SettingsTopBar(s.title) { nav.popBackStack() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(s.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            GroupCard {
                s.items.forEachIndexed { i, item ->
                    if (i > 0) RowDivider()
                    val shown = if (item is Action && item.id == "clearClips")
                        Action(item.id, item.title, "Large items use ${Formatter.formatFileSize(ctx, usage)}. Pinned items are kept.") else item
                    ItemRow(shown, prefs, { k, v -> commitSetting(ctx, scope, k, v) }, ctx, ::onAction)
                }
            }
            val keys = s.items.filter { it !is Action && it !is Input }.map { it.key }
            if (keys.isNotEmpty()) SecondaryButton(
                "Reset to defaults", R.drawable.ic_undo,
                Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) { scope.launch { SettingsRepo.reset(ctx, keys) } }
        }
        PreviewField(Modifier.padding(vertical = 12.dp))
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = MaterialTheme.colorScheme.surface, shape = CardShape,
            text = { Text(c.text) },
            confirmButton = { TextButton(onClick = { c.run(); confirm = null }) { Text("Yes", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- snippets and dictionary

@Composable
fun SnippetsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).snippets() }
    val list by remember { dao.observe() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Snippet?>(null) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        SettingsTopBar("Text snippets") { nav.popBackStack() }
        PrimaryButton("Add snippet", R.drawable.ic_add, Modifier.fillMaxWidth()) { editing = Snippet(title = "", body = "", shortcut = ";") }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Text("No snippets yet. Add text you type often, then give it a shortcut such as ;addr.", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            if (list.isNotEmpty()) item {
                GroupCard {
                    list.forEachIndexed { i, sn ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = sn }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sn.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(sn.shortcut.ifBlank { "No shortcut" } + "  " + sn.body.take(40), fontSize = 12.5.sp, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { scope.launch { dao.delete(sn) } }) { Ic(R.drawable.ic_trash, "Delete snippet", cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        PreviewField(Modifier.padding(vertical = 12.dp))
    }
    editing?.let { e ->
        var title by remember(e) { mutableStateOf(e.title) }
        var sc by remember(e) { mutableStateOf(e.shortcut) }
        var body by remember(e) { mutableStateOf(e.body) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = cs.surface, shape = CardShape,
            title = { Text(if (e.id == 0L) "New snippet" else "Edit snippet", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, shape = FieldShape)
                    OutlinedTextField(sc, { sc = it.filter { c -> !c.isWhitespace() } }, label = { Text("Shortcut, for example ;addr") }, singleLine = true, shape = FieldShape)
                    OutlinedTextField(body, { body = it }, label = { Text("Text") }, minLines = 3, shape = FieldShape)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val shortcut = if (sc.isBlank() || sc == ";") "" else if (sc.startsWith(";")) sc else ";$sc"
                    scope.launch { dao.upsert(e.copy(title = title.ifBlank { shortcut.ifBlank { "Snippet" } }, body = body, shortcut = shortcut)) }
                    editing = null
                }) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DictScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).words() }
    val list by remember { dao.observePersonal() }.collectAsState(initial = emptyList())
    var word by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        SettingsTopBar("Personal dictionary") { nav.popBackStack() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                word, { word = it.trim() }, Modifier.weight(1f), singleLine = true, shape = FieldShape, label = { Text("New word") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = cs.primary, focusedLabelColor = cs.primary, unfocusedBorderColor = cs.outlineVariant)
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton("Add", R.drawable.ic_add) {
                if (word.isNotBlank()) { val w = word; scope.launch { dao.insert(Word(w.lowercase())) }; Suggest.learn(w); word = "" }
            }
        }
        Text(
            "Words here are never autocorrected and are suggested while you type. Stored only on this phone.",
            fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        )
        LazyColumn(
            Modifier.weight(1f).then(if (list.isEmpty()) Modifier else Modifier.clip(CardShape).background(cs.surface).border(1.dp, cs.outlineVariant, CardShape))
        ) {
            items(list, key = { it.word }) { w ->
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, Modifier.weight(1f), fontSize = 15.sp)
                    IconButton(onClick = { scope.launch { dao.delete(w) }; Suggest.forget(w.word) }) { Ic(R.drawable.ic_trash, "Delete word", cs.onSurfaceVariant) }
                }
                RowDivider()
            }
        }
        PreviewField(Modifier.padding(vertical = 12.dp))
    }
}
