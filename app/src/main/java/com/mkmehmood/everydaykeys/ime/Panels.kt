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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
// uses the same look as the Settings screens: a header with a back arrow, grouped cards with hairline borders,
// rounded tiles with an accent icon, 12dp fields and buttons.

internal val PanelCard = Dim.Card
internal val PanelField = Dim.Field

/** Every panel that replaces the keys is this tall, so the keyboard does not jump when switching between them. */
private val PanelHeight = 270.dp

/** The thin border colour used on cards and fields. */
/** The keyboard's colours expressed as the shared design palette, so panels use the same pieces as Settings. */
internal fun KbColors.palette() = Palette(
    surface = panel, text = text, sub = sub, accent = accent, onAccent = onAccent,
    hairline = sub.copy(alpha = 0.30f), tile = accent.copy(alpha = 0.15f), off = sub.copy(alpha = 0.55f)
)

private fun hair(c: KbColors) = c.palette().hairline

/** Small rounded square with an accent icon, used at the start of rows and for the tool icons. */
@Composable
internal fun PanelTile(c: KbColors, icon: Int, size: Dp = 34.dp, filled: Boolean = false) = DsTile(c.palette(), icon, size, filled)

/** A toolbar icon: the tile is the tap target. */
@Composable
internal fun ToolTile(icon: Int, desc: String, c: KbColors, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).semantics { contentDescription = desc }.clickable(onClickLabel = desc, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) { PanelTile(c, icon, 34.dp, filled) }
}

