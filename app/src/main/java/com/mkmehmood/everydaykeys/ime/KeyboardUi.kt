package com.mkmehmood.everydaykeys.ime

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.core.view.WindowCompat
import com.mkmehmood.everydaykeys.design.ExpressiveMotion
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.mkmehmood.everydaykeys.data.KbBackground
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.mkmehmood.everydaykeys.design.Dim
import com.mkmehmood.everydaykeys.design.EveryDayKeysShapes
import com.mkmehmood.everydaykeys.design.EveryDayKeysTypography
import com.mkmehmood.everydaykeys.design.MonoFontFamily
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import com.mkmehmood.everydaykeys.design.StateLayer
import com.mkmehmood.everydaykeys.design.m3Scheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import com.mkmehmood.everydaykeys.data.Prefs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import android.view.KeyEvent
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.design.DsNotice
import com.mkmehmood.everydaykeys.data.AppDb
import com.mkmehmood.everydaykeys.data.Calc
import com.mkmehmood.everydaykeys.data.Clip
import com.mkmehmood.everydaykeys.data.UnitDef
import com.mkmehmood.everydaykeys.data.Translator
import com.mkmehmood.everydaykeys.data.Units
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Height of the suggestion strip. It is always reserved so the keyboard does not jump while typing. */
internal val StripH = 48.dp

/** Key look chosen in settings: corner shape, font and weight. */
fun layoutStyle(p: Prefs): String = p.str("layoutStyle")
fun effStyle(p: Prefs): String = when (layoutStyle(p)) { "raised" -> "raised"; "flat", "pro" -> "flat"; else -> p.str("keyStyle") }
fun keyShape(p: Prefs): Shape = when (layoutStyle(p)) {
    "raised" -> EveryDayKeysShapes.small
    "flat" -> EveryDayKeysShapes.medium
    "pro" -> EveryDayKeysShapes.large
    else -> keyShapeUser(p)
}
private fun keyShapeUser(p: Prefs): Shape = when (p.str("keyShape")) {
    "square" -> RectangleShape
    "slight" -> EveryDayKeysShapes.extraSmall
    "soft" -> EveryDayKeysShapes.large
    "round" -> CircleShape                // full
    else -> EveryDayKeysShapes.medium      // the Material 3 default
}
/** Keyboard size as a fraction, 0.6 to 1.0. */
internal fun keyScale(p: Prefs): Float = (p.int("keyScale").coerceIn(60, 100)) / 100f

/** Key height as a fraction, 0.7 to 1.4. Width stays the same, so this only makes the rows taller or shorter. */
internal fun keyHeight(p: Prefs): Float = (p.int("keyHeight").coerceIn(70, 140)) / 100f

/**
 * The keyboard's colours, always Material 3 roles taken from [ColorScheme]
 * (Material You dynamic colour, or the tonal palette of the chosen accent): surface container behind the keys,
 * tonal keys, secondary-container function keys and a primary action key.
 */
class KbColors(
    val bg: Color, val key: Color, val keyAlt: Color, val text: Color, val sub: Color, val accent: Color, val panel: Color,
    val onAccent: Color,
    val tile: Color, val onTile: Color, val accentContainer: Color, val onAccentContainer: Color,
    val outline: Color, val outlineVariant: Color, val scrim: Color, val surfaceHighest: Color
) {
    companion object {
        fun of(dark: Boolean, cs: ColorScheme): KbColors {
            return KbColors(
                bg = cs.surfaceContainer,
                key = if (dark) cs.surfaceContainerHighest else cs.surfaceContainerLowest,
                keyAlt = cs.secondaryContainer,
                text = cs.onSurface, sub = cs.onSurfaceVariant, accent = cs.primary,
                panel = if (dark) cs.surfaceContainerHigh else cs.surfaceContainerLowest,
                onAccent = cs.onPrimary,
                tile = cs.secondaryContainer, onTile = cs.onSecondaryContainer,
                accentContainer = cs.primaryContainer, onAccentContainer = cs.onPrimaryContainer,
                outline = cs.outline, outlineVariant = cs.outlineVariant, scrim = cs.scrim, surfaceHighest = cs.surfaceContainerHighest
            )
        }
    }
}

