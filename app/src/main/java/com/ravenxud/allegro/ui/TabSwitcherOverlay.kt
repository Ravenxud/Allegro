package com.ravenxud.allegro.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserState
import com.ravenxud.allegro.browser.TabState
import com.ravenxud.allegro.ui.components.MiuixSheet
import com.ravenxud.allegro.ui.components.SheetRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 标签切换器：Stub → Grid 缩放 + 背景模糊。
 *
 * 动画由 `updateTransition` 驱动：首帧拿到尺寸、被打断时自动反向，
 * 不依赖 Animatable 的手动 snap，避免"硬切"。
 */
@Composable
fun TabSwitcherOverlay(
    visible: Boolean,
    stub: Bitmap?,
    progress: Float,
    state: BrowserState,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onReopen: () -> Unit,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
    onCloseOthers: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    simplify: Boolean = false,
) {
    var lockedIndex by remember { mutableStateOf(0) }
    var menuTab by remember { mutableStateOf<TabState?>(null) }
    var dismissed by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            dismissed = false
            lockedIndex = state.tabs.indexOfFirst { it.id == state.activeTabId }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(visible, progress) {
        if (!visible && !dismissed && progress <= 0.001f) {
            dismissed = true
            onDismissed()
        }
    }

    if (!visible && progress <= 0.001f) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val start = Rect(0f, 0f, screenWidth, screenHeight)
        val target = gridCardRect(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
            index = lockedIndex.coerceIn(0, state.tabs.lastIndex.coerceAtLeast(0)),
            density = density.density,
            topInset = statusTop,
        )
        val p = progress.coerceIn(0f, 1f)
        val padPx = 12f * density.density
        val gapPx = 8f * density.density
        val cardWidthPx = (screenWidth - padPx * 2 - gapPx) / 2f
        val cardHeightPx = cardWidthPx / 0.62f + 44f * density.density

        // 1) 背景：截图铺满并模糊，随进度压暗
        if (stub != null && screenWidth > 0f) {
            Image(
                bitmap = stub.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - 0.25f * p }
                    .blur(24.dp),
            )
        }

        // 2) 遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.12f * p *
                            (1f - (dragOffset / screenHeight.coerceAtLeast(1f)).coerceIn(0f, 0.7f))
                    )
                )
                .clickable(enabled = p > 0.5f, onClick = onDismiss),
        )

        // 3) 网格：从 0.82 放大到 1
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            val atTop = gridState.firstVisibleItemIndex == 0 &&
                                gridState.firstVisibleItemScrollOffset == 0
                            if (atTop && amount > 0) {
                                change.consume()
                                dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                            }
                        },
                        onDragEnd = {
                            if (dragOffset > with(density) { 96.dp.toPx() }) onDismiss()
                            else scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                        },
                        onDragCancel = {
                            scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                        },
                    )
                }
                .graphicsLayer {
                    alpha = p
                    val scale = if (simplify) 1f else (0.82f + 0.18f * p)
                    scaleX = scale
                    scaleY = scale
                    translationY = dragOffset
                },
        ) {
            TabGrid(
                state = state,
                gridState = gridState,
                cardWidthPx = cardWidthPx,
                cardHeightPx = cardHeightPx,
                onMove = onMove,
                onSelect = onSelect,
                onClose = onClose,
                onNew = onNew,
                onReopen = onReopen,
                onDismiss = onDismiss,
                onLongPress = { menuTab = it },
            )
        }

        // 4) 页面缩略：截图 → 卡片矩形
        if (p < 0.999f && screenWidth > 0f) {
            val rect = lerpRect(start, target, p)
            val scaleX = rect.width / start.width.coerceAtLeast(1f)
            val scaleY = rect.height / start.height.coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = rect.left
                        translationY = rect.top
                        transformOrigin = TransformOrigin(0f, 0f)
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                    }
                    .size(
                        with(density) { start.width.toDp() },
                        with(density) { start.height.toDp() },
                    )
                    .background(MiuixTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (stub != null) {
                    Image(
                        bitmap = stub.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = state.activeTab?.title?.take(1).orEmpty().ifBlank { "·" },
                        fontSize = 56.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // 5) 长按菜单
        MiuixSheet(
            visible = menuTab != null,
            onDismiss = { menuTab = null },
            title = menuTab?.title,
        ) {
            val tab = menuTab
            if (tab != null) {
                SheetRow(MiuixIcons.Close, stringResource(R.string.tabs_close_others)) {
                    onCloseOthers(tab.id); menuTab = null
                }
                SheetRow(MiuixIcons.Copy, stringResource(R.string.tabs_copy_link)) {
                    onCopyLink(tab.url); menuTab = null
                }
                SheetRow(MiuixIcons.Delete, stringResource(R.string.tabs_close_tab)) {
                    onClose(tab.id); menuTab = null
                }
            }
        }
    }
}

private fun gridCardRect(
    width: Int,
    height: Int,
    index: Int,
    density: Float,
    topInset: Float = 0f,
): Rect {
    if (width <= 0) return Rect.Zero
    val pad = 12f * density
    val spacing = 8f * density
    val header = (56f + 12f) * density
    val textArea = 44f * density
    val cardWidth = (width - pad * 2 - spacing) / 2f
    val cardHeight = cardWidth / 0.62f + textArea
    val row = index / 2
    val col = index % 2
    val left = pad + col * (cardWidth + spacing)
    val top = topInset + header + row * (cardHeight + spacing)
    return Rect(left, top, left + cardWidth, top + cardHeight)
}

private fun lerpRect(start: Rect, end: Rect, t: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * t,
    top = start.top + (end.top - start.top) * t,
    right = start.right + (end.right - start.right) * t,
    bottom = start.bottom + (end.bottom - start.bottom) * t,
)

@Composable
private fun TabGrid(
    state: BrowserState,
    gridState: LazyGridState,
    cardWidthPx: Float,
    cardHeightPx: Float,
    onMove: (Int, Int) -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onReopen: () -> Unit,
    onDismiss: () -> Unit,
    onLongPress: (TabState) -> Unit,
) {
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tabs_title, state.tabs.size), fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(MiuixIcons.Close, stringResource(R.string.cd_close))
                }
            }

            if (state.tabs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.tabs_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.tabs, key = { _, it -> it.id }) { index, item ->
                        TabCard(
                            tab = item,
                            active = item.id == state.activeTabId,
                            dragging = dragId == item.id,
                            dragOffset = if (dragId == item.id) dragOffset else Offset.Zero,
                            onSelect = { onSelect(item.id) },
                            onClose = { onClose(item.id) },
                            onLongPress = { onLongPress(item) },
                            onDragStart = {
                                dragId = item.id
                                dragOffset = Offset.Zero
                            },
                            onDrag = { delta ->
                                dragOffset += delta
                                val colShift = (dragOffset.x / (cardWidthPx + spacingPx)).roundToInt()
                                val rowShift = (dragOffset.y / (cardHeightPx + spacingPx)).roundToInt()
                                val target = (index + colShift + rowShift * 2)
                                    .coerceIn(0, state.tabs.lastIndex)
                                if (target != index) {
                                    onMove(index, target)
                                    dragOffset = Offset.Zero
                                }
                            },
                            onDragEnd = {
                                dragId = null
                                dragOffset = Offset.Zero
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onNew, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tabs_new))
                }
                Button(onClick = onReopen, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tabs_reopen))
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabState,
    active: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var appeared by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissWidth = with(density) { 120.dp.toPx() }

    LaunchedEffect(Unit) {
        delay(40)
        appeared = true
    }

    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                alpha = if (appeared) 1f else 0f
                translationX = dragOffset.x
                translationY = dragOffset.y
                val s = if (dragging) 1.04f else 1f
                scaleX = s
                scaleY = s
            }
            .pointerInput(tab.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.16f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                MiuixIcons.Delete,
                contentDescription = stringResource(R.string.cd_close),
                modifier = Modifier.padding(end = 20.dp),
                tint = MiuixTheme.colorScheme.error,
            )
        }

        Card(
            onClick = onSelect,
            onLongPress = onLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offsetX = (offsetX + amount).coerceIn(-dismissWidth, 0f)
                        },
                        onDragEnd = {
                            if (offsetX < -dismissWidth * 0.6f) onClose()
                            else scope.launch { animate(offsetX, 0f) { value, _ -> offsetX = value } }
                        },
                        onDragCancel = {
                            scope.launch { animate(offsetX, 0f) { value, _ -> offsetX = value } }
                        },
                    )
                },
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.62f)
                        .clip(shape)
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .then(
                            if (active) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, shape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val preview = tab.preview
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = tab.title.take(1).ifBlank { "·" },
                            fontSize = 28.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val blank = tab.url == "about:blank"
                        Text(
                            text = if (blank) stringResource(R.string.tab_new_page)
                            else tab.title.ifBlank { tab.url },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            color = if (active) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            // 空白页不显示 about:blank 这种实现细节
                            text = if (blank) "" else tab.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(MiuixIcons.Close, stringResource(R.string.cd_close))
                    }
                }
            }
        }
    }
}
