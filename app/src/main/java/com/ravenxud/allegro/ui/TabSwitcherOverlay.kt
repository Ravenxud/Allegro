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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.ravenxud.allegro.ui.components.pressScale
import com.ravenxud.allegro.ui.components.SheetRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 标签切换器：Stub → Grid 缩放 + 背景模糊。
 *
 * 动画由 `updateTransition` 驱动：首帧拿到尺寸、被打断时自动反向，
 * 不依赖 Animatable 的手动 snap，避免"硬切"。
 */
/** 标签页展示排序：只影响展示顺序，不改底层 tabs 顺序与 WebView 池索引。 */
enum class TabSort(val label: String) {
    RECENT("按打开时间"),
    TITLE("按标题"),
    SITE("按网站"),
}

private fun List<TabState>.bySort(sort: TabSort): List<TabState> = when (sort) {
    TabSort.RECENT -> this
    TabSort.TITLE -> sortedBy { it.title.lowercase() }
    TabSort.SITE -> sortedBy {
        runCatching { java.net.URI(it.url).host }.getOrNull().orEmpty().lowercase()
    }
}

@Composable
fun TabSwitcherOverlay(
    visible: Boolean,
    stub: Bitmap?,
    progressProvider: () -> Float,
    state: BrowserState,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onNewIncognito: () -> Unit,
    onReopen: () -> Unit,
    recentClosed: List<com.ravenxud.allegro.data.entity.ClosedTabEntity> = emptyList(),
    onRestoreClosed: (com.ravenxud.allegro.data.entity.ClosedTabEntity) -> Unit = {},
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
    onCloseOthers: (String) -> Unit,
    onMoveToGroup: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    simplify: Boolean = false,
) {
    var showRecent by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TabSort.RECENT) }
    var lockedIndex by remember { mutableStateOf(0) }
    var menuTab by remember { mutableStateOf<TabState?>(null) }
    var dismissed by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            runCatching { gridState.scrollToItem(0) }
            dismissed = false
            lockedIndex = state.tabs.indexOfFirst { it.id == state.activeTabId }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            // 只在"进度归零"这一件事上等待；读值发生在 snapshotFlow 里，不触发重组
            androidx.compose.runtime.snapshotFlow { progressProvider() }
                .firstOrNull { it <= 0.001f }
            if (!dismissed) {
                dismissed = true
                onDismissed()
            }
        }
    }

    // 关键：关闭且收尾完成后必须不渲染。
    // 否则全屏遮罩（可点击）会常驻视图树，把整个 App 的触摸都吞掉。
    if (!visible && dismissed) return

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
        val pp = progressProvider
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
                    .graphicsLayer {
                        // 背景图已预模糊，这里只做透明度/缩放，不再跑 RenderEffect
                        alpha = 1f - 0.12f * pp()
                        val bs = 0.99f + 0.01f * pp()
                        scaleX = bs
                        scaleY = bs
                    },
            )
        }

        // 2) 遮罩（alpha 在 drawBehind 里算：组合期读进度会导致每帧重组）
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = pp().coerceIn(0f, 1f)
                    val dragFade = 1f - (dragOffset / screenHeight.coerceAtLeast(1f))
                        .coerceIn(0f, 0.7f)
                    drawRect(Color.Black.copy(alpha = 0.32f * p * dragFade))
                }
                .clickable(enabled = visible, onClick = onDismiss),
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
                            else scope.launch { animate(dragOffset, 0f, animationSpec = com.ravenxud.allegro.ui.theme.Motion.Tabs.Snap) { v, _ -> dragOffset = v } }
                        },
                        onDragCancel = {
                            scope.launch { animate(dragOffset, 0f, animationSpec = com.ravenxud.allegro.ui.theme.Motion.Tabs.Snap) { v, _ -> dragOffset = v } }
                        },
                    )
                }
                .graphicsLayer {
                    val p = pp()
                    alpha = p
                    // 幅度从 0.82 收到 0.92：整体只做轻微"浮起"，细部交给逐卡错峰
                    val scale = if (simplify) 1f else (0.92f + 0.08f * p)
                    scaleX = scale
                    scaleY = scale
                    translationY = dragOffset
                },
        ) {
            TabGrid(
                progressProvider = pp,
                sort = sort,
                onOpenRecent = { showRecent = true },
                onCycleSort = {
                    sort = when (sort) {
                        TabSort.RECENT -> TabSort.TITLE
                        TabSort.TITLE -> TabSort.SITE
                        TabSort.SITE -> TabSort.RECENT
                    }
                },
                state = state,
                gridState = gridState,
                cardWidthPx = cardWidthPx,
                cardHeightPx = cardHeightPx,
                onMove = onMove,
                onSelect = onSelect,
                onClose = onClose,
                menuTabId = menuTab?.id,
                onMenuDismiss = { menuTab = null },
                onMenuCloseOthers = { menuTab?.let { onCloseOthers(it.id) }; menuTab = null },
                onMenuMoveToGroup = { menuTab?.let { onMoveToGroup(it.id) }; menuTab = null },
                onMenuCopyLink = { keepOpen -> menuTab?.let { onCopyLink(it.url) }; if (!keepOpen) menuTab = null },
                onMenuClose = { menuTab?.let { onClose(it.id) }; menuTab = null },
                onNew = onNew,
                onNewIncognito = onNewIncognito,
                onReopen = onReopen,
                onDismiss = onDismiss,
                onLongPress = { menuTab = it },
            )
        }

        // 4) 页面缩略：截图 → 卡片矩形（共享元素）
        // 注意：所有随进度变化的量都必须在 graphicsLayer 里算，
        // 在组合期算会让整棵树每帧重组（之前卡顿的根因）。
        if (stub != null && screenWidth > 0f) {
            Box(
                modifier = Modifier
                    .size(
                        with(density) { start.width.toDp() },
                        with(density) { start.height.toDp() },
                    )
                    .graphicsLayer {
                        val p = pp().coerceIn(0f, 1f)
                        val e = if (simplify) p else smoothstep(p)
                        val rect = lerpRect(start, target, e)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = rect.left
                        translationY = rect.top
                        scaleX = rect.width / start.width.coerceAtLeast(1f)
                        scaleY = rect.height / start.height.coerceAtLeast(1f)
                        alpha = 1f - ((e - 0.72f) / 0.28f).coerceIn(0f, 1f)
                        shadowElevation = 10.dp.toPx()
                        clip = true
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp * e)
                    },
            ) {
                Image(
                    bitmap = stub.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpRect(start: Rect, end: Rect, t: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * t,
    top = start.top + (end.top - start.top) * t,
    right = start.right + (end.right - start.right) * t,
    bottom = start.bottom + (end.bottom - start.bottom) * t,
)

@Composable
private fun TabGrid(
    progressProvider: () -> Float,
    sort: TabSort,
    onCycleSort: () -> Unit,
    state: BrowserState,
    gridState: LazyGridState,
    cardWidthPx: Float,
    cardHeightPx: Float,
    menuTabId: String?,
    onMenuDismiss: () -> Unit,
    onMenuCloseOthers: () -> Unit,
    onMenuMoveToGroup: () -> Unit,
    onMenuCopyLink: (Boolean) -> Unit,
    onMenuClose: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onNewIncognito: () -> Unit,
    onReopen: () -> Unit,
    onOpenRecent: () -> Unit = {},
    onDismiss: () -> Unit,
    onLongPress: (TabState) -> Unit,
) {
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

    // 必须透明：否则这张不透明面会把下面的"模糊页面背景"整块盖住（观感像白闪/遮罩）
    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tabs_title, state.tabs.size), fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                // 排序：点击循环 时间 → 标题 → 网站（非「按打开时间」时禁用拖拽重排）
                com.ravenxud.allegro.ui.components.PillButton(
                    label = "最近关闭",
                    onClick = onOpenRecent,
                    icon = MiuixIcons.Recent,
                )
                Spacer(Modifier.width(8.dp))
                com.ravenxud.allegro.ui.components.PillButton(
                    label = sort.label,
                    onClick = onCycleSort,
                    icon = MiuixIcons.Sort,
                )
                Spacer(Modifier.width(8.dp))
                com.ravenxud.allegro.ui.components.CircleIconButton(
                    icon = MiuixIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.cd_close),
                )
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
                    val shown = state.tabs.bySort(sort)
                    itemsIndexed(shown, key = { _, it -> it.id }) { index, item ->
                        TabCard(
                            // 20ms/张的错峰：在绘制层折算，组合期不读进度
                            cardIndex = index,
                            progressProvider = progressProvider,
                            menuOpen = menuTabId == item.id,
                            onMenuDismiss = onMenuDismiss,
                            onMenuCloseOthers = onMenuCloseOthers,
                            onMenuMoveToGroup = onMenuMoveToGroup,
                            onMenuCopyLink = onMenuCopyLink,
                            onMenuClose = onMenuClose,
                            tab = item,
                            active = item.id == state.activeTabId,
                            dragging = dragId == item.id,
                            dragOffset = if (dragId == item.id) dragOffset else Offset.Zero,
                            onSelect = { onSelect(item.id) },
                            onClose = { onClose(item.id) },
                            draggable = sort == TabSort.RECENT,
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

            // Luna 形态：左「+」圆钮 / 中间胶囊 / 右「✓」圆钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.ravenxud.allegro.ui.components.CircleIconButton(
                    icon = MiuixIcons.Add,
                    onClick = onNew,
                    contentDescription = stringResource(R.string.tabs_new),
                    size = 52.dp,
                )
                com.ravenxud.allegro.ui.components.PillButton(
                    label = stringResource(R.string.tabs_incognito),
                    onClick = onNewIncognito,
                )
                com.ravenxud.allegro.ui.components.PillButton(
                    label = stringResource(R.string.tabs_count, state.tabs.size),
                    onClick = onDismiss,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.Ok,
                        contentDescription = stringResource(R.string.action_done),
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

        }
    }
}

