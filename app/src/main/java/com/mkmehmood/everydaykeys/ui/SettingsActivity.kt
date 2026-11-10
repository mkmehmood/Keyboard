package com.mkmehmood.everydaykeys.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.Backup
import com.mkmehmood.everydaykeys.data.ClipStore
import com.mkmehmood.everydaykeys.data.Db
import com.mkmehmood.everydaykeys.data.P
import com.mkmehmood.everydaykeys.data.Snap
import com.mkmehmood.everydaykeys.data.Snippet
import com.mkmehmood.everydaykeys.data.dataStore
import com.mkmehmood.everydaykeys.data.resetPrefs
import com.mkmehmood.everydaykeys.data.setPref
import com.mkmehmood.everydaykeys.data.snapFlow
import com.mkmehmood.everydaykeys.ime.Layouts
import com.mkmehmood.everydaykeys.ime.Tools
import com.mkmehmood.everydaykeys.smart.SuggestionEngine
import kotlinx.coroutines.launch

private const val REPO = "https://github.com/mkmehmood/keyboard"

class SettingsActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent.getStringExtra("route")
        setContent { AppTheme { SettingsApp(route, resumeTick.intValue) { finish() } } }
    }

    override fun onResume() { super.onResume(); resumeTick.intValue++ }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF00201C), primaryContainer = Color(0xFF123B38), onPrimaryContainer = Color(0xFFCFF3EE),
        background = Color(0xFF11171D), surface = Color(0xFF1A222A),
    ) else lightColorScheme(
        primary = Color(0xFF0F766E), onPrimary = Color.White, primaryContainer = Color(0xFFDDF1EF), onPrimaryContainer = Color(0xFF0B3B37),
        background = Color(0xFFEAF0F4), surface = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Ic(res: Int, desc: String? = null, tint: Color = LocalContentColor.current, size: Int = 24) =
    Icon(painterResource(res), desc, tint = tint, modifier = Modifier.size(size.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) = TopAppBar(
    title = { Text(title, fontWeight = FontWeight.SemiBold) },
    navigationIcon = { IconButton(onClick = onBack) { Ic(R.drawable.ic_chevron_left, "Back") } },
    actions = actions,
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
)

@Composable
fun SettingsApp(startRoute: String?, tick: Int, finish: () -> Unit) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val snap by remember { ctx.snapFlow() }.collectAsState(initial = Snap(emptyPreferences()))
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(nav, startDestination = startRoute?.let { if (it == "snippets") "home" else "home" } ?: "home") {
            composable("home") { Home(nav, tick, finish) }
            composable("sec/{id}") { e -> SectionScreen(Sections.byId(e.arguments?.getString("id") ?: "languages"), snap, nav) }
            composable("languages") { LanguagesScreen(snap, nav) }
            composable("toolbar") { ToolbarScreen(snap, nav) }
            composable("snippets") { SnippetsScreen(nav) }
            composable("dictionary") { DictionaryScreen(nav) }
        }
    }
    LaunchedEffect(startRoute) { if (startRoute == "snippets") nav.navigate("snippets") }
}

// ---------------- Home ----------------
@Composable
fun Home(nav: NavController, tick: Int, finish: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    Scaffold(topBar = { Bar("Settings", finish) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search settings") }, shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Ic(R.drawable.ic_search, "Search") },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Ic(R.drawable.ic_close, "Clear search") } },
                    )
                }
                item { StatusCard(tick) }
                if (query.isBlank()) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column {
                                Sections.all.forEachIndexed { i, s ->
                                    SectionRow(s.title, s.sub, s.icon) { nav.navigate("sec/${s.id}") }
                                    if (i < Sections.all.size - 1) HorizontalDivider(Modifier.padding(start = 68.dp))
                                }
                            }
                        }
                    }
                } else {
                    val q = query.trim().lowercase()
                    val hits = Sections.all.flatMap { s ->
                        val own = if (s.title.lowercase().contains(q) || s.sub.lowercase().contains(q)) listOf(Triple(s, s.title, s.sub)) else emptyList()
                        own + s.items.filter { it.title.lowercase().contains(q) || it.desc.lowercase().contains(q) }.map { Triple(s, it.title, s.title) }
                    }
                    if (hits.isEmpty()) item { Text("No settings match \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(hits) { (s, title, sub) ->
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            SectionRow(title, sub, s.icon) { nav.navigate("sec/${s.id}") }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = preview, onValueChange = { preview = it }, modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Type here to preview changes") }, shape = RoundedCornerShape(14.dp),
                leadingIcon = { Ic(R.drawable.ic_keyboard, "Preview field") },
            )
        }
    }
}

