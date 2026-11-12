package com.mkmehmood.everydaykeys.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mkmehmood.everydaykeys.design.*
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppNav() } }
        // make the real icon match the saved setting (it can drift after a restore or an interrupted change)
        val app = applicationContext
        MainScope().launch { Launcher.sync(app, SettingsRepo.flow(app).first().bool("hideLauncher")) }
    }

    override fun onStart() { super.onStart(); Launcher.uiVisible++ }

    override fun onStop() {
        super.onStop()
        Launcher.uiVisible = maxOf(0, Launcher.uiVisible - 1)
        // a rotation restarts this screen, so wait for the real close
        if (Launcher.uiVisible == 0 && !isChangingConfigurations) Launcher.flush(applicationContext)
    }
}

/** The Material 3 theme only, with no background: for transparent helper screens that show a dialog. */
@Composable
fun AppColors(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val p by remember { SettingsRepo.flow(ctx) }.collectAsState(initial = Prefs(emptyPreferences()))
    EveryDayKeysTheme(dark = isSystemInDarkTheme(), dynamic = p.bool("dynamicColor"), accent = p.str("accent"), content = content)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) = AppColors {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, content = content)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    // Material 3 "fade through" motion with the emphasized easing
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    NavHost(
        nav, "home",
        enterTransition = { fadeIn(tween(210, 90, ease)) + scaleIn(tween(300, 0, ease), initialScale = 0.92f) },
        exitTransition = { fadeOut(tween(90, 0, ease)) },
        popEnterTransition = { fadeIn(tween(210, 90, ease)) + scaleIn(tween(300, 0, ease), initialScale = 0.92f) },
        popExitTransition = { fadeOut(tween(90, 0, ease)) }
    ) {
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
    val enabled = Launcher.imeEnabled(ctx)
    val sel = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(ctx.packageName + "/") == true
    return enabled to sel
}

