package com.ravenxud.allegro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravenxud.allegro.ui.theme.Dimens
import com.ravenxud.allegro.ui.theme.Shapes
import com.ravenxud.allegro.ui.theme.TypeScale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组卡片（Luna 形态）：24dp 圆角、白色面、行与行之间用细分隔线。
 * 与 Miuix `Card` 的区别：圆角与内边距由令牌控制，不随组件默认值漂移。
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.Card)
            .background(MiuixTheme.colorScheme.surface)
            .padding(vertical = 2.dp),
        content = content,
    )
}

/** 组间分隔线（只出现在组内行之间，不跨组）。 */
@Composable
fun GroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Dimens.RowStart, end = Dimens.RowEnd)
            .size(width = 1.dp, height = 1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/**
 * 设置行（Luna 形态）：**纯文字 + 右侧 chevron，无行首图标**。
 * 行高 60dp；有 summary 时两行排列，summary 13sp 灰字。
 */
@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.RowHeight)
            .clickable(onClick = onClick)
            .padding(start = Dimens.RowStart, end = Dimens.RowEnd, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = TypeScale.Row,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            summary?.let {
                Text(
                    text = it,
                    fontSize = TypeScale.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 组标题（Luna 没有，但我们的一页式设置需要；保持 13sp 灰、贴左上）。 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
        fontSize = TypeScale.Secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 圆形图标按钮（Luna 的返回 / 关闭 / 设置）。 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    size: androidx.compose.ui.unit.Dp = Dimens.CircleButton,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** 胶囊按钮（Luna 的「隐身浏览」「N 个标签页」）。 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .heightIn(min = Dimens.PillHeight)
            .clip(Shapes.Capsule)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(
            text = label,
            fontSize = TypeScale.Row,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
