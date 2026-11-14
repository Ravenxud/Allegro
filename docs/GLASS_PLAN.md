# 玻璃材质 + 动效 完整实施方案（v3）

> 本文合并：`glass-integration-draft.md` v1.2（Luna 级观感）+ 对本工程的适配修正。
> 取代 `GLASS_PLAN.md` v2（效果优先版）与 `ANIMATION.md` 的性能门槛部分。
> 目标不是"加一点模糊"，而是把**材质、圆角、过滚、转场**四条线一次做成一套系统。

---

## 0. 决策锁定（不再讨论）

| # | 决策 | 值 | 影响 |
|---|---|---|---|
| 1 | `minSdk` | **26 → 33** | 放弃 Android 12 及以下；AGSL/RenderEffect 无条件用 |
| 2 | `targetSdk` | **37 → 36** | 预测性返回默认开启；升 37 作为后续独立任务 |
| 3 | `compileSdk` | 37（不变） | 保留新常量与 lint |
| 4 | 材质库 | **Backdrop 2.0.1**（唯一） | 不引 Haze、不引 Material3 |
| 5 | 自研材质 | **三段 AGSL**（IOR 玻璃 / 超椭圆 / 镜像过滚）+ 噪声 | 编译失败退 `lens()` |
| 6 | 导航 | **不引入 Navigation Compose** | 保留 `SecondaryScreen` 状态机；用 `AnimatedContent + sharedBounds` 做共享元素 |
| 7 | 预测性返回 | **保留现有 `OnBackPressedCallback` 通道**，只收敛魔法数 | 该通道本身就是预测性返回 API，不是旧实现 |
| 8 | 顶栏材质 | **静止快照模糊**（PixelCopy）+ 滚动 scrim | WebView 不进任何实时捕获层 |
| 9 | 网页可见时的底栏 | scrim；**覆盖层出现后**才升级真玻璃 | 避免"玻璃后面是黑洞" |
| 10 | 质量档 | `High / Medium / Off`，默认 High，**不按设备自动降级** | 低端机默认也全开 |

---

## 1. 现状与差距

| 能力 | 现状 | 目标 |
|---|---|---|
| 材质 | 不透明 `Surface(color=surface)` + 少量 `tween` | 玻璃（模糊 + 折射 + 色散 + 受光 + 内阴影） |
| 圆角 | `RoundedCornerShape` / Miuix 默认 | 超椭圆 `SquircleShape(smoothing=0.8)` |
| 动效令牌 | 16 处裸 `tween`，11 种时长 | 全部收敛到 `Motion.kt`（4 档时长 / 6 曲线 / 3 spring） |
| 转场 | 自定义 `AnimatedVisibility` 滑入 | 保留骨架 + 两条链路 `sharedBounds` morph |
| 过滚 | 自研 `PullRefreshLayout` 指示器 | 越界挂镜像折叠 + 弹簧回弹 + 触感 |
| 顶栏模糊 | 无（只有一条进度条） | 快照模糊 + 滚动切 scrim |
| 大屏 | `LayoutWidth` 600/840 + `TabsRail` | 保持，补玻璃与分屏策略 |

---

## 2. 设备基线与连带清理

`minSdk 33` 会让现有 3 处版本分支变成恒真，必须一并处理，否则 lint `ObsoleteSdkInt` 会让 CI 红：

| 文件 | 现状 | 处理 |
|---|---|---|
| `browser/TabWebView.kt:117` | `if (SDK_INT >= O) safeBrowsingEnabled = true` | 直接删条件，无条件启用 |
| `browser/WebViewBrowserController.kt:916` | `if (SDK_INT >= Q) WebStorage.deleteOrigin(...)` | 直接删条件 |
| `MainActivity.kt:158` | `if (SDK_INT < TIRAMISU) return`（通知权限） | TIRAMISU = 33 = minSdk，条件恒假 → 删除分支，直接请求 |

