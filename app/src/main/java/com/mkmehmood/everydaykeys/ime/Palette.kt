package com.mkmehmood.everydaykeys.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.mkmehmood.everydaykeys.data.P
import com.mkmehmood.everydaykeys.data.Snap

data class Palette(
    val bg: Int, val key: Int, val func: Int, val text: Int, val sub: Int,
    val accent: Int, val onAccent: Int, val dark: Boolean,
)

object Palettes {
    val accents = listOf("Teal" to "0F766E", "Blue" to "2563EB", "Purple" to "7C3AED", "Orange" to "EA580C", "Rose" to "E11D48")
    val backgrounds = listOf("Mist" to "D6DCE4", "Sand" to "E9E2D0", "Slate" to "263238", "Midnight" to "0F172A")

    private fun hex(s: String) = Color.parseColor("#$s")

    fun from(ctx: Context, s: Snap): Palette {
        val systemDark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = when (s[P.theme]) {
            "dark" -> hex("1B222B")
            "light" -> hex("D6DCE4")
            "custom" -> hex(s[P.customBg])
            else -> if (systemDark) hex("1B222B") else hex("D6DCE4")
        }
        val accent = hex(s[P.accent])
        val dark = ColorUtils.calculateLuminance(bg) < 0.4
        return if (dark) Palette(bg, ColorUtils.blendARGB(bg, Color.WHITE, 0.14f), ColorUtils.blendARGB(bg, Color.BLACK, 0.25f),
            hex("E8EDF2"), hex("9AA7B5"), accent, Color.WHITE, true)
        else Palette(bg, Color.WHITE, ColorUtils.blendARGB(bg, Color.BLACK, 0.10f),
            hex("17202A"), hex("5B6775"), accent, Color.WHITE, false)
    }
}