@Composable
fun SectionRow(title: String, sub: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Ic(icon, null, MaterialTheme.colorScheme.primary, 22)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Ic(R.drawable.ic_chevron_right, null, MaterialTheme.colorScheme.onSurfaceVariant, 20)
    }
}

@Composable
fun StatusCard(tick: Int) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        enabled = imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
        val def = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        selected = def.startsWith(ctx.packageName + "/")
    }
    val ok = enabled && selected
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Ic(if (ok) R.drawable.ic_check else R.drawable.ic_keyboard, null, MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(if (ok) "Everyday Keys is on" else if (!enabled) "Enable Everyday Keys" else "Switch to Everyday Keys", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(if (ok) "Enabled and selected as your keyboard" else if (!enabled) "Step 1 of 2: turn it on in Android keyboard settings" else "Step 2 of 2: choose it as your active keyboard",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!ok) {
                Button(onClick = {
                    if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    else (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                }) {
                    Ic(if (!enabled) R.drawable.ic_settings else R.drawable.ic_swap, null, size = 18)
                    Spacer(Modifier.width(6.dp))
                    Text(if (!enabled) "Enable" else "Switch")
                }
            }
        }
    }
}

// ---------------- Generic section screen ----------------
@Composable
fun SectionScreen(sec: Section, snap: Snap, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    var meterKey by remember { mutableIntStateOf(0) }
    var kind by remember { mutableStateOf("backup") }
    val toast = { m: String -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = when (kind) {
                    "clip_export" -> Backup.exportAll(ctx, settings = false, snippets = false, dict = false, clips = true)
                    "snip_export" -> Backup.exportAll(ctx, settings = false, snippets = true, dict = false, clips = false)
                    else -> Backup.exportAll(ctx)
                }
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                toast("Saved")
            } catch (e: Exception) { toast("Could not save: ${e.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                Backup.importAll(ctx, text)
                meterKey++
                toast("Imported")
            } catch (e: Exception) { toast("Could not import: ${e.message}") }
        }
    }

    fun run(id: String) {
        when (id) {
            "clip_clear", "dict_reset", "data_delete" -> confirm = id
            "clip_export", "snip_export", "backup" -> { kind = id; exporter.launch("everyday-keys-$id.json") }
            "clip_import", "snip_import", "restore" -> importer.launch(arrayOf("application/json", "text/plain", "*/*"))
            "licenses" -> showLicenses = true
            "privacy_policy" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$REPO/blob/main/PRIVACY.md")))
            "feedback" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$REPO/issues")))
            "rate" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO)))
        }
    }

    Scaffold(topBar = {
        Bar(sec.title, { nav.popBackStack() }) {
            if (sec.prefs.isNotEmpty()) IconButton({ scope.launch { ctx.resetPrefs(sec.prefs); toast("Reset to defaults") } }) { Ic(R.drawable.ic_reset, "Reset section to defaults") }
        }
    }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sec.items) { item ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    ItemRow(item, snap, nav, meterKey) { run(it) }
                }
            }
        }
    }

    confirm?.let { id ->
        val msg = when (id) {
            "clip_clear" -> "Remove all unpinned clipboard items and their files?"
            "dict_reset" -> "Forget all learned words and word pairs? Words you added yourself are kept."
            else -> "Delete settings, snippets, dictionary and clipboard history? This cannot be undone."
        }
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") }, text = { Text(msg) },
            confirmButton = {
                TextButton({
                    confirm = null
                    scope.launch {
                        val db = Db.get(ctx)
                        when (id) {
                            "clip_clear" -> ClipStore(ctx).clearUnpinned()
                            "dict_reset" -> { db.words().clearLearned(); db.bigrams().clear() }
                            else -> {
                                ClipStore(ctx).clearEverything(); db.snippets().deleteAll(); db.words().deleteAll(); db.bigrams().clear()
                                ctx.dataStore.edit { it.clear() }
                            }
                        }
                        meterKey++
                        toast("Done")
                    }
                }) { Ic(R.drawable.ic_check, null, size = 18); Spacer(Modifier.width(6.dp)); Text("Confirm") }
            },
            dismissButton = { TextButton({ confirm = null }) { Ic(R.drawable.ic_close, null, size = 18); Spacer(Modifier.width(6.dp)); Text("Cancel") } })
    }
    if (showLicenses) AlertDialog(onDismissRequest = { showLicenses = false },
        title = { Text("Open-source licenses") },
        text = { Text("Everyday Keys is released under the MIT License.\n\nIt uses AndroidX (Core, AppCompat, RecyclerView, Room, DataStore, Biometric, Navigation), Jetpack Compose and Material 3, and Kotlin coroutines, all under the Apache License 2.0.") },
        confirmButton = { TextButton({ showLicenses = false }) { Ic(R.drawable.ic_check, null, size = 18) } })
}