其余不受影响：`usesCleartextTraffic`、edge-to-edge、FileProvider、Room v3。

---

## 3. 依赖（坐标与版本均已核对 Maven）

```kotlin
// gradle/libs.versions.toml
[versions]
backdrop = "2.0.1"            // io.github.kyant0:backdrop（有 backdrop-android 变体）
profileinstaller = "1.4.1"
metrics = "1.0.0"

[libraries]
backdrop = { group = "io.github.kyant0", name = "backdrop", version.ref = "backdrop" }
androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileinstaller" }
androidx-metrics = { group = "androidx.metrics", name = "metrics-performance", version.ref = "metrics" }
```

> ⚠️ **包名与坐标不是一回事**（P0 实测）：Maven 坐标是 `io.github.kyant0:backdrop`，
> 但 Kotlin 包名是 **`com.kyant.backdrop.*`**（`Backdrop` / `LayerBackdrop` / `drawBackdrop` / `blur` / `lens` / `vibrancy`）。
> 文档里所有 `import io.github.kyant0.backdrop.*` 的写法都是错的。

**不引入**：Haze（与 Backdrop 功能重叠且会打架）、Navigation Compose（本文 §0-6）、navigationevent（现有回调已够）、materialkolor（Maven 上未查到稳定坐标）、Material3。

R8：`app/proguard-rules.pro` 追加
```
-keep class io.github.kyant0.backdrop.** { *; }
-keepclassmembers class ** { @androidx.compose.runtime.Composable <methods>; }
```
（AGSL 是 `const-string`，R8 默认保留；仍需 release 包真机启动验一次。）

---

## 4. 架构与唯一入口

```
L3 细节：三段 AGSL（IOR / 超椭圆 / 镜像）+ 噪声      ui/glass/GlassShaders.kt
L2 材质：Backdrop 层捕获 + blur + vibrancy + 自研效果  ui/glass/GlassModifier.kt
L1 动效：Motion 令牌 / sharedBounds / 预测性返回        ui/theme/Motion.kt
L0 基座：Tokens（Spacing/Radius/IconSize）+ SquircleShape
```

**唯一入口**：`Modifier.appGlass(spec, shape, backdrop)`、`Modifier.barGlass(...)`。
call site 禁止 `import io.github.kyant0.backdrop.*`（唯一例外：`ui/glass/GlassTopBar.kt`）。

### 4.1 WebView 排除规则（本方案最关键的一条）

```
Root
├─ BackdropScope（只捕获下面这层）
│   ├─ 背景（主题色 / 首页）
│   ├─ 二级页面板 / 菜单 / 标签面板 / 侧栏 / 首页
│   └─ 顶栏、底栏（覆盖层出现时才用真玻璃）
└─ WebView 宿主（AndroidView → PullRefreshLayout → FrameLayout → TabWebView）
        ↑ 永远不在 BackdropScope 内，永远不加 renderEffect
```

- 网页可见时：顶栏 = **快照模糊**（`captureRegion` 抓顶栏区域，≤720px，去抖 150ms，保鲜 1.5s）；底栏 = **scrim**。
- 覆盖层/二级页出现时：WebView 已被置 `GONE`，玻璃可以整片覆盖，直接 `appGlass`。
- 视频全屏（`onShowCustomView`）：全站玻璃置空，只留纯色。

---

## 5. 材质地图

| 区域 | 网页可见时 | 覆盖层 / 二级页 | 参数 |
|---|---|---|---|
| 顶栏 | 快照模糊 + 顶部 scrim | 真玻璃（无折射） | blur 24dp |
| 底栏 | scrim（85%→0 渐变） | **真玻璃 + IOR** | blur 24 / 折射 16/32 / IOR 1.35 / 色散 0.35 |
| 应用菜单 / 长按菜单 | — | 真玻璃（低强度） | 折射 12/24，圆角 28 + smoothing 0.8 |
| 二级页面板 | — | 真玻璃（无折射，避免大面扭曲） | blur 24 |
| 标签面板背景 | — | 真玻璃 + 缩略图在底层 | 暗化 12%，模糊 24 |
| 大屏侧栏 | 真玻璃（背后是内容区） | 真玻璃 | blur 16 |
| 首页 | 真玻璃（背后是纯 Compose 背景） | — | 玻璃搜索框用 IOR 小面积 |
| 下拉指示器 | 小面积 IOR | — | 折射随拖拽距离 |
| 设置卡片 / 空态 | 半透明 + 描边（**不做模糊**） | 同左 | 控层数 |

