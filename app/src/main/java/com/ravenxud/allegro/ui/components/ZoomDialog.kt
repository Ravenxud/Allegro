package com.ravenxud.allegro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ravenxud.allegro.ui.theme.Shapes
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面缩放对话框：**步进式，不依赖软键盘**。
 *
 * 为什么不用输入框：Miuix 的 `OverlayDialog` + `TextField` 在真机上出现过
 * "对话框打不开 / 输不进去"的情况，而缩放本质只需要 ±10% 的离散调整。
 * 这里用系统 `Dialog`（独立窗口，一定在最上层）+ 步进按钮，绕开整类问题。
 */
@Composable
fun ZoomDialog(
    show: Boolean,
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    step: Int = 10,
    range: IntRange = 80..140,
) {
    if (!show) return
    var value by remember(show) { mutableIntStateOf(current.coerceIn(range)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MiuixTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "页面缩放",
                    fontSize = 17.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "范围 ${range.first}% – ${range.last}%",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "−$step%",
                        onClick = { value = (value - step).coerceAtLeast(range.first) },
                    )
                    Text(
                        text = "$value%",
                        fontSize = 24.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        text = "+$step%",
                        onClick = { value = (value + step).coerceAtMost(range.last) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    TextButton(
                        text = "确定",
                        onClick = {
                            onConfirm(value)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}
