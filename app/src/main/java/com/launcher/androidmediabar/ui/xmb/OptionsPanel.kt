package com.launcher.androidmediabar.ui.xmb

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.androidmediabar.R
import com.launcher.androidmediabar.model.XmbItem

@Composable
fun xmbItemTitle(item: XmbItem): String = itemTitle(item).resolve()

private val PanelBackground = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.12f to Color.Black.copy(alpha = 0.82f),
    1f to Color.Black.copy(alpha = 0.9f),
)

/** Side panel hosting the item options and the launcher's settings menus. */
@Composable
fun OptionsPanel(state: PanelState?, context: MenuContext, modifier: Modifier = Modifier) {
    // Keep showing the last content while the exit animation runs.
    val retained = remember { mutableStateOf(state) }
    SideEffect { if (state != null) retained.value = state }
    val shown = state ?: retained.value

    AnimatedVisibility(
        visible = state != null,
        modifier = modifier,
        enter = slideInHorizontally { it / 2 } + fadeIn(),
        exit = slideOutHorizontally { it / 2 } + fadeOut(),
    ) {
        if (shown == null) return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxHeight()
                .width(XmbDimens.PanelWidth)
                .background(PanelBackground),
        ) {
            // Thin edge separating the panel from the bar behind it.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent),
                        ),
                    ),
            )
            // Slide between menu levels: deeper pages come from the right.
            AnimatedContent(
                targetState = shown.stack.size,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                modifier = Modifier.fillMaxSize(),
                label = "menuLevel",
            ) { depth ->
                val page = shown.stack.getOrNull(depth - 1) ?: shown.top
                PageContent(page, context)
            }
            KeyHints(
                isRoot = shown.stack.size == 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = XmbDimens.OverscanHorizontal, bottom = XmbDimens.OverscanVertical),
            )
        }
    }
}

@Composable
private fun PageContent(page: PanelPage, context: MenuContext) {
    val entries = menuEntries(page.page, context)
    val selected = page.selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    // Entries with a current value use two lines (label + value) so neither gets truncated.
    val twoLine = entries.any { it.value != null }
    val rowHeight = if (twoLine) ROW_HEIGHT_TWO_LINES else ROW_HEIGHT
    val visibleRows = if (twoLine) XmbDimens.PanelVisibleRows - 1 else XmbDimens.PanelVisibleRows
    val scrollTarget = (selected - visibleRows / 2).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = scrollTarget)
    LaunchedEffect(scrollTarget) {
        // Keep the highlighted row near the middle of the visible window.
        listState.animateScrollToItem(scrollTarget)
    }
    // Same leading column for every row of a page, so labels always line up.
    val leading = when {
        entries.any { it.image != null } -> LeadingSlot.Image
        entries.any { it.icon != null || it.swatch != null } -> LeadingSlot.Icon
        else -> LeadingSlot.None
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = XmbDimens.OverscanHorizontal),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = pageTitle(page.page).resolve(),
                style = XmbTextStyles.PanelTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entries.size > visibleRows) {
                BasicText("${selected + 1}/${entries.size}", style = XmbTextStyles.PanelTitle)
            }
        }
        BasicText(
            text = pageHeader(page.page).resolve(),
            style = XmbTextStyles.ItemTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.heightIn(max = rowHeight * visibleRows),
        ) {
            itemsIndexed(entries) { index, entry ->
                EntryRow(entry, isSelected = index == selected, leading = leading, height = rowHeight)
            }
        }
        // Room for the key hints at the bottom.
        Spacer(Modifier.height(40.dp))
    }
}

private val ROW_HEIGHT = 48.dp
private val ROW_HEIGHT_TWO_LINES = 62.dp

private enum class LeadingSlot(val width: Dp) { None(0.dp), Icon(22.dp), Image(40.dp) }

@Composable
private fun EntryRow(entry: MenuEntry, isSelected: Boolean, leading: LeadingSlot, height: Dp) {
    val contentAlpha = if (isSelected) 1f else 0.6f
    val tint = Color.White.copy(alpha = contentAlpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(vertical = 2.dp)
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != LeadingSlot.None) {
            Box(Modifier.width(leading.width), contentAlignment = Alignment.Center) {
                when {
                    entry.image != null -> Image(
                        bitmap = entry.image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alpha = if (isSelected) 1f else 0.75f,
                        modifier = Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    entry.swatch != null -> Box(
                        Modifier
                            .size(20.dp)
                            .background(entry.swatch, CircleShape)
                            .border(1.5.dp, tint, CircleShape),
                    )
                    entry.icon != null -> RowIcon(entry.icon, tint)
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                text = entry.label.resolve(),
                style = XmbTextStyles.PanelOption.copy(color = tint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.value?.let {
                BasicText(
                    text = it.resolve(),
                    style = XmbTextStyles.PanelOption.copy(
                        color = Color.White.copy(alpha = contentAlpha * 0.7f),
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            entry.toggle != null -> {
                Spacer(Modifier.width(10.dp))
                ToggleSwitch(on = entry.toggle, alpha = contentAlpha)
            }
            entry.checked -> {
                Spacer(Modifier.width(8.dp))
                RowIcon(Icons.Rounded.Check, tint)
            }
            entry.action is MenuAction.Open -> {
                Spacer(Modifier.width(4.dp))
                RowIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tint)
            }
        }
    }
}

@Composable
private fun ToggleSwitch(on: Boolean, alpha: Float) {
    val track by animateColorAsState(
        if (on) Color.White.copy(alpha = 0.85f * alpha) else Color.White.copy(alpha = 0.2f * alpha),
        label = "toggleTrack",
    )
    val thumbOffset by animateDpAsState(if (on) 16.dp else 0.dp, label = "toggleThumb")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .background(track, CircleShape)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .background(if (on) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = alpha), CircleShape),
        )
    }
}

@Composable
private fun KeyHints(isRoot: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        KeyChip("OK")
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.hint_select), style = XmbTextStyles.PanelTitle)
        Spacer(Modifier.width(24.dp))
        KeyChip("◀")
        Spacer(Modifier.width(8.dp))
        BasicText(
            stringResource(if (isRoot) R.string.hint_close else R.string.hint_back),
            style = XmbTextStyles.PanelTitle,
        )
    }
}

@Composable
private fun KeyChip(label: String) {
    Box(
        Modifier
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = XmbTextStyles.PanelTitle.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}
