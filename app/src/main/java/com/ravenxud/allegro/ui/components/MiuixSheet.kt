package com.ravenxud.allegro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.ui.glass.appGlass
import com.ravenxud.allegro.ui.theme.Motion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一底部面板（Luna 观感）：
 * - 32dp 大圆角卡片，**整块**玻璃（网页快照模糊 + 淡色 tint），能透出背后网页
 * - 顶部：拖拽条 + 左侧关闭圆钮 + 右侧胶囊动作区
 * - 内容默认走 4 列圆形图标网格（SheetGrid）
 * - 进场用弹簧（可打断、带一点过冲），出场用加速曲线
 *
 * 快照几何：**不测量面板位置**（`positionInWindow()` 在本工程里不可信，
 * 实测返回 top=2936 / win=4694，而屏幕只有 2672 高）。
 * 改为「快照自身宽高比 + 底对齐」：图宽 = 面板宽 = 屏宽，
 * 图高由 bitmap 比例推出，底边贴面板底（= 屏底）→ 与真实位置严格重合。
 */
@Composable
fun MiuixSheet(
    state: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** 背景捕获层；为 null 时走"网页快照"或纯色。 */
    backdrop: com.kyant.backdrop.Backdrop? = null,
    /** 网页快照（PixelCopy 整窗抓取）：有它就做半透明玻璃。 */
    snapshot: android.graphics.Bitmap? = null,
    /** 是否显示左上角关闭圆钮（Luna 主页菜单有）。 */
    showClose: Boolean = false,
    /** 右上角胶囊里的动作按钮。 */
    actions: @Composable RowScope.() -> Unit = {},
    /** 内容是否可纵向滚动（网格分页面板传 false）。 */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        // 只负责接收"面板外点击"，不绘制任何颜色 —— 页面颜色/亮度完全不变
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(Motion.Duration.Instant)),
            exit = fadeOut(tween(Motion.Duration.Quick)),
        ) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        }

        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.align(Alignment.BottomCenter),
            // 弹簧进场：Luna 的手感不是匀速缓动，而是 340 刚度、略欠阻尼的落位
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 340f,
                ),
                initialOffsetY = { it },
            ) + fadeIn(tween(Motion.Duration.Quick)),
            exit = slideOutVertically(
                animationSpec = tween(Motion.Duration.Standard, easing = Motion.Easing.Accelerate),
            ) { it } + fadeOut(tween(Motion.Duration.Quick)),
        ) {
            SheetCard(
                onDismiss = onDismiss,
                title = title,
                backdrop = backdrop,
                snapshot = snapshot,
                showClose = showClose,
                actions = actions,
                glassAlpha = 0.88f,
                scrollable = scrollable,
                content = content,
            )
        }
    }
}

@Composable
private fun SheetCard(
    onDismiss: () -> Unit,
    title: String?,
    backdrop: com.kyant.backdrop.Backdrop?,
    snapshot: android.graphics.Bitmap?,
    showClose: Boolean,
    actions: @Composable RowScope.() -> Unit,
    glassAlpha: Float,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val surfaceColor = MiuixTheme.colorScheme.surface
    // 面板总高上限（Luna 约 0.66 屏），内容再多也自己滚
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    val hasWebGlass = backdrop == null && snapshot != null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (backdrop != null) {
                    // 首页/列表：模糊背后的 Compose 内容
                    Modifier.appGlass(
                        backdrop = backdrop,
                        shape = cardShape,
                        spec = com.ravenxud.allegro.ui.glass.GlassSpec(blur = 26.dp),
                    )
                } else {
                    Modifier
                },
            ),
        shape = cardShape,
        color = if (backdrop != null) Color.Transparent else surfaceColor.copy(alpha = glassAlpha),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(cardShape),
        ) {
            if (backdrop == null) {
                // 关键：**不做快照、不做模糊**。
                // 面板与 WebView 在同一窗口，半透明色合成时看到的就是"当前真实网页"
                // —— 零抓取、零对齐、零过期。之前四版的所有错位/重影/旧图都源于"抓图"这一步。
                Box(
                    Modifier
                        .matchParentSize()
                        .background(surfaceColor.copy(alpha = glassAlpha)),
                )
            }

            Column(Modifier.fillMaxWidth()) {
                // ---------------- 顶部 ----------------
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.28f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.24f)),
                    )
                }
                if (showClose || title != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (showClose) {
                            SheetCircleButton(icon = MiuixIcons.Close, onClick = onDismiss)
                        }
                        title?.let {
                            Text(
                                text = it,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        } ?: Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(surfaceColor.copy(alpha = 0.75f))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            content = actions,
                        )
                    }
                }

                // ---------------- 内容 ----------------
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .then(
                            if (scrollable) {
                                Modifier
                                    .heightIn(max = maxContentHeight)
                                    .verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        )
                        .padding(bottom = 10.dp),
                    content = content,
                )
            }
        }
    }
}

