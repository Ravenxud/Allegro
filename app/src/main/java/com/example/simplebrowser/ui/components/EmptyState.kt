package com.example.simplebrowser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebrowser.ui.theme.IconSize
import com.example.simplebrowser.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 统一空状态：图标 + 标题 + 说明 + 可选操作。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize.empty),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            )
            Text(title, fontSize = 15.sp)
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (actionText != null && onAction != null) {
                Box(Modifier.padding(top = Spacing.s)) {
                    Button(onClick = onAction) { Text(actionText) }
                }
            }
        }
    }
}
