package com.mkmehmood.everydaykeys.ime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import com.mkmehmood.everydaykeys.data.KbBackground
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import android.view.KeyEvent
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.data.AppDb
import com.mkmehmood.everydaykeys.data.Calc
import com.mkmehmood.everydaykeys.data.Clip
import com.mkmehmood.everydaykeys.data.UnitDef
import com.mkmehmood.everydaykeys.data.Translator
import com.mkmehmood.everydaykeys.data.Units
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Height of the suggestion strip. It is always reserved so the keyboard does not jump while typing. */
private val StripH = 44.dp

class KbColors(val bg: Color, val key: Color, val keyAlt: Color, val text: Color, val sub: Color, val accent: Color, val panel: Color) {
    companion object {
        fun of(dark: Boolean, accentName: String): KbColors {
            val acc = when (accentName) {
                "blue" -> Color(0xFF2563EB); "purple" -> Color(0xFF7C3AED); "orange" -> Color(0xFFC2410C); else -> Color(0xFF0E7C7B)
            }
            return if (dark) KbColors(Color(0xFF161C24), Color(0xFF2E3744), Color(0xFF232B36), Color(0xFFE8ECF1), Color(0xFF9AA7B6), acc, Color(0xFF1E252E))
            else KbColors(Color(0xFFD5DCE3), Color.White, Color(0xFFBFC9D3), Color(0xFF101C28), Color(0xFF4A5A69), acc, Color.White)
        }
    }
}