/** Writes a setting. Hiding the launcher icon is refused until the keyboard is enabled, so the user can never lock themselves out. */
private fun commitSetting(ctx: Context, scope: CoroutineScope, k: String, v: String) {
    if (k == "hideLauncher" && v == "true") {
        if (!imeStatus(ctx).first) {
            Snack.post("Enable Everyday Keys in Android settings first, so you can still reach these settings.")
            return
        }
        Snack.post("The icon will be hidden when you leave this screen. Open settings again from the keyboard's gear icon or Android's keyboard settings.")
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
    SettingsScaffold("Settings", onBack = { owner.finish() }) {
        SearchBox(q, { q = it }, "Search settings")
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (q.isBlank()) {
                item { StatusCard(ctx, status.first, status.second) }
                item {
                    GroupCard {
                        Defs.sections.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            ListRow(
                                s.title, Modifier.clickable { nav.navigate("s/${s.id}") }, sub = s.desc,
                                leading = { Tile(s.icon) },
                                trailing = { Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) }
                            )
                        }
                    }
                }
            } else {
                val hits = Defs.sections.flatMap { s -> s.items.filter { it !is Action && (it.title.contains(q, true) || s.title.contains(q, true)) }.map { s to it } }
                item {
                    if (hits.isEmpty()) Text("No settings match \"$q\"", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(8.dp))
                    else GroupCard {
                        hits.forEachIndexed { i, (s, it) ->
                            if (i > 0) RowDivider()
                            Text(s.title, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                            ItemRow(it, prefs, { k, v -> commitSetting(ctx, scope, k, v) }, ctx) {}
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Setup status as a Material 3 card in the primary container colour. */
@Composable
private fun StatusCard(ctx: Context, enabled: Boolean, selected: Boolean) {
    val ok = enabled && selected
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = CardShape, colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Ic(if (ok) R.drawable.ic_check else R.drawable.ic_keyboard, null, cs.onPrimary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    if (ok) "Everyday Keys is on" else if (!enabled) "Step 1 of 2: enable Everyday Keys" else "Step 2 of 2: switch to Everyday Keys",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (ok) "Enabled and selected as your keyboard" else "Two quick steps from Android",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!ok) PrimaryButton(if (!enabled) "Enable" else "Switch") {
                if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                else ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun ItemRow(item: Item, prefs: Prefs, set: (String, String) -> Unit, ctx: Context, onAction: (Action) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pal = settingsPalette()
    when (item) {
        is Toggle -> {
            val on = prefs.bool(item.key)
            ListRow(
                item.title, Modifier.toggleable(on, role = Role.Switch) { set(item.key, it.toString()) },
                trailing = { DsSwitch(pal, on) }
            )
        }
        is Choice -> {
            var open by remember { mutableStateOf(false) }
            val cur = item.options.firstOrNull { it.first == prefs.str(item.key) }?.second ?: ""
            ListRow(
                item.title, Modifier.clickable(role = Role.DropdownList) { open = true }, sub = cur,
                trailing = { Ic(R.drawable.ic_down, null, cs.onSurfaceVariant) }
            )
            if (open) {
                // Material 3 single-choice dialog
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(item.title) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            item.options.forEach { (v, l) ->
                                val sel = v == prefs.str(item.key)
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Dim.Field)
                                        .selectable(sel, role = Role.RadioButton) { set(item.key, v); open = false },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(sel, null, Modifier.padding(horizontal = 8.dp))
                                    Text(l, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
                )
            }
        }
        is Multi -> if (item.key == "toolbarItems" || item.key == "languages") OrderedMulti(item, prefs, set) else Column(Modifier.padding(vertical = 8.dp)) {
            Text(item.title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
            val cur = prefs.str(item.key).split(",").filter { it.isNotBlank() }
            item.options.forEach { (v, l) ->
                val on = v in cur
                Row(
                    Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { set(item.key, (if (v in cur) cur - v else cur + v).joinToString(",")) }
                        .padding(horizontal = 8.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(on, null, Modifier.padding(horizontal = 8.dp))
                    Text(l, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is Input -> {
            var v by remember(prefs.str(item.key)) { mutableStateOf(prefs.str(item.key)) }
            OutlinedTextField(
                v, { v = it; set(item.key, it.trim()) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
                label = { Text(item.title) },
                visualTransformation = if (item.secret) PasswordVisualTransformation() else VisualTransformation.None
            )
        }
        is Slide -> {
            var v by remember(prefs.int(item.key)) { mutableStateOf(prefs.int(item.key).toFloat()) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(v.toInt().toString(), color = cs.primary, style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    v, { v = it }, valueRange = item.min.toFloat()..item.max.toFloat(),
                    onValueChangeFinished = { set(item.key, v.toInt().toString()) }
                )
            }
        }
        is Action -> {
            val desc = if (item.id == "version")
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
            else item.desc
            val note = item.id == "version" || item.id == "privacyNote" || item.id == "localNote" || item.id == "translateNote" || item.id == "layoutNote"
            ListRow(
                item.title, if (note) Modifier else Modifier.clickable { onAction(item) }, sub = desc,
                titleColor = if (item.id == "wipeAll") cs.error else Color.Unspecified,
                leading = if (note) ({ Ic(R.drawable.ic_info, null, cs.onSurfaceVariant) }) else null,
                trailing = if (note) null else ({ Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) })
            )
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
        Text(item.title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
        cur.forEachIndexed { i, id ->
            val name = labels[id] ?: id
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                }
                Text(name, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { save(cur.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, enabled = i > 0) { Ic(R.drawable.ic_up, "Move $name up") }
                IconButton(onClick = { save(cur.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, enabled = i < cur.lastIndex) { Ic(R.drawable.ic_down, "Move $name down") }
                IconButton(onClick = { save(cur - id) }, enabled = cur.size > 1) { Ic(R.drawable.ic_close, "Remove $name") }
            }
        }
        item.options.filter { it.first !in cur }.forEach { (id, name) ->
            Row(
                Modifier.fillMaxWidth().clickable { save(cur + id) }.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Ic(R.drawable.ic_add, "Add $name", cs.primary, 20.dp) }
                Text(name, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
    }
}

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
    fun toast(t: String) = Snack.post(t)

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.export(ctx, uri) }.onSuccess { toast("Backup saved") }.onFailure { toast("Backup failed") } }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.import(ctx, uri) }.onSuccess { toast("Restored") }.onFailure { toast("Restore failed") } }
    }

    fun onAction(a: Action) {
        when (a.id) {
            "bgEdit" -> nav.navigate("background")
            "resizeKb" -> { commitSetting(ctx, scope, "resizeMode", "true"); toast("Tap the box at the bottom to open the keyboard, then drag its edges") }
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
                    ctx.store.edit { it.clear() }; Launcher.request(ctx, false); toast("All data deleted")
                }
            }
            "export" -> exportL.launch("everyday-keys-backup.json")
            "import" -> importL.launch(arrayOf("application/json", "text/plain"))
            "link" -> a.url?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }

    SettingsScaffold(s.title, onBack = { nav.popBackStack() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(s.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
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
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(c.text) },
            confirmButton = { TextButton(onClick = { c.run(); confirm = null }) { Text("Yes") } },
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
    SettingsScaffold(
        "Text snippets", onBack = { nav.popBackStack() },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { editing = Snippet(title = "", body = "", shortcut = ";") },
                icon = { Ic(R.drawable.ic_add) }, text = { Text("Add snippet") }
            )
        }
    ) {
        if (list.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Tile(R.drawable.ic_snippet, 56.dp)
                Text("No snippets yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Add text you type often, then give it a shortcut such as ;addr.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                GroupCard {
                    list.forEachIndexed { i, sn ->
                        if (i > 0) RowDivider()
                        ListRow(
                            sn.title, Modifier.clickable { editing = sn },
                            sub = sn.shortcut.ifBlank { "No shortcut" } + "  " + sn.body.take(40).replace('\n', ' '),
                            trailing = { IconButton(onClick = { scope.launch { dao.delete(sn) } }) { Ic(R.drawable.ic_trash, "Delete snippet", cs.onSurfaceVariant) } }
                        )
                    }
                }
            }
        }
    }
    editing?.let { e ->
        var title by remember(e) { mutableStateOf(e.title) }
        var sc by remember(e) { mutableStateOf(e.shortcut) }
        var body by remember(e) { mutableStateOf(e.body) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.id == 0L) "New snippet" else "Edit snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(sc, { sc = it.filter { c -> !c.isWhitespace() } }, label = { Text("Shortcut, for example ;addr") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Text") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val shortcut = if (sc.isBlank() || sc == ";") "" else if (sc.startsWith(";")) sc else ";$sc"
                    scope.launch { dao.upsert(e.copy(title = title.ifBlank { shortcut.ifBlank { "Snippet" } }, body = body, shortcut = shortcut)) }
                    editing = null
                }) { Text("Save") }
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
    SettingsScaffold("Personal dictionary", onBack = { nav.popBackStack() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(word, { word = it.trim() }, Modifier.weight(1f), singleLine = true, label = { Text("New word") })
            Spacer(Modifier.width(12.dp))
            PrimaryButton("Add", R.drawable.ic_add) {
                if (word.isNotBlank()) { val w = word; scope.launch { dao.insert(Word(w.lowercase())) }; Suggest.learn(w); word = "" }
            }
        }
        Text(
            "Words here are never autocorrected and are suggested while you type. Stored only on this phone.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        )
        LazyColumn(
            Modifier.weight(1f).then(if (list.isEmpty()) Modifier else Modifier.clip(CardShape).background(cs.surfaceContainer))
        ) {
            items(list, key = { it.word }) { w ->
                ListRow(w.word, trailing = { IconButton(onClick = { scope.launch { dao.delete(w) }; Suggest.forget(w.word) }) { Ic(R.drawable.ic_trash, "Delete word", cs.onSurfaceVariant) } })
                RowDivider()
            }
        }
    }
}
