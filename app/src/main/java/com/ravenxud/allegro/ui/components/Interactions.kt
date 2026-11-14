package com.ravenxud.allegro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.ravenxud.allegro.ui.theme.Motion

/**
 * 按压反馈：0.96 缩放 + 0.9 透明（令牌见 [Motion.Press]）。
 *
 * 用指针手势检测按下/抬起，**不消费事件**，所以可以和 `clickable` 共存，
 * 不需要在每个调用点把 InteractionSource 传给 clickable。
 */
fun Modifier.pressScale(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.Press.SCALE else 1f,
        animationSpec = tween(Motion.Press.DURATION),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) Motion.Press.ALPHA else 1f,
        animationSpec = tween(Motion.Press.DURATION),
        label = "pressAlpha",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}
