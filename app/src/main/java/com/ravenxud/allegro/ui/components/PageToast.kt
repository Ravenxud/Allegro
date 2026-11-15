package com.ravenxud.allegro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.ui.theme.Motion
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页内提示（替代系统 Toast）。
 *
 * 系统 Toast 在 Android 11+ 是系统窗口，样式无法跟随 App、位置固定在底部、还会被输入法顶走；
 * 这里用应用内浮层：从底部 8dp 升起 + 淡入，2 秒后自动退，观感与 Miuix 一致。
 *
 * 用法：宿主放在页面根部（**不要放进 Column / 滚动容器**），状态由调用方持有。
 */
@Composable
fun PageToastHost(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val state = androidx.compose.runtime.remember {
        MutableTransitionState(false)
    }
    state.targetState = message != null

    if (!state.currentState && !state.targetState) return

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = state,
            enter = slideInVertically(
                animationSpec = tween(Motion.Duration.Standard),
                initialOffsetY = { it / 3 },
            ) + fadeIn(tween(Motion.Duration.Standard)),
            exit = slideOutVertically(
                animationSpec = tween(Motion.Duration.Quick),
                targetOffsetY = { it / 3 },
            ) + fadeOut(tween(Motion.Duration.Quick)),
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier
                    .padding(bottom = 96.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}
