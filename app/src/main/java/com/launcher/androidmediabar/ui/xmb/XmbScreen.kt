package com.launcher.androidmediabar.ui.xmb

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.SoundEffectConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launcher.androidmediabar.LauncherUiState
import com.launcher.androidmediabar.LauncherViewModel
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.data.LauncherIntents
import com.launcher.androidmediabar.data.Ps3ThemeImporter
import com.launcher.androidmediabar.data.queryActivitiesCompat
import com.launcher.androidmediabar.data.XmbCatalog
import com.launcher.androidmediabar.model.AppItem
import com.launcher.androidmediabar.model.CategoryType
import com.launcher.androidmediabar.model.SettingItem
import com.launcher.androidmediabar.model.LauncherMenuItem
import com.launcher.androidmediabar.model.XmbCategory
import com.launcher.androidmediabar.model.XmbItem
import com.launcher.androidmediabar.model.Wallpaper
import com.launcher.androidmediabar.model.XmbThemeSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun XmbLauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val themeIcons by viewModel.themeIcons.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.wallpaperErrors.collect {
            Toast.makeText(context, R.string.theme_image_error, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.ps3ImportResults.collect { result ->
            val message = when (result) {
                is Ps3ThemeImporter.Result.Success -> {
                    val author = result.theme.author
                    if (author != null) {
                        resources.getString(R.string.ps3_import_ok_author, result.theme.name, author)
                    } else {
                        resources.getString(R.string.ps3_import_ok, result.theme.name)
                    }
                }
                is Ps3ThemeImporter.Result.Failure -> resources.getString(result.messageRes)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val onPicked: (Uri?) -> Unit = { uri -> if (uri != null) viewModel.importWallpaper(uri) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onPicked)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onPicked)
    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onPicked)

    fun pickImage() {
        // Many TVs lack the photo picker or DocumentsUI (often replaced by a stub that only says
        // "not supported"): try each real handler in turn, ending with any installed file manager.
        val mediaRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val attempts = listOf(
            ActivityResultContracts.PickVisualMedia().createIntent(context, mediaRequest) to
                { photoPicker.launch(mediaRequest) },
            ActivityResultContracts.OpenDocument().createIntent(context, arrayOf("image/*")) to
                { documentPicker.launch(arrayOf("image/*")) },
            ActivityResultContracts.GetContent().createIntent(context, "image/*") to
                { contentPicker.launch("image/*") },
        )
        val launched = attempts.any { (intent, launch) ->
            context.hasRealHandler(intent) && try {
                launch()
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!launched) Toast.makeText(context, R.string.theme_picker_unavailable, Toast.LENGTH_LONG).show()
    }

    // .p3t has no MIME type: pick any file, the importer checks the P3TF magic.
    val onThemeFile: (Uri?) -> Unit = { uri -> if (uri != null) viewModel.importPs3Theme(uri) }
    val themeDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onThemeFile)
    val themeContentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onThemeFile)

    fun pickPs3Theme() {
        val attempts = listOf(
            ActivityResultContracts.OpenDocument().createIntent(context, arrayOf("*/*")) to
                { themeDocumentPicker.launch(arrayOf("*/*")) },
            ActivityResultContracts.GetContent().createIntent(context, "*/*") to
                { themeContentPicker.launch("*/*") },
        )
        val launched = attempts.any { (intent, launch) ->
            context.hasRealHandler(intent) && try {
                launch()
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!launched) Toast.makeText(context, R.string.theme_picker_unavailable, Toast.LENGTH_LONG).show()
    }

    XmbLauncher(
        state = uiState,
        theme = theme,
        wallpaper = wallpaper,
        icons = themeIcons,
        onThemeChange = viewModel::updateTheme,
        onPickImage = ::pickImage,
        onImportPs3Theme = ::pickPs3Theme,
        onToggleColumn = viewModel::toggleColumn,
        onAssignApp = viewModel::assignApp,
    )
}

/** Tracks OK/Enter presses so a long press opens the options instead of launching. */
private class ConfirmKeyTracker {
    var isDown = false
    var longPressHandled = false
}

@Composable
fun XmbLauncher(
    state: LauncherUiState,
    modifier: Modifier = Modifier,
    theme: XmbThemeSettings = XmbThemeSettings(),
    wallpaper: Wallpaper? = null,
    icons: ThemeIcons = ThemeIcons(),
    onThemeChange: (XmbThemeSettings) -> Unit = {},
    onPickImage: () -> Unit = {},
    onImportPs3Theme: () -> Unit = {},
    onToggleColumn: (CategoryType) -> Unit = {},
    onAssignApp: (packageName: String, column: CategoryType?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val view = LocalView.current
    val categories = state.categories

    // Tracked by type so hiding/showing columns never moves the selection to another column.
    var selectedType by rememberSaveable { mutableStateOf(CategoryType.APPS) }
    val selectedCategory = categories.indexOfType(selectedType)
    var initialized by rememberSaveable { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<CategoryType, Int>() }
    var panel by remember { mutableStateOf<PanelState?>(null) }
    val confirmKey = remember { ConfirmKeyTracker() }
    val focusRequester = remember { FocusRequester() }
    val now by rememberCurrentMinute()

    // Once apps are loaded, start on Video if there is something to watch.
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded && !initialized) {
            initialized = true
            if (categories.any { it.type == CategoryType.VIDEO && it.items.isNotEmpty() }) {
                selectedType = CategoryType.VIDEO
            }
        }
    }
    val menuContext = remember(theme, state.layout, state.apps) { MenuContext(theme, state.layout, state.apps) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun selectedIndexOf(category: XmbCategory): Int =
        (selectedItems[category.type] ?: 0).coerceIn(0, maxOf(category.items.lastIndex, 0))

    fun currentItem(): XmbItem? {
        val category = categories[selectedCategory]
        return category.items.getOrNull(selectedIndexOf(category))
    }

    fun moveCategory(delta: Int) {
        val next = (selectedCategory + delta).coerceIn(0, categories.lastIndex)
        if (next != selectedCategory) {
            selectedType = categories[next].type
            view.playSoundEffect(if (delta < 0) SoundEffectConstants.NAVIGATION_LEFT else SoundEffectConstants.NAVIGATION_RIGHT)
        }
    }

    fun moveItem(delta: Int) {
        val category = categories[selectedCategory]
        if (category.items.isEmpty()) return
        val current = selectedIndexOf(category)
        val next = (current + delta).coerceIn(0, category.items.lastIndex)
        if (next != current) {
            selectedItems[category.type] = next
            view.playSoundEffect(if (delta < 0) SoundEffectConstants.NAVIGATION_UP else SoundEffectConstants.NAVIGATION_DOWN)
        }
    }

    fun activate(item: XmbItem) {
        view.playSoundEffect(SoundEffectConstants.CLICK)
        if (item is LauncherMenuItem) {
            panel = PanelState.open(pageFor(item), menuContext)
            return
        }
        if (!LauncherIntents.launch(context, item)) {
            Toast.makeText(context, R.string.launch_error, Toast.LENGTH_SHORT).show()
        }
    }

    fun openOptions() {
        val item = currentItem() ?: return
        panel = PanelState.open(pageFor(item), menuContext)
    }

    fun confirmPanel(current: PanelState) {
        val entry = menuEntries(current.top.page, menuContext).getOrNull(current.top.selected) ?: return
        val action = entry.action
        if (action !is MenuAction.Launch) view.playSoundEffect(SoundEffectConstants.CLICK)
        when (action) {
            is MenuAction.Open -> panel = current.push(action.page, menuContext)
            is MenuAction.ApplyTheme -> {
                if (action.settings != theme) onThemeChange(action.settings)
                panel = current.pop()
            }
            MenuAction.PickImage -> {
                panel = null
                onPickImage()
            }
            MenuAction.ImportPs3Theme -> {
                panel = null
                onImportPs3Theme()
            }
            is MenuAction.ToggleColumn -> onToggleColumn(action.type)
            is MenuAction.AssignApp -> {
                onAssignApp(action.packageName, action.type)
                // From an app's own options there is nothing left to do: close everything.
                panel = if (current.stack.first().page is MenuPage.ItemOptions) null else current.pop()
            }
            is MenuAction.Launch -> {
                panel = null
                activate(action.item)
            }
            is MenuAction.AppInfo -> {
                panel = null
                LauncherIntents.openAppInfo(context, action.packageName)
            }
            is MenuAction.Uninstall -> {
                panel = null
                LauncherIntents.uninstall(context, action.packageName)
            }
        }
    }

    fun movePanel(current: PanelState, delta: Int) {
        val size = menuEntries(current.top.page, menuContext).size
        val next = current.withSelected(current.top.selected + delta, size)
        if (next.top.selected != current.top.selected) {
            panel = next
            view.playSoundEffect(if (delta < 0) SoundEffectConstants.NAVIGATION_UP else SoundEffectConstants.NAVIGATION_DOWN)
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val current = panel
        if (event.key.isConfirm()) {
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        confirmKey.isDown = true
                        confirmKey.longPressHandled = false
                    } else if (!confirmKey.longPressHandled && current == null) {
                        // First repeat == long press.
                        confirmKey.longPressHandled = true
                        openOptions()
                    }
                }
                KeyEventType.KeyUp -> {
                    if (confirmKey.isDown && !confirmKey.longPressHandled) {
                        if (current != null) confirmPanel(current) else currentItem()?.let(::activate)
                    }
                    confirmKey.isDown = false
                    confirmKey.longPressHandled = false
                }
            }
            return true
        }
        if (event.type != KeyEventType.KeyDown) return false

        if (current != null) {
            when (event.key) {
                Key.DirectionUp -> movePanel(current, -1)
                Key.DirectionDown -> movePanel(current, 1)
                Key.DirectionRight -> {
                    val entry = menuEntries(current.top.page, menuContext).getOrNull(current.top.selected)
                    if (entry?.action is MenuAction.Open) confirmPanel(current)
                }
                // Going back without confirming discards the live preview.
                Key.DirectionLeft -> panel = current.pop()
                Key.Menu, Key.Info, Key.ButtonY -> panel = null
                Key.Back -> return false // handled by BackHandler
            }
            return true
        }
        return when (event.key) {
            Key.DirectionLeft -> { moveCategory(-1); true }
            Key.DirectionRight -> { moveCategory(1); true }
            Key.DirectionUp -> { moveItem(-1); true }
            Key.DirectionDown -> { moveItem(1); true }
            Key.Menu, Key.Info, Key.ButtonY -> { openOptions(); true }
            else -> false
        }
    }

    // A home screen must never close on Back; the later handler has priority.
    BackHandler { }
    BackHandler(enabled = panel != null) { panel = panel?.pop() }

    // While a theme value list is open, the highlighted value is previewed live.
    val effectiveTheme = panel?.themePreview(menuContext) ?: theme

    // With the side panel open the bar steps back so the two never fight for attention:
    // other columns and the clock (both under the panel) disappear, the rest is dimmed.
    // Theme value lists dim less, so text legibility over the new background can be judged.
    val panelShown = animateFloatAsState(if (panel != null) 1f else 0f, label = "panelShown")
    val contentDim = animateFloatAsState(
        targetValue = when {
            panel == null -> 0f
            panel?.top?.page is MenuPage.ThemeValues -> 0.3f
            else -> 1f
        },
        label = "contentDim",
    )

    val categoryPosition = animateFloatAsState(
        targetValue = selectedCategory.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categoryPosition",
    )
    val visibleCategories by remember {
        derivedStateOf(structuralEqualityPolicy()) {
            val p = categoryPosition.value
            setOf(floor(p).toInt(), ceil(p).toInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent(::handleKey)
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        XmbBackground(
            settings = effectiveTheme,
            wallpaper = wallpaper,
            baseColor = backgroundColorFor(now, effectiveTheme),
            modifier = Modifier.fillMaxSize(),
        )

        XmbClock(
            now = now,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = XmbDimens.OverscanVertical, end = XmbDimens.OverscanHorizontal)
                .graphicsLayer { alpha = 1f - panelShown.value },
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - 0.65f * contentDim.value
                    translationX = -(XmbDimens.PanelContentShift * panelShown.value).toPx()
                }
                .drawWithContent {
                    // Long titles must never run underneath the side panel.
                    val shown = panelShown.value
                    val edge = size.width - (XmbDimens.PanelWidth - XmbDimens.PanelContentShift).toPx() * shown
                    clipRect(right = edge) { this@drawWithContent.drawContent() }
                },
        ) {
            categories.forEachIndexed { index, category ->
                if (index in visibleCategories) {
                    key(category.type) {
                        CategoryItemList(
                            category = category,
                            categoryIndex = index,
                            categoryPosition = categoryPosition,
                            selectedIndex = selectedIndexOf(category),
                            icons = icons,
                        )
                    }
                }
            }

            CategoryBar(categories, categoryPosition, panelShown, icons)
        }

        OptionsPanel(state = panel, context = menuContext, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** PS3 theme icons include transparent margins, so they are drawn larger than the vector icons. */
private const val PS3_ICON_SCALE = 1.35f

/** Android TV ships stub activities for unsupported system intents; they must not count as a picker. */
private const val TV_STUBS_PACKAGE = "com.android.tv.frameworkpackagestubs"

private fun Context.hasRealHandler(intent: Intent): Boolean =
    packageManager
        .queryActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .any { it.activityInfo.packageName != TV_STUBS_PACKAGE }

private fun List<XmbCategory>.indexOfType(type: CategoryType): Int =
    indexOfFirst { it.type == type }.coerceAtLeast(0)

private fun Key.isConfirm(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter || this == Key.ButtonA

@Composable
private fun CategoryBar(
    categories: List<XmbCategory>,
    categoryPosition: State<Float>,
    panelShown: State<Float>,
    icons: ThemeIcons,
) {
    categories.forEachIndexed { index, category ->
        key(category.type) {
            Column(
                modifier = Modifier
                    .width(XmbDimens.CategoryColumnWidth)
                    .graphicsLayer {
                        val d = index - categoryPosition.value
                        translationX = (XmbDimens.AnchorX + XmbDimens.CategorySpacing * d - XmbDimens.CategoryColumnWidth / 2).toPx()
                        translationY = (XmbDimens.CategoryY - XmbDimens.CategoryIconSize / 2).toPx()
                        // Only the current column stays while a side panel covers the right half.
                        alpha = lerpF(1f - panelShown.value, 1f, selectedness(d))
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val iconModifier = Modifier
                    .size(XmbDimens.CategoryIconSize)
                    .graphicsLayer {
                        val s = selectedness(index - categoryPosition.value)
                        val scale = lerpF(0.8f, 1.25f, s)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerpF(0.55f, 1f, s)
                    }
                val themeIcon = icons.pick(Ps3IconMap.category(category.type))
                if (themeIcon != null) {
                    // PS3 icons carry their own margins: draw them a bit larger than the vectors.
                    Image(
                        bitmap = themeIcon,
                        contentDescription = null,
                        modifier = iconModifier.requiredSize(XmbDimens.CategoryIconSize * PS3_ICON_SCALE),
                    )
                } else {
                    Image(
                        imageVector = category.icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = iconModifier,
                    )
                }
                Spacer(Modifier.height(16.dp))
                BasicText(
                    text = stringResource(category.titleRes),
                    style = XmbTextStyles.Category,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = selectedness(index - categoryPosition.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryItemList(
    category: XmbCategory,
    categoryIndex: Int,
    categoryPosition: State<Float>,
    selectedIndex: Int,
    icons: ThemeIcons,
) {
    val itemPosition = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "itemPosition",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val d = categoryIndex - categoryPosition.value
                alpha = (1f - abs(d) * 1.6f).coerceIn(0f, 1f)
                translationX = (XmbDimens.ListShift * d).toPx()
            },
    ) {
        if (category.items.isEmpty()) {
            BasicText(
                text = stringResource(R.string.empty_category),
                style = XmbTextStyles.ItemSubtitle,
                modifier = Modifier.graphicsLayer {
                    translationX = (XmbDimens.AnchorX - XmbDimens.ThumbWidth / 2).toPx()
                    translationY = (XmbDimens.SelectedItemY - 12.dp).toPx()
                },
            )
            return@Box
        }
        val first = (selectedIndex - XmbDimens.ItemsWindowAbove).coerceAtLeast(0)
        val last = (selectedIndex + XmbDimens.ItemsWindowBelow).coerceAtMost(category.items.lastIndex)
        for (i in first..last) {
            val item = category.items[i]
            key(item.key) {
                XmbItemRow(item = item, index = i, itemPosition = itemPosition, icons = icons)
            }
        }
    }
}

@Composable
private fun XmbItemRow(item: XmbItem, index: Int, itemPosition: State<Float>, icons: ThemeIcons) {
    val title = xmbItemTitle(item)
    val subtitle = when (item) {
        is AppItem -> item.versionName?.let { stringResource(R.string.version_format, it) }
        is SettingItem -> stringResource(item.descriptionRes)
        is LauncherMenuItem -> stringResource(item.descriptionRes)
    }
    Row(
        modifier = Modifier
            .height(XmbDimens.ThumbHeight)
            .graphicsLayer {
                val rel = index - itemPosition.value
                val scale = lerpF(XmbDimens.UnselectedItemScale, 1f, selectedness(rel))
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationX = (XmbDimens.AnchorX - XmbDimens.ThumbWidth * scale / 2).toPx()
                translationY = (itemCenterY(rel) - XmbDimens.ThumbHeight / 2).toPx()
                alpha = if (rel < 0f) {
                    lerpF(1f, 0.55f, (-rel).coerceAtMost(1f))
                } else {
                    lerpF(1f, 0.85f, rel.coerceAtMost(1f))
                }
                // The halo is larger than the row: an offscreen layer (the default when alpha < 1)
                // would clip it to a rectangle while scrolling. Apply alpha per draw instead.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemThumbnail(item = item, icons = icons, selectedness = { selectedness(index - itemPosition.value) })
        Spacer(Modifier.width(24.dp))
        Column(Modifier.widthIn(max = 560.dp)) {
            BasicText(text = title, style = XmbTextStyles.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = XmbTextStyles.ItemSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = selectedness(index - itemPosition.value) },
                )
            }
        }
    }
}

@Composable
private fun ItemThumbnail(item: XmbItem, icons: ThemeIcons, selectedness: () -> Float) {
    Box(
        modifier = Modifier
            .size(XmbDimens.ThumbWidth, XmbDimens.ThumbHeight)
            .drawBehind {
                // Soft halo behind the selected item.
                val s = selectedness()
                if (s > 0f) {
                    val radius = size.width * 0.7f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.35f * s), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Apps keep their own banner/icon: the theme only replaces the generic artwork.
        val themeIcon = if (item is AppItem && (item.banner != null || item.icon != null)) {
            null
        } else {
            icons.pick(Ps3IconMap.item(item))
        }
        if (themeIcon != null) {
            Image(bitmap = themeIcon, contentDescription = null, modifier = Modifier.size(XmbDimens.ThumbHeight))
            return@Box
        }
        when (item) {
            is AppItem -> {
                val banner = item.banner
                val icon = item.icon
                when {
                    banner != null -> Image(
                        bitmap = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(XmbDimens.ThumbWidth, XmbDimens.ThumbHeight)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    icon != null -> Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(64.dp))
                    else -> Image(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            is SettingItem -> Image(
                imageVector = item.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(52.dp),
            )
            is LauncherMenuItem -> Image(
                imageVector = item.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun XmbLauncherPreview() {
    XmbLauncher(LauncherUiState(isLoaded = true, categories = XmbCatalog.buildCategories(emptyList())))
}
