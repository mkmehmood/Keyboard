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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF2DD4BF), onPrimary = Color.Black, primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1))
    else lightColorScheme(primary = Color(0xFF0F766E), primaryContainer = Color(0xFFDDF0EE), onPrimaryContainer = Color(0xFF0B3D39), background = Color(0xFFF1F4F8), surface = Color.White)
    MaterialTheme(colorScheme = scheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content) }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, "home") {
        composable("home") { HomeScreen(nav) }
        composable("s/{id}") { SectionScreen(it.arguments?.getString("id") ?: "lang", nav) }
        composable("snippets") { SnippetsScreen(nav) }
        composable("dictionary") { DictScreen(nav) }
    }
}

@Composable
private fun Ic(id: Int, desc: String? = null, tint: Color = LocalContentColor.current, size: Dp = 24.dp) =
    Icon(painterResource(id), desc, tint = tint, modifier = Modifier.size(size))

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Ic(R.drawable.ic_back, "Back") }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
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
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        TopBar("Settings") { owner.finish() }
        OutlinedTextField(
            q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            placeholder = { Text("Search settings") }, leadingIcon = { Ic(R.drawable.ic_search) }
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.isBlank()) {
                item { StatusCard(ctx, status.first, status.second) }
                items(Defs.sections) { s ->
                    Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { nav.navigate("s/${s.id}") }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Ic(s.icon, null, MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.title, fontWeight = FontWeight.Medium)
                                Text(s.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Ic(R.drawable.ic_chevron)
                        }
                    }
                }
            } else {
                val hits = Defs.sections.flatMap { s -> s.items.filter { it !is Action && (it.title.contains(q, true) || s.title.contains(q, true)) }.map { s to it } }
                items(hits) { pair ->
                    Column {
                        Text(pair.first.title, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        ItemRow(pair.second, prefs, { k, v -> scope.launch { SettingsRepo.set(ctx, k, v) } }, ctx) {}
                    }
                }
            }
        }
        OutlinedTextField(
            "", {}, Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(14.dp),
            placeholder = { Text("Type here to preview changes") }, leadingIcon = { Ic(R.drawable.ic_keyboard) }
        )
    }
}

