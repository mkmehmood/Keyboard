package com.mkmehmood.everydaykeys.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkmehmood.everydaykeys.R

// Shared look for every settings screen, taken from the Everyday Keys design board:
// soft page background, white grouped cards with hairline borders, teal icon tiles and controls.

internal val CardShape = RoundedCornerShape(16.dp)
internal val FieldShape = RoundedCornerShape(12.dp)

@Composable
internal fun Ic(id: Int, desc: String? = null, tint: Color = LocalContentColor.current, size: Dp = 24.dp) =
    Icon(painterResource(id), desc, tint = tint, modifier = Modifier.size(size))

@Composable
internal fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Ic(R.drawable.ic_back, "Back") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
    }
}

/** White card with a hairline border. Put rows inside it and separate them with [RowDivider]. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape),
        content = content
    )
}

@Composable
internal fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Small rounded square with a teal icon, used at the start of rows. */
@Composable
internal fun Tile(icon: Int, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) { Ic(icon, null, MaterialTheme.colorScheme.primary, 20.dp) }
}

@Composable
internal fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(FieldShape).background(cs.surface).border(1.dp, cs.outlineVariant, FieldShape)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_search, null, cs.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, fontSize = 15.sp)
                    inner()
                }
                if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Ic(R.drawable.ic_close, "Clear search", cs.onSurfaceVariant, 20.dp) }
            }
        }
    )
}

/** The field at the bottom of every screen, with a dashed outline, so changes can be tried straight away. */
@Composable
internal fun PreviewField(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val dash = cs.outline
    BasicTextField(
        text, { text = it }, modifier.fillMaxWidth(),
        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(FieldShape).background(cs.surface)
                    .drawBehind {
                        val w = 1.dp.toPx()
                        drawRoundRect(
                            dash, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ic(R.drawable.ic_keyboard, null, cs.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (text.isEmpty()) Text("Type here to preview changes", color = cs.onSurfaceVariant, fontSize = 15.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
internal fun PrimaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), shape = FieldShape) {
        if (icon != null) { Ic(icon, null, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SecondaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick, modifier.heightIn(min = 48.dp), shape = FieldShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        if (icon != null) { Ic(icon, null, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun settingsSwitchColors(): SwitchColors {
    val cs = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = cs.primary, checkedBorderColor = cs.primary,
        uncheckedThumbColor = Color.White, uncheckedTrackColor = cs.outline, uncheckedBorderColor = cs.outline
    )
}

@Composable
internal fun settingsSliderColors(): SliderColors {
    val cs = MaterialTheme.colorScheme
    return SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.outlineVariant)
}
