package com.launcher.androidmediabar

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.androidmediabar.data.AppRepository
import com.launcher.androidmediabar.data.ColumnLayoutRepository
import com.launcher.androidmediabar.data.Ps3ThemeImporter
import com.launcher.androidmediabar.data.ThemeRepository
import com.launcher.androidmediabar.data.XmbCatalog
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.BackgroundType
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.LayoutSettings
import com.launcher.androidmediabar.model.XmbCategory
import com.launcher.androidmediabar.model.Wallpaper
import com.launcher.androidmediabar.model.XmbThemeSettings
import com.launcher.androidmediabar.ui.xmb.Ps3IconMap
import com.launcher.androidmediabar.ui.xmb.ThemeIcons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LauncherUiState(
    val isLoaded: Boolean,
    val categories: List<XmbCategory>,
    /** Every installed app, including those in hidden columns (for the settings menus). */
    val apps: List<AppItem> = emptyList(),
    val layout: LayoutSettings = LayoutSettings(),
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val themeRepository = ThemeRepository(application)
    private val layoutRepository = ColumnLayoutRepository(application)
    private val apps = MutableStateFlow<List<AppItem>?>(null)
    private val _wallpaperErrors = Channel<Unit>(Channel.CONFLATED)
    private val _ps3ImportResults = Channel<Ps3ThemeImporter.Result>(Channel.CONFLATED)

    val uiState: StateFlow<LauncherUiState> = combine(apps, layoutRepository.settings) { list, layout ->
        LauncherUiState(
            isLoaded = list != null,
            categories = XmbCatalog.buildCategories(list.orEmpty(), layout),
            apps = list.orEmpty(),
            layout = layout,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherUiState(
            isLoaded = false,
            categories = XmbCatalog.buildCategories(emptyList(), layoutRepository.settings.value),
            layout = layoutRepository.settings.value,
        ),
    )

    val theme: StateFlow<XmbThemeSettings> = themeRepository.settings

    /** The background picture (custom or PS3 theme), decoded only while it is actually in use. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaper: StateFlow<Wallpaper?> = themeRepository.settings
        .map { s ->
            when (s.background) {
                BackgroundType.THEME -> null
                BackgroundType.IMAGE -> s.background to s.imageVersion
                BackgroundType.PS3 -> s.background to s.ps3Version
            }
        }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { themeRepository.loadWallpaper(it.first) } }
        // Eagerly: returning Home from an app must not flash the theme color while re-decoding.
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Icons of the installed PS3 theme, decoded only while enabled. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val themeIcons: StateFlow<ThemeIcons> = themeRepository.settings
        .map { if (it.ps3Icons) it.ps3Version else 0L }
        .distinctUntilChanged()
        .mapLatest { version ->
            if (version != 0L) ThemeIcons(themeRepository.loadPs3Icons(Ps3IconMap.allIds)) else ThemeIcons()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeIcons())

    /** Emits when an imported image could not be decoded. */
    val wallpaperErrors: Flow<Unit> = _wallpaperErrors.receiveAsFlow()

    /** Outcome of each PS3 theme import, to be shown to the user. */
    val ps3ImportResults: Flow<Ps3ThemeImporter.Result> = _ps3ImportResults.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.packageChanges()
                .onStart { emit(Unit) }
                .collectLatest { apps.value = repository.loadApps() }
        }
    }

    fun updateTheme(settings: XmbThemeSettings) = themeRepository.update(settings)

    fun toggleColumn(type: CategoryType) = layoutRepository.toggleColumn(type)

    fun assignApp(packageName: String, type: CategoryType?) = layoutRepository.assignApp(packageName, type)

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            if (!themeRepository.importWallpaper(uri)) _wallpaperErrors.trySend(Unit)
        }
    }

    fun importPs3Theme(uri: Uri) {
        viewModelScope.launch { _ps3ImportResults.trySend(themeRepository.importPs3Theme(uri)) }
    }

}
