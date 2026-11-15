package com.ravenxud.allegro.ui

import com.ravenxud.allegro.ui.theme.AllegroIcons
import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableLongStateOf
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
import top.yukonga.miuix.kmp.icon.extended.More
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
    /** 清晰缩略图（720 宽、无模糊）：卡片用它，背景仍用预模糊的 [stub]。 */
    thumb: Bitmap? = null,
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
    var openedAt by remember { mutableLongStateOf(0L) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            openedAt = android.os.SystemClock.uptimeMillis()
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
        val cardHeightPx = cardWidthPx / 0.72f + 26f * density.density

        // 1) 背景：主题底色打底 + 一张低透明度的模糊截图（Luna 的标签页底也是"浅底 + 淡截图"，
        //    直接用整屏截图当背景会让整页和首页壁纸抢戏，和 Design System 的浅色面不统一）
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
            if (stub != null && screenWidth > 0f) {
                Image(
                    bitmap = stub.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.22f * pp()
                            val bs = 0.99f + 0.01f * pp()
                            scaleX = bs
                            scaleY = bs
                        },
                )
            }
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
                    // 没有整窗截图（首次启动 / 标签恢复）时，飞行元素不存在，
                    // 只靠 0.92→1 会像"纯淡入"；这时把网格的入场幅度加大。
                    val base = if (stub == null) 0.86f else 0.92f
                    val scale = if (simplify) 1f else (base + (1f - base) * p)
                    scaleX = scale
                    scaleY = scale
                    translationY = dragOffset + if (stub == null) (1f - p) * 28f else 0f
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
                onLongPress = { tab ->
                    // 打开面板的那一下按键如果被识别成长按，会"凭空"弹出卡片菜单
                    if (android.os.SystemClock.uptimeMillis() - openedAt > 300L) menuTab = tab
                },
            )
        }

        // 4) 页面缩略：预览图 → 卡片矩形（共享元素）
        // 关键：这张图必须和卡片最终画的是**同一张**（tab.preview），
        // 否则飞行结束时会"换成缩略图"，露出一次跳变。
        val activePreview = state.tabs.firstOrNull { it.id == state.activeTabId }?.preview

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
                        alpha = 1f - ((e - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        shadowElevation = 10.dp.toPx()
                        clip = true
                        // 圆角只在贴近卡片的那 30% 行程里收放，中途始终等于卡片圆角（Shapes.Tile = 20dp），
                        // 否则飞行途中的圆角比网格卡片小，看起来「方了」。
                        val rf = smoothstep((e / 0.3f).coerceIn(0f, 1f))
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp * rf)
                    },
            ) {
                Image(
                    bitmap = (activePreview ?: thumb ?: stub).asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 最近关闭面板：必须在外层 Box（不能进 Column，否则 fillMaxSize 会压垮网格）
        MiuixSheet(
            visible = showRecent,
            onDismiss = { showRecent = false },
            title = "最近关闭的标签页",
            solid = true,
        ) {
            if (recentClosed.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("没有最近关闭的标签", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                recentClosed.forEach { entity ->
                    SheetRow(
                        icon = AllegroIcons.Recent,
                        title = entity.title.ifBlank { entity.url },
                        value = runCatching { java.net.URI(entity.url).host }.getOrNull(),
                        onClick = {
                            onRestoreClosed(entity)
                            showRecent = false
                        },
                    )
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
    val cardWidth = (width - pad * 2 - spacing) / 2f
    // 目标矩形只取「预览框」：标题在卡外，若把标题算进来，飞行图会盖住下面的标题
    val cardHeight = cardWidth / 0.72f
    val row = index / 2
    val col = index % 2
    val left = pad + col * (cardWidth + spacing)
    val top = topInset + header + row * (cardHeight + spacing)
    return Rect(left, top, left + cardWidth, top + cardHeight)
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.28f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
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
    var showHeaderMenu by remember { mutableStateOf(false) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }

    // 必须透明：否则这张不透明面会把下面的"模糊页面背景"整块盖住（观感像白闪/遮罩）
    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.tabs_title, state.tabs.size), fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                // 头部只留一个溢出按钮：最近关闭 / 排序 收进菜单（与设置页、菜单页的规范一致）
                com.ravenxud.allegro.ui.components.CircleIconButton(
                    icon = AllegroIcons.More,
                    onClick = { showHeaderMenu = true },
                    contentDescription = "更多",
                )
                Spacer(Modifier.width(8.dp))
                com.ravenxud.allegro.ui.components.CircleIconButton(
                    icon = AllegroIcons.Close,
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
                    icon = AllegroIcons.Add,
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
                        AllegroIcons.Ok,
                        contentDescription = stringResource(R.string.action_done),
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

        }

        // 头部溢出菜单：与 Column 同级（不参与 Column 的高度分配）
        MiuixSheet(
            visible = showHeaderMenu,
            onDismiss = { showHeaderMenu = false },
            title = "标签页",
            solid = true,
        ) {
            SheetRow(
                icon = AllegroIcons.Recent,
                title = "最近关闭的标签页",
                onClick = {
                    showHeaderMenu = false
                    onOpenRecent()
                },
            )
            SheetRow(
                icon = AllegroIcons.Sort,
                title = "排列方式",
                value = sort.label,
                onClick = onCycleSort,
            )
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
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissWidth = with(density) { 120.dp.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                // 入场：按索引错峰（前 6 张，步长 0.05），alpha 走 smoothstep、scale 带回弹
                val stagger = (cardIndex.coerceAtMost(6)) * 0.05f
                val raw = ((progressProvider() - stagger) / 0.62f).coerceIn(0f, 1f)
                val fade = smoothstep(raw)
                alpha = fade
                translationX = dragOffset.x
                translationY = dragOffset.y + (1f - fade) * 28f
                val s = if (dragging) 1.04f else (0.92f + 0.08f * easeOutBack(raw))
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
                AllegroIcons.Delete,
                contentDescription = stringResource(R.string.cd_close),
                modifier = Modifier.padding(end = 20.dp),
                tint = MiuixTheme.colorScheme.error,
            )
        }

        // 不用 Surface/Card：它们的默认 elevation 会在卡片外画一圈阴影（截图里的"诡异描边"），
        // 且 Miuix Surface 不吃 shape 参数，圆角会与缩略图不一致。
        Box(
            Modifier
                .fillMaxWidth()
                .pressScale()
                .clip(shape)
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = onLongPress,
                )
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
            // Luna 形态：卡面只有预览，标题与关闭放到卡外/卡角，避免一屏只放得下两张
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(shape)
                        .background(MiuixTheme.colorScheme.surfaceVariant),
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
                        Icon(
                            imageVector = AllegroIcons.Copy,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center).size(26.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.40f),
                        )
                    }

                    // 右上角关闭：半透明圆底，和 Luna 一致
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.82f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AllegroIcons.Close,
                            stringResource(R.string.cd_close),
                            modifier = Modifier.size(13.dp),
                        )
                    }

                    // 选中态：内圈细描边，不用蓝色粗框
                    if (active) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .border(
                                    1.5.dp,
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    shape,
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    tab.favicon?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (tab.url == "about:blank") stringResource(R.string.tab_new_page)
                        else tab.title.ifBlank { tab.url },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = if (active) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 长按菜单：锚定在本卡片旁边（不再是贴底 sheet）
        com.ravenxud.allegro.ui.components.AnchoredMenu(
            expanded = menuOpen,
            onDismiss = onMenuDismiss,
        ) {
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = AllegroIcons.Close,
                label = stringResource(R.string.tabs_close_others),
                onClick = onMenuCloseOthers,
            )
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = AllegroIcons.Folder,
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
                icon = if (copied) AllegroIcons.Ok else AllegroIcons.Copy,
                label = if (copied) "已复制" else stringResource(R.string.tabs_copy_link),
                onClick = {
                    if (!copied) {
                        onMenuCopyLink(true)
                        copied = true
                    }
                },
            )
            com.ravenxud.allegro.ui.components.MenuActionRow(
                icon = AllegroIcons.Delete,
                label = stringResource(R.string.tabs_close_tab),
                onClick = onMenuClose,
            )
        }
    }
}
