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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.mkmehmood.everydaykeys.design.*
import com.mkmehmood.everydaykeys.ime.effStyle
import com.mkmehmood.everydaykeys.ime.keyShape
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
    val dark = when (p.str("theme")) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    EveryDayKeysTheme(dark = dark, dynamic = p.bool("dynamicColor"), accent = p.str("accent"), content = content)
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
        composable("image_studio") { ImageStudioScreen(nav) }
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

private val sectionGroups: List<Pair<String, List<String>>> = listOf(
    "Input & Typing" to listOf("lang", "numpad", "smart", "translate"),
    "Clipboard & Snippets" to listOf("clip", "snippets"),
    "Design & Feedback" to listOf("appearance", "keystyle", "keys", "toolbar"),
    "Privacy & Backup" to listOf("privacy", "backup", "about")
)

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
    val sectionsById = remember { Defs.sections.associateBy { it.id } }

    SettingsScaffold("Everyday Keys", onBack = { owner.finish() }) {
        SearchBox(q, { q = it }, "Search settings")
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (q.isBlank()) {
                item { StatusCard(ctx, status.first, status.second) }
                item {
                    KeyboardThemeCard(
                        selected = prefs.str("theme"),
                        onSelect = { commitSetting(ctx, scope, "theme", it) }
                    )
                }
                item {
                    GroupCard {
                        ListRow(
                            "Create & edit images",
                            Modifier.clickable { nav.navigate("image_studio") }.testTag("home_image_studio_card"),
                            sub = "Create or edit images from prompts with Gemini Nano Banana 2.1",
                            leading = { Tile(R.drawable.ic_sparkle, filled = true) },
                            trailing = { Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) }
                        )
                    }
                }
                sectionGroups.forEach { (groupTitle, ids) ->
                    val itemsInGroup = ids.mapNotNull { sectionsById[it] }
                    if (itemsInGroup.isNotEmpty()) {
                        item(key = "group_$groupTitle") {
                            Column {
                                SectionHeader(groupTitle)
                                GroupCard {
                                    itemsInGroup.forEachIndexed { i, s ->
                                        if (i > 0) RowDivider()
                                        ListRow(
                                            s.title,
                                            Modifier.clickable { nav.navigate("s/${s.id}") }.testTag("section_row_${s.id}"),
                                            sub = s.desc,
                                            leading = { Tile(s.icon) },
                                            trailing = { Ic(R.drawable.ic_right, null, cs.onSurfaceVariant) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val hits = Defs.sections.flatMap { s ->
                    s.items.filter { it !is Action && (it.title.contains(q, true) || s.title.contains(q, true)) }.map { s to it }
                }
                item {
                    if (hits.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Tile(R.drawable.ic_search, 56.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("No settings match \"$q\"", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try searching for theme, clipboard, autocorrect, or language.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        GroupCard {
                            hits.forEachIndexed { i, (s, it) ->
                                if (i > 0) RowDivider()
                                Surface(
                                    shape = CircleShape,
                                    color = cs.primaryContainer,
                                    contentColor = cs.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                                ) {
                                    Text(
                                        s.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                ItemRow(it, prefs, { k, v -> commitSetting(ctx, scope, k, v) }, ctx) {}
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** Expressive Material 3 hero setup status card with layered tonal gradient surface. */
@Composable
private fun StatusCard(ctx: Context, enabled: Boolean, selected: Boolean) {
    val ok = enabled && selected
    val cs = MaterialTheme.colorScheme
    val c1 = if (ok) cs.primaryContainer else cs.secondaryContainer
    val c2 = if (ok) cs.tertiaryContainer.copy(alpha = 0.6f) else cs.primaryContainer.copy(alpha = 0.7f)
    val fg = if (ok) cs.onPrimaryContainer else cs.onSecondaryContainer

    Surface(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = if (ok) 0.18f else 0.30f))
    ) {
        Box(
            Modifier.fillMaxWidth().drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(c1, c2),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(52.dp),
                    shape = CircleShape,
                    color = cs.primary,
                    contentColor = cs.onPrimary,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Ic(if (ok) R.drawable.ic_check else R.drawable.ic_keyboard, null, cs.onPrimary, 26.dp)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = cs.surface.copy(alpha = 0.65f),
                        contentColor = cs.primary
                    ) {
                        Text(
                            if (ok) "Active" else if (!enabled) "Step 1 of 2" else "Step 2 of 2",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        if (ok) "Everyday Keys is ready" else if (!enabled) "Enable Everyday Keys" else "Switch to Everyday Keys",
                        style = MaterialTheme.typography.titleMedium,
                        color = fg
                    )
                    Text(
                        if (ok) "Enabled and selected as your default keyboard" else if (!enabled) "Turn it on in Android keyboard settings" else "Choose it from your input method picker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg.copy(alpha = 0.85f)
                    )
                }
                if (!ok) {
                    PrimaryButton(
                        if (!enabled) "Enable" else "Switch",
                        modifier = Modifier.testTag("setup_status_button")
                    ) {
                        if (!enabled) ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        else ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    }
                }
            }
        }
    }
}

/** Dedicated Material 3 theme mode selector card for Light, Dark, and System default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardThemeCard(selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val options = remember {
        listOf(
            "system" to "System",
            "light" to "Light",
            "dark" to "Dark"
        )
    }
    GroupCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(R.drawable.ic_sun, filled = true)
                Column(Modifier.weight(1f)) {
                    Text("Keyboard theme", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        "Switch between Light, Dark, or System default",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { idx, (value, label) ->
                    SegmentedButton(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                        modifier = Modifier.testTag("theme_selector_$value")
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- live preview

/** Live miniature Material 3 keyboard preview shown on Appearance and Key Style screens. */
@Composable
private fun LiveKeyboardPreview(prefs: Prefs) {
    val ctx = LocalContext.current
    val dark = when (prefs.str("theme")) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val cs = remember(dark, prefs.bool("dynamicColor"), prefs.str("accent")) {
        m3Scheme(ctx, dark, prefs.bool("dynamicColor"), prefs.str("accent"))
    }
    val style = effStyle(prefs)
    val shape = keyShape(prefs)
    val outline = style == "outline"
    val bg = cs.surfaceContainer
    val keyBg = if (dark) cs.surfaceContainerHighest else cs.surfaceContainerLowest
    val keyAltBg = cs.secondaryContainer
    val showHints = prefs.bool("keyHints") || prefs.str("layoutStyle") == "pro"

    @Composable
    fun MiniKey(
        label: String? = null,
        icon: Int? = null,
        hint: String? = null,
        tone: Int = 0,
        modifier: Modifier
    ) {
        val container = when {
            outline -> Color.Transparent
            tone == 2 -> cs.primary
            tone == 1 -> keyAltBg
            else -> keyBg
        }
        val fg = when {
            outline && tone == 2 -> cs.primary
            outline -> cs.onSurface
            tone == 2 -> cs.onPrimary
            tone == 1 -> cs.onSecondaryContainer
            else -> cs.onSurface
        }
        val m = modifier.height(42.dp)
            .then(if (style == "raised") Modifier.shadow(2.dp, shape) else Modifier)
            .clip(shape)
            .background(container)
            .then(if (outline) Modifier.border(1.5.dp, if (tone == 2) cs.primary else cs.outline, shape) else Modifier)
        Box(m, contentAlignment = Alignment.Center) {
            if (icon != null) Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(18.dp))
            else if (label != null) Text(label, color = fg, style = MaterialTheme.typography.titleMedium)
            if (showHints && hint != null) {
                Text(
                    hint,
                    color = cs.primary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 2.dp)
                )
            }
        }
    }

    Card(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Live preview", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Surface(shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                    Text(
                        if (prefs.bool("dynamicColor")) "Material You" else prefs.str("accent").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Q" to "1", "W" to "2", "E" to "3", "R" to "4", "T" to "5", "Y" to "6").forEach { (k, h) ->
                    MiniKey(label = k, hint = h, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MiniKey(icon = R.drawable.ic_up, tone = 1, modifier = Modifier.weight(1.3f))
                MiniKey(icon = R.drawable.ic_emoji, tone = 1, modifier = Modifier.weight(1f))
                MiniKey(icon = R.drawable.ic_space, modifier = Modifier.weight(3.2f))
                MiniKey(icon = R.drawable.ic_enter, tone = 2, modifier = Modifier.weight(1.5f))
            }
        }
    }
}

// ---------------------------------------------------------------- rows

private val InlineSegmentedKeys = setOf("theme", "keyStyle", "autocorrect", "pasteSpeed", "oneHanded", "suggestCount", "aiTone")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(item: Item, prefs: Prefs, set: (String, String) -> Unit, ctx: Context, onAction: (Action) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pal = settingsPalette()
    when (item) {
        is Toggle -> {
            val on = prefs.bool(item.key)
            ListRow(
                item.title,
                Modifier.toggleable(on, role = Role.Switch) { set(item.key, it.toString()) }.testTag("toggle_${item.key}"),
                trailing = { DsSwitch(pal, on) }
            )
        }
        is Choice -> {
            val current = prefs.str(item.key)
            when {
                item.key == "accent" -> AccentSwatchPicker(item.title, item.options, current) { set(item.key, it) }
                item.key in InlineSegmentedKeys && item.options.size <= 4 -> {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            item.options.forEachIndexed { idx, (value, label) ->
                                val shortLabel = label.replace("Follow system", "Auto")
                                SegmentedButton(
                                    selected = value == current,
                                    onClick = { set(item.key, value) },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = item.options.size),
                                    modifier = Modifier.testTag("seg_${item.key}_$value")
                                ) {
                                    Text(shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                else -> {
                    var open by remember { mutableStateOf(false) }
                    val cur = item.options.firstOrNull { it.first == current }?.second ?: ""
                    ListRow(
                        item.title,
                        Modifier.clickable(role = Role.DropdownList) { open = true }.testTag("choice_${item.key}"),
                        sub = cur,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ic(R.drawable.ic_down, null, cs.onSurfaceVariant)
                            }
                        }
                    )
                    if (open) {
                        AlertDialog(
                            onDismissRequest = { open = false },
                            shape = RoundedCornerShape(28.dp),
                            title = { Text(item.title, style = MaterialTheme.typography.headlineSmall) },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    item.options.forEach { (v, l) ->
                                        val sel = v == current
                                        Surface(
                                            shape = Dim.Field,
                                            color = if (sel) cs.secondaryContainer else Color.Transparent,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                .selectable(sel, role = Role.RadioButton) { set(item.key, v); open = false }
                                        ) {
                                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(sel, null)
                                                Text(
                                                    l,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (sel) cs.onSecondaryContainer else cs.onSurface,
                                                    modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
                        )
                    }
                }
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
                v, { v = it; set(item.key, it.trim()) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("input_${item.key}"),
                singleLine = true,
                shape = Dim.Field,
                label = { Text(item.title) },
                visualTransformation = if (item.secret) PasswordVisualTransformation() else VisualTransformation.None
            )
        }
        is Slide -> {
            var v by remember(prefs.int(item.key)) { mutableStateOf(prefs.int(item.key).toFloat()) }
            val unit = when {
                item.title.contains("(ms)") -> " ms"
                item.title.contains("(%)") -> "%"
                else -> ""
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Surface(shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                        Text(
                            "${v.toInt()}$unit",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
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
            val note = item.id == "version" || item.id == "privacyNote" || item.id == "localNote" || item.id == "translateNote" || item.id == "layoutNote" || item.id == "aiNote"
            when {
                item.id == "version" -> ListRow(
                    item.title,
                    leading = { Tile(R.drawable.ic_info) },
                    trailing = {
                        Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                            Text(
                                "v$desc",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                )
                note -> Surface(
                    Modifier.fillMaxWidth().padding(12.dp),
                    shape = Dim.Field,
                    color = cs.surfaceContainerHigh
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Ic(R.drawable.ic_info, null, cs.primary, 20.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                            if (desc.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                else -> {
                    val isDestructive = item.id == "wipeAll"
                    ListRow(
                        item.title,
                        Modifier.clickable { onAction(item) }.testTag("action_${item.id}"),
                        sub = desc,
                        titleColor = if (isDestructive) cs.error else Color.Unspecified,
                        leading = if (isDestructive) ({
                            Surface(Modifier.size(Dim.TileSize), shape = CircleShape, color = cs.errorContainer) {
                                Box(contentAlignment = Alignment.Center) { Ic(R.drawable.ic_trash, null, cs.onErrorContainer, 22.dp) }
                            }
                        }) else null,
                        trailing = { Ic(R.drawable.ic_right, null, if (isDestructive) cs.error else cs.onSurfaceVariant) }
                    )
                }
            }
        }
    }
}

/** Visual Material 3 accent seed swatch selector (Teal, Blue, Purple, Orange). */
@Composable
private fun AccentSwatchPicker(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, label) ->
                val scheme = when (id) {
                    "blue" -> if (dark) M3Schemes.BlueDark else M3Schemes.BlueLight
                    "purple" -> if (dark) M3Schemes.PurpleDark else M3Schemes.PurpleLight
                    "orange" -> if (dark) M3Schemes.OrangeDark else M3Schemes.OrangeLight
                    else -> if (dark) M3Schemes.TealDark else M3Schemes.TealLight
                }
                val isSel = id == selected
                val borderC by animateColorAsState(if (isSel) cs.primary else cs.outlineVariant.copy(alpha = 0.6f), label = "swatchBorder")
                Surface(
                    onClick = { onSelect(id) },
                    shape = Dim.Field,
                    color = if (isSel) cs.primaryContainer else cs.surfaceContainerHigh,
                    border = BorderStroke(if (isSel) 2.dp else 1.dp, borderC),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("accent_swatch_$id")
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(scheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) Ic(R.drawable.ic_check, null, scheme.onPrimary, 16.dp)
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSel) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
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
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(item.title, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
        cur.forEachIndexed { i, id ->
            val name = labels[id] ?: id
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(28.dp), shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily))
                    }
                }
                Text(name, Modifier.weight(1f).padding(horizontal = 14.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(
                    onClick = { save(cur.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) },
                    enabled = i > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Ic(R.drawable.ic_up, "Move $name up") }
                IconButton(
                    onClick = { save(cur.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) },
                    enabled = i < cur.lastIndex,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Ic(R.drawable.ic_down, "Move $name down") }
                IconButton(
                    onClick = { save(cur - id) },
                    enabled = cur.size > 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) { Ic(R.drawable.ic_close, "Remove $name") }
            }
        }
        val remaining = item.options.filter { it.first !in cur }
        if (remaining.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Tap to add", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            remaining.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable { save(cur + id) }.padding(horizontal = 16.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(Modifier.size(28.dp), shape = CircleShape, color = cs.secondaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Ic(R.drawable.ic_add, "Add $name", cs.onSecondaryContainer, 18.dp) }
                    }
                    Text(name, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                }
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
            "imageStudio" -> nav.navigate("image_studio")
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
            Text(s.desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
            AnimatedVisibility(visible = id == "appearance" || id == "keystyle") {
                LiveKeyboardPreview(prefs)
            }
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
                Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("reset_section_button")
            ) { scope.launch { SettingsRepo.reset(ctx, keys) } }
            Spacer(Modifier.height(8.dp))
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RoundedCornerShape(28.dp),
            icon = { Ic(R.drawable.ic_info, null, MaterialTheme.colorScheme.primary) },
            text = { Text(c.text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { Button(onClick = { c.run(); confirm = null }) { Text("Confirm") } },
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
                icon = { Ic(R.drawable.ic_add) },
                text = { Text("Add snippet") },
                containerColor = cs.primaryContainer,
                contentColor = cs.onPrimaryContainer,
                modifier = Modifier.testTag("add_snippet_fab")
            )
        }
    ) {
        if (list.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(72.dp), shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Ic(R.drawable.ic_snippet, null, cs.onPrimaryContainer, 34.dp) }
                }
                Text("No snippets yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Save phrases you type often, then trigger them with a shortcut such as ;addr.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                GroupCard {
                    list.forEachIndexed { i, sn ->
                        if (i > 0) RowDivider()
                        ListRow(
                            sn.title, Modifier.clickable { editing = sn },
                            sub = sn.body.take(48).replace('\n', ' '),
                            leading = if (sn.shortcut.isNotBlank()) ({
                                Surface(shape = Dim.Chip, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                                    Text(
                                        sn.shortcut,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }) else ({ Tile(R.drawable.ic_snippet) }),
                            trailing = {
                                IconButton(
                                    onClick = { scope.launch { dao.delete(sn) } },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) { Ic(R.drawable.ic_trash, "Delete snippet", cs.onSurfaceVariant) }
                            }
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
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (e.id == 0L) "New snippet" else "Edit snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, shape = Dim.Field)
                    OutlinedTextField(sc, { sc = it.filter { c -> !c.isWhitespace() } }, label = { Text("Shortcut, for example ;addr") }, singleLine = true, shape = Dim.Field)
                    OutlinedTextField(body, { body = it }, label = { Text("Text") }, minLines = 3, shape = Dim.Field)
                }
            },
            confirmButton = {
                Button(onClick = {
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
            OutlinedTextField(
                word, { word = it.trim() },
                Modifier.weight(1f).testTag("dict_word_input"),
                singleLine = true,
                shape = Dim.Field,
                label = { Text("New word") }
            )
            Spacer(Modifier.width(12.dp))
            PrimaryButton("Add", R.drawable.ic_add, Modifier.testTag("dict_add_button")) {
                if (word.isNotBlank()) { val w = word; scope.launch { dao.insert(Word(w.lowercase())) }; Suggest.learn(w); word = "" }
            }
        }
        Text(
            "Words here are never autocorrected and are suggested while you type. Stored only on this phone.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        )
        if (list.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(Modifier.size(72.dp), shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Ic(R.drawable.ic_text, null, cs.onSecondaryContainer, 34.dp) }
                }
                Text("No personal words yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Add names, slang, or technical terms above so Everyday Keys remembers them.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).clip(CardShape).background(cs.surfaceContainerLow)
                    .border(0.5.dp, cs.outlineVariant.copy(alpha = 0.55f), CardShape)
            ) {
                itemsIndexed(list, key = { _, w -> w.word }) { i, w ->
                    if (i > 0) RowDivider()
                    ListRow(
                        w.word,
                        trailing = {
                            IconButton(
                                onClick = { scope.launch { dao.delete(w) }; Suggest.forget(w.word) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) { Ic(R.drawable.ic_trash, "Delete word", cs.onSurfaceVariant) }
                        }
                    )
                }
            }
        }
    }
}
