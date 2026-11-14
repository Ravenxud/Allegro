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
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val surfaceColor = MiuixTheme.colorScheme.surface
    // 面板总高上限（Luna 约 0.66 屏），内容再多也自己滚
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
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
        color = if (hasWebGlass || backdrop != null) Color.Transparent else surfaceColor,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(cardShape),
        ) {
            if (hasWebGlass) {
                // 图宽 = 屏宽；高度按快照比例；底边贴面板底 → 与屏幕坐标 1:1 重合
                Image(
                    bitmap = snapshot!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(snapshot.width.toFloat() / snapshot.height.toFloat())
                        .align(Alignment.BottomCenter)
                        .blur(24.dp),
                )
                // 淡色覆盖：能看见网页，同时保证图标与文字可读（Luna 同样是浅色压一层）
                Box(
                    Modifier
                        .matchParentSize()
                        .background(surfaceColor.copy(alpha = 0.62f)),
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
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(rememberScrollState())
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
fun ColumnScope.SheetGrid(
    items: List<SheetItem>,
    columns: Int = 4,
) {
    items.chunked(columns).forEach { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            row.forEach { item ->
                SheetGridCell(item, Modifier.weight(1f))
            }
            repeat(columns - row.size) {
                Spacer(Modifier.weight(1f))
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
