package com.mkmehmood.everydaykeys.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkmehmood.everydaykeys.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The shared Material 3 Expressive design system for Everyday Keys.
 *
 * Aligns the Settings workspace and IME surfaces with Android 16 Material 3 Expressive:
 * spatial bouncy springs vs. smooth effects transitions, soft segmented container seams,
 * expressive scalloped emblem geometry, and tonal surface layering.
 */

/** Material 3 Expressive motion & state layer tokens. */
object StateLayer { const val Pressed = 0.10f }

object ExpressiveMotion {
    const val EmphasizedDurationMs = 300
    const val FastDurationMs = 150

    fun <T> spatialFast() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> spatialDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> effectsDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

/** Material 3 Expressive shape scale and sizes. */
object Dim {
    val Card = RoundedCornerShape(24.dp)       // expressive container radius
    val InnerCard = RoundedCornerShape(8.dp)   // inner seam radius for segmented list groups
    val Field = RoundedCornerShape(16.dp)      // medium field radius
    val Button = CircleShape                   // M3 pill button
    val Chip = RoundedCornerShape(12.dp)       // expressive small/chip radius
    val TileShape = CircleShape                // leading icon container
    val Badge = RoundedCornerShape(10.dp)
    val TileSize = 42.dp
    val RowMin = 62.dp
    val ButtonHeight = 46.dp

    /** Android 16 Material 3 Expressive segmented card shape: outer 22dp, inner seam 6dp. */
    fun segmented(index: Int, count: Int): RoundedCornerShape {
        if (count <= 1) return RoundedCornerShape(22.dp)
        val top = if (index == 0) 22.dp else 6.dp
        val bottom = if (index == count - 1) 22.dp else 6.dp
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    /** Soft multi-lobed M3 Expressive badge / flower shape. */
    fun scallop(lobes: Int = 8, depth: Float = 0.10f): Shape = GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = minOf(cx, cy) * (1f - depth)
        val amp = minOf(cx, cy) * depth
        val steps = lobes * 16
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
            val r = baseR + amp * cos(lobes * theta)
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** Material 3 colour roles the shared components need. */
@Immutable
class Palette(
    val surface: Color,   // card background (surfaceContainer)
    val text: Color,      // onSurface
    val sub: Color,       // onSurfaceVariant
    val accent: Color,    // primary
    val onAccent: Color,  // onPrimary
    val hairline: Color,  // outlineVariant
    val tile: Color,      // container behind a leading icon (secondaryContainer)
    val off: Color,       // outline: switch thumb and border when off
    val onTile: Color = accent,        // icon on [tile] (onSecondaryContainer)
    val trackOff: Color = hairline,    // switch track when off (surfaceContainerHighest)
    val accentContainer: Color = tile, // tonal highlight (primaryContainer)
    val onAccentContainer: Color = accent
)

@Composable
fun settingsPalette(): Palette {
    val cs = MaterialTheme.colorScheme
    return Palette(
        surface = cs.surfaceContainer, text = cs.onSurface, sub = cs.onSurfaceVariant, accent = cs.primary, onAccent = cs.onPrimary,
        hairline = cs.outlineVariant, tile = cs.secondaryContainer, off = cs.outline, onTile = cs.onSecondaryContainer,
        trackOff = cs.surfaceContainerHighest, accentContainer = cs.primaryContainer, onAccentContainer = cs.onPrimaryContainer
    )
}

/** Circular tonal container with an icon: the Material 3 leading icon. Used at the start of rows and for toolbar icons. */
@Composable
fun DsTile(p: Palette, icon: Int, size: Dp = Dim.TileSize, filled: Boolean = false) {
    Surface(
        Modifier.size(size),
        shape = Dim.TileShape,
        color = if (filled) p.accent else p.tile,
        tonalElevation = if (filled) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = if (filled) p.onAccent else p.onTile, modifier = Modifier.size(size * 0.56f))
        }
    }
}

/** Filled Material 3 Expressive card (surface container, 24dp expressive shape, subtle tonal border). */
@Composable
fun DsGroup(p: Palette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().animateContentSize(ExpressiveMotion.spatialDefault()),
        shape = Dim.Card,
        colors = CardDefaults.cardColors(containerColor = p.surface, contentColor = p.text),
        border = BorderStroke(0.5.dp, p.hairline.copy(alpha = 0.45f)),
        content = content
    )
}

