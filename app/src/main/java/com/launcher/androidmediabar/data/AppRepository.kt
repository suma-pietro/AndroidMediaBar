package com.launcher.androidmediabar.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Reads the launchable apps installed on the device and observes install/uninstall events. */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) {
        // TV (leanback) entry points win over phone-style ones for the same package.
        val entries = LinkedHashMap<String, Pair<ResolveInfo, Boolean>>()
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach {
            entries.putIfAbsent(it.activityInfo.packageName, it to true)
        }
        query(Intent.CATEGORY_LAUNCHER).forEach {
            entries.putIfAbsent(it.activityInfo.packageName, it to false)
        }
        entries.values
            .filter { (info, _) -> info.activityInfo.packageName != appContext.packageName }
            .mapNotNull { (info, leanback) -> runCatching { info.toAppItem(leanback) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    /** Emits every time a package is installed, removed or updated. */
    fun packageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Package broadcasts are protected and can only be sent by the system.
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    private fun query(category: String): List<ResolveInfo> =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(category),
            PackageManager.ResolveInfoFlags.of(0L),
        )

    private fun ResolveInfo.toAppItem(leanback: Boolean): AppItem {
        val activity = activityInfo
        val appInfo = activity.applicationInfo
        val packageName = activity.packageName
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)).versionName
        }.getOrNull()
        val banner = activity.loadBanner(packageManager)
            ?.toBitmap(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)
            ?.asImageBitmap()
        val icon = if (banner == null) {
            loadIcon(packageManager)?.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)?.asImageBitmap()
        } else {
            null
        }
        return AppItem(
            packageName = packageName,
            label = loadLabel(packageManager).toString(),
            component = ComponentName(packageName, activity.name),
            isLeanback = leanback,
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            versionName = versionName,
            category = categorize(appInfo),
            icon = icon,
            banner = banner,
        )
    }

    private fun categorize(info: ApplicationInfo): CategoryType {
        when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> return CategoryType.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> return CategoryType.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> return CategoryType.VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> return CategoryType.PHOTO
        }
        @Suppress("DEPRECATION")
        if ((info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return CategoryType.GAMES

        // Many TV apps don't declare a category: fall back to package-name hints.
        val pkg = info.packageName.lowercase()
        return when {
            MUSIC_HINTS.any { it in pkg } -> CategoryType.MUSIC
            VIDEO_HINTS.any { it in pkg } -> CategoryType.VIDEO
            PHOTO_HINTS.any { it in pkg } -> CategoryType.PHOTO
            else -> CategoryType.APPS
        }
    }

    private companion object {
        const val BANNER_WIDTH_PX = 320
        const val BANNER_HEIGHT_PX = 180
        const val ICON_SIZE_PX = 192

        val MUSIC_HINTS = listOf(
            "music", "spotify", "deezer", "tidal", "soundcloud", "audible", "podcast", "radio",
        )
        val VIDEO_HINTS = listOf(
            "youtube", "netflix", "avod", "primevideo", "disney", "hbomax", "plex", "kodi", "xbmc",
            "vlc", "dazn", "raiplay", "mediaset", "twitch", "paramount", "skyshowtime", "nowtv",
            "jellyfin", "emby", "crunchyroll", "appletv", "mubi", "pluto", "tubi",
        )
        val PHOTO_HINTS = listOf("photos", "gallery", "camera")
    }
}
