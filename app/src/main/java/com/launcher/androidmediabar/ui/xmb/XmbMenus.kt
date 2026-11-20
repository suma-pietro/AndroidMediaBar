package com.launcher.androidmediabar.ui.xmb

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.data.XmbCatalog
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.LauncherMenu
import com.launcher.androidmediabar.model.LauncherMenuItem
import com.launcher.androidmediabar.model.LayoutSettings
import com.launcher.androidmediabar.model.SettingItem
import com.launcher.androidmediabar.model.ThemeSetting
import com.launcher.androidmediabar.model.XmbItem
import com.launcher.androidmediabar.model.XmbThemeSettings

/** Text that is resolved only at composition time (string resources, possibly nested). */
@Immutable
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

internal fun res(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        val resolved = args.map { if (it is UiText) it.resolve() else it }
        stringResource(id, *resolved.toTypedArray())
    }
}

/** One level of the side-panel menu. */
@Immutable
sealed interface MenuPage {
    data class ItemOptions(val item: XmbItem) : MenuPage
    data object Theme : MenuPage
    data class ThemeValues(val setting: ThemeSetting) : MenuPage
    data object Layout : MenuPage
    data object Columns : MenuPage
    data object Apps : MenuPage
    data class AppColumn(val packageName: String, val label: String) : MenuPage
}

/** What happens when OK is pressed on an entry. */
@Immutable
sealed interface MenuAction {
    data class Open(val page: MenuPage) : MenuAction
    data class ApplyTheme(val settings: XmbThemeSettings) : MenuAction
    data object PickImage : MenuAction
    data object ImportPs3Theme : MenuAction
    data class ToggleColumn(val type: CategoryType) : MenuAction
    data class AssignApp(val packageName: String, val type: CategoryType?) : MenuAction
    data class Launch(val item: XmbItem) : MenuAction
    data class AppInfo(val packageName: String) : MenuAction
    data class Uninstall(val packageName: String) : MenuAction
}

@Immutable
data class MenuEntry(
    val label: UiText,
    val action: MenuAction,
    val value: UiText? = null,
    val icon: ImageVector? = null,
    val swatch: Color? = null,
    val checked: Boolean = false,
    /** On/off state for entries that toggle in place (drawn as a switch). */
    val toggle: Boolean? = null,
    val image: ImageBitmap? = null,
)

/** Everything the menus need to describe the current state. */
@Immutable
data class MenuContext(
    val theme: XmbThemeSettings,
    val layout: LayoutSettings,
    val apps: List<AppItem>,
)

@Immutable
data class PanelPage(val page: MenuPage, val selected: Int)

/** The side panel: a stack of pages, Back pops one level. */
@Immutable
data class PanelState(val stack: List<PanelPage>) {
    val top: PanelPage get() = stack.last()

    fun push(page: MenuPage, ctx: MenuContext) = PanelState(stack + PanelPage(page, initialSelection(page, ctx)))

    fun pop(): PanelState? = if (stack.size > 1) PanelState(stack.dropLast(1)) else null

    fun withSelected(index: Int, size: Int) =
        PanelState(stack.dropLast(1) + top.copy(selected = index.coerceIn(0, (size - 1).coerceAtLeast(0))))

    companion object {
        fun open(page: MenuPage, ctx: MenuContext) = PanelState(listOf(PanelPage(page, initialSelection(page, ctx))))
    }
}

/** Single-choice pages start on the current value. */
private fun initialSelection(page: MenuPage, ctx: MenuContext): Int = when (page) {
    is MenuPage.ThemeValues, is MenuPage.AppColumn -> menuEntries(page, ctx).indexOfFirst { it.checked }.coerceAtLeast(0)
    else -> 0
}

/** Launcher menu items open their own page; everything else opens its options. */
fun pageFor(item: XmbItem): MenuPage = when (item) {
    is LauncherMenuItem -> when (item.menu) {
        LauncherMenu.THEME -> MenuPage.Theme
        LauncherMenu.LAYOUT -> MenuPage.Layout
    }
    else -> MenuPage.ItemOptions(item)
}

/** Live preview of the theme value currently highlighted, if any. */
fun PanelState.themePreview(ctx: MenuContext): XmbThemeSettings? {
    val page = top.page
    if (page !is MenuPage.ThemeValues) return null
    val entry = menuEntries(page, ctx).getOrNull(top.selected) ?: return null
    return (entry.action as? MenuAction.ApplyTheme)?.settings
}

fun pageTitle(page: MenuPage): UiText = when (page) {
    is MenuPage.ItemOptions -> res(R.string.options_title)
    MenuPage.Theme, MenuPage.Layout -> res(R.string.category_settings)
    is MenuPage.ThemeValues -> res(R.string.menu_theme)
    MenuPage.Columns, MenuPage.Apps -> res(R.string.menu_layout)
    is MenuPage.AppColumn -> res(R.string.menu_move_to)
}