@Composable
private fun StatusCard(ctx: Context, enabled: Boolean, selected: Boolean) {
    val ok = enabled && selected
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Ic(if (ok) R.drawable.ic_check else R.drawable.ic_keyboard, null, MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (ok) "Everyday Keys is on" else if (!enabled) "Step 1 of 2: enable Everyday Keys" else "Step 2 of 2: switch to Everyday Keys",
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (ok) "Enabled and selected as your keyboard" else "Two quick steps from Android",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (!ok) Button(onClick = {
                if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                else ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }) { Text(if (!enabled) "Enable" else "Switch") }
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
private fun ItemRow(item: Item, prefs: Prefs, set: (String, String) -> Unit, ctx: Context, onAction: (Action) -> Unit) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    when (item) {
        is Toggle -> Row(
            Modifier.fillMaxWidth().clickable { set(item.key, (!prefs.bool(item.key)).toString()) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.title, Modifier.weight(1f))
            Switch(prefs.bool(item.key), { set(item.key, it.toString()) })
        }
        is Choice -> {
            var open by remember { mutableStateOf(false) }
            val cur = item.options.firstOrNull { it.first == prefs.str(item.key) }?.second ?: ""
            Box {
                Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f))
                    Text(cur, color = sub, fontSize = 14.sp)
                    Ic(R.drawable.ic_chevron)
                }
                DropdownMenu(open, { open = false }) {
                    item.options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { set(item.key, v); open = false }) }
                }
            }
        }
        is Multi -> Column(Modifier.padding(vertical = 6.dp)) {
            Text(item.title)
            val cur = prefs.str(item.key).split(",").filter { it.isNotBlank() }
            item.options.forEach { (v, l) ->
                Row(
                    Modifier.fillMaxWidth().clickable { set(item.key, (if (v in cur) cur - v else cur + v).joinToString(",")) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { Checkbox(v in cur, null, Modifier.padding(12.dp)); Text(l) }
            }
        }
        is Slide -> {
            var v by remember(prefs.int(item.key)) { mutableStateOf(prefs.int(item.key).toFloat()) }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row { Text(item.title, Modifier.weight(1f)); Text(v.toInt().toString(), color = sub) }
                Slider(v, { v = it }, valueRange = item.min.toFloat()..item.max.toFloat(), onValueChangeFinished = { set(item.key, v.toInt().toString()) })
            }
        }
        is Action -> {
            val desc = if (item.id == "version")
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
            else item.desc
            Row(Modifier.fillMaxWidth().clickable { onAction(item) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title)
                    if (desc.isNotBlank()) Text(desc, fontSize = 13.sp, color = sub)
                }
                if (item.id != "version" && item.id != "privacyNote" && item.id != "localNote") Ic(R.drawable.ic_chevron)
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
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.export(ctx, uri) }.onSuccess { toast("Backup saved") }.onFailure { toast("Backup failed") } }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { Backup.import(ctx, uri) }.onSuccess { toast("Restored") }.onFailure { toast("Restore failed") } }
    }

    fun onAction(a: Action) {
        when (a.id) {
            "snippets" -> nav.navigate("snippets")
            "dictionary" -> nav.navigate("dictionary")
            "clearClips" -> confirm = Confirm("Clear all unpinned clipboard history?") {
                scope.launch { ClipRepo(ctx).clearUnpinned(); usage = ClipRepo(ctx).usageBytes() }
            }
            "resetDict" -> confirm = Confirm("Remove all personal dictionary words?") {
                scope.launch { AppDb.get(ctx).words().clear(); toast("Dictionary reset") }
            }
            "wipeAll" -> confirm = Confirm("Delete clipboard, snippets, dictionary and settings? This cannot be undone.") {
                scope.launch {
                    ClipRepo(ctx).clearAll(); AppDb.get(ctx).snippets().clear(); AppDb.get(ctx).words().clear()
                    ctx.store.edit { it.clear() }; toast("All data deleted")
                }
            }
            "export" -> exportL.launch("everyday-keys-backup.json")
            "import" -> importL.launch(arrayOf("application/json", "text/plain"))
            "link" -> a.url?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        TopBar(s.title) { nav.popBackStack() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            s.items.forEach { item ->
                val shown = if (item is Action && item.id == "clearClips")
                    Action(item.id, item.title, "Large items use ${Formatter.formatFileSize(ctx, usage)}. Pinned items are kept.") else item
                ItemRow(shown, prefs, { k, v -> scope.launch { SettingsRepo.set(ctx, k, v) } }, ctx, ::onAction)
                HorizontalDivider()
            }
            val keys = s.items.filter { it !is Action }.map { it.key }
            if (keys.isNotEmpty()) OutlinedButton(
                onClick = { scope.launch { SettingsRepo.reset(ctx, keys) } },
                modifier = Modifier.padding(vertical = 16.dp)
            ) { Ic(R.drawable.ic_undo, null, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Reset to defaults") }
        }
        OutlinedTextField("", {}, Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(14.dp),
            placeholder = { Text("Type here to preview changes") }, leadingIcon = { Ic(R.drawable.ic_keyboard) })
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
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        TopBar("Text snippets") { nav.popBackStack() }
        Button(onClick = { editing = Snippet(title = "", body = "", shortcut = ";") }) {
            Ic(R.drawable.ic_add, null, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Add snippet")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { sn ->
                Row(Modifier.fillMaxWidth().clickable { editing = sn }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sn.title, fontWeight = FontWeight.Medium)
                        Text(sn.shortcut.ifBlank { "No shortcut" } + "  " + sn.body.take(40), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { scope.launch { dao.delete(sn) } }) { Ic(R.drawable.ic_trash, "Delete snippet") }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        TopBar("Personal dictionary") { nav.popBackStack() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(word, { word = it.trim() }, Modifier.weight(1f), singleLine = true, label = { Text("New word") })
            IconButton(onClick = {
                if (word.isNotBlank()) { val w = word; scope.launch { dao.insert(Word(w.lowercase())) }; Suggest.learn(w); word = "" }
            }) { Ic(R.drawable.ic_add, "Add word") }
        }
        Text("Words here are never autocorrected and are suggested while you type. Stored only on this phone.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.word }) { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.word, Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { dao.delete(w) }; Suggest.forget(w.word) }) { Ic(R.drawable.ic_trash, "Delete word") }
                }
            }
        }
    }
}
