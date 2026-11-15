package com.ravenxud.allegro.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ravenxud.allegro.ui.components.pressScale
import com.ravenxud.allegro.ui.theme.AllegroIcons
import com.ravenxud.allegro.ui.theme.Dimens
import com.ravenxud.allegro.ui.theme.Shapes
import com.ravenxud.allegro.ui.theme.TypeScale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 内容穿透式顶栏（方案 B）。
 *
 * 页面结构：
 * ```
 * Box {
 *     content(Modifier.fillMaxSize())                       // 内容铺到屏幕顶部
 *     AllegroTopBar(title = …, onBack = …)                  // 浮在上面
 * }
 * // 内容自己的 padding 用 AllegroScaffold 提供
 * ```
 *
 * 透明背景 + 底部发丝线：页面内容可以滚到栏下，栏本身不画底色。
 */
@Composable
fun AllegroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** 是否显示底部发丝线（内容滚到栏下时打开，避免文字与栏重叠）。 */
    hairline: Boolean = true,
    /** 非空时绘制 Miuix 玻璃（折射 + 受光），需指向被记录的页面内容层。 */
    backdrop: Backdrop? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glassMod = if (backdrop != null) {
        Modifier.glass(
            backdrop = backdrop,
            shape = GlassShape(cornerRadius = 0.dp),
            style = GlassStyles.CommonMediumRegularLowLight,
            material = GlassMaterials.puredThinGlass(isSystemInDarkTheme()),
            shading = false,
        )
    } else {
        Modifier
    }
    Column(modifier.fillMaxWidth().then(glassMod).windowInsetsPadding(WindowInsets.statusBars)) {
        // 标题按整屏宽度居中：先铺满再居中，避免被左侧返回钮挤偏半个按钮宽
        Box(Modifier.fillMaxWidth().height(Dimens.TopBar)) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.CircleButton + Dimens.PagePadding),
                textAlign = TextAlign.Center,
                fontSize = TypeScale.Title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.TopBar)
                    .padding(horizontal = Dimens.PagePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    AllegroIconButton(AllegroIcons.Back, onClick = onBack)
                } else {
                    Spacer(Modifier.width(Dimens.CircleButton))
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        // 玻璃自带边缘受光，再画发丝线会出现两道线
        if (hairline && backdrop == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
        }
    }
}

/** 圆形图标按钮：44dp，图标 20dp，自带按压反馈与无障碍标签。 */
@Composable
fun AllegroIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(Dimens.CircleButton)
            .clip(CircleShape)
            .pressScale()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/**
 * 分组卡片：24dp squircle + 可选的组标题。
 * 行必须用 [AllegroRow]，之间用 [AllegroDivider]。
 */
@Composable
fun AllegroCard(
    modifier: Modifier = Modifier,
    header: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        header?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 6.dp, top = Dimens.GroupGap, bottom = 6.dp),
                fontSize = TypeScale.Secondary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Surface(shape = Shapes.Card, color = MiuixTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), content = content)
        }
    }
}

/** 行间分隔线：左右留出与行文案对齐的缩进。 */
@Composable
fun AllegroDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Dimens.RowStart, end = Dimens.RowEnd)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/**
 * 设置/列表行：60dp、16sp 主文案、13sp 副文案、默认右侧 chevron。
 *
 * 约定：**点击与长按都由本组件提供**，页面不要再加 `clickable`
 * （0.71.1 的"长按失效"就是因为换组件时丢了事件）。
 */
@Composable
fun AllegroRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    leadingIcon: ImageVector? = null,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = Dimens.RowHeight)
        .pressScale()
        .then(
            when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                onClick != null -> Modifier.clickable(onClick = onClick)
                else -> Modifier
            },
        )
        .padding(start = Dimens.RowStart, end = Dimens.RowEnd, top = 10.dp, bottom = 10.dp)

    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(Dimens.Icon))
            Spacer(Modifier.width(Dimens.SpacingXS))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                fontSize = TypeScale.Body,
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
        value?.let {
            Text(
                text = it,
                fontSize = TypeScale.Secondary,
                maxLines = 1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        when {
            trailing != null -> trailing()
            showChevron -> Icon(
                AllegroIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 页面内容区域的统一内边距（配合内容穿透顶栏：顶部留出栏高）。 */
fun Modifier.allegroContentPadding(): Modifier = this
    .statusBarsPadding()
    .padding(top = Dimens.TopBar, start = Dimens.PagePadding, end = Dimens.PagePadding)