@Composable
fun KeyboardRoot(svc: EveryDayKeysService) {
    val p = svc.prefs
    val dark = when (p.str("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val base = remember(dark, p.str("accent")) { KbColors.of(dark, p.str("accent")) }
    val bmp = remember(p.str("bgOn"), p.str("bgVersion")) { if (p.bool("bgOn")) KbBackground.load(ctx) else null }
    val c = if (bmp == null) base else {
        val a = p.int("bgKeyAlpha") / 100f
        KbColors(Color.Transparent, base.key.copy(alpha = a), base.keyAlt.copy(alpha = a), base.text, base.sub, base.accent, base.panel.copy(alpha = 0.92f))
    }
    val oh = p.str("oneHanded")
    val align = when (oh) { "left" -> Alignment.BottomStart; "right" -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
    val h = p.int("keyHeight").dp
    Box(Modifier.fillMaxWidth().background(base.bg), contentAlignment = align) {
        if (bmp != null) {
            val blurDp = p.int("bgBlur")
            Box(Modifier.matchParentSize().clipToBounds()) {
                Image(
                    bmp, null, Modifier.matchParentSize().then(if (blurDp > 0) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = p.int("bgDim") / 100f)))
        }
        Column(Modifier.fillMaxWidth(if (oh == "off") 1f else 0.78f).padding(horizontal = 4.dp, vertical = 4.dp)) {
            if (svc.progress != null) ProgressCard(svc, c)
            // toolbar and suggestions sit on a soft panel so they stay readable over any photo
            Column(Modifier.fillMaxWidth().then(if (bmp != null) Modifier.clip(RoundedCornerShape(12.dp)).background(base.bg.copy(alpha = 0.8f)) else Modifier)) {
                if (p.bool("showToolbar")) Toolbar(svc, c)
                if (svc.panel == Panel.NONE) Strip(svc, c)
            }
            when (svc.panel) {
                Panel.NONE -> Keys(svc, c, h)
                Panel.CLIP -> { ClipPanel(svc, c); if (svc.searching) Keys(svc, c, h) }
                Panel.SNIPPETS -> SnippetPanel(svc, c)
                Panel.EMOJI -> { EmojiPanel(svc, c); if (svc.searching) Keys(svc, c, h) }
                Panel.CURSOR -> CursorPanel(svc, c, h)
                Panel.CONVERT -> ConvertPanel(svc, c)
            }
        }
    }
}

@Composable
private fun Keys(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    when (svc.mode) {
        Mode.LETTERS -> LettersView(svc, c, h)
        Mode.NUMPAD -> NumpadView(svc, c, h)
        Mode.SYM1, Mode.SYM2, Mode.SYM3, Mode.SYM4 -> SymbolsView(svc, c, h)
    }
}

// ---------------------------------------------------------------- key primitive

@Composable
fun Key(
    svc: EveryDayKeysService, c: KbColors, modifier: Modifier, desc: String,
    label: String? = null, icon: Int? = null, tone: Int = 0, active: Boolean = false,
    repeat: Boolean = false, onLong: (() -> Unit)? = null, onTap: () -> Unit
) {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    var pressed by remember { mutableStateOf(false) }
    val p = svc.prefs
    val base = when { tone == 2 || active -> c.accent; tone == 1 -> c.keyAlt; else -> c.key }
    val fg = if (tone == 2 || active) Color.White else c.text
    val shape = RoundedCornerShape(8.dp)
    var m = modifier.clip(shape).background(if (pressed) base.copy(alpha = 0.65f) else base)
        .drawBehind {
            // 1dp lower edge gives each key a slight raised look.
            val e = 1.dp.toPx()
            drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(0f, size.height - e), size = Size(size.width, e))
        }
    if (p.bool("borders")) m = m.border(1.dp, c.sub.copy(alpha = 0.5f), shape)
    m = m
        .semantics { contentDescription = desc; role = Role.Button; onClick(label = desc) { tap(); true } }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                svc.feedback()
                if (repeat) {
                    tap()
                    var done = false
                    withTimeoutOrNull(400) { waitForUpOrCancellation(); done = true }
                    while (!done) {
                        tap()
                        withTimeoutOrNull(50) { waitForUpOrCancellation(); done = true }
                    }
                } else {
                    var released = false
                    val up = withTimeoutOrNull(svc.prefs.int("longPressMs").toLong()) {
                        val u = waitForUpOrCancellation(); released = true; u
                    }
                    if (released) {
                        if (up != null) tap()
                    } else {
                        val lp = long
                        if (lp != null) { lp(); waitForUpOrCancellation() }
                        else { val u = waitForUpOrCancellation(); if (u != null) tap() }
                    }
                }
                pressed = false
            }
        }
    Box(m, contentAlignment = Alignment.Center) {
        if (icon != null) Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(22.dp))
        else if (label != null) Text(label, color = fg, fontSize = p.int("fontSize").sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (pressed && tone == 0 && label != null && label.length <= 2 && label.isNotBlank() && p.bool("keyPreview")) {
            val lift = with(LocalDensity.current) { 70.dp.roundToPx() }
            val bubble = RoundedCornerShape(12.dp)
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -lift)) {
                Box(
                    Modifier.size(52.dp, 64.dp).clip(bubble).background(c.key).border(1.dp, c.sub.copy(alpha = 0.4f), bubble),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun IconTap(icon: Int, desc: String, c: KbColors, tint: Color = c.text, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClickLabel = desc, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(painterResource(icon), desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SpaceKey(svc: EveryDayKeysService, c: KbColors, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape).background(c.key)
            .semantics { contentDescription = "Space. Swipe left or right to move the cursor."; role = Role.Button; onClick { svc.onChar(" "); true } }
            .pointerInput(Unit) { detectTapGestures(onPress = { svc.feedback() }, onTap = { svc.onChar(" ") }) }
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { _, d ->
                    acc += d
                    val step = (70f - svc.prefs.int("dragSens") * 6f).coerceAtLeast(8f)
                    while (acc > step) { svc.moveCursor(1); acc -= step }
                    while (acc < -step) { svc.moveCursor(-1); acc += step }
                }
            },
        contentAlignment = Alignment.Center
    ) { Icon(painterResource(R.drawable.ic_space), null, tint = c.text, modifier = Modifier.size(22.dp)) }
}

// ---------------------------------------------------------------- layouts

@Composable
private fun LettersView(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val rows = Layouts.letters[svc.lang] ?: Layouts.letters.getValue("en")
    val up = svc.shift != Shift.OFF
    val gap = Arrangement.spacedBy(5.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (svc.prefs.bool("numberRow")) Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            "1234567890".forEach { d ->
                val s = d.toString()
                Key(svc, c, Modifier.weight(1f).height(h * 0.85f), s, label = s) { svc.onChar(s) }
            }
        }
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                if (i == 1 && row.size < 10) Spacer(Modifier.weight(0.5f))
                if (i == 2) Key(svc, c, Modifier.weight(1.4f).height(h), "Shift", icon = if (svc.shift == Shift.LOCK) R.drawable.ic_caps else R.drawable.ic_shift,
                    tone = 1, active = svc.shift != Shift.OFF) { svc.onShift() }
                row.forEach { k ->
                    val label = if (up) k.uppercase() else k
                    val a = Layouts.accents[k]
                    val onLong: (() -> Unit)? = if (svc.prefs.bool("accentPopup") && a != null) {
                        { svc.alts = a.map { if (up) it.uppercase() else it } }
                    } else null
                    Key(svc, c, Modifier.weight(1f).height(h), label, label = label, onLong = onLong) { svc.onChar(k) }
                }
                if (i == 2) Key(svc, c, Modifier.weight(1.4f).height(h), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
                if (i == 1 && row.size < 10) Spacer(Modifier.weight(0.5f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key(svc, c, Modifier.weight(1.2f).height(h), "Number pad. Long press for symbols.", icon = R.drawable.ic_numpad, tone = 1,
                onLong = { svc.mode = Mode.SYM1 }) { svc.mode = Mode.NUMPAD }
            if (svc.prefs.bool("langKey")) Key(svc, c, Modifier.weight(1f).height(h), "Switch language", icon = R.drawable.ic_globe, tone = 1) { svc.nextLang() }
            Key(svc, c, Modifier.weight(1f).height(h), "Emoji", icon = R.drawable.ic_emoji, tone = 1) { svc.openPanel(Panel.EMOJI) }
            SpaceKey(svc, c, Modifier.weight(3.4f).height(h))
            Key(svc, c, Modifier.weight(0.8f).height(h), "Comma", label = ",", tone = 1) { svc.onChar(",") }
            Key(svc, c, Modifier.weight(0.8f).height(h), "Period. Long press for punctuation.", label = ".", tone = 1,
                onLong = { svc.alts = Layouts.periodAlts }) { svc.onChar(".") }
            Key(svc, c, Modifier.weight(1.3f).height(h), "Enter", icon = R.drawable.ic_enter, tone = 2) { svc.onEnter() }
        }
    }
}

@Composable
private fun NumpadView(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val gap = Arrangement.spacedBy(5.dp)
    val calc = svc.prefs.str("padStyle") == "calc"
    val digitRows = if (calc) listOf("789", "456", "123") else listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (svc.prefs.bool("mathRow")) Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Layouts.ops.forEach { (label, commit) ->
                Key(svc, c, Modifier.weight(1f).height(h * 0.8f), label, label = label, tone = 1) {
                    if (commit == "=") svc.onEquals() else svc.commitText(commit)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                digitRows.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                        r.forEach { d -> val s = d.toString(); Key(svc, c, Modifier.weight(1f).height(h), s, label = s) { svc.onChar(s) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                    Key(svc, c, Modifier.weight(1f).height(h), "Back to letters. Long press for symbols.", icon = R.drawable.ic_keyboard, tone = 1,
                        onLong = { svc.mode = Mode.SYM1 }) { svc.mode = Mode.LETTERS }
                    Key(svc, c, Modifier.weight(1f).height(h), "0", label = "0") { svc.onChar("0") }
                    Key(svc, c, Modifier.weight(1f).height(h), "Decimal point", label = ".") { svc.onChar(".") }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(svc, c, Modifier.fillMaxWidth().height(h), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
                Key(svc, c, Modifier.fillMaxWidth().height(h), "Comma", label = ",", tone = 1) { svc.onChar(",") }
                Key(svc, c, Modifier.fillMaxWidth().height(h), "Space", icon = R.drawable.ic_space, tone = 1) { svc.onChar(" ") }
                Key(svc, c, Modifier.fillMaxWidth().height(h), "Enter", icon = R.drawable.ic_enter, tone = 2) { svc.onEnter() }
            }
        }
    }
}

@Composable
private fun SymbolsView(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val gap = Arrangement.spacedBy(5.dp)
    val symModes = listOf(Mode.SYM1, Mode.SYM2, Mode.SYM3, Mode.SYM4)
    val page = symModes.indexOf(svc.mode).coerceAtLeast(0)
    val rows = Layouts.symPages[page]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                row.forEach { k -> Key(svc, c, Modifier.weight(1f).height(h), k, label = k) { svc.commitText(k) } }
                if (i == 2) Key(svc, c, Modifier.weight(1.3f).height(h), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key(svc, c, Modifier.weight(1.2f).height(h), "Back to letters", icon = R.drawable.ic_keyboard, tone = 1) { svc.mode = Mode.LETTERS }
            Key(svc, c, Modifier.weight(1.2f).height(h), "Next symbol page (${page + 1} of ${symModes.size})", icon = R.drawable.ic_swap, tone = 1) {
                svc.mode = symModes[(page + 1) % symModes.size]
            }
            Key(svc, c, Modifier.weight(1.2f).height(h), "Number pad", icon = R.drawable.ic_numpad, tone = 1) { svc.mode = Mode.NUMPAD }
            SpaceKey(svc, c, Modifier.weight(3f).height(h))
            Key(svc, c, Modifier.weight(1.3f).height(h), "Enter", icon = R.drawable.ic_enter, tone = 2) { svc.onEnter() }
        }
    }
}

// ---------------------------------------------------------------- bars

private class Tool(val icon: Int, val desc: String, val run: (EveryDayKeysService) -> Unit)

private fun tool(id: String): Tool? = when (id) {
    "clip" -> Tool(R.drawable.ic_clipboard, "Clipboard") { it.openPanel(Panel.CLIP) }
    "snip" -> Tool(R.drawable.ic_snippet, "Text snippets") { it.openPanel(Panel.SNIPPETS) }
    "voice" -> Tool(R.drawable.ic_mic, "Voice typing") { it.startVoice() }
    "translate" -> Tool(R.drawable.ic_translate, "Translate selected or typed text") { it.translate() }
    "undo" -> Tool(R.drawable.ic_undo, "Undo") { it.undo() }
    "redo" -> Tool(R.drawable.ic_redo, "Redo") { it.redo() }
    "cursor" -> Tool(R.drawable.ic_code, "Cursor and edit keys") { it.openPanel(Panel.CURSOR) }
    "convert" -> Tool(R.drawable.ic_ruler, "Unit converter") { it.openPanel(Panel.CONVERT) }
    "settings" -> Tool(R.drawable.ic_settings, "Settings") { it.openSettings() }
    else -> null
}

@Composable
private fun Toolbar(svc: EveryDayKeysService, c: KbColors) {
    val ids = svc.prefs.str("toolbarItems").split(",").filter { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ids.forEach { id -> tool(id)?.let { t -> IconTap(t.icon, t.desc, c, c.text) { svc.feedback(); t.run(svc) } } }
    }
}

@Composable
private fun Strip(svc: EveryDayKeysService, c: KbColors) {
    val tr = svc.trans
    if (tr != null) { TranslationBar(svc, c, tr); return }
    val alts = svc.alts
    val items = alts ?: svc.suggestions
    val pending = if (alts == null && svc.showClipChip) svc.pendingClip else null
    if (items.isEmpty() && pending == null) { Spacer(Modifier.fillMaxWidth().height(StripH)); return }
    Row(Modifier.fillMaxWidth().height(StripH), verticalAlignment = Alignment.CenterVertically) {
        if (pending != null) ClipChip(svc, c, pending, items.isEmpty())
        items.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.width(1.dp).height(18.dp).background(c.sub.copy(alpha = 0.4f)))
            Text(
                s, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { if (alts != null) svc.onAlt(s) else svc.applySuggestion(s) }.padding(vertical = 8.dp)
            )
        }
        if (alts != null) IconTap(R.drawable.ic_close, "Close alternates", c) { svc.alts = null }
    }
}

@Composable
private fun TranslationBar(svc: EveryDayKeysService, c: KbColors, tr: EveryDayKeysService.Trans) {
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_translate), null, tint = c.accent, modifier = Modifier.padding(horizontal = 10.dp).size(20.dp))
        val ok = tr.result != null
        Text(
            tr.result ?: tr.message.orEmpty(), color = if (ok) c.text else c.sub, fontSize = if (ok) 17.sp else 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(if (ok) Modifier.clickable { svc.feedback(); svc.applyTranslation() } else Modifier).padding(vertical = 8.dp)
        )
        if (tr.target.isNotBlank()) Text(
            "→ " + Translator.nameOf(tr.target), color = c.accent, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.panel)
                .clickable { svc.feedback(); svc.nextTranslateTarget() }.padding(horizontal = 10.dp, vertical = 6.dp)
        )
        IconTap(R.drawable.ic_close, "Dismiss translation", c) { svc.dismissTranslation() }
    }
}