/** 便捷重载：直接传 visible。 */
@Composable
fun MiuixSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    backdrop: com.kyant.backdrop.Backdrop? = null,
    snapshot: android.graphics.Bitmap? = null,
    showClose: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.runtime.remember {
        MutableTransitionState(false)
    }
    state.targetState = visible
    MiuixSheet(
        state = state,
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
        backdrop = backdrop,
        snapshot = snapshot,
        showClose = showClose,
        actions = actions,
        content = content,
    )
}

/** 顶部圆形按钮（关闭、单个动作）。 */
@Composable
fun SheetCircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** 网格里的一项。 */
data class SheetItem(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 4 列圆形图标网格（Luna 主页菜单的形态）。
 * 每格：56dp 白色圆 + 24dp 图标 + 12sp 标签。
 */
@Composable
fun SheetGrid(
    items: List<SheetItem>,
    columns: Int = 4,
    ghosts: Int = 0,
) {
    Column(Modifier.fillMaxWidth()) {
    val rows = (items.size + ghosts + columns - 1) / columns
    repeat(rows) { rowIndex ->
        val row = items.drop(rowIndex * columns).take(columns)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            row.forEach { item ->
                SheetGridCell(item, Modifier.weight(1f))
            }
            // 占位格与真实格同高：保证翻页时面板高度不变（不会"硬切面积"）
            repeat(columns - row.size) {
                Spacer(
                    Modifier
                        .weight(1f)
                        .height(SHEET_CELL_HEIGHT),
                )
            }
        }
    }
    }
}

/** 单元格固定高度（圆 + 8dp 间距 + 标签 + 上下内边距）。 */
private val SHEET_CELL_HEIGHT = 104.dp

/** 分页网格：一页最多 12 项（3 行 × 4 列），页面指示点居中。 */
@Composable
fun SheetPagedGrid(
    items: List<SheetItem>,
    columns: Int = 4,
    rowsPerPage: Int = 3,
) {
    val perPage = columns * rowsPerPage
    val pages = items.chunked(perPage).ifEmpty { listOf(emptyList()) }
    val ghosts = pages.map { perPage - it.size }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            SheetGrid(pages[page], columns, ghosts[page])
        }
        if (pages.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = if (active) 0.55f else 0.18f,
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetGridCell(item: SheetItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
                .then(
                    if (item.active) {
                        Modifier.border(
                            1.5.dp,
                            MiuixTheme.colorScheme.primary,
                            CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SheetSection(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
fun SheetRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}


/** 系统是否支持跨窗口模糊（API 31+ 且未被系统/设备禁用）。 */
@Composable
fun crossWindowBlurEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            false
        } else {
            runCatching {
                context.getSystemService(android.view.WindowManager::class.java)
                    ?.isCrossWindowBlurEnabled == true
            }.getOrDefault(false)
        }
    }
}

/**
 * 用**独立 Dialog 窗口**承载面板，并开启系统级 `FLAG_BLUR_BEHIND`。
 *
 * 为什么必须这样：模糊"自己窗口里的 WebView"是抓不到的（前六版失败的原因），
 * 但让**系统合成器**去模糊"下层窗口"，系统是能拿到真实像素的 —— 这是唯一能
 * 在网页上出现真正模糊的路径。系统不支持时自动退回半透明（不崩、不白屏）。
 */
@Composable
fun MiuixSheetDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    showClose: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    blurRadiusDp: Int = 28,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val blurOn = crossWindowBlurEnabled()
    val density = androidx.compose.ui.platform.LocalDensity.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (
            androidx.compose.ui.platform.LocalView.current.parent
                as? androidx.compose.ui.window.DialogWindowProvider
            )?.window
        androidx.compose.runtime.LaunchedEffect(window, blurOn) {
            window ?: return@LaunchedEffect
            runCatching {
                window.setDimAmount(0f)
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
                )
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                if (blurOn && android.os.Build.VERSION.SDK_INT >= 31) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val px = with(density) { blurRadiusDp.dp.roundToPx() }
                    window.attributes = window.attributes.apply { blurBehindRadius = px }
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 有系统模糊时面板要更透，模糊才看得见；没有就保持较实的浅色保证可读
            SheetCard(
                onDismiss = onDismiss,
                title = title,
                backdrop = null,
                snapshot = null,
                showClose = showClose,
                actions = actions,
                glassAlpha = if (blurOn) 0.52f else 0.9f,
                scrollable = scrollable,
                content = content,
            )
        }
    }
}