**层数上限 3**（视觉考虑）：顶 + 底 + 一层内容；列表卡片一律不做玻璃。

---

## 6. 三段 AGSL

### 6.1 IOR 玻璃（核心）
- 输入：`uniform shader content`；**必须**与 `runtimeShaderEffect(..., "content")` 的输入名完全一致（写错不报错，画面全黑）。
- 参数：`size/cornerRadii/refractionHeight/refractionAmount/ior/dispersion/lightDir/tint/innerShadow`。
- 硬约束：`refractionHeight ≤ minCornerRadius`（28dp 圆角 → 16dp 安全）、`refractionAmount ≤ min(w,h)`；越界夹紧并 `DiagLog.w`。
- 色散：7 次采样，权重 r/g/b 各和为 1（已核算，不会整体偏色）。
- 高光：`pow(dot(normalize(grad), normalize(lightDir)), 6) * (ior-1) * 0.6`。
- 内阴影：底部 `smoothstep` 递减。

### 6.2 超椭圆
`smoothing=0.8` 的 SDF 混合（圆→超椭圆），实现 `SquircleShape : CornerBasedShape`，替换玻璃层的 `RoundedCornerShape(28.dp)`。

### 6.3 镜像过滚
`mirrorFold(u, size)` + `scale = 1 + overdrag*0.25`、`dim = 0.92`；只在 `PullRefreshLayout` 越界时挂载，回弹走 `Motion.Springs.Snappy`。

### 6.4 噪声
`noise=0.02` 三通道哈希抖动，叠在玻璃表面，消除"塑料感"渐变带。

### 6.5 失败兜底
`RuntimeShader` 编译异常 → 退回 Backdrop `lens()` → 再失败退回半透明 + 描边；每次只记一次 `DiagLog.e`，不弹 UI。

---

## 7. 动效

### 7.1 令牌（唯一来源 `ui/theme/Motion.kt`）
```
Duration: Micro 120 / Short 180 / Medium 280 / Long 400
Easing:   Standard (.25,.1,.25,1) / Decelerate (0,0,.58,1) / Accelerate (.42,0,1,1)
          Emphasized (.2,0,0,1) / OvershootSoft (.61,1,.88,1) / Overshoot (.64,1.56,.34,1)
Springs:  Snappy (.85/380) / Gentle (1/260) / Bouncy (.7/420)
PredictiveBack: SCALE_DROP .06 / ALPHA_DROP .25 / CORNER_RADIUS_DP 28
```
验收：`grep -rn "tween(" app/src/main` 只命中 `Motion.kt`。

### 7.2 34 项清单
- 转场 7、列表与状态 9、微交互 10（见 `docs/ANIMATION.md` §2，参数换令牌）
- 液态专属 8（L1–L8：按压凹陷、滚动物理视差、边缘流光、色散呼吸、卡片落位折射、悬浮拖尾、转场渗出、主题液体过渡）

### 7.3 共享元素（不引 Navigation）
```kotlin
SharedTransitionLayout {
  AnimatedContent(targetState = screen, transitionSpec = { … Motion … }) { target ->
    …
    Modifier.sharedBounds(rememberSharedContentState("tab-${id}"), this@AnimatedContent)
  }
}
```
链路两条：标签面板卡片 → 浏览页；书签项 → 打开动画。key 必须稳定（禁止 index）。顶栏/底栏不参与。