/** Last copied, not-yet-pasted item. Tap to paste; the X dismisses it. */
@Composable
private fun RowScope.ClipChip(svc: EveryDayKeysService, c: KbColors, item: Clip, wide: Boolean) {
    val text = item.preview.replace(Regex("\\s+"), " ").trim()
    Row(
        Modifier.weight(if (wide) 1f else 1.15f).padding(horizontal = 3.dp).height(32.dp)
            .clip(RoundedCornerShape(16.dp)).background(c.panel)
            .clickable { svc.feedback(); svc.pasteClips(listOf(item)) }.padding(start = 10.dp, end = if (wide) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_clipboard), "Paste last copied", tint = c.accent, modifier = Modifier.size(16.dp))
        Text(text, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        if (wide) {
            if (item.length > 200) Text("%,d chars".format(item.length), color = c.sub, fontSize = 11.sp)
            IconTap(R.drawable.ic_close, "Dismiss", c) { svc.pendingClip = null }
        }
    }
}

@Composable
private fun ProgressCard(svc: EveryDayKeysService, c: KbColors) {
    val pr = svc.progress ?: 0f
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.panel).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_clipboard), null, tint = c.accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Pasting a large document", color = c.text, fontSize = 14.sp)
                Text("Sent in small chunks so nothing is cut off", color = c.sub, fontSize = 11.sp)
            }
            Text("${(pr * 100).toInt()}%", color = c.accent, fontSize = 14.sp)
            IconTap(R.drawable.ic_close, "Cancel paste", c) { svc.cancelPaste() }
        }
        LinearProgressIndicator(progress = pr, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = c.accent, trackColor = c.keyAlt)
    }
}

