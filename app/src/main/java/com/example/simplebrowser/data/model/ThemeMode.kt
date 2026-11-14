package com.example.simplebrowser.data.model

/** App 主题模式：浅色 / 深色 / 跟随系统。 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}