@Composable
internal fun PanelHeader(c: KbColors, title: String, backDesc: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTap(R.drawable.ic_back, backDesc, c) { onBack() }
        Text(
            title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
    c: KbColors, icon: Int, title: String, sub: String?, selected: Boolean = false,
    onLong: (() -> Unit)? = null, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelTile(c, icon, filled = selected)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Search field. Tapping it sends the keyboard's letters to the search text. */
@Composable
internal fun PanelSearch(svc: EveryDayKeysService, c: KbColors, hint: String, modifier: Modifier) {
    val on = svc.searching
    Row(
        modifier.height(44.dp).clip(PanelField).background(c.panel)
            .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else hair(c), PanelField)
            .clickable { svc.searching = true }.padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = c.sub, modifier = Modifier.size(20.dp))
        Text(
            if (svc.query.isEmpty()) hint else svc.query, color = if (svc.query.isEmpty()) c.sub else c.text,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        if (svc.query.isNotEmpty()) IconTap(R.drawable.ic_close, "Clear search", c, c.sub, Modifier.size(40.dp)) { svc.query = "" }
    }
}

/** Filled (primary) or outlined button, 44dp tall (Settings uses 48dp). */
@Composable
internal fun PanelButton(svc: EveryDayKeysService, c: KbColors, text: String, icon: Int?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    DsButton(c.palette(), text, icon, primary, modifier, 44.dp) { svc.feedback(); onClick() }

@Composable
internal fun PanelChip(c: KbColors, text: String, selected: Boolean, desc: String, onClick: () -> Unit) {
    Text(
        text, color = if (selected) c.onAccent else c.text, fontSize = 15.sp, maxLines = 1,
        modifier = Modifier.heightIn(min = 40.dp).clip(PanelField).background(if (selected) c.accent else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, hair(c), PanelField))
            .clickable(onClickLabel = desc) { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

/** Small grey note under a card, with an icon. */
@Composable
internal fun PanelNote(c: KbColors, icon: Int, text: String) = DsNote(c.palette(), icon, text)

/** Brief confirmation over the keyboard after copy, cut, paste, select all, undo or redo. It never blocks touches. */
@Composable
internal fun NoticePill(c: KbColors, n: EveryDayKeysService.Notice) = DsNotice(c.palette(), n.icon, n.text)

@Composable
internal fun PanelEmpty(c: KbColors, icon: Int, text: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PanelTile(c, icon, 44.dp)
        Text(text, color = c.sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------------------------------------------------------------- strip items

@Composable
internal fun TranslationBar(svc: EveryDayKeysService, c: KbColors, tr: EveryDayKeysService.Trans) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 44.dp).clip(PanelField).background(c.panel)
            .border(1.dp, hair(c), PanelField).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelTile(c, R.drawable.ic_translate, 30.dp)
        val ok = tr.result != null
        Text(
            tr.result ?: tr.message.orEmpty(), color = if (ok) c.text else c.sub, fontSize = if (ok) 16.sp else 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (ok) Modifier.clickable { svc.feedback(); svc.applyTranslation() } else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        if (tr.target.isNotBlank()) Text(
            Translator.nameOf(tr.target), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.15f))
                .clickable { svc.feedback(); svc.nextTranslateTarget() }.padding(horizontal = 10.dp, vertical = 6.dp)
        )
        IconTap(R.drawable.ic_close, "Dismiss translation", c) { svc.dismissTranslation() }
    }
}

/** Last copied, not-yet-pasted item. Tap to paste; the X dismisses it. */
@Composable
internal fun RowScope.ClipChip(svc: EveryDayKeysService, c: KbColors, item: Clip, wide: Boolean) {
    val text = item.preview.replace(Regex("\\s+"), " ").trim()
    Row(
        Modifier.weight(if (wide) 1f else 1.15f).padding(horizontal = 3.dp).height(38.dp)
            .clip(PanelField).background(c.panel).border(1.dp, hair(c), PanelField)
            .clickable(onClickLabel = "Paste last copied") { svc.feedback(); svc.pasteClips(listOf(item)) }
            .padding(start = 6.dp, end = if (wide) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanelTile(c, R.drawable.ic_clipboard, 26.dp)
        Text(text, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        if (wide) {
            if (item.length > 200) Text("%,d chars".format(item.length), color = c.sub, fontSize = 11.sp)
            IconTap(R.drawable.ic_close, "Dismiss", c, c.sub, Modifier.size(36.dp)) { svc.pendingClip = null }
        }
    }
}

@Composable
internal fun ProgressCard(svc: EveryDayKeysService, c: KbColors) {
    val pr = svc.progress ?: 0f
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(PanelCard).background(c.panel).border(1.dp, hair(c), PanelCard).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTile(c, R.drawable.ic_paste)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Pasting a large document", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Sent in small chunks so nothing is cut off", color = c.sub, fontSize = 12.5.sp)
            }
            Text(
                "${(pr * 100).toInt()}%", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp)
            )
            IconTap(R.drawable.ic_close, "Cancel paste", c) { svc.cancelPaste() }
        }
        LinearProgressIndicator(
            progress = pr, color = c.accent, trackColor = c.accent.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

// ---------------------------------------------------------------- clipboard

@Composable
internal fun ClipPanel(svc: EveryDayKeysService, c: KbColors) {
    if (svc.clipUnlocked) ClipPanelBody(svc, c) else LockedClipPanel(svc, c)
}

@Composable
private fun LockedClipPanel(svc: EveryDayKeysService, c: KbColors) {
    Column(Modifier.fillMaxWidth().height(PanelHeight).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader(c, "Clipboard", "Back to keyboard", { svc.closePanel() })
        PanelGroup(c, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PanelTile(c, R.drawable.ic_lock, 48.dp)
                Text("Clipboard history is locked", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Text("Use your fingerprint or screen lock to open it.", color = c.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                PanelButton(svc, c, "Unlock", R.drawable.ic_lock, true, Modifier.fillMaxWidth(0.7f)) { svc.requestUnlock() }
            }
        }
    }
}

@Composable
private fun ClipPanelBody(svc: EveryDayKeysService, c: KbColors) {
    val all by remember(svc.query) { svc.clips.observe(svc.query) }.collectAsState(initial = emptyList<Clip>())
    var filter by remember { mutableStateOf(0) }
    val list = when (filter) {
        1 -> all.filter { it.pinned }
        2 -> all.filter { it.preview.startsWith("http") }
        3 -> all.filter { it.filePath != null }
        else -> all
    }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var preview by remember { mutableStateOf<Clip?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    // the "Clear all?" button disarms itself after a few seconds so a stray tap later does nothing
    LaunchedEffect(confirmClear) { if (confirmClear) { delay(3000); confirmClear = false } }
    Column(
        Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else PanelHeight).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pv = preview
        if (pv != null) {
            ClipPreview(svc, c, pv, onBack = { preview = null }, onPaste = { svc.pasteClips(listOf(pv)); preview = null })
            return@Column
        }
        if (svc.searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_back, "Stop searching", c) { svc.searching = false; svc.query = "" }
                PanelSearch(svc, c, "Search clipboard", Modifier.weight(1f))
            }
        } else {
            if (selected.isEmpty()) {
                PanelHeader(c, "Clipboard", "Back to keyboard", { svc.closePanel() }) {
                    if (confirmClear) Text(
                        "Clear all?", color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.accent)
                            .clickable(onClickLabel = "Confirm: clear all unpinned items") { scope.launch { svc.clips.clearUnpinned() }; confirmClear = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    else IconTap(R.drawable.ic_trash, "Clear all unpinned items", c, c.sub) { confirmClear = true }
                }
            } else {
                // selection mode: the header says how many are picked and pastes them as one
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTap(R.drawable.ic_close, "Clear selection", c) { selected = emptySet() }
                    Text("${selected.size} selected", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 2.dp))
                    PanelButton(svc, c, "Paste all", R.drawable.ic_paste, true, Modifier.width(124.dp)) {
                        svc.pasteClips(list.filter { it.id in selected }); selected = emptySet()
                    }
                }
            }
            PanelSearch(svc, c, "Search clipboard", Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val counts = listOf(0, all.count { it.pinned }, all.count { it.preview.startsWith("http") }, all.count { it.filePath != null })
                itemsIndexed(listOf("All", "Pinned", "Links", "Large")) { i, name ->
                    PanelChip(c, if (i > 0 && counts[i] > 0) "$name ${counts[i]}" else name, i == filter, "Show $name items") { filter = i }
                }
            }
        }
        PanelGroup(c, Modifier.weight(1f)) {
            if (list.isEmpty()) PanelEmpty(c, R.drawable.ic_clipboard, when { svc.query.isNotEmpty() -> "No matches"; filter != 0 -> "No items in this filter"; else -> "Nothing copied yet. Copy some text and it shows up here. No size limit, and it stays on this phone." })
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
                        c, when { clip.pinned -> R.drawable.ic_pin; large -> R.drawable.ic_snippet; link -> R.drawable.ic_code; else -> R.drawable.ic_clipboard },
                        first, sub, selected = isSel,
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
    PanelHeader(c, "Preview", "Back to the clipboard list", onBack) {
        IconTap(R.drawable.ic_pin, if (clip.pinned) "Unpin" else "Pin", c, if (clip.pinned) c.accent else c.sub, Modifier.size(40.dp)) {
            scope.launch { svc.clips.setPinned(clip, !clip.pinned) }; onBack()
        }
        IconTap(R.drawable.ic_trash, "Delete", c, c.sub, Modifier.size(40.dp)) { scope.launch { svc.clips.delete(clip) }; onBack() }
        PanelButton(svc, c, "Paste", R.drawable.ic_paste, true, Modifier.width(104.dp)) { onPaste() }
    }
    PanelGroup(c, Modifier.weight(1f)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(if (text == null) "Loading..." else text.take(limit), color = c.text, fontSize = 14.sp)
        }
    }
    if (text != null) PanelNote(
        c, R.drawable.ic_info,
        if (text.length > limit) "%,d characters. Showing the first %,d here; the whole item is pasted.".format(text.length, limit) else "%,d characters".format(text.length)
    )
}

// ---------------------------------------------------------------- snippets, emoji

@Composable
internal fun SnippetPanel(svc: EveryDayKeysService, c: KbColors) {
    val ctx = LocalContext.current
    val all by remember { AppDb.get(ctx).snippets().observe() }.collectAsState(initial = emptyList())
    val q = svc.query.trim().lowercase()
    val list = if (q.isEmpty()) all else all.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) || it.shortcut.lowercase().contains(q) }
    Column(Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else PanelHeight).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (svc.searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_back, "Stop searching", c) { svc.searching = false; svc.query = "" }
                PanelSearch(svc, c, "Search snippets", Modifier.weight(1f))
            }
        } else {
            PanelHeader(c, "Text snippets", "Back to keyboard", { svc.closePanel() }) {
                if (all.size > 4) IconTap(R.drawable.ic_search, "Search snippets", c, c.sub) { svc.searching = true }
                IconTap(R.drawable.ic_add, "Add or manage snippets in Settings", c, c.accent) { svc.openSettings() }
            }
        }
        PanelGroup(c, Modifier.weight(1f)) {
            when {
                all.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    PanelTile(c, R.drawable.ic_snippet, 44.dp)
                    Text("No snippets yet", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    Text("Save text you type often, then insert it with one tap or a shortcut like ;addr.", color = c.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    PanelButton(svc, c, "Add a snippet", R.drawable.ic_add, true, Modifier.fillMaxWidth(0.7f)) { svc.openSettings() }
                }
                list.isEmpty() -> PanelEmpty(c, R.drawable.ic_search, "No snippets match")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                        if (i > 0) PanelDivider(c)
                        PanelRow(c, R.drawable.ic_snippet, s.title, s.body.replace(Regex("\\s+"), " ").take(60), onClick = { svc.feedback(); svc.commitText(s.body); svc.closePanel() }) {
                            if (s.shortcut.isNotBlank()) Text(
                                s.shortcut, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (!svc.searching && all.isNotEmpty()) PanelNote(c, R.drawable.ic_info, "Tap to insert. Typing a shortcut such as ;addr expands it too.")
    }
}

@Composable
internal fun EmojiPanel(svc: EveryDayKeysService, c: KbColors) {
    val recents = svc.prefs.str("recentEmoji").split(" ").filter { it.isNotBlank() }
    val q = svc.query.trim().lowercase()
    val items = if (q.isEmpty()) (recents + Emojis.all.map { it.first }).distinct()
    else Emojis.all.filter { it.second.contains(q) }.map { it.first }
    Column(Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else 270.dp).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Box(Modifier.height(42.dp).clickable(onClickLabel = "Insert emoji") { svc.commitEmoji(e) }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 24.sp)
                    }
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
                    color = c.text, fontSize = if (v == null) 14.sp else 18.sp, modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
            }
        }
        if (v != null) PanelButton(svc, c, "Replace the number with the result", R.drawable.ic_check, true, Modifier.fillMaxWidth()) { svc.convInsert() }
    }
}

