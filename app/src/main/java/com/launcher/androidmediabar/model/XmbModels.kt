package com.launcher.androidmediabar.model

import android.content.ComponentName
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector

/** Horizontal axis of the cross media bar. */
enum class CategoryType {
    SETTINGS, PHOTO, MUSIC, VIDEO, GAMES, APPS;

    /** Settings must always stay reachable, so it can be neither hidden nor used for apps. */
    val isUserColumn: Boolean get() = this != SETTINGS
}

/** User choices about which columns are shown and where each app goes. */
@Immutable
data class LayoutSettings(
    val hidden: Set<CategoryType> = emptySet(),
    /** packageName -> column chosen by the user; absent = automatic. */
    val assignments: Map<String, CategoryType> = emptyMap(),
) {
    fun columnOf(app: AppItem): CategoryType = assignments[app.packageName] ?: app.category
}

@Immutable
data class XmbCategory(
    val type: CategoryType,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val items: List<XmbItem>,
)

/** Vertical axis of the cross media bar. */
@Immutable
sealed interface XmbItem {
    val key: String
}

@Immutable
data class AppItem(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val isLeanback: Boolean,
    val isSystemApp: Boolean,
    val versionName: String?,
    val category: CategoryType,
    val icon: ImageBitmap?,
    val banner: ImageBitmap?,
) : XmbItem {
    override val key: String get() = "app:$packageName"
}

@Immutable
data class SettingItem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val action: String,
) : XmbItem {
    override val key: String get() = "setting:$id"
}

/** The launcher's own settings menus, opened in the side panel. */
enum class LauncherMenu { THEME, LAYOUT }

/** Launcher settings entry: opens an in-place menu instead of an external screen. */
@Immutable
data class LauncherMenuItem(
    val menu: LauncherMenu,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
) : XmbItem {
    override val key: String get() = "menu:${menu.name}"
}
