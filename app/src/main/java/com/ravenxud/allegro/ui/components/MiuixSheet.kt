package com.ravenxud.allegro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.ui.theme.Motion
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import com.ravenxud.allegro.ui.glass.appGlass
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一底部面板：无遮罩 + 顶部 28dp 圆角 + 拖拽条 + 分组行。
 * 面板外保持网页原始亮度，点击空白处仍可关闭；主菜单、长按菜单共用。
 */
@Composable
fun MiuixSheet(
    state: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** 背景捕获层；为 null 时面板保持不透明（背后抓不到内容就别做假玻璃）。 */
    backdrop: com.kyant.backdrop.Backdrop? = null,
    /** 背景快照（网页可见时用 PixelCopy 抓）：有它就画"模糊的快照"当玻璃底。 */
    snapshot: android.graphics.Bitmap? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(Motion.enter()),
            exit = fadeOut(Motion.exit(Motion.Duration.Quick)),
        ) {
            // 只负责接收面板外点击，不绘制任何颜色。
            // 即使低透明度黑色遮罩也会改变网页观感，因此这里必须完全透明。
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visibleState = state,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.slide(Motion.Duration.Emphasized)) { it },
            exit = slideOutVertically(Motion.slide()) { it },
        ) {
            val panelShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val surfaceColor = MiuixTheme.colorScheme.surface

            // 顶栏挡板是唯一做玻璃的一条：把"整窗快照"按屏幕坐标 1:1 对齐后只露出这一条。
            // 面板贴底且满宽 ⇒ 屏幕高 = 面板顶 y + 面板高，屏幕宽 = 面板宽，无需额外测量。
            var panelTopPx by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableIntStateOf(0)
            }
            var screenSizePx by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
            }
            val bandHeight = if (title == null) 26.dp else 52.dp
            // 行数会随功能增加（页面动作 9 行 + 常用 3 行 + 外观 3 行 + 更多），
            // 早期写死 Column 会被屏幕裁掉，导致最底部的「设置」看不见 —— 必须可滚动并限高
            val maxContentHeight =
                (LocalConfiguration.current.screenHeightDp * 0.64f).dp - bandHeight

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { c ->
                        panelTopPx = c.positionInWindow().y.toInt()
                        screenSizePx = androidx.compose.ui.unit.IntSize(
                            c.size.width,
                            panelTopPx + c.size.height,
                        )
                    }
                    .then(
                        if (backdrop != null) {
                            // 真玻璃：模糊背后的 Compose 内容（首页/列表），而不是调 alpha 出重影
                            Modifier.appGlass(
                                backdrop = backdrop,
                                shape = panelShape,
                                spec = com.ravenxud.allegro.ui.glass.GlassSpec(blur = 26.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
                shape = panelShape,
                // 内容区保持不透明：WebView 表面抓不到实时内容，半透明只会透出"文字重影"。
                // 玻璃只做在顶栏挡板（见下），那里有可对齐的快照源。
                color = surfaceColor,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // --------------------------- 顶栏挡板（玻璃） ---------------------------
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(bandHeight)
                            .clip(panelShape),
                    ) {
                        if (snapshot != null && panelTopPx > 0 && screenSizePx.height > 0) {
                            androidx.compose.foundation.Image(
                                bitmap = snapshot.asImageBitmap(),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                                modifier = Modifier
                                    .requiredSize(
                                        with(density) { screenSizePx.width.toDp() },
                                        with(density) { screenSizePx.height.toDp() },
                                    )
                                    // 快照是整窗的：上移"面板顶"的距离，就与屏幕坐标重合
                                    .offset { androidx.compose.ui.unit.IntOffset(0, -panelTopPx) }
                                    .blur(18.dp),
                            )
                            // 轻微 tint：任何网页上都读得清标题，同时保留背后色彩
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(surfaceColor.copy(alpha = 0.34f)),
                            )
                            // 底边渐隐到不透明面板色，消除"玻璃条 / 纯色区"的硬接缝
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            listOf(Color.Transparent, surfaceColor),
                                        ),
                                    ),
                            )
                        }
                        // 顶部细高光（玻璃边缘）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.20f)),
                        )
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .padding(vertical = 8.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    ),
                            )
                            title?.let {
                                Text(
                                    text = it,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    // --------------------------- 内容区（不透明） ---------------------------
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .heightIn(max = maxContentHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                    ) {
                        content()
                    }
                }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.runtime.remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    state.targetState = visible
    MiuixSheet(
        state = state,
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
        backdrop = backdrop,
        snapshot = snapshot,
        content = content,
    )
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