// ---------------------------------------------------------------- panels

@Composable
private fun SearchBox(svc: EveryDayKeysService, c: KbColors, hint: String, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.height(40.dp).clip(shape).background(c.bg.copy(alpha = 0.5f))
            .border(if (svc.searching) 2.dp else 0.dp, c.accent, shape)
            .clickable { svc.searching = true }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = c.sub, modifier = Modifier.size(20.dp))
        Text(if (svc.query.isEmpty()) hint else svc.query, color = if (svc.query.isEmpty()) c.sub else c.text,
            fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ClipPanel(svc: EveryDayKeysService, c: KbColors) {
    if (svc.clipUnlocked) ClipPanelBody(svc, c) else LockedClipPanel(svc, c)
}

@Composable
private fun LockedClipPanel(svc: EveryDayKeysService, c: KbColors) {
    Column(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = c.accent, modifier = Modifier.size(32.dp))
        Text("Clipboard history is locked", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text("Use your fingerprint or screen lock to open it.", color = c.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(svc, c, Modifier.width(120.dp).height(48.dp), "Unlock clipboard history", icon = R.drawable.ic_lock, tone = 2) { svc.requestUnlock() }
            Key(svc, c, Modifier.width(120.dp).height(48.dp), "Back to keyboard", icon = R.drawable.ic_keyboard, tone = 1) { svc.closePanel() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipPanelBody(svc: EveryDayKeysService, c: KbColors) {
    val list by remember(svc.query) { svc.clips.observe(svc.query) }.collectAsState(initial = emptyList<Clip>())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    Column(
        Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else 270.dp)
            .clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchBox(svc, c, "Search clipboard", Modifier.weight(1f))
            if (selected.isNotEmpty()) IconTap(R.drawable.ic_add, "Merge selected items into one paste", c, c.accent) {
                svc.pasteClips(list.filter { it.id in selected }); selected = emptySet()
            }
            IconTap(R.drawable.ic_trash, "Clear all unpinned items", c) { scope.launch { svc.clips.clearUnpinned() } }
            IconTap(R.drawable.ic_close, "Close clipboard", c) { svc.closePanel() }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list, key = { it.id }) { clip ->
                val isSel = clip.id in selected
                val first = clip.preview.lineSequence().firstOrNull { it.isNotBlank() } ?: clip.preview
                val large = clip.filePath != null
                val link = clip.preview.startsWith("http")
                val sub = when {
                    clip.pinned -> "Pinned"
                    large -> "Large item, ${clip.length / 1000} K characters, stored as a file"
                    link -> "Link"
                    else -> "Text"
                }
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onLongClick = { selected = if (isSel) selected - clip.id else selected + clip.id },
                            onClick = {
                                if (selected.isNotEmpty()) selected = if (isSel) selected - clip.id else selected + clip.id
                                else svc.pasteClips(listOf(clip))
                            })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (isSel) c.accent else c.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(when { clip.pinned -> R.drawable.ic_pin; large -> R.drawable.ic_snippet; link -> R.drawable.ic_code; else -> R.drawable.ic_clipboard }),
                            null, tint = if (isSel) Color.White else c.accent, modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(first, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, color = c.sub, fontSize = 12.sp, maxLines = 1)
                    }
                    IconTap(R.drawable.ic_pin, if (clip.pinned) "Unpin" else "Pin", c, if (clip.pinned) c.accent else c.sub) {
                        scope.launch { svc.clips.setPinned(clip, !clip.pinned) }
                    }
                    IconTap(R.drawable.ic_trash, "Delete", c, c.sub) { scope.launch { svc.clips.delete(clip) } }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(painterResource(R.drawable.ic_lock), null, tint = c.sub, modifier = Modifier.size(14.dp))
            Text("No size limit. Stays on this phone.", color = c.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun SnippetPanel(svc: EveryDayKeysService, c: KbColors) {
    val ctx = LocalContext.current
    val list by remember { AppDb.get(ctx).snippets().observe() }.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text snippets", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
            IconTap(R.drawable.ic_settings, "Manage snippets", c) { svc.openSettings() }
            IconTap(R.drawable.ic_close, "Close snippets", c) { svc.closePanel() }
        }
        if (list.isEmpty()) Text("No snippets yet. Add some in Settings, then type a shortcut like ;addr.", color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { svc.commitText(s.body); svc.closePanel() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = c.text, fontSize = 15.sp, maxLines = 1)
                        Text(s.body.take(60), color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(s.shortcut, color = c.accent, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EmojiPanel(svc: EveryDayKeysService, c: KbColors) {
    val recents = svc.prefs.str("recentEmoji").split(" ").filter { it.isNotBlank() }
    val q = svc.query.trim().lowercase()
    val items = if (q.isEmpty()) (recents + Emojis.all.map { it.first }).distinct()
    else Emojis.all.filter { it.second.contains(q) }.map { it.first }
    Column(Modifier.fillMaxWidth().height(if (svc.searching) 150.dp else 270.dp).clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchBox(svc, c, "Search emoji", Modifier.weight(1f))
            IconTap(R.drawable.ic_keyboard, "Back to keyboard", c) { svc.closePanel() }
        }
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.weight(1f)) {
            items(items) { e ->
                Box(Modifier.height(42.dp).clickable(onClickLabel = "Insert emoji") { svc.commitEmoji(e) }, contentAlignment = Alignment.Center) {
                    Text(e, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun ConvertPanel(svc: EveryDayKeysService, c: KbColors) {
    val cat = Units.cats[svc.convCat]
    val from = cat.units[svc.convFrom.coerceIn(0, cat.units.lastIndex)]
    val to = cat.units[svc.convTo.coerceIn(0, cat.units.lastIndex)]
    val v = svc.convText.replace(",", "").toDoubleOrNull()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTap(R.drawable.ic_chevron_left, "Previous category", c) { svc.convStep(-1) }
            Text(cat.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconTap(R.drawable.ic_chevron_right, "Next category", c) { svc.convStep(1) }
            IconTap(R.drawable.ic_close, "Close converter", c) { svc.closePanel() }
        }
        UnitRow("From", cat.units, svc.convFrom, c) { svc.convFrom = it }
        UnitRow("To", cat.units, svc.convTo, c) { svc.convTo = it }
        Text(
            if (v == null) "Type or select a number first, then open the converter."
            else "${Calc.format(v)} ${from.id} = ${Calc.format(Units.convert(cat, v, from, to))} ${to.id}",
            color = c.text, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (v != null) Key(svc, c, Modifier.weight(1f).height(48.dp), "Replace the number with the result", icon = R.drawable.ic_check, tone = 2) { svc.convInsert() }
            Key(svc, c, Modifier.weight(1f).height(48.dp), "Back to keyboard", icon = R.drawable.ic_keyboard, tone = 1) { svc.closePanel() }
        }
    }
}

@Composable
private fun UnitRow(label: String, units: List<UnitDef>, selected: Int, c: KbColors, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.sub, fontSize = 13.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(units) { i, u ->
                val on = i == selected
                Text(
                    u.name, color = if (on) Color.White else c.text, fontSize = 15.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) c.accent else c.key)
                        .clickable(onClickLabel = "Choose ${u.name}") { onPick(i) }.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CursorPanel(svc: EveryDayKeysService, c: KbColors, h: Dp) {
    val gap = Arrangement.spacedBy(5.dp)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key(svc, c, Modifier.weight(1f).height(h), "Cursor left", icon = R.drawable.ic_left, repeat = true) { svc.moveCursor(-1) }
            Key(svc, c, Modifier.weight(1f).height(h), "Cursor up", icon = R.drawable.ic_up, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            Key(svc, c, Modifier.weight(1f).height(h), "Cursor down", icon = R.drawable.ic_down, repeat = true) { svc.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            Key(svc, c, Modifier.weight(1f).height(h), "Cursor right", icon = R.drawable.ic_right, repeat = true) { svc.moveCursor(1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key(svc, c, Modifier.weight(1f).height(h), "Select all", icon = R.drawable.ic_select_all, tone = 1) { svc.selectAll() }
            Key(svc, c, Modifier.weight(1f).height(h), "Copy", icon = R.drawable.ic_copy, tone = 1) { svc.copy() }
            Key(svc, c, Modifier.weight(1f).height(h), "Cut", icon = R.drawable.ic_cut, tone = 1) { svc.cut() }
            Key(svc, c, Modifier.weight(1f).height(h), "Paste latest history item", icon = R.drawable.ic_paste, tone = 1) { svc.paste() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Key(svc, c, Modifier.weight(1f).height(h), "Back to keyboard", icon = R.drawable.ic_keyboard, tone = 2) { svc.closePanel() }
        }
    }
}
