package com.mkmehmood.everydaykeys.ime

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.design.*
import com.mkmehmood.everydaykeys.data.AppDb
import com.mkmehmood.everydaykeys.data.Calc
import com.mkmehmood.everydaykeys.data.Clip
import com.mkmehmood.everydaykeys.data.Translator
import com.mkmehmood.everydaykeys.data.UnitDef
import com.mkmehmood.everydaykeys.data.Units
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Every screen that opens above the keyboard (clipboard, snippets, emoji, converter, cursor keys, paste progress, translation)
// uses the same Material 3 look as the Settings screens: filled cards, tonal circular icon containers,
// pill search and buttons, filter chips.

internal val PanelCard = Dim.Card
internal val PanelField = Dim.Field

/** Every panel that replaces the keys is this tall, so the keyboard does not jump when switching between them. */
private val PanelHeight = 270.dp

/** Panels take the height of the key area they replace, so every mode and panel has the same keyboard size. */
@Composable
internal fun panelHeight(svc: EveryDayKeysService): Dp {
    val keysH = with(androidx.compose.ui.platform.LocalDensity.current) { svc.keysHeightPx.toDp() }
    return if (svc.keysHeightPx > 0) keysH else PanelHeight
}

/** The thin border colour used on cards and fields. */
/** The keyboard's colours expressed as the shared design palette, so panels use the same pieces as Settings. */
internal fun KbColors.palette() = Palette(
    surface = panel, text = text, sub = sub, accent = accent, onAccent = onAccent,
    hairline = sub.copy(alpha = 0.30f), tile = tile, off = sub.copy(alpha = 0.55f), onTile = onTile,
    trackOff = sub.copy(alpha = 0.25f), accentContainer = accentContainer, onAccentContainer = onAccentContainer
)

private fun hair(c: KbColors) = c.palette().hairline

/** A plain icon (no box) for row starts and tools; accent when [filled], otherwise the quiet secondary colour. */
@Composable
internal fun PanelTile(c: KbColors, icon: Int, size: Dp = 34.dp, filled: Boolean = false) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = if (filled) c.accent else c.sub, modifier = Modifier.size(size * 0.65f))
    }
}

/** A toolbar icon, 48dp tall tap target. */
@Composable
internal fun ToolTile(icon: Int, desc: String, c: KbColors, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(48.dp).semantics { contentDescription = desc }, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { PanelTile(c, icon, 34.dp, filled) }
    }
}

@Composable
internal fun PanelHeader(c: KbColors, title: String, backDesc: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(R.drawable.ic_back, backDesc, c) { onBack() }
        Text(
            title, color = c.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 2.dp)
        )
        trailing()
    }
}

/** Card with a hairline border. Put rows inside it and separate them with [PanelDivider]. */
@Composable
internal fun PanelGroup(c: KbColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = DsGroup(c.palette(), modifier, content)

@Composable
internal fun PanelDivider(c: KbColors) = DsDivider(c.palette())

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PanelRow(
    c: KbColors, icon: Int?, title: String, sub: String?, selected: Boolean = false,
    onLong: (() -> Unit)? = null, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (icon == null) 52.dp else 56.dp)
            .background(if (selected && icon == null) c.accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) PanelTile(c, icon, filled = selected)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Search field. Tapping it sends the keyboard's letters to the search text. */
@Composable
internal fun PanelSearch(svc: EveryDayKeysService, c: KbColors, hint: String, modifier: Modifier, flat: Boolean = false, trailing: @Composable RowScope.() -> Unit = {}) {
    val on = svc.searching
    // a pill-shaped Material 3 search surface; tapping it sends the keyboard's letters to the search text
    Surface(
        onClick = { svc.searching = true }, modifier = modifier.height(if (flat) 40.dp else 44.dp), shape = CircleShape, color = c.key,
        border = if (on) androidx.compose.foundation.BorderStroke(2.dp, c.accent) else null
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), null, tint = c.sub, modifier = Modifier.size(20.dp))
            Text(
                if (svc.query.isEmpty()) hint else svc.query, color = if (svc.query.isEmpty()) c.sub else c.text,
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            if (svc.query.isNotEmpty()) IconTap(R.drawable.ic_close, "Clear search", c, c.sub, Modifier.size(40.dp)) { svc.query = "" }
            trailing()
        }
    }
}

