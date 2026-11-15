# Miuix 玻璃（miuix-glass）内嵌说明

## 来源
`compose-miuix-ui/miuix` 的 `miuix-glass` / `miuix-material` / `miuix-blur` 三个模块，
main 分支快照（对应 Compose Multiplatform 1.12.2 / Kotlin 2.4.21，与本工程版本一致）。
glass 模块在 0.9.4 tag 时尚不存在，且 `miuix-material` 未发布到 Maven，故只能源码内嵌。

## 已内嵌（38 个文件，约 460KB）
- `top/yukonga/miuix/kmp/material/`（20）玻璃材质令牌：GlassShape / GlassStyle(s) / GlassMaterials /
  EdgeStroke / drawGlass* / agsl/MaterialAgsl / sensor/DeviceTilt 等
- `top/yukonga/miuix/kmp/blur/`（15）Backdrop 层捕获与效果管线：
  rememberLayerBackdrop / layerBackdrop / BackdropEffectScope / drawBackdrop / ProgressiveBlur
- `top/yukonga/miuix/kmp/glass/`（3）`Modifier.glass(...)` + internal/GlassShader.kt（MiuixGlass AGSL）
  + internal/GlassUniforms.kt

## 相对上游的改动
1. 删除 `miuix-glass` 的 UI 组件（GlassTopAppBar / GlassSurface / GlassMotion / GlassPanel /
   GlassPress 等），它们依赖尚未发布的 miuix-ui 内部 API（BlurTopAppBar、TopAppBarState 新字段）；
   顶栏由工程自己的 `AllegroTopBar` 用 `Modifier.glass` 实现。
2. `expect`/`actual` 合并为 Android 实现（本工程是单平台 Android 应用）：
   RuntimeShader.kt、RenderEffect.kt、sensor/DeviceTilt.kt、blur/internal/RenderEffectCompat.kt。
   合并时给 `rememberDeviceTilt` 补回默认参数 `smoothing = 0.15f`。
3. 不引入 Maven 的 `miuix-blur` 依赖：源码与二进制同包同名会导致重复声明。

## 用法
```kotlin
val backdrop = rememberLayerBackdrop()
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(bg).layerBackdrop(backdrop)) { content() }
    AllegroTopBar(title = …, backdrop = backdrop)   // 内部 Modifier.glass(…, shading = false)
}
```
`backdrop` 必须是**兄弟节点**的内容层，放进被记录的子树会造成采样环。

## 构建注记
本机 aapt2 需经 shim：`-Pandroid.aapt2FromMavenOverride=/opt/aapt2shim/aapt2`
