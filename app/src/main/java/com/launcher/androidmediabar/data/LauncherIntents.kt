package com.launcher.androidmediabar.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.SettingItem
import com.launcher.androidmediabar.model.LauncherMenuItem
import com.launcher.androidmediabar.model.XmbItem

/** Builds and fires the intents triggered from the cross media bar. */
object LauncherIntents {

    fun launch(context: Context, item: XmbItem): Boolean = when (item) {
        is AppItem -> start(
            context,
            Intent(Intent.ACTION_MAIN)
                .addCategory(if (item.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                .setComponent(item.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        // Not every TV ships every settings screen: fall back to the main settings page.
        is SettingItem -> start(context, Intent(item.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            start(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Handled inside the launcher (side-panel menus).
        is LauncherMenuItem -> false
    }

    fun openAppInfo(context: Context, packageName: String): Boolean = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun uninstall(context: Context, packageName: String): Boolean = start(
        context,
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