/** Filled (primary) or outlined button, 44dp tall (Settings uses 48dp). */
@Composable
internal fun PanelButton(svc: EveryDayKeysService, c: KbColors, text: String, icon: Int?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    DsButton(c.palette(), text, icon, primary, modifier, 44.dp) { svc.feedback(); onClick() }

@Composable
internal fun PanelChip(c: KbColors, text: String, selected: Boolean, desc: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text, maxLines = 1) },
        modifier = Modifier.semantics { contentDescription = desc },
        shape = Dim.Chip,
        leadingIcon = if (selected) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = c.text, selectedContainerColor = c.tile, selectedLabelColor = c.onTile, selectedLeadingIconColor = c.onTile
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = c.sub.copy(alpha = 0.6f))
    )
}

/** Small grey note under a card, with an icon. */
@Composable
internal fun PanelNote(c: KbColors, icon: Int, text: String) = DsNote(c.palette(), icon, text)

@Composable
internal fun PanelEmpty(c: KbColors, icon: Int, text: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PanelTile(c, icon, 44.dp)
        Text(text, color = c.sub, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------------------------------------------------------------- strip items

@Composable
internal fun TranslationBar(svc: EveryDayKeysService, c: KbColors, tr: EveryDayKeysService.Trans) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 44.dp).clip(PanelField).background(c.panel).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelTile(c, R.drawable.ic_translate, 30.dp)
        val ok = tr.result != null
        Surface(onClick = { svc.feedback(); svc.applyTranslation() }, enabled = ok, color = Color.Transparent, modifier = Modifier.weight(1f)) {
            Text(
                tr.result ?: tr.message.orEmpty(), color = if (ok) c.text else c.sub, style = if (ok) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        if (tr.target.isNotBlank()) Surface(
            onClick = { svc.feedback(); svc.nextTranslateTarget() }, shape = CircleShape, color = c.accentContainer, contentColor = c.onAccentContainer,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Text(
                Translator.nameOf(tr.target), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        IconTap(R.drawable.ic_close, "Dismiss translation", c) { svc.dismissTranslation() }
    }
}

/** Last copied, not-yet-pasted item. Tap to paste; the X dismisses it. */
@Composable
internal fun RowScope.ClipChip(svc: EveryDayKeysService, c: KbColors, item: Clip, wide: Boolean) {
    val text = item.preview.replace(Regex("\\s+"), " ").trim()
    Surface(
        onClick = { svc.feedback(); svc.pasteClips(listOf(item)) },
        modifier = Modifier.weight(if (wide) 1f else 1.15f).padding(horizontal = 3.dp).height(38.dp).semantics { contentDescription = "Paste last copied" },
        shape = CircleShape, color = c.panel
    ) {
      Row(Modifier.padding(start = 6.dp, end = if (wide) 2.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelTile(c, R.drawable.ic_clipboard, 26.dp)
        Text(text, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        if (wide) {
            if (item.length > 200) Text("%,d chars".format(item.length), color = c.sub, style = MaterialTheme.typography.labelSmall)
            IconTap(R.drawable.ic_close, "Dismiss", c, c.sub, Modifier.size(36.dp)) { svc.pendingClip = null }
        }
      }
    }
}

@Composable
internal fun ProgressCard(svc: EveryDayKeysService, c: KbColors) {
    val pr = svc.progress ?: 0f
    Card(Modifier.fillMaxWidth().padding(bottom = 6.dp), shape = PanelCard, colors = CardDefaults.cardColors(containerColor = c.panel)) {
      Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTile(c, R.drawable.ic_clipboard)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Pasting a large document", color = c.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("Sent in small chunks so nothing is cut off", color = c.sub, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${(pr * 100).toInt()}%", color = c.onAccentContainer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(c.accentContainer).padding(horizontal = 10.dp, vertical = 5.dp)
            )
            IconTap(R.drawable.ic_close, "Cancel paste", c) { svc.cancelPaste() }
        }
        LinearProgressIndicator(
            progress = { pr }, color = c.accent, trackColor = c.accentContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
      }
    }
}

// ---------------------------------------------------------------- clipboard

@Composable
internal fun ClipPanel(svc: EveryDayKeysService, c: KbColors) {
    if (svc.clipUnlocked) ClipPanelBody(svc, c) else LockedClipPanel(svc, c)
}

@Composable
private fun LockedClipPanel(svc: EveryDayKeysService, c: KbColors) {
    Column(Modifier.fillMaxWidth().height(panelHeight(svc)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelGroup(c, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PanelTile(c, R.drawable.ic_lock, 48.dp, true)
                Text("Clipboard history is locked", color = c.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text("Use your fingerprint or screen lock to open it.", color = c.sub, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                PanelButton(svc, c, "Unlock", R.drawable.ic_lock, true, Modifier.fillMaxWidth(0.7f)) { svc.requestUnlock() }
            }
        }
    }
}

@Composable
private fun ClipPanelBody(svc: EveryDayKeysService, c: KbColors) {
    val list by remember(svc.query) { svc.clips.observe(svc.query) }.collectAsState(initial = emptyList<Clip>())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var preview by remember { mutableStateOf<Clip?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    // the "Clear all?" button disarms itself after a few seconds so a stray tap later does nothing
    LaunchedEffect(confirmClear) { if (confirmClear) { delay(3000); confirmClear = false } }
    // the preview grows to the height of the key area it replaces, so the keyboard never gets taller than normal
    val keysH = with(androidx.compose.ui.platform.LocalDensity.current) { svc.keysHeightPx.toDp() }
    val previewH = panelHeight(svc)
    Column(
        Modifier.fillMaxWidth().height(if (preview != null) previewH else if (svc.searching) 150.dp else panelHeight(svc)).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pv = preview
        if (pv != null) {
            ClipPreview(svc, c, pv, onBack = { preview = null }, onPaste = { svc.pasteClips(listOf(pv)); preview = null })
            return@Column
        }
        if (selected.isNotEmpty() && !svc.searching) {
            // picking several: how many, and one button to paste them together
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_close, "Clear selection", c) { selected = emptySet() }
                Text("${selected.size} selected", color = c.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                PanelButton(svc, c, "Paste all", R.drawable.ic_clipboard, true, Modifier.width(124.dp)) {
                    svc.pasteClips(list.filter { it.id in selected }); selected = emptySet()
                }
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            PanelSearch(svc, c, "Search clipboard", Modifier.weight(1f), flat = true) {
                if (!svc.searching) {
                    if (confirmClear) Button(
                        onClick = { scope.launch { svc.clips.clearUnpinned() }; confirmClear = false },
                        modifier = Modifier.padding(end = 6.dp).height(32.dp).semantics { contentDescription = "Confirm: clear all unpinned items" },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
                    ) { Text("Clear all?", maxLines = 1) }
                    else IconTap(R.drawable.ic_trash, "Clear all unpinned items", c, c.sub, Modifier.size(40.dp)) { confirmClear = true }
                }
            }
            if (svc.searching) IconTap(R.drawable.ic_close, "Stop searching", c, c.sub) { svc.searching = false; svc.query = "" }
        }
        PanelGroup(c, Modifier.weight(1f)) {
            if (list.isEmpty()) PanelEmpty(c, R.drawable.ic_clipboard, when { svc.query.isNotEmpty() -> "No matches"; else -> "Nothing copied yet. Copy some text and it shows up here. No size limit, and it stays on this phone." })
            else LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, clip -> clip.id }) { i, clip ->
                    val isSel = clip.id in selected
                    val first = clip.preview.lineSequence().firstOrNull { it.isNotBlank() } ?: clip.preview
                    val large = clip.filePath != null
                    val link = clip.preview.startsWith("http")
                    val kind = when {
                        large -> "Large item, ${clip.length / 1000} K characters, stored as a file"
                        link -> "Link"
                        else -> "Text, %,d characters".format(clip.length)
                    }
                    val sub = if (clip.pinned) "Pinned, $kind" else kind
                    if (i > 0) PanelDivider(c)
                    PanelRow(
                        c, null, first, sub, selected = isSel,
                        onLong = { selected = if (isSel) selected - clip.id else selected + clip.id },
                        onClick = {
                            if (selected.isNotEmpty()) selected = if (isSel) selected - clip.id else selected + clip.id
                            else if (svc.prefs.bool("clipPreview")) preview = clip
                            else svc.pasteClips(listOf(clip))
                        }
                    ) {
                        IconTap(R.drawable.ic_pin, if (clip.pinned) "Unpin" else "Pin", c, if (clip.pinned) c.accent else c.sub, Modifier.size(40.dp)) {
                            scope.launch { svc.clips.setPinned(clip, !clip.pinned) }
                        }
                        IconTap(R.drawable.ic_trash, "Delete", c, c.sub, Modifier.size(40.dp)) { scope.launch { svc.clips.delete(clip) } }
                    }
                }
            }
        }
    }
}

/** Shows the whole clip, scrollable, so it can be checked before it goes into the text field. */
@Composable
private fun ColumnScope.ClipPreview(svc: EveryDayKeysService, c: KbColors, clip: Clip, onBack: () -> Unit, onPaste: () -> Unit) {
    val limit = 20_000
    var body by remember(clip.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(clip.id) { body = runCatching { svc.clips.text(clip) }.getOrDefault(clip.preview) }
    val text = body
    val scope = rememberCoroutineScope()
    PanelGroup(c, Modifier.weight(1f)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(if (text == null) "Loading..." else text.take(limit), color = c.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
    // one bar under the text: back to the list, size, pin, delete, paste
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(R.drawable.ic_close, "Back to the clipboard list", c, c.sub) { onBack() }
        Text(
            when { text == null -> ""; text.length > limit -> "%,d characters, first %,d shown".format(text.length, limit); else -> "%,d characters".format(text.length) },
            color = c.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        IconTap(R.drawable.ic_pin, if (clip.pinned) "Unpin" else "Pin", c, if (clip.pinned) c.accent else c.sub, Modifier.size(40.dp)) {
            scope.launch { svc.clips.setPinned(clip, !clip.pinned) }; onBack()
        }
        IconTap(R.drawable.ic_trash, "Delete", c, c.sub, Modifier.size(40.dp)) { scope.launch { svc.clips.delete(clip) }; onBack() }
        PanelButton(svc, c, "Paste", R.drawable.ic_clipboard, true, Modifier.width(104.dp)) { onPaste() }
    }
}

// ---------------------------------------------------------------- snippets, emoji

@Composable
internal fun SnippetPanel(svc: EveryDayKeysService, c: KbColors) {
    val ctx = LocalContext.current
    val all by remember { AppDb.get(ctx).snippets().observe() }.collectAsState(initial = emptyList())
    val q = svc.query.trim().lowercase()
    val list = if (q.isEmpty()) all else all.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) || it.shortcut.lowercase().contains(q) }
    Column(Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else panelHeight(svc)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelSearch(svc, c, "Search snippets", Modifier.weight(1f), flat = true)
            if (svc.searching) IconTap(R.drawable.ic_close, "Stop searching", c, c.sub) { svc.searching = false; svc.query = "" }
            else IconTap(R.drawable.ic_add, "Add or manage snippets in Settings", c, c.accent) { svc.openSettings() }
        }
        PanelGroup(c, Modifier.weight(1f)) {
            when {
                all.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    PanelTile(c, R.drawable.ic_snippet, 44.dp)
                    Text("No snippets yet", color = c.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text("Save text you type often, then insert it with one tap or a shortcut like ;addr.", color = c.sub, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    PanelButton(svc, c, "Add a snippet", R.drawable.ic_add, true, Modifier.fillMaxWidth(0.7f)) { svc.openSettings() }
                }
                list.isEmpty() -> PanelEmpty(c, R.drawable.ic_search, "No snippets match")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                        if (i > 0) PanelDivider(c)
                        PanelRow(c, null, s.title, s.body.replace(Regex("\\s+"), " ").take(60), onClick = { svc.feedback(); svc.commitText(s.body); svc.closePanel() }) {
                            if (s.shortcut.isNotBlank()) Surface(
                                shape = Dim.Badge, color = c.accentContainer, contentColor = c.onAccentContainer, modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(s.shortcut, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmojiPanel(svc: EveryDayKeysService, c: KbColors) {
    val recents = svc.prefs.str("recentEmoji").split(" ").filter { it.isNotBlank() }
    val q = svc.query.trim().lowercase()
    val items = if (q.isEmpty()) (recents + Emojis.all.map { it.first }).distinct()
    else Emojis.all.filter { it.second.contains(q) }.map { it.first }
    Column(Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else panelHeight(svc)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (svc.searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_back, "Stop searching", c) { svc.searching = false; svc.query = "" }
                PanelSearch(svc, c, "Search emoji", Modifier.weight(1f))
            }
        } else {
            PanelHeader(c, "Emoji", "Back to keyboard", { svc.closePanel() }) {
                IconTap(R.drawable.ic_search, "Search emoji", c, c.sub) { svc.searching = true }
            }
        }
        PanelGroup(c, Modifier.weight(1f)) {
            if (items.isEmpty()) PanelEmpty(c, R.drawable.ic_emoji, "No emoji match")
            else LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize().padding(4.dp)) {
                items(items) { e ->
                    Surface(
                        onClick = { svc.commitEmoji(e) }, color = Color.Transparent,
                        modifier = Modifier.height(42.dp).semantics { contentDescription = "Insert emoji $e" }
                    ) { Box(contentAlignment = Alignment.Center) { Text(e, style = MaterialTheme.typography.headlineSmall) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- converter, cursor keys

@Composable
internal fun ConvertPanel(svc: EveryDayKeysService, c: KbColors) {
    val cat = Units.cats[svc.convCat]
    val from = cat.units[svc.convFrom.coerceIn(0, cat.units.lastIndex)]
    val to = cat.units[svc.convTo.coerceIn(0, cat.units.lastIndex)]
    val v = svc.convText.replace(",", "").toDoubleOrNull()
    Column(Modifier.fillMaxWidth().padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader(c, cat.name, "Back to keyboard", { svc.closePanel() }) {
            IconTap(R.drawable.ic_swap, "Swap units", c, c.accent) { val t = svc.convFrom; svc.convFrom = svc.convTo; svc.convTo = t }
            IconTap(R.drawable.ic_chevron_left, "Previous category", c, c.sub) { svc.convStep(-1) }
            IconTap(R.drawable.ic_chevron_right, "Next category", c, c.sub) { svc.convStep(1) }
        }
        PanelGroup(c) {
            UnitRow("From", cat.units, svc.convFrom, c) { svc.convFrom = it }
            PanelDivider(c)
            UnitRow("To", cat.units, svc.convTo, c) { svc.convTo = it }
        }
        PanelGroup(c) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelTile(c, R.drawable.ic_ruler)
                Text(
                    if (v == null) "Type or select a number first, then open the converter."
                    else "${Calc.format(v)} ${from.id} = ${Calc.format(Units.convert(cat, v, from, to))} ${to.id}",
                    color = c.text, style = if (v == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            }
        }
        if (v != null) PanelButton(svc, c, "Replace the number with the result", R.drawable.ic_check, true, Modifier.fillMaxWidth()) { svc.convInsert() }
    }
}

@Composable
private fun UnitRow(label: String, units: List<UnitDef>, selected: Int, c: KbColors, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(units) { i, u -> PanelChip(c, u.name, i == selected, "Choose ${u.name}") { onPick(i) } }
        }
    }
}

/** Two plain cards, no title: the arrows with Home and End on the left, select, copy, cut, paste, undo and redo on the right. */
@Composable
internal fun CursorPanel(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val gap = Arrangement.spacedBy(5.dp)
    val key = Modifier.fillMaxHeight()
    Column(Modifier.fillMaxWidth().height(panelHeight(svc)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelGroup(c, Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key.weight(1f), "Line start", label = "Home") { svc.sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
                        Key(svc, c, key.weight(1f), "Cursor up", icon = R.drawable.ic_up, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
                        Key(svc, c, key.weight(1f), "Line end", label = "End") { svc.sendKey(KeyEvent.KEYCODE_MOVE_END) }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key.weight(1f), "Cursor left", icon = R.drawable.ic_left, repeat = true) { svc.moveCursor(-1) }
                        Key(svc, c, key.weight(1f), "Cursor down", icon = R.drawable.ic_down, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                        Key(svc, c, key.weight(1f), "Cursor right", icon = R.drawable.ic_right, repeat = true) { svc.moveCursor(1) }
                    }
                }
            }
            PanelGroup(c, Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key.weight(1f), "Select all", icon = R.drawable.ic_select_all, tone = 1) { svc.selectAll() }
                        Key(svc, c, key.weight(1f), "Copy", icon = R.drawable.ic_copy, tone = 1) { svc.copy() }
                        Key(svc, c, key.weight(1f), "Cut", icon = R.drawable.ic_cut, tone = 1) { svc.cut() }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key.weight(1f), "Paste latest history item", icon = R.drawable.ic_clipboard, tone = 1) { svc.paste() }
                        Key(svc, c, key.weight(1f), "Undo", icon = R.drawable.ic_undo) { svc.undo() }
                        Key(svc, c, key.weight(1f), "Redo", icon = R.drawable.ic_redo) { svc.redo() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- quick settings

@Composable
private fun QuickBoxContent(c: KbColors, icon: Int, title: String, sub: String, active: Boolean) {
    val p = c.palette()
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = if (active) p.onAccentContainer else p.sub, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.sub, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

/** A Material 3 tile: a toggleable surface when [onToggle] is given, otherwise a plain button surface that calls [onClick]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuickBox(
    c: KbColors, icon: Int, title: String, sub: String, active: Boolean, modifier: Modifier,
    clickLabel: String = title, onToggle: ((Boolean) -> Unit)? = null, onClick: () -> Unit = {}
) {
    val p = c.palette()
    val color = if (active) p.accentContainer else p.surface
    val m = modifier.height(64.dp)
    if (onToggle != null) Surface(checked = active, onCheckedChange = onToggle, modifier = m, shape = Dim.Card, color = color) { QuickBoxContent(c, icon, title, sub, active) }
    else Surface(onClick = onClick, modifier = m.semantics { contentDescription = clickLabel }, shape = Dim.Card, color = color) { QuickBoxContent(c, icon, title, sub, active) }
}

@Composable
private fun QuickTile(svc: EveryDayKeysService, c: KbColors, icon: Int, title: String, key: String, modifier: Modifier, on: String = "true", off: String = "false", onLabel: String = "On") {
    val enabled = svc.prefs.str(key) != off
    QuickBox(
        c, icon, title, if (enabled) onLabel else "Off", enabled, modifier,
        onToggle = { v -> svc.feedback(); svc.setPref(key, if (v) on else off) }
    )
}

/** A tile that steps through a fixed list of values on each tap. Highlighted when it is not on the first (default) value. */
@Composable
private fun QuickCycle(svc: EveryDayKeysService, c: KbColors, icon: Int, title: String, key: String, options: List<Pair<String, String>>, modifier: Modifier) {
    val i = options.indexOfFirst { it.first == svc.prefs.str(key) }.coerceAtLeast(0)
    QuickBox(
        c, icon, title, options[i].second, i != 0, modifier, clickLabel = "Change $title, now ${options[i].second}"
    ) { svc.feedback(); svc.setPref(key, options[(i + 1) % options.size].first) }
}

@Composable
private fun QuickLabel(c: KbColors, text: String) =
    Text(text, color = c.sub, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

/** The switches people reach for while typing, as tiles: tap to turn on or off. Grouped by what they change. */
@Composable
internal fun QuickPanel(svc: EveryDayKeysService, c: KbColors) {
    val side = Arrangement.spacedBy(8.dp)
    Column(Modifier.fillMaxWidth().height(panelHeight(svc)).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader(c, "Quick settings", "Back to keyboard", { svc.closePanel() }) {
            IconTap(R.drawable.ic_settings, "All settings", c, c.sub) { svc.openSettings() }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLabel(c, "Typing")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                QuickTile(svc, c, R.drawable.ic_sparkle, "Suggestions", "suggestions", Modifier.weight(1f))
                QuickTile(svc, c, R.drawable.ic_check, "Autocorrect", "autocorrect", Modifier.weight(1f), on = "mild", off = "off", onLabel = "Mild")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                QuickTile(svc, c, R.drawable.ic_text, "Auto-capitalize", "autoCap", Modifier.weight(1f))
                QuickTile(svc, c, R.drawable.ic_clipboard, "Clipboard", "clipEnabled", Modifier.weight(1f))
            }
            QuickLabel(c, "Feedback")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                QuickTile(svc, c, R.drawable.ic_vibration, "Vibration", "vibrate", Modifier.weight(1f))
                QuickTile(svc, c, R.drawable.ic_sound, "Key sound", "sound", Modifier.weight(1f))
            }
            QuickLabel(c, "Look and size")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                QuickCycle(svc, c, R.drawable.ic_sun, "Theme", "theme", listOf("system" to "Auto", "light" to "Light", "dark" to "Dark"), Modifier.weight(1f))
                QuickCycle(svc, c, R.drawable.ic_one_hand, "One-handed", "oneHanded", listOf("off" to "Off", "left" to "Left", "right" to "Right"), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = side) {
                QuickTile(svc, c, R.drawable.ic_numpad, "Number row", "numberRow", Modifier.weight(1f))
                QuickBox(c, R.drawable.ic_tune, "Resize keyboard", "Drag edges", false, Modifier.weight(1f), clickLabel = "Resize the keyboard") { svc.feedback(); svc.closePanel(); svc.setPref("resizeMode", "true") }
            }
        }
    }
}