### 7.4 预测性返回（保留现有通道）
现有 `MainActivity.handleOnBackStarted/Progressed/Cancelled/Pressed` 不动，只把 `0.06f/0.25f/28dp` 换成 `Motion.PredictiveBack`，并给 `BrowserScreen` 的 `graphicsLayer` 补圆角。

### 7.5 触感
`Vibrator.hasAmplitudeControl`：标签切换、开关、拖拽越界三处，振幅 0.3/0.5，时长 8–12ms。

---

## 8. 关键实现细则

1. **快照节流**：`onScroll` 只切状态；`delay(150ms)` 后抓图，`job.cancel()` 去抖；滚动中立即回 scrim。
2. **不要第二份 PixelCopy**：从 `WebViewBrowserController.captureStub` 抽出 `captureRegion(rect, downscale)`，两处共用。
3. **深色对比**：`surfaceAlpha` 深色 0.18 / 浅色 0.12，`tint` 白 8%，描边白 18%；深浅各截一张图核对文字对比度。
4. **圆角一致性**：玻璃 28dp + smoothing 0.8；`Radius.sheet` 已是 28dp，直接复用。
5. **层数控制**：`GlassBudget`（debug）统计单帧 `appGlass` 次数，>3 打 `DiagLog.w`（不崩）。
6. **回滚**：`SettingsRepository` 隐藏 key `glass_enabled=false` → 全站 `Off`；外观页提供 High/Medium/Off。

---

## 8.5 P0 实测记录（已完成）

| 项 | 结果 |
|---|---|
| minSdk 33 / targetSdk 36 | ✅ 生效，manifest 已核对 |
| 依赖解析 | ✅ `backdrop 2.0.1`（`backdrop-android` 变体）、`profileinstaller 1.4.1`、`metrics-performance 1.0.0` |
| Backdrop 真实 API | `rememberLayerBackdrop(GraphicsLayer) { drawRect; drawContent }`、`Modifier.layerBackdrop(bd)`、`Modifier.drawBackdrop(bd, shape = {…}, effects = { blur(px); vibrancy() })`、`lens(h, amount, depthEffect, chromaticAberration)` |
| 包名 | **`com.kyant.backdrop`**（不是 `io.github.kyant0.backdrop`） |
| lint / 单测 / release 打包 | ✅ 全绿 |
| 体积变化 | debug 13MB → **34MB**，release 1.9MB → **3.5MB**；原因：minSdk ≥ 28 后 **dex 改为不压缩存储**（安装更快、可 mmap），叠加新依赖 |
| 待真机验证 | AGSL 是否能编译、Backdrop 是否出模糊（冒烟页：设置 → 诊断 → 着色器冒烟测试） |

## 9. 落地步骤（P0–P6）

