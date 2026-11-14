package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ravenxud.allegro.R
import com.ravenxud.allegro.ui.glass.GlassSpec
import com.ravenxud.allegro.ui.components.CircleIconButton
import com.ravenxud.allegro.ui.theme.Dimens
import com.ravenxud.allegro.ui.theme.TypeScale
import com.ravenxud.allegro.ui.glass.appGlass
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级页面统一外壳：MIUIX SmallTopAppBar + 返回 + 可选操作。
 *
 * 注意（0.34.2 结论）：这里**不做玻璃**。`Scaffold` 的 `topBar` 不覆盖内容，
 * 栏的后面什么都没有，`layerBackdrop` 捕获到的是空白 —— 模糊一块空白等于没效果。
 * 真玻璃需要"内容穿透到栏下"（页面列表改用 contentPadding 留出首项空间），属结构改造。
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    /** false 时退回旧 SmallTopAppBar（出问题可一处回退）。 */
    floating: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    if (!floating) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.cd_back))
                        }
                    },
                    actions = actions,
                )
            },
        ) { padding ->
            content(Modifier.padding(padding))
        }
        return
    }

    // Luna 形态：内容从屏幕顶部开始（穿透到栏下），顶部只浮一条 56dp 的栏。
    // 注意：这里必须自己铺一层不透明底色 —— 二级页是叠在首页之上的浮层，
    // 少了这层就会透出下层（0.44 的回归 bug）。
    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        content(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = Dimens.TopBar),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(Dimens.TopBar)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = MiuixIcons.Back,
                onClick = onBack,
                contentDescription = stringResource(R.string.cd_back),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = TypeScale.Title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            // 右侧 actions（在 RowScope 内调用；无 action 时不占位）
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
