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
import androidx.compose.ui.draw.clip
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                // 半透明：能透出被压暗的页面；配合顶部 1px 高光，比纯实色更有层次
                color = MiuixTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                // 行数会随功能增加（页面动作 9 行 + 常用 3 行 + 外观 3 行 + 更多），
                // 早期写死 Column 会被屏幕裁掉，导致最底部的「设置」看不见 —— 必须可滚动并限高
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
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

/** 便捷重载：直接传 visible。 */
@Composable
fun MiuixSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.runtime.remember {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    state.targetState = visible
    MiuixSheet(state = state, onDismiss = onDismiss, modifier = modifier, title = title, content = content)
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