| 步 | 内容 | 关键文件 | DoD | 工量 |
|---|---|---|---|---|
| **P0** | SDK 基线（minSdk 33 / targetSdk 36）+ 加 3 个依赖 + 清 3 处恒真分支 + **AGSL 冒烟页** | `app/build.gradle.kts`、`libs.versions.toml`、`MainActivity.kt`、`browser/*`、`ui/debug/ShaderSmokeScreen.kt`（过渡用） | 编译过；冒烟页能出图/能显示"AGSL 编译失败"原因 | 0.5 天 |
| **P1** | `Motion.kt` + 全量替换裸 `tween` + `PredictiveBack` 收敛 + `GlassSpec`/`appGlass` 骨架（先用 `lens()`） | `ui/theme/Motion.kt`、`ui/glass/*`、`ui/BrowserScreen.kt`、`MiuixSheet.kt`、`HomeScreen.kt`、`MainActivity.kt` | 视觉零变化；`grep tween` 只在 Motion.kt | 1 天 |
| **P2** | Backdrop 接入：二级页 / 菜单 / 标签面板 / 侧栏 / 首页 / 底栏（覆盖层态） | `ui/screens/ScreenScaffold.kt`、`TabSwitcherOverlay.kt`、`Overlays.kt`、`TabsRail.kt`、`HomeScreen.kt` | 玻璃无空洞、深浅文字可读 | 2 天 |
| **P3** | IOR 玻璃 + 噪声替换 `lens()` | `ui/glass/GlassShaders.kt`、`GlassModifier.kt` | 边缘有折射/色散/高光；快速滚动无撕裂 | 2–3 天 |
| **P4** | 超椭圆 + 镜像过滚 + 触感 | `ui/glass/SquircleShape.kt`、`PullRefreshLayout.kt`、`Haptics.kt` | 圆角顺、下拉有弹性、触感三处生效 | 1.5 天 |
| **P5** | 顶栏快照模糊 + 滚动 scrim + 视频全屏禁用 + 大屏策略补玻璃 | `browser/SnapshotBlurController.kt`、`ui/glass/GlassTopBar.kt`、`TabWebView.kt`、`BrowserScreen.kt` | 滚动 30s 无长帧堆积；视频全屏无黑块；平板正确 | 2 天 |
| **P6** | 外观设置（High/Medium/Off、顶/底栏风格）+ baseline profile + JankStats + R8 校验 | `SettingsScreen.kt`、`app/proguard-rules.pro`、`baseline-prof.txt` | 三档即时生效；release 包能装能开 | 1 天 |

总计约 **10 个工作日**。每步单独出 APK，你先验再进下一步。

---

## 10. 验收：谁验什么

| 项 | 我在沙箱能做 | 只能你真机做 |
|---|---|---|
| 编译 / lint / 单测 / release 打包 | ✅ | — |
| AGSL 是否编译通过、是否出图 | ❌（无 Android 13+ 设备） | ✅ 必须 |
| 玻璃观感（折射强度、色散、圆角、深色对比） | ❌ | ✅ 截图/录屏 |
| 性能（P90 / 长帧 / GPU 时间） | ❌ | ✅ JankStats 输出 |
| 预测性返回 / 共享元素 / 触感 | ❌ | ✅ |

真机验收矩阵（每次 P 结束跑一遍）：
- 设备：Android 13、14、16 各一台（16 为主调参平台）
- 主题：深 / 浅
- 场景：冷启动 → 首页；滚动 30s；开合面板 ×10；切标签 ×5；下拉刷新；视频全屏进出；平板/分屏

---

## 11. 风险与回滚

| 风险 | 缓解 |
|---|---|
| AGSL 在某些 GPU/ROM 上编译失败 | 三级兜底（AGSL → lens → 半透明）；冒烟页先验证 |
| `uniform shader content` 名字不匹配导致全黑 | 冒烟页固定用例 + 日志断言 |
| 顶栏快照抓到旧画面（页面已变） | 保鲜 1.5s + `onPageFinished`/旋转/主题切换 `invalidate()` |
| 玻璃层过多导致视觉糊成一团 | 层数 ≤3、卡片不做玻璃、深浅截图核对 |
| minSdk 33 丢用户 | 已确认；旧版用户停留在 0.32.x |
| targetSdk 36 行为变化（预测性返回/大屏/edge-to-edge） | P0 先落地并回归三项；升 37 单独 PR |
| 依赖升级破坏 API | 版本锁定 2.0.1 / 1.4.1 / 1.0.0 |
| 回滚 | `glass_enabled=false` 一键全站关闭；targetSdk 一行回退 |

---

## 12. 明确不做

- 不做 Navigation Compose 重构（改用 `AnimatedContent + sharedBounds`）
- 不做动态取色（materialkolor 坐标未确认）
- 不做多 Activity / SharedViewKit
- 不做 Lottie / 粒子 / 3D
- 不做"低端机自动降级"（用户手动选质量档）
- 不做 WebView 区域实时 backdrop（物理上抓不到 `<video>`）
- 不在本次升 `targetSdk 37`（见 v1.2 §15 检查清单，后续独立 PR）
