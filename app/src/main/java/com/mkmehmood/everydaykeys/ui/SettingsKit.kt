package com.mkmehmood.everydaykeys.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.design.*

// Shared Material 3 building blocks for every settings screen:
// large collapsing top app bar, filled cards, list items, pill search bar, tonal icon containers,
// filled / tonal / outlined buttons and an extended FAB.

internal val CardShape = Dim.Card
internal val FieldShape = Dim.Field

@Composable
internal fun Ic(id: Int, desc: String? = null, tint: Color = LocalContentColor.current, size: Dp = 24.dp) =
    Icon(painterResource(id), desc, tint = tint, modifier = Modifier.size(size))

/** Page scaffold: large top app bar that collapses on scroll, content, the try-it field at the bottom and an optional FAB. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String, onBack: () -> Unit, fab: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection).imePadding(),
        containerColor = cs.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Ic(R.drawable.ic_back, "Back") } },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer)
            )
        },
        bottomBar = { PreviewBar() },
        floatingActionButton = fab
    ) { pad -> Column(Modifier.padding(pad).padding(horizontal = 16.dp), content = content) }
}

/** Filled card. Put list rows inside it and separate them with [RowDivider]. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Card(
        modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), content = content
    )

@Composable
internal fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** Circular tonal container with an icon, the Material 3 leading icon. */
@Composable
internal fun Tile(icon: Int, size: Dp = Dim.TileSize) = DsTile(settingsPalette(), icon, size)

/** A Material 3 list item on a transparent background (it sits inside a card). */
@Composable
internal fun ListRow(
    title: String, modifier: Modifier = Modifier, sub: String? = null, titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) = ListItem(
    headlineContent = { Text(title, color = titleColor) },
    modifier = modifier,
    supportingContent = if (sub.isNullOrBlank()) null else ({ Text(sub) }),
    leadingContent = leading,
    trailingContent = trailing,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
)

/** Pill-shaped Material 3 search bar. */
@Composable
internal fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value, onChange, modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
        placeholder = { Text(placeholder) },
        leadingIcon = { Ic(R.drawable.ic_search, null, cs.onSurfaceVariant) },
        trailingIcon = if (value.isEmpty()) null else ({ IconButton(onClick = { onChange("") }) { Ic(R.drawable.ic_close, "Clear search", cs.onSurfaceVariant) } }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent
        )
    )
}

/** The field at the bottom of every screen so changes can be tried straight away. */
@Composable
internal fun PreviewField(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    TextField(
        text, { text = it }, modifier.fillMaxWidth(), shape = CircleShape,
        placeholder = { Text("Type here to preview changes") },
        leadingIcon = { Ic(R.drawable.ic_keyboard, null, cs.onSurfaceVariant) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
internal fun PreviewBar() {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) { PreviewField() }
}

@Composable
private fun ButtonLabel(text: String, icon: Int?) {
    if (icon != null) { Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)) }
    Text(text, maxLines = 1)
}

/** Filled button. */
@Composable
internal fun PrimaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier) { ButtonLabel(text, icon) }

/** Filled tonal button, the medium-emphasis action. */
@Composable
internal fun TonalButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    FilledTonalButton(onClick, modifier) { ButtonLabel(text, icon) }

/** Outlined button, the lower-emphasis action. */
@Composable
internal fun SecondaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier) { ButtonLabel(text, icon) }

@Composable
internal fun settingsSwitchColors(): SwitchColors = dsSwitchColors(settingsPalette())

@Composable
internal fun settingsSliderColors(): SliderColors = SliderDefaults.colors()
