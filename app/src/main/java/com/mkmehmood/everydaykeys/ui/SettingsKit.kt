package com.mkmehmood.everydaykeys.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkmehmood.everydaykeys.R
import com.mkmehmood.everydaykeys.design.*
import kotlinx.coroutines.channels.Channel

// Shared Material 3 Expressive building blocks for every settings screen:
// large collapsing top app bar, tonal cards, list items, pill search bar, tonal icon containers,
// filled / tonal / outlined buttons and an extended FAB.

/** Messages for the Material 3 snackbar. Queued, so one posted just before leaving a screen still shows on the next. */
internal object Snack {
    private val ch = Channel<String>(Channel.BUFFERED)
    fun post(message: String) { ch.trySend(message) }
    suspend fun collect(show: suspend (String) -> Unit) { for (m in ch) show(m) }
}

internal val CardShape = Dim.Card
internal val FieldShape = Dim.Field

@Composable
internal fun Ic(id: Int, desc: String? = null, tint: Color = LocalContentColor.current, size: Dp = 24.dp) =
    Icon(painterResource(id), desc, tint = tint, modifier = Modifier.size(size))

/** Page scaffold: large top app bar that collapses on scroll, adaptive content column, try-it preview bar and optional FAB. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScaffold(
    title: String, onBack: () -> Unit, fab: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { Snack.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection).imePadding(),
        containerColor = cs.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("nav_back_button")
                    ) { Ic(R.drawable.ic_back, "Back") }
                },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surfaceContainer,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface
                )
            )
        },
        bottomBar = { PreviewBar() },
        floatingActionButton = fab
    ) { pad ->
        Box(
            Modifier.fillMaxSize().padding(pad),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.fillMaxSize().widthIn(max = 680.dp).padding(horizontal = 16.dp),
                content = content
            )
        }
    }
}

/** Category header shown above grouped Material 3 preference cards. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)
    )
}

/** Filled tonal card. Put list rows inside it and separate them with [RowDivider]. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().animateContentSize(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow, contentColor = cs.onSurface),
        border = BorderStroke(0.5.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        content = content
    )
}

@Composable
internal fun RowDivider() =
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

/** Circular tonal container with an icon, the Material 3 leading icon. */
@Composable
internal fun Tile(icon: Int, size: Dp = Dim.TileSize, filled: Boolean = false) = DsTile(settingsPalette(), icon, size, filled)

/** A Material 3 list item on a transparent background (it sits inside a card). */
@Composable
internal fun ListRow(
    title: String, modifier: Modifier = Modifier, sub: String? = null, titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) = ListItem(
    headlineContent = { Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge) },
    modifier = modifier.heightIn(min = Dim.RowMin),
    supportingContent = if (sub.isNullOrBlank()) null else ({ Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }),
    leadingContent = leading,
    trailingContent = trailing,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
)

/** Pill-shaped Material 3 search bar. */
@Composable
internal fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = CircleShape,
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (value.isNotEmpty()) cs.primary.copy(alpha = 0.5f) else cs.outlineVariant.copy(alpha = 0.45f))
    ) {
        TextField(
            value, onChange,
            Modifier.fillMaxWidth().testTag("settings_search_input"),
            singleLine = true,
            shape = CircleShape,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(placeholder, color = cs.onSurfaceVariant) },
            leadingIcon = { Ic(R.drawable.ic_search, null, cs.primary) },
            trailingIcon = if (value.isEmpty()) null else ({
                IconButton(onClick = { onChange("") }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Ic(R.drawable.ic_close, "Clear search", cs.onSurfaceVariant)
                }
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

/** The field at the bottom of every screen so changes can be tried straight away. */
@Composable
internal fun PreviewField(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val fieldBg = if (cs.surface.luminance() < 0.5f) cs.surfaceContainerHighest else cs.surfaceContainerLowest
    var text by remember { mutableStateOf("") }
    Surface(
        modifier.fillMaxWidth(),
        shape = CircleShape,
        color = fieldBg,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f))
    ) {
        TextField(
            text, { text = it },
            Modifier.fillMaxWidth().testTag("keyboard_preview_input"),
            singleLine = true,
            shape = CircleShape,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("Type here to preview changes", color = cs.onSurfaceVariant) },
            leadingIcon = { Ic(R.drawable.ic_keyboard, null, cs.primary) },
            trailingIcon = if (text.isEmpty()) null else ({
                IconButton(onClick = { text = "" }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Ic(R.drawable.ic_close, "Clear preview text", cs.onSurfaceVariant)
                }
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
internal fun PreviewBar() {
    val cs = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        color = cs.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                PreviewField(Modifier.widthIn(max = 680.dp))
            }
        }
    }
}

@Composable
private fun ButtonLabel(text: String, icon: Int?) {
    if (icon != null) { Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)) }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
}

/** Filled button. */
@Composable
internal fun PrimaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick, modifier.heightIn(min = 44.dp), shape = CircleShape) { ButtonLabel(text, icon) }

/** Filled tonal button, the medium-emphasis action. */
@Composable
internal fun TonalButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    FilledTonalButton(onClick, modifier.heightIn(min = 44.dp), shape = CircleShape) { ButtonLabel(text, icon) }

/** Outlined button, the lower-emphasis action. */
@Composable
internal fun SecondaryButton(text: String, icon: Int? = null, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick, modifier.heightIn(min = 44.dp), shape = CircleShape) { ButtonLabel(text, icon) }

@Composable
internal fun settingsSwitchColors(): SwitchColors = dsSwitchColors(settingsPalette())

@Composable
internal fun settingsSliderColors(): SliderColors = SliderDefaults.colors()
