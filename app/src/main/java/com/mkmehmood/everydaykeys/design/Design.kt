package com.mkmehmood.everydaykeys.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The one design system for Everyday Keys.
 *
 * The Settings screens and everything the keyboard shows above the keys (clipboard, snippets, emoji, converter,
 * cursor keys, quick settings, paste progress, action notices) are built from the pieces in this file, so a card,
 * tile, button, toggle or note looks and measures the same wherever it appears. Only the colours differ:
 * Settings feeds in [settingsPalette] (the Material theme), the keyboard feeds in its own palette (the key colours,
 * background picture and accent the user chose).
 */

object Dim {
    val Card = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(12.dp)
    val TileShape = RoundedCornerShape(9.dp)
    val Badge = RoundedCornerShape(10.dp)
    val TileSize = 34.dp
    val RowMin = 56.dp
}

@Immutable
class Palette(
    val surface: Color,   // card and field background
    val text: Color,
    val sub: Color,       // secondary text and icons
    val accent: Color,
    val onAccent: Color,
    val hairline: Color,  // 1dp borders and dividers
    val tile: Color,      // background behind an accent icon
    val off: Color        // switch track when off
)

@Composable
fun settingsPalette(): Palette {
    val cs = MaterialTheme.colorScheme
    return Palette(cs.surface, cs.onSurface, cs.onSurfaceVariant, cs.primary, cs.onPrimary, cs.outlineVariant, cs.primaryContainer, cs.outline)
}

/** Small rounded square with an accent icon. Used at the start of rows and for toolbar icons. */
@Composable
fun DsTile(p: Palette, icon: Int, size: Dp = Dim.TileSize, filled: Boolean = false) {
    Box(
        Modifier.size(size).clip(Dim.TileShape).background(if (filled) p.accent else p.tile),
        contentAlignment = Alignment.Center
    ) { Icon(painterResource(icon), null, tint = if (filled) p.onAccent else p.accent, modifier = Modifier.size(size * 0.6f)) }
}

/** White card with a hairline border. Put rows inside it and separate them with [DsDivider]. */
@Composable
fun DsGroup(p: Palette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Dim.Card).background(p.surface).border(1.dp, p.hairline, Dim.Card), content = content)
}

@Composable
fun DsDivider(p: Palette) = Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))

/** Filled (primary) or outlined button. */
@Composable
fun DsButton(p: Palette, text: String, icon: Int?, primary: Boolean, modifier: Modifier = Modifier, height: Dp = 48.dp, onClick: () -> Unit) {
    val fg = if (primary) p.onAccent else p.accent
    Row(
        modifier.height(height).clip(Dim.Field).background(if (primary) p.accent else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(1.dp, p.hairline, Dim.Field))
            .clickable(role = Role.Button) { onClick() }.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small grey note with an icon, shown under a card. */
@Composable
fun DsNote(p: Palette, icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = p.sub, modifier = Modifier.size(14.dp))
        Text(text, color = p.sub, fontSize = 12.5.sp, maxLines = 2, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun dsSwitchColors(p: Palette): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = p.accent, checkedBorderColor = p.accent,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = p.off, uncheckedBorderColor = p.off
)

/** A row with an optional tile, a title, an optional description and a switch. The whole row is the tap target. */
@Composable
fun DsToggleRow(p: Palette, icon: Int?, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.RowMin).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { DsTile(p, icon); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = p.text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = p.sub, fontSize = 12.5.sp)
        }
        Switch(checked, null, colors = dsSwitchColors(p))
    }
}

/** Short confirmation shown over the keyboard after copy, cut, paste, select all, undo or redo. Read out by TalkBack. */
@Composable
fun DsNotice(p: Palette, icon: Int, text: String) {
    Row(
        Modifier.clip(Dim.Field).background(p.surface).border(1.dp, p.hairline, Dim.Field)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = text }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DsTile(p, icon, 28.dp)
        Text(text, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
    }
}
