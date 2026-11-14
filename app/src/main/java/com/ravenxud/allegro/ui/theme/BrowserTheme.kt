package com.ravenxud.allegro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ravenxud.allegro.data.model.ThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 把 [ThemeMode] 解析成实际深浅色。 */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * 全局主题：MIUIX 配色 + 状态栏图标 + 窗口底色。
 * 窗口底色跟随主题，避免切换瞬间闪白。
 */
@Composable
fun BrowserTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.resolveDark()
    MiuixTheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        val view = LocalView.current
        val background = MiuixTheme.colorScheme.background
        SideEffect {
            view.setBackgroundColor(background.toArgb())
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        content()
    }
}
