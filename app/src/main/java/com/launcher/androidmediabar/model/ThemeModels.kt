package com.launcher.androidmediabar.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.launcher.androidmediabar.R

/** Background tint. [ORIGINAL] follows the current month like the classic cross media bar. */
enum class ThemeColor(@param:StringRes val labelRes: Int, val color: Color?) {
    ORIGINAL(R.string.theme_color_original, null),
    SILVER(R.string.theme_color_silver, Color(0xFF8A8F99)),
    GOLD(R.string.theme_color_gold, Color(0xFFB5A642)),
    GREEN(R.string.theme_color_green, Color(0xFF4F9A3A)),
    PINK(R.string.theme_color_pink, Color(0xFFD27FA8)),
    EMERALD(R.string.theme_color_emerald, Color(0xFF2E9E6B)),
    VIOLET(R.string.theme_color_violet, Color(0xFF7A5BB5)),
    CYAN(R.string.theme_color_cyan, Color(0xFF2BA3B8)),
    BLUE(R.string.theme_color_blue, Color(0xFF2D6FC4)),
    PURPLE(R.string.theme_color_purple, Color(0xFF8E44AD)),
    ORANGE(R.string.theme_color_orange, Color(0xFFC9822B)),
    BROWN(R.string.theme_color_brown, Color(0xFF8B5A2B)),
    RED(R.string.theme_color_red, Color(0xFFB03A3A)),
    BLACK(R.string.theme_color_black, Color(0xFF2A2A30)),
    ;

    companion object {
        /** January..December, used by [ORIGINAL]. */
        val Monthly = listOf(SILVER, GOLD, GREEN, PINK, EMERALD, VIOLET, CYAN, BLUE, PURPLE, ORANGE, BROWN, RED)
    }
}

enum class WaveAnimation(@param:StringRes val labelRes: Int, val animated: Boolean) {
    CLASSIC(R.string.theme_anim_classic, true),
    SPARKLE(R.string.theme_anim_sparkle, true),
    BOKEH(R.string.theme_anim_bokeh, true),
    STATIC(R.string.theme_anim_static, false),
    NONE(R.string.theme_anim_none, false),
}

/** [IMAGE] is the user's own picture, [PS3] the wallpaper of the installed PS3 theme. */
enum class BackgroundType { THEME, IMAGE, PS3 }

enum class BlurLevel(@param:StringRes val labelRes: Int, val radiusDp: Int) {
    NONE(R.string.theme_blur_none, 0),
    LIGHT(R.string.theme_blur_light, 6),
    MEDIUM(R.string.theme_blur_medium, 14),
    STRONG(R.string.theme_blur_strong, 28),
}

@Immutable
data class XmbThemeSettings(
    val color: ThemeColor = ThemeColor.ORIGINAL,
    val animation: WaveAnimation = WaveAnimation.CLASSIC,
    val background: BackgroundType = BackgroundType.THEME,
    val blur: BlurLevel = BlurLevel.NONE,
    /** 20..100: 100 means the background is shown as-is. */
    val brightness: Int = 100,
    val dayNight: Boolean = true,
    /** Bumped every time a new wallpaper is imported; 0 = none. */
    val imageVersion: Long = 0L,
    /** Name of the installed PS3 theme (icons, wallpaper), null when none. */
    val ps3Theme: String? = null,
    val ps3Author: String? = null,
    /** Draw the PS3 theme icons instead of the built-in ones. */
    val ps3Icons: Boolean = true,
    /** Bumped at every PS3 theme import; 0 = no theme installed. */
    val ps3Version: Long = 0L,
) {
    val hasImage: Boolean get() = imageVersion != 0L
    val hasPs3Theme: Boolean get() = ps3Version != 0L

    /** A picture (custom or PS3) is drawn instead of the animated theme color. */
    val usesImage: Boolean get() = background != BackgroundType.THEME

    companion object {
        val BrightnessSteps = listOf(100, 85, 70, 55, 40, 25)
    }
}

/** A decoded background picture and the [BackgroundType] it belongs to. */
@Immutable
class Wallpaper(val type: BackgroundType, val image: ImageBitmap)

/** Customization entries listed in the Settings category. */
enum class ThemeSetting { COLOR, ANIMATION, BACKGROUND, ICONS, BLUR, BRIGHTNESS, DAY_NIGHT }
