package com.launcher.androidmediabar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.model.BackgroundType
import com.launcher.androidmediabar.model.Wallpaper
import com.launcher.androidmediabar.model.XmbThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Persists the launcher look and stores the custom wallpaper inside the app's private storage. */
class ThemeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val wallpaperFile = File(appContext.filesDir, WALLPAPER_FILE)
    private val ps3WallpaperFile = File(appContext.filesDir, PS3_WALLPAPER_FILE)
    private val ps3IconsDir = File(appContext.filesDir, PS3_ICONS_DIR)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<XmbThemeSettings> = _settings.asStateFlow()

    fun update(settings: XmbThemeSettings) {
        _settings.value = settings
        prefs.edit {
            putString(KEY_COLOR, settings.color.name)
            putString(KEY_ANIMATION, settings.animation.name)
            putString(KEY_BACKGROUND, settings.background.name)
            putString(KEY_BLUR, settings.blur.name)
            putInt(KEY_BRIGHTNESS, settings.brightness)
            putBoolean(KEY_DAY_NIGHT, settings.dayNight)
            putLong(KEY_IMAGE_VERSION, settings.imageVersion)
            putString(KEY_PS3_THEME, settings.ps3Theme)
            putString(KEY_PS3_AUTHOR, settings.ps3Author)
            putBoolean(KEY_PS3_ICONS, settings.ps3Icons)
            putLong(KEY_PS3_VERSION, settings.ps3Version)
        }
    }

    /**
     * Copies the picked image into private storage, downscaled to the TV resolution.
     * Copying means we never depend on a (possibly temporary) content URI grant.
     */
    suspend fun importWallpaper(uri: Uri): Boolean {
        if (!saveWallpaper(ImageDecoder.createSource(appContext.contentResolver, uri), wallpaperFile)) return false
        update(_settings.value.copy(background = BackgroundType.IMAGE, imageVersion = System.currentTimeMillis()))
        return true
    }

    /** Installs the picked PS3 theme (.p3t): its wallpaper and its icons. */
    suspend fun importPs3Theme(uri: Uri): Ps3ThemeImporter.Result = withContext(Dispatchers.IO) {
        val result = runCatching { Ps3ThemeImporter.import(appContext.contentResolver, uri) }
            .getOrElse { Ps3ThemeImporter.Result.Failure(R.string.ps3_err_bad_p3t) }
        if (result !is Ps3ThemeImporter.Result.Success) return@withContext result
        val theme = result.theme

        if (!saveWallpaper(ImageDecoder.createSource(theme.background), ps3WallpaperFile)) {
            return@withContext Ps3ThemeImporter.Result.Failure(R.string.ps3_err_no_images)
        }
        saveIcons(theme.icons)
        val now = System.currentTimeMillis()
        update(
            _settings.value.copy(
                background = BackgroundType.PS3,
                ps3Theme = theme.name,
                ps3Author = theme.author,
                ps3Icons = true,
                ps3Version = now,
            ),
        )
        result
    }

    /** Decodes the stored icons of the installed PS3 theme whose IDs are in [ids]. */
    suspend fun loadPs3Icons(ids: Set<String>): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id ->
            val file = File(ps3IconsDir, "$id.png")
            if (!file.exists()) return@mapNotNull null
            BitmapFactory.decodeFile(file.path)?.let { id to it.asImageBitmap() }
        }.toMap()
    }

    private suspend fun saveWallpaper(source: ImageDecoder.Source, target: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                // Scale so the image still covers the whole screen, never upscale.
                val scale = max(TARGET_WIDTH.toFloat() / w, TARGET_HEIGHT.toFloat() / h).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val tmp = File(appContext.filesDir, "${target.name}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            check(tmp.renameTo(target))
        }.isSuccess
    }

    /** Replaces the stored icon set: one PNG per system icon ID, decoding one icon at a time. */
    private fun saveIcons(icons: Map<String, ByteArray>) {
        val tmp = File(appContext.filesDir, "$PS3_ICONS_DIR.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        for ((id, gim) in icons) {
            if (!Ps3ThemeImporter.ICON_ID.matches(id)) continue
            val image = GimDecoder.decode(gim) ?: continue
            val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            File(tmp, "$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ps3IconsDir.deleteRecursively()
        check(tmp.renameTo(ps3IconsDir))
    }

    /** Decodes the picture used by [type] (custom image or PS3 theme wallpaper). */
    suspend fun loadWallpaper(type: BackgroundType): Wallpaper? = withContext(Dispatchers.IO) {
        val file = when (type) {
            BackgroundType.THEME -> return@withContext null
            BackgroundType.IMAGE -> wallpaperFile
            BackgroundType.PS3 -> ps3WallpaperFile
        }
        if (!file.exists()) return@withContext null
        runCatching {
            Wallpaper(type, ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)).asImageBitmap())
        }.getOrNull()
    }

    private fun read(): XmbThemeSettings {
        val defaults = XmbThemeSettings()
        val hasImage = wallpaperFile.exists()
        val hasPs3 = ps3IconsDir.isDirectory && ps3WallpaperFile.exists()
        val background = enumOr(prefs.getString(KEY_BACKGROUND, null), defaults.background)
        return XmbThemeSettings(
            color = enumOr(prefs.getString(KEY_COLOR, null), defaults.color),
            animation = enumOr(prefs.getString(KEY_ANIMATION, null), defaults.animation),
            background = when (background) {
                BackgroundType.IMAGE -> if (hasImage) background else BackgroundType.THEME
                BackgroundType.PS3 -> if (hasPs3) background else BackgroundType.THEME
                BackgroundType.THEME -> background
            },
            blur = enumOr(prefs.getString(KEY_BLUR, null), defaults.blur),
            brightness = prefs.getInt(KEY_BRIGHTNESS, defaults.brightness).coerceIn(10, 100),
            dayNight = prefs.getBoolean(KEY_DAY_NIGHT, defaults.dayNight),
            imageVersion = if (hasImage) prefs.getLong(KEY_IMAGE_VERSION, 1L).coerceAtLeast(1L) else 0L,
            ps3Theme = if (hasPs3) prefs.getString(KEY_PS3_THEME, null) else null,
            ps3Author = if (hasPs3) prefs.getString(KEY_PS3_AUTHOR, null) else null,
            ps3Icons = prefs.getBoolean(KEY_PS3_ICONS, defaults.ps3Icons),
            ps3Version = if (hasPs3) prefs.getLong(KEY_PS3_VERSION, 1L).coerceAtLeast(1L) else 0L,
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val PREFS_NAME = "xmb_theme"
        const val WALLPAPER_FILE = "wallpaper.jpg"
        const val PS3_ICONS_DIR = "ps3_icons"
        const val PS3_WALLPAPER_FILE = "ps3_wallpaper.jpg"
        const val TARGET_WIDTH = 1920
        const val TARGET_HEIGHT = 1080


        const val KEY_COLOR = "color"
        const val KEY_ANIMATION = "animation"
        const val KEY_BACKGROUND = "background"
        const val KEY_BLUR = "blur"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_DAY_NIGHT = "day_night"
        const val KEY_IMAGE_VERSION = "image_version"
        const val KEY_PS3_THEME = "ps3_theme"
        const val KEY_PS3_AUTHOR = "ps3_author"
        const val KEY_PS3_ICONS = "ps3_icons"
        const val KEY_PS3_VERSION = "ps3_version"
    }
}