@Composable
private fun TabCard(
    modifier: Modifier = Modifier,
    cardIndex: Int,
    progressProvider: () -> Float,
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onMenuCloseOthers: () -> Unit,
    onMenuMoveToGroup: () -> Unit,
    onMenuCopyLink: (Boolean) -> Unit,
    onMenuClose: () -> Unit,
    tab: TabState,
    active: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    draggable: Boolean,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val shape = com.ravenxud.allegro.ui.theme.Shapes.Tile
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
        modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                // 单卡：0.9 → 1 浮起 + 淡入（错峰在这里折算）
                val e = ((progressProvider() - cardIndex * 0.045f) / 0.55f)
                    .coerceIn(0f, 1f)
                alpha = e
                translationX = dragOffset.x
                translationY = dragOffset.y + (1f - e) * 24f
                val s = if (dragging) 1.04f else (0.9f + 0.1f * e)
                scaleX = s
                scaleY = s
            }
            .pointerInput(tab.id, draggable) {
                if (!draggable) return@pointerInput
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
                .pressScale()
                .pressScale()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offsetX = (offsetX + amount).coerceIn(-dismissWidth, 0f)
                        },
                        onDragEnd = {
                            if (offsetX < -dismissWidth * 0.6f) onClose()
                            else scope.launch { animate(offsetX, 0f, animationSpec = com.ravenxud.allegro.ui.theme.Motion.Tabs.Snap) { v, _ -> offsetX = v } }
                        },
                        onDragCancel = {
                            scope.launch { animate(offsetX, 0f, animationSpec = com.ravenxud.allegro.ui.theme.Motion.Tabs.Snap) { v, _ -> offsetX = v } }
                        },
                    )
                },
        ) {
            // Luna 形态：预览铺满 + 标题条**叠在预览底部**（不是卡片下方）
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
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // 底部标题条：40dp、半透明面、上边一条发丝线
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.86f))
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val blank = tab.url == "about:blank"
                    tab.favicon?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (blank) stringResource(R.string.tab_new_page)
                        else tab.title.ifBlank { tab.url },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = if (active) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            MiuixIcons.Close,
                            stringResource(R.string.cd_close),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // 长按菜单：锚定在本卡片旁边（不再是贴底 sheet）
        com.ravenxud.allegro.ui.components.AnchoredMenu(
            expanded = menuOpen,
            onDismiss = onMenuDismiss,
        ) {
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = MiuixIcons.Close,
                label = stringResource(R.string.tabs_close_others),
                onClick = onMenuCloseOthers,
            )
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = MiuixIcons.Folder,
                label = stringResource(R.string.move_to_group),
                onClick = onMenuMoveToGroup,
            )
            // C3：复制后图标短暂变 ✓（菜单不关闭）
            var copied by remember(menuOpen) { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    kotlinx.coroutines.delay(600)
                    copied = false
                }
            }
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = if (copied) MiuixIcons.Ok else MiuixIcons.Copy,
                label = if (copied) "已复制" else stringResource(R.string.tabs_copy_link),
                onClick = {
                    if (!copied) {
                        onMenuCopyLink(true)
                        copied = true
                    }
                },
            )
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = MiuixIcons.Delete,
                label = stringResource(R.string.tabs_close_tab),
                onClick = onMenuClose,
            )
        }
    }
}