@Composable
private fun UnitRow(label: String, units: List<UnitDef>, selected: Int, c: KbColors, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.sub, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(units) { i, u -> PanelChip(c, u.name, i == selected, "Choose ${u.name}") { onPick(i) } }
        }
    }
}

@Composable
private fun PanelCaption(c: KbColors, text: String) =
    Text(text, color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 4.dp))

/** Two cards: Move (Home, End and the arrows) and Edit (select, copy, cut, paste, undo, redo). Keys share the panel height. */
@Composable
internal fun CursorPanel(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val gap = Arrangement.spacedBy(5.dp)
    val key = Modifier.weight(1f).fillMaxHeight()
    Column(Modifier.fillMaxWidth().height(PanelHeight).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader(c, "Cursor and edit keys", "Back to keyboard", { svc.closePanel() })
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelGroup(c, Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PanelCaption(c, "Move")
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key, "Line start", label = "Home") { svc.sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
                        Key(svc, c, key, "Cursor up", icon = R.drawable.ic_up, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
                        Key(svc, c, key, "Line end", label = "End") { svc.sendKey(KeyEvent.KEYCODE_MOVE_END) }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key, "Cursor left", icon = R.drawable.ic_left, repeat = true) { svc.moveCursor(-1) }
                        Key(svc, c, key, "Cursor down", icon = R.drawable.ic_down, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                        Key(svc, c, key, "Cursor right", icon = R.drawable.ic_right, repeat = true) { svc.moveCursor(1) }
                    }
                }
            }
            PanelGroup(c, Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PanelCaption(c, "Edit")
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key, "Select all", icon = R.drawable.ic_select_all, tone = 1) { svc.selectAll() }
                        Key(svc, c, key, "Copy", icon = R.drawable.ic_copy, tone = 1) { svc.copy() }
                        Key(svc, c, key, "Cut", icon = R.drawable.ic_cut, tone = 1) { svc.cut() }
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, key, "Paste latest history item", icon = R.drawable.ic_paste, tone = 1) { svc.paste() }
                        Key(svc, c, key, "Undo", icon = R.drawable.ic_undo) { svc.undo() }
                        Key(svc, c, key, "Redo", icon = R.drawable.ic_redo) { svc.redo() }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- quick settings

