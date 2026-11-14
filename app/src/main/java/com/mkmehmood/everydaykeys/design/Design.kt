package com.mkmehmood.everydaykeys.design

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkmehmood.everydaykeys.R

/*
 * The shared Material 3 design system for Everyday Keys.
 *
 * The Settings screens and everything the keyboard shows above the keys (clipboard, snippets, emoji, converter,
 * cursor keys, quick settings, paste progress, action notices) use the pieces in this file, so a card, tonal icon,
 * button, switch or note looks and measures the same wherever it appears. Only the colours differ:
 * Settings feeds in [settingsPalette] (the Material 3 colour scheme), the keyboard feeds in a palette built from the
 * same Material 3 roles (dynamic colour or the chosen accent) in [com.mkmehmood.everydaykeys.ime.KbColors].
 */

/** Material 3 state layer opacity for the pressed state (spec value). */
object StateLayer { const val Pressed = 0.10f }

/** Material 3 Expressive shape scale and sizes. */
object Dim {
    val Card = RoundedCornerShape(20.dp)       // shape.large
    val Field = RoundedCornerShape(14.dp)      // shape.medium
    val Button = CircleShape                   // M3 buttons are pill-shaped
    val Chip = RoundedCornerShape(10.dp)       // shape.small
    val TileShape = CircleShape                // leading icon container
    val Badge = RoundedCornerShape(10.dp)
    val TileSize = 42.dp
    val RowMin = 60.dp
    val ButtonHeight = 44.dp
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

/** Filled Material 3 card (surface container, large expressive shape, subtle tonal border). */
@Composable
fun DsGroup(p: Palette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().animateContentSize(),
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

/** Material 3 switch with the check icon in the thumb when on. */
@Composable
fun DsSwitch(p: Palette, checked: Boolean, onChange: ((Boolean) -> Unit)? = null) {
    Switch(
        checked, onChange, colors = dsSwitchColors(p),
        thumbContent = if (checked) { { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize)) } } else null
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
