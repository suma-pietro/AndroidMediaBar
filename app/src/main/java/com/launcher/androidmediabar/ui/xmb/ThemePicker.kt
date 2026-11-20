package com.launcher.androidmediabar.ui.xmb

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.model.BackgroundType
import com.launcher.androidmediabar.model.BlurLevel
import com.launcher.androidmediabar.model.ThemeColor
import com.launcher.androidmediabar.model.ThemeSetting
import com.launcher.androidmediabar.model.WaveAnimation
import com.launcher.androidmediabar.model.XmbThemeSettings
import java.time.LocalDate

/* Entries of the "Theme customization" menu and of each of its value lists. */

internal val ThemeSetting.titleRes: Int
    get() = when (this) {
        ThemeSetting.COLOR -> R.string.theme_color
        ThemeSetting.ANIMATION -> R.string.theme_animation
        ThemeSetting.BACKGROUND -> R.string.theme_background
        ThemeSetting.ICONS -> R.string.theme_icons
        ThemeSetting.BLUR -> R.string.theme_blur
        ThemeSetting.BRIGHTNESS -> R.string.theme_brightness
        ThemeSetting.DAY_NIGHT -> R.string.theme_day_night
    }

private val ThemeSetting.icon: ImageVector
    get() = when (this) {
        ThemeSetting.COLOR -> Icons.Rounded.Palette
        ThemeSetting.ANIMATION -> Icons.Rounded.Waves
        ThemeSetting.BACKGROUND -> Icons.Rounded.Wallpaper
        ThemeSetting.ICONS -> Icons.Rounded.Apps
        ThemeSetting.BLUR -> Icons.Rounded.BlurOn
        ThemeSetting.BRIGHTNESS -> Icons.Rounded.BrightnessMedium
        ThemeSetting.DAY_NIGHT -> Icons.Rounded.DarkMode
    }

internal fun themeRootEntries(s: XmbThemeSettings): List<MenuEntry> {
    // Installing a PS3 theme makes its wallpaper and icons selectable below.
    val import = MenuEntry(
        label = res(R.string.theme_import_ps3),
        icon = Icons.Rounded.SportsEsports,
        action = MenuAction.ImportPs3Theme,
    )
    val settings = ThemeSetting.entries
        // Icons can only be switched once a PS3 theme provides an alternative set.
        .filter { it != ThemeSetting.ICONS || s.hasPs3Theme }
        // Compose blur relies on RenderEffect, available from Android 12 only.
        .filter { it != ThemeSetting.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        .map { setting ->
            MenuEntry(
                label = res(setting.titleRes),
                value = themeValue(setting, s),
                icon = setting.icon,
                action = MenuAction.Open(MenuPage.ThemeValues(setting)),
            )
        }
    return listOf(import) + settings
}

private fun themeValue(setting: ThemeSetting, s: XmbThemeSettings): UiText = when (setting) {
    ThemeSetting.COLOR -> res(s.color.labelRes)
    ThemeSetting.ANIMATION -> res(s.animation.labelRes)
    ThemeSetting.BACKGROUND -> backgroundLabel(s)
    ThemeSetting.ICONS -> iconsLabel(s, s.ps3Icons)
    ThemeSetting.BLUR -> res(s.blur.labelRes)
    ThemeSetting.BRIGHTNESS -> res(R.string.theme_brightness_format, s.brightness)
    ThemeSetting.DAY_NIGHT -> res(if (s.dayNight) R.string.theme_on else R.string.theme_off)
}

private fun backgroundLabel(s: XmbThemeSettings): UiText = when (s.background) {
    BackgroundType.THEME -> res(R.string.theme_bg_theme)
    BackgroundType.IMAGE -> res(R.string.theme_bg_image)
    BackgroundType.PS3 -> res(R.string.theme_bg_ps3, s.ps3Theme.orEmpty())
}

private fun iconsLabel(s: XmbThemeSettings, ps3: Boolean): UiText =
    if (ps3) res(R.string.theme_icons_ps3, s.ps3Theme.orEmpty()) else res(R.string.theme_icons_default)

internal fun themeValueEntries(setting: ThemeSetting, s: XmbThemeSettings): List<MenuEntry> {
    fun choice(label: UiText, result: XmbThemeSettings, swatch: Color? = null) =
        MenuEntry(label = label, action = MenuAction.ApplyTheme(result), swatch = swatch, checked = result == s)

    return when (setting) {
        ThemeSetting.COLOR -> {
            val month = ThemeColor.Monthly[LocalDate.now().monthValue - 1]
            ThemeColor.entries.map { c -> choice(res(c.labelRes), s.copy(color = c), swatch = c.color ?: month.color) }
        }
        ThemeSetting.ANIMATION -> WaveAnimation.entries.map { choice(res(it.labelRes), s.copy(animation = it)) }
        ThemeSetting.BACKGROUND -> buildList {
            fun option(type: BackgroundType) = s.copy(background = type).let { choice(backgroundLabel(it), it) }
            add(option(BackgroundType.THEME))
            if (s.hasPs3Theme) add(option(BackgroundType.PS3))
            if (s.hasImage) add(option(BackgroundType.IMAGE))
            add(
                MenuEntry(
                    label = res(if (s.hasImage) R.string.theme_bg_pick_other else R.string.theme_bg_pick),
                    action = MenuAction.PickImage,
                    icon = Icons.Rounded.Wallpaper,
                ),
            )
        }
        ThemeSetting.ICONS -> listOf(
            choice(iconsLabel(s, ps3 = true), s.copy(ps3Icons = true)),
            choice(iconsLabel(s, ps3 = false), s.copy(ps3Icons = false)),
        )
        ThemeSetting.BLUR -> BlurLevel.entries.map {
            val label = if (s.usesImage) {
                res(it.labelRes)
            } else {
                res(R.string.theme_only_image, res(it.labelRes))
            }
            choice(label, s.copy(blur = it))
        }
        ThemeSetting.BRIGHTNESS -> XmbThemeSettings.BrightnessSteps.map {
            choice(res(R.string.theme_brightness_format, it), s.copy(brightness = it))
        }
        ThemeSetting.DAY_NIGHT -> listOf(
            choice(res(R.string.theme_on), s.copy(dayNight = true)),
            choice(res(R.string.theme_off), s.copy(dayNight = false)),
        )
    }
}
