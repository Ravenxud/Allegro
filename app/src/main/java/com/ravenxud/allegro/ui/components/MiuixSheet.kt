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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
 * 统一底部面板：32% 遮罩 + 顶部 28dp 圆角 + 拖拽条 + 分组行。
 * 主菜单、长按菜单共用，避免两套样式。
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
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
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
                // 不用半透明：背后多半是 WebView（抓不到、无法模糊），透出来只有文字重影。
                // 真玻璃要等「网页区域快照模糊」（P5）；在那之前保持不透明 + 高光更干净。
                color = MiuixTheme.colorScheme.surface,
            ) {
                // 行数会随功能增加（页面动作 9 行 + 常用 3 行 + 外观 3 行 + 更多），
                // 早期写死 Column 会被屏幕裁掉，导致最底部的「设置」看不见 —— 必须可滚动并限高
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                if (snapshot != null) {
                    // 网页区域抓不到实时内容：用一张 PixelCopy 快照模糊后当玻璃底
                    androidx.compose.foundation.Image(
                        bitmap = snapshot.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(20.dp),
                    )
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .matchParentSize()
                            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.18f)),
                    )
                }
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.18f)),
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                        )
                    }
                    title?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
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
