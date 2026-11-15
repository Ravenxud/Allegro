package com.ravenxud.allegro.ui.nav

import com.ravenxud.allegro.ui.SecondaryScreen
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 二级页路由：一个枚举值一个 entry。
 *
 * 重写目标（替换掉手写的 `AnimatedVisibility + MutableTransitionState + 背景缩放`）：
 * 转场、系统返回、边缘滑动返回、转场期输入拦截，全部交给 `miuix-nav` 的 `NavDisplay`。
 *
 * 约定：`BROWSER` 不是路由 —— 栈为空即表示「在浏览器主界面」。
 */
data class SecondaryRoute(val screen: SecondaryScreen) : NavKey {
    // 约定：screen 不应为 BROWSER。
    // 这里**不做 require**——构造点若拿到 BROWSER 就直接崩溃，代价太大（0.68.0 启动闪退就源于此）。
    // 渲染侧对 BROWSER 走空分支，等价于"没有二级页"。
}
