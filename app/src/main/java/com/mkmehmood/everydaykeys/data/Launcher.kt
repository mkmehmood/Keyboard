package com.mkmehmood.everydaykeys.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager

/**
 * Shows or hides the app icon in the launcher. Settings stays reachable from the keyboard and Android's keyboard settings.
 *
 * Two safety rules:
 *  - The icon is never hidden unless the keyboard is enabled, because that is the way back into Settings.
 *  - While the Settings screen is open the change waits until it is closed. Switching off the entry the screen
 *    was opened from can make some phones close the app on the spot.
 */
object Launcher {
    private const val ALIAS = "com.mkmehmood.everydaykeys.LauncherAlias"

    /** Number of Settings screens currently started. */
    @Volatile var uiVisible = 0
    @Volatile private var pending: Boolean? = null

    private fun alias(ctx: Context) = ComponentName(ctx.packageName, ALIAS)

    fun isHidden(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(alias(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** True when Everyday Keys is switched on in Android's keyboard list. */
    fun imeEnabled(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == ctx.packageName }
    }.getOrDefault(false)

    /** Changes the icon now, but only if it is not already in the wanted state (every change makes launchers redraw). */
    fun apply(ctx: Context, hide: Boolean) {
        if (isHidden(ctx) == hide) return
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
    }

    /** Asks for the icon to be hidden or shown: at once, or when Settings is closed if it is open right now. */
    fun request(ctx: Context, hide: Boolean) {
        val wanted = hide && imeEnabled(ctx)
        if (uiVisible > 0) pending = wanted else { pending = null; apply(ctx, wanted) }
    }

    /** Called when the last Settings screen stops. Applies a change that was waiting. */
    fun flush(ctx: Context) {
        val p = pending ?: return
        pending = null
        apply(ctx, p)
    }

    /** Makes the real icon match the saved setting, e.g. after a restore or an interrupted change. */
    fun sync(ctx: Context, hideSaved: Boolean) = request(ctx, hideSaved)
}