/** Inset divider, as in Material 3 lists. */
@Composable
fun DsDivider(p: Palette) = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = p.hairline.copy(alpha = 0.65f))

/** Material 3 filled or outlined button with an optional leading icon. */
@Composable
fun DsButton(p: Palette, text: String, icon: Int?, primary: Boolean, modifier: Modifier = Modifier, height: Dp = Dim.ButtonHeight, onClick: () -> Unit) {
    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
    val m = modifier.heightIn(min = height)
    val pad = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    if (primary) {
        Button(
            onClick, m,
            shape = Dim.Button,
            contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            content = label
        )
    } else {
        OutlinedButton(
            onClick, m,
            shape = Dim.Button,
            contentPadding = pad,
            border = BorderStroke(1.dp, p.off.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.accent),
            content = label
        )
    }
}

/** Supporting tonal note banner with an icon, shown under a card. */
@Composable
fun DsNote(p: Palette, icon: Int, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = Dim.Field,
        color = p.tile.copy(alpha = 0.35f),
        contentColor = p.sub
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = p.onTile, modifier = Modifier.size(18.dp))
            Text(text, color = p.sub, style = MaterialTheme.typography.bodySmall, maxLines = 3, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Material 3 switch colours: primary track with on-primary thumb when on, outlined when off. */
@Composable
fun dsSwitchColors(p: Palette): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = p.onAccent, checkedTrackColor = p.accent, checkedBorderColor = p.accent, checkedIconColor = p.accent,
    uncheckedThumbColor = p.off, uncheckedTrackColor = p.trackOff, uncheckedBorderColor = p.off, uncheckedIconColor = p.trackOff
)

/** Material 3 Expressive switch with animated thumb icon morphing. */
@Composable
fun DsSwitch(p: Palette, checked: Boolean, onChange: ((Boolean) -> Unit)? = null) {
    Switch(
        checked, onChange, colors = dsSwitchColors(p),
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
                label = "switchThumbIcon"
            ) { on ->
                if (on) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize))
                else Spacer(Modifier.size(SwitchDefaults.IconSize))
            }
        }
    )
}

/** A list item with an optional tonal icon, a headline, optional supporting text and a switch. The whole row is the tap target. */
@Composable
fun DsToggleRow(p: Palette, icon: Int?, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.RowMin).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { DsTile(p, icon); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = p.text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, color = p.sub, style = MaterialTheme.typography.bodyMedium)
        }
        DsSwitch(p, checked)
    }
}

/** Android 16 Material 3 Expressive linear progress bar with animated progress and end stop-indicator dot. */
@Composable
fun DsExpressiveProgress(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val anim by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ExpressiveMotion.spatialDefault(),
        label = "expressiveProgress"
    )
    Box(
        modifier.fillMaxWidth().height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anim > 0.01f) {
                Box(
                    Modifier
                        .weight(anim.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(color)
                )
            }
            val rem = (1f - anim).coerceAtLeast(0f)
            if (rem > 0.01f) {
                Box(
                    Modifier
                        .weight(rem)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(trackColor),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        Modifier
                            .padding(end = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

/** One-line confirmation bar (Copied, Undo...) with an optional follow-up text button, like a Material 3 snackbar. Read out by TalkBack. */
@Composable
fun DsNotice(p: Palette, icon: Int, text: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().fillMaxHeight(),
        shape = CircleShape,
        color = p.accentContainer,
        contentColor = p.onAccentContainer,
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, tint = p.onAccentContainer, modifier = Modifier.size(20.dp))
            Text(
                text, color = p.onAccentContainer, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (action != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = p.onAccentContainer)
                ) { Text(action, style = MaterialTheme.typography.labelLarge) }
            } else Spacer(Modifier.width(16.dp))
        }
    }
}