@Composable
private fun QuickBox(c: KbColors, icon: Int, title: String, sub: String, active: Boolean, modifier: Modifier) {
    val p = c.palette()
    Row(
        modifier.height(64.dp).clip(Dim.Card).background(if (active) p.tile else p.surface)
            .border(1.dp, if (active) p.accent else p.hairline, Dim.Card).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = if (active) p.accent else p.sub, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = p.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.sub, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun QuickTile(svc: EveryDayKeysService, c: KbColors, icon: Int, title: String, key: String, modifier: Modifier, on: String = "true", off: String = "false", onLabel: String = "On") {
    val enabled = svc.prefs.str(key) != off
    QuickBox(
        c, icon, title, if (enabled) onLabel else "Off", enabled,
        modifier.toggleable(value = enabled, role = Role.Switch) { v -> svc.feedback(); svc.setPref(key, if (v) on else off) }
    )
}

/** A tile that steps through a fixed list of values on each tap. Highlighted when it is not on the first (default) value. */
@Composable
private fun QuickCycle(svc: EveryDayKeysService, c: KbColors, icon: Int, title: String, key: String, options: List<Pair<String, String>>, modifier: Modifier) {
    val i = options.indexOfFirst { it.first == svc.prefs.str(key) }.coerceAtLeast(0)
    QuickBox(
        c, icon, title, options[i].second, i != 0,
        modifier.clickable(onClickLabel = "Change $title, now ${options[i].second}", role = Role.Button) {
            svc.feedback(); svc.setPref(key, options[(i + 1) % options.size].first)
        }
    )
}

@Composable
private fun QuickLabel(c: KbColors, text: String) =
    Text(text, color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

/** The switches people reach for while typing, as tiles: tap to turn on or off. Grouped by what they change. */
@Composable
internal fun QuickPanel(svc: EveryDayKeysService, c: KbColors) {
    val side = Arrangement.spacedBy(8.dp)
    Column(Modifier.fillMaxWidth().height(PanelHeight).padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                QuickBox(c, R.drawable.ic_tune, "Resize keyboard", "Drag edges", false, Modifier.weight(1f).clickable(onClickLabel = "Resize the keyboard", role = Role.Button) { svc.feedback(); svc.closePanel(); svc.setPref("resizeMode", "true") })
            }
        }
    }
}
