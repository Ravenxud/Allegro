/*
 * Vendored from KernelSU Style UI Kit (GPL-3.0)
 *   https://github.com/chenaizhang/KernelSU-Style-UI-Kit
 *   app/src/main/java/com/example/kernelsustyleuikit/ui/component/miuix/ScaleDialog.kt
 *
 * 修改（相对上游）：
 *  1. 去掉对上游 R.string 的依赖，改为参数传入 title / summary / 取值范围
 *  2. 数值范围与单位可配置，便于复用到「页面缩放」「字号」等场景
 *  3. 包名调整
 * 逻辑与交互保持上游实现。本文件随 Allegro 以 GPL-3.0 发布。
 */
package com.ravenxud.allegro.ui.components.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 百分比输入对话框（上游用于「页面缩放」）。
 *
 * @param value 当前值（0.8 表示 80%）
 * @param range 允许的百分比范围，超出会被钳制
 */
@Composable
fun PercentInputDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    range: IntRange = 80..110,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = "${range.first}% - ${range.last}%",
        onDismissRequest = onDismissRequest,
        content = {
            var text by remember(show) {
                mutableStateOf((value() * 100).toInt().toString())
            }
            TextField(
                modifier = Modifier.padding(bottom = 16.dp),
                value = text,
                maxLines = 1,
                trailingIcon = {
                    Text(
                        text = "%",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.onSurfaceVariantActions,
                    )
                },
                onValueChange = { newValue ->
                    if (newValue.isEmpty()) {
                        text = ""
                    } else if (newValue.all { it.isDigit() }) {
                        text = newValue
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val parsed = text.toIntOrNull()
                        val clamped = parsed?.coerceIn(range) ?: (value() * 100).toInt()
                        onValueChange(clamped / 100f)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}
