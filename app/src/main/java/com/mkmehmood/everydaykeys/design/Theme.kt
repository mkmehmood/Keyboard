package com.mkmehmood.everydaykeys.design

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * Material 3 theme for Everyday Keys.
 *
 * Colour: Material You dynamic colour (wallpaper based) on Android 12 and newer when it is switched on in Settings.
 * Otherwise the exact tonal-spot palette generated from the chosen accent seed (see Schemes.kt).
 * Shape and type use the standard Material 3 scales.
 */

val EveryDayKeysShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** The full Material 3 colour scheme for the given settings. Used by the Settings app and by the keyboard. */
fun m3Scheme(ctx: Context, dark: Boolean, dynamic: Boolean, accent: String): ColorScheme {
    if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
    return when (accent) {
        "blue" -> if (dark) M3Schemes.BlueDark else M3Schemes.BlueLight
        "purple" -> if (dark) M3Schemes.PurpleDark else M3Schemes.PurpleLight
        "orange" -> if (dark) M3Schemes.OrangeDark else M3Schemes.OrangeLight
        else -> if (dark) M3Schemes.TealDark else M3Schemes.TealLight
    }
}

@Composable
fun EveryDayKeysTheme(dark: Boolean, dynamic: Boolean, accent: String = "teal", content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    MaterialTheme(
        colorScheme = m3Scheme(ctx, dark, dynamic, accent),
        shapes = EveryDayKeysShapes,
        typography = Typography(),
        content = content
    )
}
