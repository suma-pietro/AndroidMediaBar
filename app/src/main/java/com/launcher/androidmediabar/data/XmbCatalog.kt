package com.launcher.androidmediabar.data

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.LauncherMenu
import com.launcher.androidmediabar.model.LauncherMenuItem
import com.launcher.androidmediabar.model.LayoutSettings
import com.launcher.androidmediabar.model.SettingItem
import com.launcher.androidmediabar.model.XmbCategory

/** Static definition of the cross media bar: categories and system shortcuts. */
object XmbCatalog {

    class Column(@param:StringRes val titleRes: Int, val icon: ImageVector)

    private val columns: Map<CategoryType, Column> = linkedMapOf(
        CategoryType.SETTINGS to Column(R.string.category_settings, Icons.Rounded.HomeRepairService),
        CategoryType.PHOTO to Column(R.string.category_photo, Icons.Rounded.PhotoLibrary),
        CategoryType.MUSIC to Column(R.string.category_music, Icons.Rounded.MusicNote),
        CategoryType.VIDEO to Column(R.string.category_video, Icons.Rounded.Movie),
        CategoryType.GAMES to Column(R.string.category_games, Icons.Rounded.SportsEsports),
        CategoryType.APPS to Column(R.string.category_apps, Icons.Rounded.Widgets),
    )

    fun column(type: CategoryType): Column = columns.getValue(type)

    /** Columns that can be hidden or receive apps, in on-screen order. */
    val userColumns: List<CategoryType> = columns.keys.filter { it.isUserColumn }

    /** The launcher's own settings, listed first in the Settings category. */
    val launcherMenuItems: List<LauncherMenuItem> = listOf(
        LauncherMenuItem(LauncherMenu.THEME, R.string.menu_theme, R.string.menu_theme_desc, Icons.Rounded.Palette),
        LauncherMenuItem(LauncherMenu.LAYOUT, R.string.menu_layout, R.string.menu_layout_desc, Icons.Rounded.ViewColumn),
    )

    val settingsItems: List<SettingItem> = listOf(
        SettingItem("all", R.string.setting_all, R.string.setting_all_desc, Icons.Rounded.Settings, Settings.ACTION_SETTINGS),
        SettingItem("network", R.string.setting_network, R.string.setting_network_desc, Icons.Rounded.Wifi, Settings.ACTION_WIFI_SETTINGS),
        SettingItem("bluetooth", R.string.setting_bluetooth, R.string.setting_bluetooth_desc, Icons.Rounded.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingItem("display", R.string.setting_display, R.string.setting_display_desc, Icons.Rounded.Tv, Settings.ACTION_DISPLAY_SETTINGS),
        SettingItem("sound", R.string.setting_sound, R.string.setting_sound_desc, Icons.AutoMirrored.Rounded.VolumeUp, Settings.ACTION_SOUND_SETTINGS),
        SettingItem("date", R.string.setting_date, R.string.setting_date_desc, Icons.Rounded.Schedule, Settings.ACTION_DATE_SETTINGS),
        SettingItem("apps", R.string.setting_apps, R.string.setting_apps_desc, Icons.Rounded.Apps, Settings.ACTION_APPLICATION_SETTINGS),
        SettingItem("storage", R.string.setting_storage, R.string.setting_storage_desc, Icons.Rounded.Storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingItem("home", R.string.setting_home, R.string.setting_home_desc, Icons.Rounded.Home, Settings.ACTION_HOME_SETTINGS),
    )

    fun buildCategories(apps: List<AppItem>, layout: LayoutSettings = LayoutSettings()): List<XmbCategory> {
        val byType = apps.groupBy(layout::columnOf)
        return columns
            .filterKeys { !it.isUserColumn || it !in layout.hidden }
            .map { (type, column) ->
                val items = if (type == CategoryType.SETTINGS) launcherMenuItems + settingsItems else byType[type].orEmpty()
                XmbCategory(type, column.titleRes, column.icon, items)
            }
    }
}
