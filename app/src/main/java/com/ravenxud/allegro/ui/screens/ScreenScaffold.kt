package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ravenxud.allegro.R
import com.ravenxud.allegro.ui.glass.GlassSpec
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
 * 0.34 起顶栏是**玻璃**：页面内容被 `layerBackdrop` 捕获，顶栏用 `appGlass` 读取并模糊。
 * 第一帧捕获层还没画完，顶栏先用实色，避免闪一下透明。
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(graphicsLayer) {
        drawRect(surface)
        drawContent()
    }
    var captureReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { captureReady = true }

    Scaffold(
        topBar = {
            val barShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            SmallTopAppBar(
                title = title,
                modifier = if (captureReady) {
                    Modifier.appGlass(
                        backdrop = backdrop,
                        shape = barShape,
                        spec = GlassSpec(blur = 20.dp),
                    )
                } else {
                    Modifier.background(surface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, stringResource(R.string.cd_back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        // 被捕获的内容层：顶栏玻璃读取它做模糊
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .layerBackdrop(backdrop),
        ) {
            content(Modifier.fillMaxSize())
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