@Composable
fun ItemRow(item: Item, snap: Snap, nav: NavController, meterKey: Int, onAction: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    when (item) {
        is Toggle -> Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { scope.launch { ctx.setPref(item.p, !snap[item.p]) } }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.title, fontSize = 16.sp); if (item.desc.isNotEmpty()) Text(item.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = snap[item.p], onCheckedChange = { v -> scope.launch { ctx.setPref(item.p, v) } })
        }
        is Slide -> {
            var v by remember(snap[item.p]) { mutableFloatStateOf(snap[item.p]) }
            Column(Modifier.padding(14.dp)) {
                Row { Text(item.title, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text(if (item.max <= 1f) "${(v * 100).toInt()}%" else "${v.toInt()}${item.unit}", color = MaterialTheme.colorScheme.primary) }
                Slider(value = v, onValueChange = { v = it }, valueRange = item.min..item.max, onValueChangeFinished = { scope.launch { ctx.setPref(item.p, v) } })
            }
        }
        is Choice -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(item.title, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (item.desc.isNotEmpty()) Text(item.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp))
            item.options.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { scope.launch { ctx.setPref(item.p, value) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = snap[item.p] == value, onClick = { scope.launch { ctx.setPref(item.p, value) } })
                    Text(label, fontSize = 15.sp)
                }
            }
        }
        is Link -> SectionRow(item.title, item.desc, R.drawable.ic_chevron_right) { nav.navigate(item.route) }
        is Action -> Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onAction(item.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(actionIcon(item.id), null, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(item.title, fontSize = 16.sp); if (item.desc.isNotEmpty()) Text(item.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        is Note -> Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Ic(R.drawable.ic_info, null, MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 14.dp)) {
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                val d = if (item.title == "Version") runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("") else item.desc
                if (d.isNotEmpty()) Text(d, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is Meter -> {
            var bytes by remember { mutableLongStateOf(-1L) }
            LaunchedEffect(meterKey) { bytes = ClipStore(ctx).usageBytes() }
            Column(Modifier.padding(14.dp)) {
                Text(item.title, fontSize = 16.sp)
                Text(if (bytes < 0) "Calculating" else "%.1f MB used by clipboard files and history".format(bytes / 1_000_000.0), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { if (bytes < 0) 0f else (bytes / 100_000_000f).coerceIn(0.02f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

// ---------------- Languages and toolbar editors ----------------
@Composable
fun LanguagesScreen(snap: Snap, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = snap[P.layouts].split(",").filter { id -> Layouts.all.any { it.id == id } }
    val ordered = enabled.map { Layouts.byId(it) } + Layouts.all.filter { it.id !in enabled }
    fun save(l: List<String>) { if (l.isNotEmpty()) scope.launch { ctx.setPref(P.layouts, l.joinToString(",")) } }
    Scaffold(topBar = { Bar("Languages and layouts", { nav.popBackStack() }) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ordered) { def ->
                val on = def.id in enabled
                val idx = enabled.indexOf(def.id)
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { c -> save(if (c) enabled + def.id else enabled - def.id) })
                        Text(def.name, Modifier.weight(1f), fontSize = 16.sp)
                        if (on) {
                            IconButton({ if (idx > 0) save(enabled.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) }) }, enabled = idx > 0) { Ic(R.drawable.ic_chevron_up, "Move up") }
                            IconButton({ if (idx < enabled.size - 1) save(enabled.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) }) }, enabled = idx < enabled.size - 1) { Ic(R.drawable.ic_chevron_down, "Move down") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarScreen(snap: Snap, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = snap[P.toolbar].split(",").filter { Tools.byId(it) != null }
    val ordered = enabled.mapNotNull { Tools.byId(it) } + Tools.all.filter { it.id !in enabled }
    fun save(l: List<String>) { scope.launch { ctx.setPref(P.toolbar, l.joinToString(",")) } }
    Scaffold(topBar = { Bar("Toolbar tools", { nav.popBackStack() }) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ordered) { t ->
                val on = t.id in enabled
                val idx = enabled.indexOf(t.id)
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = { c -> save(if (c) enabled + t.id else enabled - t.id) })
                        Ic(t.icon, null, MaterialTheme.colorScheme.primary)
                        Text(t.name, Modifier.weight(1f).padding(start = 12.dp), fontSize = 16.sp)
                        if (on) {
                            IconButton({ if (idx > 0) save(enabled.toMutableList().also { it.add(idx - 1, it.removeAt(idx)) }) }, enabled = idx > 0) { Ic(R.drawable.ic_chevron_up, "Move up") }
                            IconButton({ if (idx < enabled.size - 1) save(enabled.toMutableList().also { it.add(idx + 1, it.removeAt(idx)) }) }, enabled = idx < enabled.size - 1) { Ic(R.drawable.ic_chevron_down, "Move down") }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Snippets and dictionary ----------------
@Composable
fun SnippetsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Db.get(ctx) }
    val list by remember { db.snippets().observe() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { Bar("Text snippets", { nav.popBackStack() }) },
        floatingActionButton = { FloatingActionButton({ adding = true }) { Ic(R.drawable.ic_add, "Add snippet") } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) item { Text("No snippets yet. Add your email, address or phone, give each a shortcut like ;addr, then type the shortcut and a space.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list) { s ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(s.shortcut, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Text(s.body.replace('\n', ' ').take(70), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton({ editing = s }) { Ic(R.drawable.ic_edit, "Edit snippet") }
                        IconButton({ scope.launch { db.snippets().delete(s.id) } }) { Ic(R.drawable.ic_trash, "Delete snippet") }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val base = editing
        var title by remember(base) { mutableStateOf(base?.title ?: "") }
        var shortcut by remember(base) { mutableStateOf(base?.shortcut ?: ";") }
        var body by remember(base) { mutableStateOf(base?.body ?: "") }
        AlertDialog(onDismissRequest = { adding = false; editing = null },
            title = { Text(if (base == null) "New snippet" else "Edit snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(shortcut, { shortcut = it.trim() }, label = { Text("Shortcut, for example ;addr") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Text to insert") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton({
                    val sc = if (shortcut.startsWith(";")) shortcut else ";$shortcut"
                    if (title.isNotBlank() && sc.length > 1 && body.isNotEmpty()) {
                        scope.launch { db.snippets().upsert(Snippet(id = base?.id ?: 0, title = title.trim(), shortcut = sc, body = body)) }
                        adding = false; editing = null
                    }
                }) { Ic(R.drawable.ic_check, "Save", size = 18); Spacer(Modifier.width(6.dp)); Text("Save") }
            },
            dismissButton = { TextButton({ adding = false; editing = null }) { Ic(R.drawable.ic_close, "Cancel", size = 18); Spacer(Modifier.width(6.dp)); Text("Cancel") } })
    }
}

@Composable
fun DictionaryScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Db.get(ctx) }
    val engine = remember { SuggestionEngine(ctx) }
    val words by remember { db.words().observe() }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            text.split(Regex("[\\s,;]+")).filter { it.length in 2..40 }.distinct().forEach { engine.addPersonal(it) }
            Toast.makeText(ctx, "Imported", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        topBar = { Bar("Personal dictionary", { nav.popBackStack() }) { IconButton({ importer.launch(arrayOf("text/plain", "*/*")) }) { Ic(R.drawable.ic_download, "Import words from a file") } } },
        floatingActionButton = { FloatingActionButton({ adding = true }) { Ic(R.drawable.ic_add, "Add word") } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (words.isEmpty()) item { Text("Words you add or type often appear here. Everything stays on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(words) { w ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(w.word, fontSize = 16.sp)
                            Text(if (w.personal) "Added by you" else "Learned, used ${w.count} times", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ scope.launch { db.words().delete(w.word) } }) { Ic(R.drawable.ic_trash, "Delete word") }
                    }
                }
            }
        }
    }
    if (adding) {
        var word by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add word") },
            text = { OutlinedTextField(word, { word = it }, singleLine = true, label = { Text("Word") }) },
            confirmButton = { TextButton({ if (word.isNotBlank()) scope.launch { engine.addPersonal(word) }; adding = false }) { Ic(R.drawable.ic_check, "Save", size = 18); Spacer(Modifier.width(6.dp)); Text("Save") } },
            dismissButton = { TextButton({ adding = false }) { Ic(R.drawable.ic_close, "Cancel", size = 18); Spacer(Modifier.width(6.dp)); Text("Cancel") } })
    }
}
