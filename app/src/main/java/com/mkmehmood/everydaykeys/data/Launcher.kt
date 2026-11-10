package com.mkmehmood.everydaykeys.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Shows or hides the app icon in the launcher. Settings stays reachable from the keyboard and Android's keyboard settings. */
object Launcher {
    fun apply(ctx: Context, hide: Boolean) {
        val cn = ComponentName(ctx.packageName, "com.mkmehmood.everydaykeys.LauncherAlias")
        ctx.packageManager.setComponentEnabledSetting(
            cn,
            if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
    }
}
