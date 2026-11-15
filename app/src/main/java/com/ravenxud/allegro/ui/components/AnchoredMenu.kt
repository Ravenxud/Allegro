package com.ravenxud.allegro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ravenxud.allegro.ui.theme.Shapes
import com.ravenxud.allegro.ui.theme.TypeScale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 锚定在父布局上的浮动菜单（替代贴底 sheet）。
 *
 * 定位逻辑参考 KernelSU Style UI Kit 的 `MenuPositionProvider`（GPL-3.0）：
 * 优先贴在锚点下方，下方空间不足翻到上方，最后在窗口内钳制。
 * 这里改写为 Compose 的 [PopupPositionProvider]（上游用的是 Miuix 的类型），
 * 属于**逻辑借鉴**，不是逐行复制。
 */
@Composable
fun AnchoredMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    // 弹出时做一次缩放+淡入，避免"硬弹出"
    val anim = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        anim.snapTo(0f)
        anim.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 480f))
    }
    val positionProvider = remember { MenuPositionProvider() }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier
                .width(232.dp)
                .graphicsLayer {
                    scaleX = 0.92f + 0.08f * anim.value
                    scaleY = 0.92f + 0.08f * anim.value
                    alpha = anim.value
                },
            shape = Shapes.FloatingPanel,
            color = MiuixTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

private class MenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Compose 1.12 起第二个参数是窗口尺寸（IntSize），不是窗口矩形
        val windowBounds = IntRect(IntOffset.Zero, windowSize)
        val margin = 8
        val below = anchorBounds.bottom + margin
        val canFitBelow = below + popupContentSize.height <= windowBounds.bottom - margin
        val canFitAbove = anchorBounds.top - margin - popupContentSize.height >= windowBounds.top + margin

        val x = anchorBounds.left.coerceIn(
            windowBounds.left + margin,
            (windowBounds.right - popupContentSize.width - margin)
                .coerceAtLeast(windowBounds.left + margin),
        )
        val y = when {
            canFitBelow -> below
            canFitAbove -> anchorBounds.top - margin - popupContentSize.height
            else -> (windowBounds.bottom - popupContentSize.height - margin)
                .coerceAtLeast(windowBounds.top + margin)
        }
        return IntOffset(x, y)
    }
}

/** 浮动菜单里的一行：图标 + 文案。 */
@Composable
fun MenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