fun pageHeader(page: MenuPage): UiText = when (page) {
    is MenuPage.ItemOptions -> itemTitle(page.item)
    MenuPage.Theme -> res(R.string.menu_theme)
    is MenuPage.ThemeValues -> res(page.setting.titleRes)
    MenuPage.Layout -> res(R.string.menu_layout)
    MenuPage.Columns -> res(R.string.menu_columns)
    MenuPage.Apps -> res(R.string.menu_assign_apps)
    is MenuPage.AppColumn -> UiText.Raw(page.label)
}

fun itemTitle(item: XmbItem): UiText = when (item) {
    is AppItem -> UiText.Raw(item.label)
    is SettingItem -> res(item.titleRes)
    is LauncherMenuItem -> res(item.titleRes)
}

fun menuEntries(page: MenuPage, ctx: MenuContext): List<MenuEntry> = when (page) {
    is MenuPage.ItemOptions -> itemOptions(page.item)
    MenuPage.Theme -> themeRootEntries(ctx.theme)
    is MenuPage.ThemeValues -> themeValueEntries(page.setting, ctx.theme)
    MenuPage.Layout -> listOf(
        MenuEntry(
            label = res(R.string.menu_columns),
            value = if (ctx.layout.hidden.isEmpty()) {
                res(R.string.columns_all_visible)
            } else {
                res(R.string.columns_hidden_count, ctx.layout.hidden.size)
            },
            icon = Icons.Rounded.ViewColumn,
            action = MenuAction.Open(MenuPage.Columns),
        ),
        MenuEntry(
            label = res(R.string.menu_assign_apps),
            value = res(R.string.apps_count, ctx.apps.size),
            icon = Icons.Rounded.Widgets,
            action = MenuAction.Open(MenuPage.Apps),
        ),
    )
    MenuPage.Columns -> XmbCatalog.userColumns.map { type ->
        val visible = type !in ctx.layout.hidden
        val column = XmbCatalog.column(type)
        MenuEntry(
            label = res(column.titleRes),
            icon = column.icon,
            toggle = visible,
            action = MenuAction.ToggleColumn(type),
        )
    }
    MenuPage.Apps -> ctx.apps.map { app ->
        MenuEntry(
            label = UiText.Raw(app.label),
            image = app.icon ?: app.banner,
            value = columnLabel(ctx.layout.columnOf(app), ctx.layout),
            action = MenuAction.Open(MenuPage.AppColumn(app.packageName, app.label)),
        )
    }
    is MenuPage.AppColumn -> {
        val app = ctx.apps.firstOrNull { it.packageName == page.packageName }
        val assigned = ctx.layout.assignments[page.packageName]
        buildList {
            if (app != null) {
                add(
                    MenuEntry(
                        label = res(R.string.column_auto, columnLabel(app.category, ctx.layout)),
                        checked = assigned == null,
                        action = MenuAction.AssignApp(page.packageName, null),
                    ),
                )
            }
            XmbCatalog.userColumns.forEach { type ->
                add(
                    MenuEntry(
                        label = columnLabel(type, ctx.layout),
                        icon = XmbCatalog.column(type).icon,
                        checked = assigned == type,
                        action = MenuAction.AssignApp(page.packageName, type),
                    ),
                )
            }
        }
    }
}

private fun columnLabel(type: CategoryType, layout: LayoutSettings): UiText {
    val name = res(XmbCatalog.column(type).titleRes)
    return if (type in layout.hidden) res(R.string.column_hidden_suffix, name) else name
}

private fun itemOptions(item: XmbItem): List<MenuEntry> = when (item) {
    is AppItem -> buildList {
        add(MenuEntry(res(R.string.option_open), MenuAction.Launch(item), icon = Icons.Rounded.PlayArrow))
        add(
            MenuEntry(
                res(R.string.option_move),
                MenuAction.Open(MenuPage.AppColumn(item.packageName, item.label)),
                icon = Icons.AutoMirrored.Rounded.DriveFileMove,
            ),
        )
        add(MenuEntry(res(R.string.option_info), MenuAction.AppInfo(item.packageName), icon = Icons.Rounded.Info))
        if (!item.isSystemApp) {
            add(MenuEntry(res(R.string.option_uninstall), MenuAction.Uninstall(item.packageName), icon = Icons.Rounded.Delete))
        }
    }
    is SettingItem, is LauncherMenuItem ->
        listOf(MenuEntry(res(R.string.option_open), MenuAction.Launch(item), icon = Icons.Rounded.PlayArrow))
}
