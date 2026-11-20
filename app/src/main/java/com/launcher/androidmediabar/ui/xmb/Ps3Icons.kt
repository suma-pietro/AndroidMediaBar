package com.launcher.androidmediabar.ui.xmb

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.LauncherMenu
import com.launcher.androidmediabar.model.LauncherMenuItem
import com.launcher.androidmediabar.model.SettingItem
import com.launcher.androidmediabar.model.XmbItem

/**
 * Every PS3 theme names its icons with the same fixed system IDs (`icon_game`, `icon_music`,
 * `icon_display_setting`…): this maps the launcher's columns and entries onto them.
 * Each list is in order of preference; a theme may omit some icons.
 */
internal object Ps3IconMap {

    fun category(type: CategoryType): List<String> = when (type) {
        CategoryType.SETTINGS -> listOf("icon_setting")
        CategoryType.PHOTO -> listOf("icon_photo")
        CategoryType.MUSIC -> listOf("icon_music")
        CategoryType.VIDEO -> listOf("icon_video", "icon_tv")
        CategoryType.GAMES -> listOf("icon_game")
        // The PS3 "Network" column hosted the store and the browser: the closest to Android apps.
        CategoryType.APPS -> listOf("icon_network", "icon_tv")
    }

    fun item(item: XmbItem): List<String> = when (item) {
        is LauncherMenuItem -> when (item.menu) {
            LauncherMenu.THEME -> listOf("icon_theme_setting", "icon_setting_item")
            LauncherMenu.LAYOUT -> listOf("icon_system_setting", "icon_setting_item")
        }
        is SettingItem -> settings[item.id].orEmpty() + "icon_setting_item"
        // Only for apps that have neither a banner nor an icon.
        is AppItem -> listOf("icon_default_h", "icon_default_v")
    }

    private val settings: Map<String, List<String>> = mapOf(
        "all" to listOf("icon_setting_item"),
        "network" to listOf("icon_network_setting"),
        "bluetooth" to listOf("icon_accessory"),
        "display" to listOf("icon_display_setting"),
        "sound" to listOf("icon_sound_setting"),
        "date" to listOf("icon_datetime_setting"),
        "apps" to listOf("icon_gamedata", "icon_game_setting"),
        "storage" to listOf("icon_usb", "icon_savedata"),
        "home" to listOf("icon_user", "icon_system_setting"),
    )

    /** Everything the launcher may draw: the only icons worth decoding. */
    val allIds: Set<String> = buildSet {
        CategoryType.entries.forEach { addAll(category(it)) }
        settings.values.forEach(::addAll)
        addAll(listOf("icon_theme_setting", "icon_system_setting", "icon_setting_item", "icon_default_h", "icon_default_v"))
    }
}

/** Icons of the installed PS3 theme, by system ID; empty when the built-in icons are used. */
@Immutable
data class ThemeIcons(val byId: Map<String, ImageBitmap> = emptyMap()) {
    fun pick(ids: List<String>): ImageBitmap? = ids.firstNotNullOfOrNull { byId[it] }
}