@Composable
fun KeyboardRoot(svc: EveryDayKeysService) {
    val p = svc.prefs
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    val dark0 = when (p.str("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    // the keyboard is a Material 3 surface too: its panels use real Material 3 components that read this theme
    MaterialTheme(
        colorScheme = remember(dark0, p.bool("dynamicColor"), p.str("accent")) { m3Scheme(ctx0, dark0, p.bool("dynamicColor"), p.str("accent")) },
        shapes = EveryDayKeysShapes, typography = EveryDayKeysTypography
    ) { KeyboardContent(svc) }
}

@Composable
private fun KeyboardContent(svc: EveryDayKeysService) {
    val p = svc.prefs
    val dark = when (p.str("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = remember(dark, p.bool("dynamicColor"), p.str("accent")) { m3Scheme(ctx, dark, p.bool("dynamicColor"), p.str("accent")) }
    val base = remember(scheme, p.str("layoutStyle")) { KbColors.of(dark, scheme) }
    val bmp = remember(p.str("bgOn"), p.str("bgVersion")) { if (p.bool("bgOn")) KbBackground.load(ctx) else null }
    val c = if (bmp == null) base else {
        val a = p.int("bgKeyAlpha") / 100f
        KbColors(Color.Transparent, base.key.copy(alpha = a), base.keyAlt.copy(alpha = a), base.text, base.sub, base.accent, base.panel.copy(alpha = 0.92f), base.onAccent,
            base.tile, base.onTile, base.accentContainer, base.onAccentContainer,
            base.outline, base.outlineVariant, base.scrim, base.surfaceHighest)
    }
    val navColor = scheme.surfaceContainer.toArgb()
    SideEffect {
        runCatching {
            val w = svc.window?.window ?: return@runCatching
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 35) {
                w.navigationBarColor = navColor
            }
            if (Build.VERSION.SDK_INT >= 29) {
                w.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = !dark
        }
    }
    val oh = p.str("oneHanded")
    val align = when (oh) { "left" -> Alignment.BottomStart; "right" -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
    var rootW by remember { mutableStateOf(0) }
    Box(
        Modifier.fillMaxWidth()
            .background(base.bg)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .onSizeChanged { rootW = it.width },
        contentAlignment = align
    ) {
        if (bmp != null) {
            val blurDp = p.int("bgBlur")
            Box(Modifier.matchParentSize().clipToBounds()) {
                Image(
                    bmp, null, Modifier.matchParentSize().then(if (blurDp > 0) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.matchParentSize().background(c.scrim.copy(alpha = p.int("bgDim") / 100f)))
        }
        // the move handle in resize mode slides the keys sideways inside the space the width setting frees up
        val shiftPx = (p.int("kbShift").coerceIn(-100, 100) / 100f * (1f - keyScale(p)) * rootW / 2f).toInt()
        Column(Modifier.fillMaxWidth(if (oh == "off") 1f else 0.78f).offset { IntOffset(shiftPx, 0) }.padding(horizontal = 4.dp, vertical = 4.dp)) {
            if (svc.progress != null) ProgressCard(svc, c)
            // only the letter pad has the suggestion strip; the number pad, symbols and every panel have nothing above their keys
            val n = svc.notice
            if (n != null) NoticeBar(c, n)
            else if (svc.panel == Panel.NONE && svc.mode == Mode.LETTERS) Strip(svc, c)
            when (svc.panel) {
                Panel.NONE -> {
                    // the letter pad sets the keyboard's height (strip + keys); the number pad and symbols grow to the same height so the keyboard never shrinks
                    val letters = svc.mode == Mode.LETTERS
                    val minH = with(LocalDensity.current) { (svc.keysHeightPx.toDp() + StripH).takeIf { svc.keysHeightPx > 0 && !letters } ?: 0.dp }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = minH).then(if (letters) Modifier.onSizeChanged { svc.keysHeightPx = it.height } else Modifier),
                        contentAlignment = Alignment.Center
                    ) { Keys(svc, c) }
                }
                Panel.CLIP -> { ClipPanel(svc, c); if (svc.searching) Keys(svc, c) }
                Panel.SNIPPETS -> { SnippetPanel(svc, c); if (svc.searching) Keys(svc, c) }
                Panel.EMOJI -> { EmojiPanel(svc, c); if (svc.searching) Keys(svc, c) }
                Panel.CURSOR -> CursorPanel(svc, c)
                Panel.CONVERT -> ConvertPanel(svc, c)
                Panel.QUICK -> QuickPanel(svc, c)
            }
        }
        if (p.bool("resizeMode")) ResizeOverlay(svc, c, Modifier.matchParentSize())
    }
}

@Composable
private fun Keys(svc: EveryDayKeysService, c: KbColors) {
    when (svc.mode) {
        Mode.LETTERS -> LettersView(svc, c)
        Mode.NUMPAD -> NumpadView(svc, c)
        Mode.SYM1, Mode.SYM2, Mode.SYM3, Mode.SYM4 -> SymbolsView(svc, c)
    }
}

// ---------------------------------------------------------------- key primitive

@Composable
fun Key(
    svc: EveryDayKeysService, c: KbColors, modifier: Modifier, desc: String,
    label: String? = null, icon: Int? = null, tone: Int = 0, active: Boolean = false,
    repeat: Boolean = false, onLong: (() -> Unit)? = null, hint: String? = null, shapeOverride: Shape? = null, onTap: () -> Unit
) {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    var pressed by remember { mutableStateOf(false) }
    val p = svc.prefs
    val base = when { tone == 2 || active -> c.accent; tone == 1 -> c.keyAlt; else -> c.key }
    val style = effStyle(p)
    val outline = style == "outline"
    val fg = when {
        outline && (tone == 2 || active) -> c.accent
        outline -> c.text
        tone == 2 || active -> c.onAccent
        tone == 1 -> c.onTile
        else -> c.text
    }
    val baseShape = shapeOverride ?: keyShape(p)
    val morphBaseDp = when {
        shapeOverride != null -> null
        layoutStyle(p) == "raised" -> 10.dp
        layoutStyle(p) == "flat" -> 14.dp
        layoutStyle(p) == "pro" -> 20.dp
        p.str("keyShape") == "slight" -> 6.dp
        p.str("keyShape") == "soft" -> 20.dp
        p.str("keyShape") == "square" || p.str("keyShape") == "round" -> null
        else -> 14.dp
    }
    val animCorner by animateDpAsState(
        targetValue = if (pressed && morphBaseDp != null) 22.dp else (morphBaseDp ?: 14.dp),
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "keyMorphCorner"
    )
    val shape = if (morphBaseDp != null) RoundedCornerShape(animCorner) else baseShape
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "keyPressScale"
    )
    // Material 3: outlined keys have no container; the pressed state is a 10% state layer of the content colour
    val fill = when {
        outline -> if (pressed) fg.copy(alpha = StateLayer.Pressed) else Color.Transparent
        pressed -> fg.copy(alpha = StateLayer.Pressed).compositeOver(base)
        else -> base
    }
    // elevated key style: Material 3 level-1 elevation + subtle tonal rim
    var m = modifier
        .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
        .then(if (style == "raised") Modifier.shadow(1.5.dp, shape) else Modifier)
        .clip(shape)
        .background(fill)
    if (outline) m = m.border(1.5.dp, if (tone == 2 || active) c.accent else c.outline, shape)
    else if (c.bg != Color.Transparent) m = m.border(0.5.dp, c.outlineVariant.copy(alpha = 0.25f), shape)
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
        else if (label != null) Text(label, color = fg, style = MaterialTheme.typography.titleLarge, maxLines = 1,
            modifier = if (hint != null) Modifier.padding(top = 8.dp) else Modifier)
        if (hint != null && label != null) Text(
            hint,
            color = c.accent,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
        )
        if (pressed && tone == 0 && label != null && label.length <= 2 && label.isNotBlank() && p.bool("keyPreview")) {
            val lift = with(LocalDensity.current) { 74.dp.roundToPx() }
            val bubble = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -lift)) {
                Surface(
                    Modifier.size(56.dp, 68.dp),
                    shape = bubble,
                    color = c.surfaceHighest,
                    contentColor = c.text,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, c.accent.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = c.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun IconTap(icon: Int, desc: String, c: KbColors, tint: Color = c.text, modifier: Modifier = Modifier.size(48.dp), onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier, colors = IconButtonDefaults.iconButtonColors(contentColor = tint)) {
        Icon(painterResource(icon), desc, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SpaceKey(svc: EveryDayKeysService, c: KbColors, modifier: Modifier) {
    val shape = if (layoutStyle(svc.prefs) == "flat" || layoutStyle(svc.prefs) == "pro") RoundedCornerShape(50) else keyShape(svc.prefs)
    val outline = effStyle(svc.prefs) == "outline"
    val langLabel = Layouts.langNames[svc.lang] ?: svc.lang.uppercase()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "spaceScale"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (outline) Color.Transparent else c.key)
            .then(if (outline) Modifier.border(1.5.dp, c.outline, shape) else Modifier.border(0.5.dp, c.outlineVariant.copy(alpha = 0.25f), shape))
            .semantics { contentDescription = "Space, $langLabel. Swipe left or right to move the cursor."; role = Role.Button; onClick { svc.onChar(" "); true } }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        svc.feedback()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { svc.onChar(" ") }
                )
            }
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
    ) {
        Text(
            langLabel,
            color = c.sub.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------- layouts

/** Largest key edge. Keys are always perfect squares; on wide screens they stop growing and the rows are centred. */
private val Hints = mapOf("a" to "@", "s" to "#", "d" to "$", "f" to "_", "g" to "&", "h" to "-", "j" to "+", "k" to "(", "l" to ")",
    "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";", "n" to "!", "m" to "?")
private val MaxKey = 64.dp
private val KeyGap = 5.dp

/**
 * One key grid shared by letters, symbols and every number pad, so the keyboard keeps the same width, height and key size
 * whichever mode is showing. [rows] is the row count of the letter keyboard (5 with the number row, otherwise 4); the other
 * modes stretch or share that same frame instead of using sizes of their own.
 */
private class Grid(val unit: Dp, val uh: Dp, val rows: Int, val extraHeight: Dp = 0.dp, val fullWidth: Dp? = null) {
    val width: Dp get() = fullWidth ?: (unit * 10 + KeyGap * 9)
    val height: Dp get() = uh * rows + KeyGap * (rows - 1) + extraHeight
    fun w(span: Float): Dp = unit * span + KeyGap * (span - 1f)
    /** Height of one row when [n] equal rows fill the whole frame. */
    fun rowH(n: Int): Dp = (height - KeyGap * (n - 1)) / n
}

private fun kbGrid(maxWidth: Dp, p: Prefs): Grid {
    val unit = minOf((maxWidth - KeyGap * 9) / 10, MaxKey) * keyScale(p)
    return Grid(unit, unit * keyHeight(p), if (p.bool("numberRow")) 5 else 4)
}

@Composable
private fun LettersView(svc: EveryDayKeysService, c: KbColors) {
    val up = svc.shift != Shift.OFF
    val layer = Layouts.shifted[svc.lang]
    val rows = (if (up && layer != null) layer else Layouts.letters[svc.lang]) ?: Layouts.letters.getValue("en")
    val upper = up && layer == null   // capitals only for scripts that have them
    val hintsOn = svc.prefs.bool("keyHints") || layoutStyle(svc.prefs) == "pro"
    val gap = Arrangement.spacedBy(KeyGap, Alignment.CenterHorizontally)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // ten key columns; every key spans a multiple of `unit` wide and is `uh` tall (`unit` times the height setting)
        val grid = kbGrid(maxWidth, svc.prefs)
        val uh = grid.uh
        fun w(span: Float) = grid.w(span)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KeyGap), horizontalAlignment = Alignment.CenterHorizontally) {
            if (svc.prefs.bool("numberRow")) Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                "1234567890".forEach { d ->
                    val s = d.toString()
                    Key(svc, c, Modifier.size(w(1f), uh), s, label = s) { svc.onChar(s) }
                }
            }
            rows.forEachIndexed { i, row ->
                // Rows with more keys than fit at full size (Hindi) get narrower keys instead of overflowing
                val area = if (i == 2) w(10f) - w(1.5f) * 2 - KeyGap * 2 else w(10f)
                val keyW = if (w(1f) * row.size + KeyGap * (row.size - 1) > area + 0.5.dp) (area - KeyGap * (row.size - 1)) / row.size else w(1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                    if (i == 1 && row.size < 10) Spacer(Modifier.size(w(0.5f), uh))
                    if (i == 2) Key(svc, c, Modifier.size(w(1.5f), uh), "Shift", icon = if (svc.shift == Shift.LOCK) R.drawable.ic_caps else R.drawable.ic_up,
                        tone = 1, active = svc.shift != Shift.OFF) { svc.onShift() }
                    row.forEach { k ->
                        val label = Layouts.display(if (upper) k.uppercase() else k)
                        val a = Layouts.accents[k]
                        val hint = if (hintsOn && svc.lang == "en") Hints[k] else null
                        val onLong: (() -> Unit)? = if (svc.prefs.bool("accentPopup") && a != null) {
                            { svc.alts = a.map { if (upper) it.uppercase() else it } + listOfNotNull(hint) }
                        } else if (hint != null) { { svc.typeSymbol(hint) } } else null
                        Key(svc, c, Modifier.size(keyW, uh), label, label = label, hint = hint, onLong = onLong) { svc.onChar(k) }
                    }
                    if (i == 2) Key(svc, c, Modifier.size(w(1.5f), uh), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
                    if (i == 1 && row.size < 10) Spacer(Modifier.size(w(0.5f), uh))
                }
            }
            val lang = svc.prefs.bool("langKey")
            val space = 10f - (1.2f + 1f + 0.8f + 0.8f + 1.3f) - (if (lang) 1f else 0f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                Key(svc, c, Modifier.size(w(1.2f), uh), "Number pad. Long press for symbols.", icon = R.drawable.ic_numpad, tone = 1,
                    onLong = { svc.mode = Mode.SYM1 }) { svc.mode = Mode.NUMPAD }
                if (lang) Key(svc, c, Modifier.size(w(1f), uh), "Switch language", icon = R.drawable.ic_globe, tone = 1) { svc.nextLang() }
                Key(svc, c, Modifier.size(w(1f), uh), "Comma", label = ",", tone = 1) { svc.onChar(",") }
                SpaceKey(svc, c, Modifier.size(w(space), uh))
                Key(svc, c, Modifier.size(w(0.8f), uh), "Emoji", icon = R.drawable.ic_emoji, tone = 1) { svc.openPanel(Panel.EMOJI) }
                Key(svc, c, Modifier.size(w(0.8f), uh), "Period. Long press for punctuation.", label = ".", tone = 1,
                    onLong = { svc.alts = Layouts.periodAlts(svc.lang) }) { svc.onChar(".") }
                Key(svc, c, Modifier.size(w(1.3f), uh), "Enter", icon = R.drawable.ic_enter, tone = 2,
                    shapeOverride = if (layoutStyle(svc.prefs) == "flat" || layoutStyle(svc.prefs) == "pro") RoundedCornerShape(50) else null) { svc.onEnter() }
            }
        }
    }
}

@Composable
private fun NumpadView(svc: EveryDayKeysService, c: KbColors) {
    val p = svc.prefs
    val kind = p.str("numpadLayout").let {
        if (it == "auto" || it.isEmpty()) when (layoutStyle(p)) { "raised" -> "top"; "flat" -> "left"; "pro" -> "circles"; else -> "classic" } else it
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the number pad fills the whole keyboard: the full width, and the height of the letter keys plus the strip the other screens give up
        val g0 = kbGrid(maxWidth, p)
        val grid = Grid(g0.unit, g0.uh, g0.rows, extraHeight = StripH, fullWidth = maxWidth)
        if (kind == "top" || kind == "left" || kind == "circles") NumpadVariant(svc, c, kind, grid) else NumpadClassic(svc, c, grid)
    }
}

@Composable
private fun NK(svc: EveryDayKeysService, c: KbColors, mod: Modifier, label: String, tone: Int = 0, shape: Shape? = null,
               desc: String = label, onLong: (() -> Unit)? = null, act: () -> Unit) =
    Key(svc, c, mod, desc, label = label, tone = tone, shapeOverride = shape, onLong = onLong, onTap = act)

@Composable
private fun NI(svc: EveryDayKeysService, c: KbColors, mod: Modifier, icon: Int, desc: String, tone: Int = 1, shape: Shape? = null,
               repeat: Boolean = false, onLong: (() -> Unit)? = null, act: () -> Unit) =
    Key(svc, c, mod, desc, icon = icon, tone = tone, shapeOverride = shape, repeat = repeat, onLong = onLong, onTap = act)

/** The three number pad designs from the preview: operators on top, operators on the left, and round keys. */
@Composable
private fun NumpadVariant(svc: EveryDayKeysService, c: KbColors, kind: String, grid: Grid) {
    val g = KeyGap
    // "top" has an extra operator row at 0.7 of a key; the others have four equal rows. Both fill the shared frame exactly.
    val kh = if (kind == "top") (grid.height - g * 4) / 4.7f else grid.rowH(4)
    val gap = Arrangement.spacedBy(g)
    val pill = RoundedCornerShape(50)
    val dig: Shape? = if (kind == "circles") pill else null
    val ops = listOf("÷" to "÷", "×" to "×", "−" to "−", "+" to "+")
    val toLetters = { svc.mode = Mode.LETTERS }
    val toSym = { svc.mode = Mode.SYM1 }
    Box(Modifier.fillMaxWidth()) {
        when (kind) {
            "top" -> Column(Modifier.width(grid.width).align(Alignment.Center), verticalArrangement = gap) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                    listOf("×" to "×", "÷" to "÷", "=" to "=", "%" to "%", "(" to "(", ")" to ")").forEach { (l, v) ->
                        NK(svc, c, Modifier.weight(1f).height(kh * 0.7f), l, 1) { if (v == "=") svc.onEquals() else svc.commitText(v) }
                    }
                }
                val side = listOf("backspace", "+", "−")
                listOf("123", "456", "789").forEachIndexed { r, digits ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                        digits.forEach { d -> val k = d.toString(); NK(svc, c, Modifier.weight(1f).height(kh), k) { svc.onChar(k) } }
                        if (r == 0) NI(svc, c, Modifier.weight(1f).height(kh), R.drawable.ic_backspace, "Backspace", repeat = true) { svc.onBackspace() }
                        else { val o = side[r]; NK(svc, c, Modifier.weight(1f).height(kh), o, 1, desc = if (o == "−") "Minus" else "Plus") { svc.commitText(o) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                    NI(svc, c, Modifier.weight(1f).height(kh), R.drawable.ic_keyboard, "Back to letters. Long press for symbols.", 1, onLong = toSym) { toLetters() }
                    NK(svc, c, Modifier.weight(0.6f).height(kh), "0") { svc.onChar("0") }
                    NK(svc, c, Modifier.weight(1.4f).height(kh), "00", desc = "Double zero") { svc.commitText("00") }
                    NK(svc, c, Modifier.weight(1f).height(kh), ".", 1, desc = "Decimal point") { svc.onChar(".") }
                    NI(svc, c, Modifier.weight(1f).height(kh), R.drawable.ic_enter, "Enter", 2) { svc.onEnter() }
                }
            }
            "left" -> Row(Modifier.width(grid.width).align(Alignment.Center), horizontalArrangement = gap) {
                Column(Modifier.weight(0.9f), verticalArrangement = gap) {
                    ops.forEach { (l, v) -> NK(svc, c, Modifier.fillMaxWidth().height(kh), l, 1) { svc.commitText(v) } }
                }
                Column(Modifier.weight(3f), verticalArrangement = gap) {
                    listOf("789", "456", "123").forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                            r.forEach { d -> val k = d.toString(); NK(svc, c, Modifier.weight(1f).height(kh), k) { svc.onChar(k) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                        NK(svc, c, Modifier.weight(0.6f).height(kh), "0") { svc.onChar("0") }
                        NK(svc, c, Modifier.weight(1.4f).height(kh), "00", desc = "Double zero") { svc.commitText("00") }
                        NK(svc, c, Modifier.weight(1f).height(kh), ".", 1, desc = "Decimal point") { svc.onChar(".") }
                    }
                }
                Column(Modifier.weight(1.1f), verticalArrangement = gap) {
                    NI(svc, c, Modifier.fillMaxWidth().height(kh), R.drawable.ic_backspace, "Backspace", repeat = true) { svc.onBackspace() }
                    NI(svc, c, Modifier.fillMaxWidth().height(kh), R.drawable.ic_keyboard, "Back to letters. Long press for symbols.", 1, onLong = toSym) { toLetters() }
                    NI(svc, c, Modifier.fillMaxWidth().height(kh * 2 + g), R.drawable.ic_enter, "Enter", 2, pill) { svc.onEnter() }
                }
            }
            else -> Row(Modifier.width(grid.width).align(Alignment.Center), horizontalArrangement = gap) {
                Column(Modifier.weight(3f), verticalArrangement = gap) {
                    listOf("123", "456", "789").forEach { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                            r.forEach { d -> val k = d.toString(); NK(svc, c, Modifier.weight(1f).height(kh), k, 0, dig) { svc.onChar(k) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                        NK(svc, c, Modifier.weight(0.6f).height(kh), "0", 0, dig) { svc.onChar("0") }
                        NK(svc, c, Modifier.weight(1.4f).height(kh), "00", 0, dig, "Double zero") { svc.commitText("00") }
                        NK(svc, c, Modifier.weight(1f).height(kh), ".", 1, dig, "Decimal point") { svc.onChar(".") }
                    }
                }
                Column(Modifier.weight(0.9f), verticalArrangement = gap) {
                    ops.forEach { (l, v) -> NK(svc, c, Modifier.fillMaxWidth().height(kh), l, 1, dig) { svc.commitText(v) } }
                }
                Column(Modifier.weight(1.1f), verticalArrangement = gap) {
                    NI(svc, c, Modifier.fillMaxWidth().height(kh), R.drawable.ic_backspace, "Backspace", shape = dig, repeat = true) { svc.onBackspace() }
                    NI(svc, c, Modifier.fillMaxWidth().height(kh), R.drawable.ic_keyboard, "Back to letters. Long press for symbols.", 1, pill, onLong = toSym) { toLetters() }
                    NI(svc, c, Modifier.fillMaxWidth().height(kh * 2 + g), R.drawable.ic_enter, "Enter", 2, pill) { svc.onEnter() }
                }
            }
        }
    }
}

@Composable
private fun NumpadClassic(svc: EveryDayKeysService, c: KbColors, grid: Grid) {
    val g = KeyGap
    val kh = grid.rowH(4)
    val gap = Arrangement.spacedBy(g)
    Box(Modifier.fillMaxWidth()) {
        // three columns: operators and ABC on the left, a large 3x3 digit block with a bottom row in the middle, %, space, backspace and enter on the right
        Row(Modifier.width(grid.width).align(Alignment.Center), horizontalArrangement = gap) {
            Column(Modifier.weight(0.62f), verticalArrangement = gap) {
                val opH = (kh * 3 + g * 2 - g * 3) / 4
                listOf("+" to "Plus", "−" to "Minus", "×" to "Multiply", "÷" to "Divide").forEach { (o, d) ->
                    Key(svc, c, Modifier.fillMaxWidth().height(opH), d, label = o, tone = 1) { svc.commitText(o) }
                }
                Key(svc, c, Modifier.fillMaxWidth().height(kh), "Back to letters. Long press for symbols.", icon = R.drawable.ic_keyboard, tone = 1,
                    onLong = { svc.mode = Mode.SYM1 }) { svc.mode = Mode.LETTERS }
            }
            Column(Modifier.weight(3f), verticalArrangement = gap) {
                listOf("123", "456", "789").forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                        r.forEach { d -> val k = d.toString(); Key(svc, c, Modifier.weight(1f).height(kh), k, label = k) { svc.onChar(k) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
                    // the first two columns hold !?# (40), 0 (40) and 00 (70) in that proportion
                    Row(Modifier.weight(2f), horizontalArrangement = gap) {
                        Key(svc, c, Modifier.weight(0.4f).height(kh), "Symbols", label = "!?#", tone = 1) { svc.mode = Mode.SYM1 }
                        Key(svc, c, Modifier.weight(0.4f).height(kh), "0", label = "0") { svc.onChar("0") }
                        Key(svc, c, Modifier.weight(0.7f).height(kh), "Double zero", label = "00") { svc.commitText("00") }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = gap) {
                        Key(svc, c, Modifier.weight(1f).height(kh), "Equals", label = "=", tone = 1) { svc.onEquals() }
                        Key(svc, c, Modifier.weight(1f).height(kh), "Decimal point", label = ".", tone = 1) { svc.onChar(".") }
                    }
                }
            }
            Column(Modifier.weight(0.62f), verticalArrangement = gap) {
                Key(svc, c, Modifier.fillMaxWidth().height(kh), "Percent", label = "%", tone = 1) { svc.commitText("%") }
                Key(svc, c, Modifier.fillMaxWidth().height(kh), "Space", icon = R.drawable.ic_space, tone = 1) { svc.onChar(" ") }
                Key(svc, c, Modifier.fillMaxWidth().height(kh), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
                Key(svc, c, Modifier.fillMaxWidth().height(kh), "Enter", icon = R.drawable.ic_enter, tone = 2) { svc.onEnter() }
            }
        }
    }
}

@Composable
private fun SymbolsView(svc: EveryDayKeysService, c: KbColors) {
    val symModes = listOf(Mode.SYM1, Mode.SYM2, Mode.SYM3, Mode.SYM4)
    val page = symModes.indexOf(svc.mode).coerceAtLeast(0)
    // with the number row on, symbols get the same digit row as the letters, so they are five rows tall like the letters
    val numRow = svc.prefs.bool("numberRow")
    val rows = if (numRow) Layouts.symPagesWithNumberRow[page] else Layouts.symPages[page]
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // symbols fill the whole keyboard: the full width, and the height of the letter keys plus the strip the other screens give up
        val g0 = kbGrid(maxWidth, svc.prefs)
        val total = g0.height + StripH
        val full = Arrangement.spacedBy(KeyGap)
        Column(Modifier.fillMaxWidth().height(total), verticalArrangement = full) {
            if (numRow) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = full) {
                "1234567890".forEach { d -> val k = d.toString(); Key(svc, c, Modifier.weight(1f).fillMaxHeight(), k, label = k) { svc.typeSymbol(k) } }
            }
            rows.forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = full) {
                    row.forEach { k -> Key(svc, c, Modifier.weight(1f).fillMaxHeight(), k, label = k) { svc.typeSymbol(k) } }
                    if (i == 2) Key(svc, c, Modifier.weight(Layouts.backspaceWeight(row.size)).fillMaxHeight(), "Backspace", icon = R.drawable.ic_backspace, tone = 1, repeat = true) { svc.onBackspace() }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = full) {
                Key(svc, c, Modifier.weight(1.2f).fillMaxHeight(), "Back to letters", icon = R.drawable.ic_keyboard, tone = 1) { svc.mode = Mode.LETTERS }
                Key(svc, c, Modifier.weight(1.2f).fillMaxHeight(), "Next symbol page (${page + 1} of ${symModes.size})", icon = R.drawable.ic_swap, tone = 1) {
                    svc.mode = symModes[(page + 1) % symModes.size]
                }
                Key(svc, c, Modifier.weight(1.2f).fillMaxHeight(), "Number pad", icon = R.drawable.ic_numpad, tone = 1) { svc.mode = Mode.NUMPAD }
                SpaceKey(svc, c, Modifier.weight(5.1f).fillMaxHeight())
                Key(svc, c, Modifier.weight(1.3f).fillMaxHeight(), "Enter", icon = R.drawable.ic_enter, tone = 2) { svc.onEnter() }
            }
        }
    }
}

// ---------------------------------------------------------------- bars

private class Tool(val icon: Int, val desc: String, val run: (EveryDayKeysService) -> Unit)

private fun tool(id: String): Tool? = when (id) {
    "clip" -> Tool(R.drawable.ic_clipboard, "Clipboard") { it.togglePanel(Panel.CLIP) }
    "snip" -> Tool(R.drawable.ic_snippet, "Text snippets") { it.togglePanel(Panel.SNIPPETS) }
    "voice" -> Tool(R.drawable.ic_mic, "Voice typing") { it.startVoice() }
    "translate" -> Tool(R.drawable.ic_translate, "Translate selected or typed text") { it.translate() }
    "undo" -> Tool(R.drawable.ic_undo, "Undo") { it.undo() }
    "redo" -> Tool(R.drawable.ic_redo, "Redo") { it.redo() }
    "cursor" -> Tool(R.drawable.ic_code, "Cursor and edit keys") { it.togglePanel(Panel.CURSOR) }
    "convert" -> Tool(R.drawable.ic_ruler, "Unit converter") { it.togglePanel(Panel.CONVERT) }
    "quick" -> Tool(R.drawable.ic_tune, "Quick settings") { it.togglePanel(Panel.QUICK) }
    "style" -> Tool(R.drawable.ic_sun, "Switch layout style") { it.cycleLayoutStyle() }
    "settings" -> Tool(R.drawable.ic_settings, "Settings") { it.openSettings() }
    // shown instead of the configured tools while text is selected
    "selectall" -> Tool(R.drawable.ic_select_all, "Select all") { it.selectAll() }
    "copy" -> Tool(R.drawable.ic_copy, "Copy") { it.copy() }
    "cut" -> Tool(R.drawable.ic_cut, "Cut") { it.cut() }
    "paste" -> Tool(R.drawable.ic_paste, "Paste") { it.paste() }
    else -> null
}

/** Tool icons only, used while a panel (clipboard, emoji...) is open and the suggestion strip is hidden. */
@Composable
private fun Toolbar(svc: EveryDayKeysService, c: KbColors) {
    Row(Modifier.fillMaxWidth().height(StripH), verticalAlignment = Alignment.CenterVertically) {
        ToolboxToggle(svc, c, open = true)
        ToolIcons(svc, c)
    }
}

@Composable
private fun ToolboxToggle(svc: EveryDayKeysService, c: KbColors, open: Boolean) {
    // while a panel is open this button is also the way back to the keys
    val inPanel = svc.panel != Panel.NONE
    ToolTile(R.drawable.ic_toolbox, if (inPanel) "Back to keyboard" else if (open) "Collapse toolbar" else "Open toolbar", c, open, Modifier.size(48.dp)) {
        svc.feedback(); if (inPanel) svc.closePanel() else svc.toolsOpen = !open
    }
}

@Composable
private fun RowScope.ToolIcons(svc: EveryDayKeysService, c: KbColors) {
    val ids = if (svc.hasSelection) listOf("selectall", "copy", "cut", "paste", "undo", "redo")
    else svc.prefs.str("toolbarItems").split(",").filter { it.isNotBlank() }
    Surface(
        Modifier.weight(1f).height(40.dp).padding(start = 4.dp, end = 2.dp),
        shape = CircleShape,
        color = c.panel.copy(alpha = 0.65f)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ids.forEach { id -> tool(id)?.let { t -> ToolTile(t.icon, t.desc, c, false, Modifier.weight(1f)) { svc.feedback(); t.run(svc) } } }
        }
    }
}

/** Confirmation (Copied, Undo...) in place of the suggestions, with a quick follow-up action such as Paste or Redo. */
@Composable
private fun NoticeBar(c: KbColors, n: EveryDayKeysService.Notice) {
    Box(Modifier.fillMaxWidth().height(StripH).padding(vertical = 4.dp)) { DsNotice(c.palette(), n.icon, n.text, n.action, n.onAction) }
}

/**
 * One row for both the toolbar and the suggestions. The toolbox icon at the left opens or collapses the tools.
 * The tools also open by themselves whenever there are no suggestions to show.
 */
@Composable
private fun Strip(svc: EveryDayKeysService, c: KbColors) {
    val tr = svc.trans
    if (tr != null) { TranslationBar(svc, c, tr); return }
    val alts = svc.alts
    val items = alts ?: svc.suggestions
    val pending = if (alts == null && svc.showClipChip) svc.pendingClip else null
    val hasToolbar = svc.prefs.bool("showToolbar")
    val noWords = items.isEmpty() && pending == null
    val showTools = hasToolbar && alts == null && (svc.toolsOpen || noWords || svc.hasSelection)
    if (!hasToolbar && noWords) { Spacer(Modifier.fillMaxWidth().height(StripH)); return }
    Row(Modifier.fillMaxWidth().height(StripH).animateContentSize(ExpressiveMotion.spatialFast()), verticalAlignment = Alignment.CenterVertically) {
        if (hasToolbar) ToolboxToggle(svc, c, open = showTools)
        if (showTools) { ToolIcons(svc, c); return@Row }
        if (pending != null) ClipChip(svc, c, pending, items.isEmpty())
        Row(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, s ->
                val isPrimary = alts == null && i == 0 && s.isNotBlank()
                Surface(
                    onClick = { if (alts != null) svc.onAlt(s) else svc.applySuggestion(s) },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp),
                    shape = CircleShape,
                    color = if (isPrimary) c.accentContainer.copy(alpha = 0.78f) else c.panel.copy(alpha = 0.72f),
                    contentColor = if (isPrimary) c.onAccentContainer else c.text,
                    border = if (isPrimary) BorderStroke(1.dp, c.accent.copy(alpha = 0.30f)) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            s,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
        if (alts != null) IconTap(R.drawable.ic_close, "Close alternates", c) { svc.alts = null }
    }
}

// ---------------------------------------------------------------- resize mode

/** One drag handle of the resize frame. [onDrag] gets the finger movement in pixels. */
@Composable
private fun ResizeHandle(
    c: KbColors, desc: String, modifier: Modifier, bars: Boolean, vertical: Boolean, shape: Shape = EveryDayKeysShapes.medium,
    onDrag: (Float, Float) -> Unit
) {
    Surface(
        modifier.semantics { contentDescription = desc }.pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); onDrag(d.x, d.y) } },
        shape = shape, color = c.accent, contentColor = c.onAccent
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bars && vertical) Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.size(28.dp, 3.dp).clip(CircleShape).background(c.onAccent)) } }
            else if (bars) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(2) { Box(Modifier.size(3.dp, 28.dp).clip(CircleShape).background(c.onAccent)) } }
            else Text("\u2725", color = c.onAccent, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * Resize mode: a frame with a handle on every edge. Top and bottom change the key height, left and right the key width,
 * the round handle moves the keys sideways. Reset goes back to the defaults and OK keeps what you see.
 */
@Composable
private fun ResizeOverlay(svc: EveryDayKeysService, c: KbColors, modifier: Modifier) {
    val p = svc.prefs
    var sz by remember { mutableStateOf(IntSize.Zero) }
    var hF by remember { mutableStateOf(p.int("keyHeight").toFloat()) }
    var wF by remember { mutableStateOf(p.int("keyScale").toFloat()) }
    var sF by remember { mutableStateOf(p.int("kbShift").toFloat()) }
    fun setH(v: Float) { val n = v.coerceIn(70f, 140f); if (n.toInt() != hF.toInt()) svc.setPref("keyHeight", n.toInt().toString()); hF = n }
    fun setW(v: Float) { val n = v.coerceIn(60f, 100f); if (n.toInt() != wF.toInt()) svc.setPref("keyScale", n.toInt().toString()); wF = n }
    fun setS(v: Float) { val n = v.coerceIn(-100f, 100f); if (n.toInt() != sF.toInt()) svc.setPref("kbShift", n.toInt().toString()); sF = n }
    // dragging an edge by a fraction of the frame changes the size by about the same fraction
    fun heightBy(dy: Float, sign: Float) = setH(hF + sign * dy / maxOf(sz.height, 1) * hF * 1.3f)
    fun widthBy(dx: Float, sign: Float) = setW(wF + sign * dx / maxOf(sz.width, 1) * wF * 2f)
    Box(
        modifier.onSizeChanged { sz = it }.border(2.dp, c.accent)
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps so keys under the frame are not typed while resizing
    ) {
        ResizeHandle(c, "Drag up or down to change the keyboard height", Modifier.align(Alignment.TopCenter).size(76.dp, 34.dp), true, true) { _, dy -> heightBy(dy, -1f) }
        ResizeHandle(c, "Drag up or down to change the keyboard height", Modifier.align(Alignment.BottomCenter).size(76.dp, 34.dp), true, true) { _, dy -> heightBy(dy, 1f) }
        ResizeHandle(c, "Drag sideways to change the keyboard width", Modifier.align(Alignment.CenterStart).size(30.dp, 72.dp), true, false) { dx, _ -> widthBy(dx, -1f) }
        ResizeHandle(c, "Drag sideways to change the keyboard width", Modifier.align(Alignment.CenterEnd).size(30.dp, 72.dp), true, false) { dx, _ -> widthBy(dx, 1f) }
        ResizeHandle(c, "Drag sideways to move the keyboard", Modifier.align(Alignment.Center).size(60.dp), false, false, CircleShape) { dx, _ ->
            setS(sF + dx / maxOf(sz.width, 1) * 200f)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            listOf("Reset", "OK").forEach { label ->
                val ok = label == "OK"
                val act = {
                    if (ok) svc.setPref("resizeMode", "false")
                    else { setH(100f); setW(100f); setS(0f); svc.setPref("keyHeight", "100"); svc.setPref("keyScale", "100"); svc.setPref("kbShift", "0") }
                }
                val m = Modifier.width(112.dp).semantics { contentDescription = if (ok) "Keep this size" else "Reset the size" }
                if (ok) Button(act, m, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)) { Text(label) }
                else FilledTonalButton(act, m, colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.accentContainer, contentColor = c.onAccentContainer)) { Text(label) }
            }
        }
    }
}
