# Simple Browser

参考 Luna 的简约版 Android 浏览器。当前进度：**里程碑 1（T1–T4）**。

## 已实现

- 工程骨架：Kotlin + ViewBinding + Room + Coroutines，无 Hilt / Compose
- 数据层：tabs / closed_tabs / history / bookmarks / downloads 五张表 + DAO + Repository
- 浏览器引擎：`BrowserController` 接口 + `WebViewBrowserController` 实现
- 页面容器：`TabWebView`（进度、标题、图标、错误页、下载回调）
- 主界面：地址栏（网址/关键词解析）、进度条、底栏 5 个按钮
- 深链：http/https、WEB_SEARCH、文本分享
- 标签持久化（URL/标题，不含页面内状态）与冷启动恢复
- 深色模式：values-night 资源 + 状态栏适配

## 尚未实现（按计划留给后续里程碑）

| 里程碑 | 内容 |
|---|---|
| T5 | WebViewPool + `saveState`，切换标签不重载 |
| T6/T7 | 标签面板、撤销关闭 UI、长按菜单 |
| T8 | JsModule 接口 + 3 个内置脚本 |
| T9 | Action 注册表 + 7 个动作 |
| T10 | 首页（毛玻璃搜索框 + 宫格 + 最近关闭） |
| T11 | 书签 / 历史 / 下载列表页 |
| T11b | 下载进度前台服务 + 通知 |
| T12 | 设置页 + DataStore |
| T13/T14 | 深链细化、saveState 恢复 |

## 构建

1. Android Studio（Koala 或更新）→ Open → 选择本目录 → 等待 Gradle Sync
2. JDK 17、Android SDK 34（Build-Tools 34.0.0）
3. 运行 `app` 配置到 API 26+ 设备或模拟器

命令行（需先生成 wrapper）：

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## 验收清单（T1–T4）

- [ ] 冷启动直接进入百度首页，地址栏显示 URL
- [ ] 输入 `bilibili.com` 回车可打开；输入「天气」走搜索引擎
- [ ] 进度条随加载推进，加载完成自动隐藏
- [ ] 后退/前进按钮在有历史时才可点
- [ ] 加载中刷新按钮变为停止图标
- [ ] 断网点任意站点，出现自定义错误页，点「重试」可重新加载
- [ ] 点网页里的下载链接，通知栏出现系统下载，文件落到应用私有 Downloads
- [ ] 从其他 App 分享链接进来，新开标签并加载该链接
- [ ] 杀进程重启，上次的 URL 仍在
- [ ] 系统切深色，界面不出现白底闪烁

## 已知限制

1. 只有一个活动 WebView，切标签会重新加载页面（T5 解决）。
2. 标签按钮目前只是新建空白标签，没有标签面板（T7 解决）。
3. 下载未写入 downloads 表、无自定义进度通知（T11/T11b 解决）。
4. 非 http(s) scheme（支付宝、微信、intent://）直接拦截，不做外部跳转。
5. 页面内状态（滚动位置、表单内容）不持久化，只存 URL/标题。

## 验证记录（2026-11-13，aarch64 Linux + JDK 17 + AGP 8.5.2）

| 项目 | 结果 |
|---|---|
| `assembleDebug` | ✅ BUILD SUCCESSFUL，APK 3.47 MB |
| 合并后 Manifest | ✅ package `com.example.simplebrowser`，minSdk 26 / target 34，launcher 为 `MainActivity` |
| 资源编译（aapt2） | ✅ 通过（本机构建需 `-Pandroid.aapt2FromMavenOverride` 指向本地 aapt2，仅环境问题） |
| Lint | ✅ 0 error；21 warning 全部是"依赖有新版本"提示 |
| 单元测试 | ✅ 5/5 通过（UrlParser） |
| 运行时 UI | ⚠️ 未验证：本环境无模拟器/真机 |

验证过程中修掉的问题：

1. `TabWebView` 只有 `(Context, Callbacks)` 构造，lint 报 `ViewConstructor` → 改为 `@JvmOverloads (Context, AttributeSet?, Int)`，`callbacks` 改为 `lateinit`，支持 XML 内联；
2. 根布局自绘背景与主题重复，lint 报 `Overdraw` → 删除根布局 `android:background`；
3. 设计令牌未使用触发 10 条 `UnusedResources` → 增加 `res/raw/keep.xml` 显式保留；
4. `UrlParser` 依赖 `android.net.Uri`，无法纯 JVM 测试 → 改用 `URLEncoder`，并补齐 `localhost:port` 识别，新增 5 个单测。

---

## 0.2.0-beta：UI 迁移到 Compose + MIUIX

### 技术栈变更（替代 1.x 中"不引入 Compose/MIUIX"的约束）

| 项 | 旧 | 新 |
|---|---|---|
| UI | AppCompat + ViewBinding + XML | **Compose + MIUIX 组件** |
| MIUIX | — | `top.yukonga.miuix.kmp:miuix:0.8.8` |
| Compose | — | Compose Multiplatform `1.10.3`（Android 运行时） |
| Kotlin | 1.9.24 | **2.3.20** |
| AGP / Gradle | 8.5.2 / 8.7 | **8.13.2 / 8.14.3** |
| compileSdk | 34 | **36** |
| Room | 2.6.1 | 2.7.2 |

**为什么不是 MIUIX 0.9.4**：0.9.4 的 AAR 要求 `compileSdk 37` 且依赖 Compose 1.12，而后者又要求 **AGP ≥ 9.1**（AGP 9 对 DSL 有破坏性变更）。当前环境与工程先用 0.8.8（同一项目、`minCompileSdk 36`、AGP 8.x 可用），API 包名一致，后续可整体升级。

### UI 结构

```
MainActivity (ComponentActivity)
└── MiuixTheme(lightColorScheme()/darkColorScheme())
    └── BrowserScreen
        ├── Scaffold(topBar = 地址栏)
        │   ├── TextField（MIUIX，圆角/交互由主题提供）
        │   └── IconButton + LinearProgressIndicator
        ├── Scaffold(bottomBar = 4 个 IconButton)
        └── AndroidView → TabWebView
```

- 配色/圆角/字阶全部来自 MIUIX 主题，删除自绘的 `colors/dimens/bg_address`；
- 深色模式由 `isSystemInDarkTheme()` + `darkColorScheme()` 切换；
- WebView 仍是纯 View，通过 `AndroidView` 嵌入，**依然只经 `BrowserController`**（新增 `attachWebView()`）。

### 版本号约定

> 编译测试期间，所有 APK 的 versionName 一律带 `-beta` 后缀。

- `versionName = "0.2.0-beta"`，`versionCode = 2`（app/build.gradle.kts）
- 产物统一命名为 `SimpleBrowser-<versionName>-<buildType>.apk`
- 转正式版时：去掉 `-beta`、`versionCode +1`，其余不变

### 验证（2026-11-13）

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ 通过 |
| 单元测试 | ✅ 5/5 |
| Lint | ✅ 0 error，12 warning（均为版本更新提示 + OldTargetApi） |
| APK | 10.2 MB（Compose/MIUIX 引入后体积增大） |
| 版本 | versionName `0.2.0-beta` / versionCode 2 |
| 运行时 | ⚠️ 未验证（无真机/模拟器） |

---

## 0.3.0-beta：AGP 9 迁移 + MIUIX 0.9.4

| 项 | 0.2.0-beta | 0.3.0-beta |
|---|---|---|
| AGP | 8.13.2 | **9.5.1** |
| Gradle | 8.14.3 | **9.9.1** |
| Kotlin | 2.3.20 | **2.4.21** |
| Compose | 1.10.3 | **1.12.2** |
| MIUIX | 0.8.8（`miuix`） | **0.9.4（`miuix-ui`）** |
| compileSdk / targetSdk | 36 | **37** |
| Room | 2.7.2 | 2.8.5 |

迁移中处理的三类问题：

1. **依赖解析顺序**：`org.jetbrains.compose:*` 在 Maven Central 而非 Google Maven，`google()` 排前面会先打一次失败请求 → `settings.gradle.kts` 改为 `mavenCentral()` 优先；
2. **AGP 9 内置 Kotlin 与 KGP 冲突** → `gradle.properties` 增加 `android.builtInKotlin=false`，`kotlin { compilerOptions }` 移到 `android {}` 之外；
3. **Google Maven 连接偶发超时** → `gradle.properties` 提高 HTTP 超时并强制 IPv4。

## 0.4.0-beta：T5–T7

- **T5 WebViewPool**（`browser/WebViewPool.kt`）：标签 ⇄ WebView 映射，最多 4 个常驻；超出时对最久未访问的 `saveState` 冻结，切回时 `restoreState` 还原，**切换标签不再重新加载**。
- **T6 标签面板**（`BrowserScreen.kt`）：全屏 MIUIX 卡片列表，显示标题/URL、关闭、新建、恢复关闭；当前标签用主题色标注。
- **T7 长按菜单**：`TabWebView` 识别链接/图片命中，控制器产出 `MenuRequest`，Compose 底部浮层展示动作（新标签、后台标签、复制、下载、分享；图片为保存/复制地址）。

新增文件：`browser/WebViewPool.kt`、`browser/MenuActions.kt`、`browser/ErrorPage.kt`、`res/drawable/ic_close.xml`。

> 注：本环境下载 `lint-gradle:32.5.1` 持续超时，0.3.0/0.4.0 的 lint 未执行；上一次成功 lint（0.2.0-beta）为 0 error。

## 0.5.0-beta：T8 JS 管线 + T9 Action 注册表

### T8 注入管线

```
js/JsModule.kt      接口：id / injectAt(DOC_START|DOC_END) / priority / js(ctx)
js/JsRegistry.kt    注册表，default() 里注册内置模块
js/JsInjector.kt    同一时机合并成一段脚本，一次 evaluateJavascript
js/modules/         ViewportModule / InvertModule / BlobDownloadModule
```

- 注入点：`onPageStarted`（DOC_START）、`onPageFinished`（DOC_END）；
- 三个内置模块都做了幂等（`window.__sb*` 标记）；
- `ViewportModule` 在 head 未就绪时挂 DOMContentLoaded 重试；桌面模式给 1280px；
- `InvertModule` 暴露 `window.__sbInvert(true/false)`，图片/视频二次反转（UI 开关留到 T10 工具栏）；
- `BlobDownloadModule` 拦截 `a[download][href^=blob:]`，经 `SimpleBrowserBridge` 把 ≤8MB 的 blob 以 base64 交给原生落盘。

**新增一个注入脚本**：实现 `JsModule` → 在 `JsRegistry.default()` 里 `register(...)`，不需要动 UI 与控制器。

### T9 Action 注册表

```
action/BrowserAction.kt   接口：id / titleRes / iconRes / groups / isAvailable / execute
action/BuiltInActions.kt  7 个内置动作（新标签、后台标签、复制链接、复制图片地址、下载、保存图片、分享）
action/ActionRegistry.kt  注册表 + forGroup()
```

长按菜单不再写死条目，改为 `ActionRegistry.forGroup(...)` 渲染，菜单项自带图标。

**新增一个动作**：实现 `BrowserAction` → 加到 `ActionRegistry.builtIn`，菜单自动出现。

### 验证

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.5.0-beta / versionCode 5 |
| APK | 10.9 MB |
| Lint | ⚠️ 未执行（lint-gradle 下载超时） |
| 运行时 | ⚠️ 未验证 |

## 0.6.0-beta：T10 首页 + 夜间模式开关

- **首页**（`ui/HomeScreen.kt`）：毛玻璃搜索框（MIUIX TextField + 半透明背景）、8 个常用站点宫格、最近关闭（读 `closed_tabs` 最近 3 条）、渐变光斑背景（`Modifier.blur`，API 31 以下自动降级）。
- **首页触发**：无标签或当前标签为 `about:blank` 时覆盖显示；`restoreTabs()` 不再自动打开百度，改为空状态进首页。
- **夜间模式按钮**：底栏新增月亮图标，`TabState.invertEnabled` + `window.__sbInvert()` 联动；页面加载完成时若开启会自动应用。
- **修复**：`persistNow()` 原实现在标签为空时直接 return，导致"关掉全部标签后重启又恢复旧标签"；改为总是写入（空列表即清空）。
- **清理**：删除未使用的 `ui/chrome/BrowserChrome.kt`（与 BrowserScreen 重复的旧实现）。

### 验证

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.6.0-beta / versionCode 6 |
| APK | 14.9 MB（AGP 9 对条目做 16KB 对齐，实际压缩内容约 10.9 MB） |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.7.0-beta：主题系统 / 返回键 / 过渡动画

针对反馈的四项：

| 问题 | 修复 |
|---|---|
| 误把"深色模式"做成网页反色 | 底栏按钮改为 **主题循环**（浅色 → 深色 → 跟随系统）；网页反色降级为**长按**按钮触发 |
| 浏览器 UI 不跟随变色 | `ThemeRepository`（SharedPreferences + StateFlow）→ `BrowserTheme(mode)` 驱动整套 MIUIX 配色；状态栏图标用 `WindowInsetsControllerCompat` 同步反转；根 View 背景色跟随主题 |
| 过渡生硬 | 首页、标签面板、长按菜单全部 `AnimatedVisibility`（fade + slide，140–250ms）；底栏背景 `animateColorAsState`；关闭菜单时保留最后一次内容，保证退场动画完整 |
| 返回键直接回桌面 | Compose `BackHandler` 分级拦截：菜单 → 面板；`MainActivity` 只在网页可后退时 `goBack`，根页面改为**连按两次退出**并 Toast 提示；Manifest 开启 `enableOnBackInvokedCallback` |

新增：`data/ThemeRepository.kt`、`data/model/ThemeMode.kt`、`res/drawable/ic_sun.xml`。

### 验证

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.7.0-beta / versionCode 7 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.7.1-beta：网页深色规整 + 返回动画

### 网页深色（替换原"反色"）

`js/modules/PageDarkModule.kt`，规则按需求收紧：

1. `prefers-color-scheme: dark` 命中 → **什么都不做**，交给网页自己的深色样式；
2. 未命中 → 只给 `html/body` 加黑色背景 + `color-scheme: dark`（让 UA 默认文字/控件走深色）；
3. **不对任何元素做反色、不修改作者定义的颜色**。

原生 API 由 `toggleInvert()` 改为 `togglePageDark()`，状态字段 `pageDarkEnabled`。

> 已知边界：WebView 的 `prefers-color-scheme` 跟随**系统**深色，不跟随 App 内主题。若"App 深色 + 系统浅色"，网页会被判定为不支持深色，从而只改背景。彻底修需让 Activity 的 `uiMode` 跟随 App 主题（会触发 recreate），留待评估。

### 返回动画

- `OnBackPressedCallback` 新增 `handleOnBackStarted / handleOnBackProgressed / handleOnBackCancelled`（activity 1.13 的预测性返回 API）；
- 手势进度通过 `mutableFloatStateOf` 传给 Compose，根布局 `graphicsLayer` 做 **缩放 0.94 + 透明度 0.75** 的跟手过渡；
- 取消手势自动回弹；完成后执行"网页后退 / 连按两次退出"。

### 验证

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.7.1-beta / versionCode 8 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.8.0-beta：T11 列表页 + T12 设置

### T11
- `ui/screens/BookmarksScreen.kt` / `HistoryScreen.kt` / `DownloadsScreen.kt`：MIUIX Card 列表、空状态、单条删除、历史清空；
- 顶栏新增"菜单"入口（`ic_menu`）：收藏本页 / 书签 / 历史 / 下载 / 设置；
- 下载现在会写入 `downloads` 表（长按下载与网页下载回调都记录），列表可查看与删除。

### T12
- `ui/screens/SettingsScreen.kt`：主题三态、搜索引擎、清除浏览数据、版本号；
- `SettingsRepository` 统一管理 `themeMode` / `searchEngineId`（**仍是 SharedPreferences**，未上 DataStore——避免为一个键值再加依赖与异步初始化，后续需要再迁）；
- 地址栏与首页搜索都改为读设置里的搜索引擎，切换即时生效。

### 验证

| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.8.0-beta / versionCode 9 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.8.1-beta：修复菜单点不开 + 入口重排

### 根因
AndroidView（WebView）是真实 View，绘制层级高于 Compose 画布。上一版把菜单/标签面板/首页都做成 Compose 覆盖层，**实际被 WebView 盖住了**——点击有效但看不见，表现为"三个点没反应"。

### 修复
- 覆盖层可见时把 WebView 宿主设 `View.GONE`（`AndroidView.update`），关闭后恢复；
- 覆盖层用 `MutableTransitionState` 跟踪进出动画状态，避免退场时 WebView 提前出现；
- 首页显示时同样隐藏 WebView，减少无谓绘制。

### 入口调整
- 底栏改为：后退 / 前进 / 主页 / **标签（圆角方框 + 数量）** / 菜单；
- 菜单（☰）里集中：收藏本页、书签、历史、下载、主题（当前值）、网页深色（开关）、设置；
- 标签按钮显示实时数量，不再是一个不明确的列表图标。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.8.1-beta / versionCode 10 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.9.0-beta：标签网格 / 缩略图 / T13·T14 收尾

### 标签网格
- `Overlays.kt` 的标签面板改为 `LazyVerticalGrid` 2 列卡片（宽高比 0.62）；
- 缩略图：切换标签或打开面板前，用 `view.draw(Canvas)` 缩放到 360px 宽截图，存 `TabState.preview`（仅内存，约 0.5MB/张）；
- 当前标签用主题色描边，未截到图时显示标题首字占位。

### T14 跨进程恢复
- `persistNow` 对每个存活 WebView 调 `saveState` → `Bundle` → `Parcel.marshall()` → 存入 `tabs.state`；
- 冷启动 `restoreTabs` 把字节放进 `pendingStates`，首次创建 WebView 时 `restoreState` 还原页面与前进后退栈。

### T13 深链
- Manifest 补 `file` scheme 与 `ACTION_PROCESS_TEXT`；
- 划词菜单（选择文本 → 分享/搜索）可直接进入 Luna 搜索。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.9.0-beta / versionCode 11 |
| APK | 11.0 MB |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.9.1-beta：圆角对齐 + 设置页改用 MIUIX 组件

### 圆角
标签卡片外框是 MIUIX `CardDefaults.CornerRadius = 16dp`，内层预览图却写死 10dp，导致内外圆角错位。
改为统一 16dp，并给文字区补 8dp 内边距。

### 设置页
接入 `top.yukonga.miuix.kmp:miuix-preference:0.9.4`：

- 顶部换成 `SmallTopAppBar`（返回按钮走 MIUIX 样式）；
- 主题 / 搜索引擎：`Card` 包一组 `RadioButtonPreference`，行间 `HorizontalDivider`；
- 清除数据：`ArrowPreference`；
- 版本信息：`BasicComponent`。

顺带把书签/历史/下载三个列表页的头部也统一为 `SmallTopAppBar`，全应用头部样式一致。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.9.1-beta / versionCode 12 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.10.0-beta：下载进度（T11b）

新增 `download/` 模块：

| 文件 | 作用 |
|---|---|
| `DownloadCenter.kt` | 统一入队 / 轮询 / 取消 / 打开；`StateFlow<Map<Long, DownloadProgress>>` |
| `DownloadNotificationService.kt` | 前台服务（dataSync），通知栏显示百分比 + 取消按钮，全部结束 10 秒后自动停止 |
| `DownloadActionReceiver.kt` | 通知"取消"按钮 → `DownloadCenter.cancel` |

- 轮询：仅当 `downloads` 表存在 `status IN (0,1)` 时每秒查询一次 DownloadManager，并把状态/字节数写回 DB；
- 列表：`DownloadsScreen` 显示真实百分比进度条、已完成可点击打开、失败/取消有对应文案；
- 权限：补齐 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`，首次启动申请通知权限。

> 验证时发现一个真问题：Manifest 在某次重构中丢了 `POST_NOTIFICATIONS` 与前台服务权限，Android 14 上启动服务会直接抛 SecurityException，已补回。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 权限/组件 | ✅ aapt2 dump 确认 5 个权限 + service/receiver 已注册 |
| 版本 | 0.10.0-beta / versionCode 13 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.11.0-beta：统一面板 / 去重 / 图标系统

### 统一底部面板
新增 `ui/components/MiuixSheet.kt`：
- 32% 黑色遮罩（点击关闭）+ 顶部 28dp 圆角 + 拖拽条；
- `SheetSection` 分组标题、`SheetRow` 单行（图标 + 标题 + 右值）；
- 主菜单与长按菜单**共用同一个组件**，不再各写一套。

主菜单结构：常用（主页/收藏/书签/历史/下载）→ 外观（主题：当前值 / 网页深色：开|关）→ 更多（设置）。

### 入口去重
- 删除顶栏的 ☰，只保留地址栏 + 刷新；
- 底栏四项：后退 / 前进 / 标签（数字）/ 菜单；
- 主页入口移到菜单第一行。

### 图标系统
- 引入 `top.yukonga.miuix.kmp:miuix-icons:0.9.4`，全部改用 `MiuixIcons.*`；
- `BrowserAction.icon` 从 drawable 改为 `ImageVector`；
- 删除 13 个手绘 vector，只保留 `ic_launcher` 与通知栏用的 `ic_download`。

### 顺带清理
- 删除未使用的 `ui/components/UiKit.kt`（早期重复实现）。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.11.0-beta / versionCode 14 |
| APK | 12.2 MB（图标库略增体积） |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.12.0-beta：设计令牌 / 空状态 / 页面转场

- `ui/theme/Tokens.kt`：Spacing / Radius / IconSize 三组令牌，新代码不再写裸 dp；
- `ui/components/EmptyState.kt`：图标 + 标题 + 说明 + 可选按钮，书签/历史/下载三页统一使用；
- 二级页面（书签/历史/下载/设置）改为 **覆盖层 + 滑入淡入**（250ms），浏览器始终在下面存活，返回时滑出；
- 首页搜索框：加放大镜前置图标、圆角收敛到 16dp 令牌。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.12.0-beta / versionCode 15 |
| APK | 12.2 MB |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.13.0-beta：新标签页重做 + 冷启动修复

### 修复
- `restoreTabs()` 原实现直接激活并加载上次的标签（常是百度），且会覆盖用户在恢复完成前新开的标签。
  现在：① 若已有标签则放弃恢复；② 恢复出的标签只入列表、不激活、不加载，冷启动一律停在首页。

### 新标签页（参考 Luna）
- 问候语 + 日期；毛玻璃搜索框（放大镜 + 16dp 圆角）；
- 常用站点 4×2 宫格（渐变底 + 首字）；
- 「最近关闭」与「书签」两个模块，使用 `Card + BasicComponent + Divider`，与设置页同构；
- 背景三层模糊光斑；内容整体淡入上移（320ms）。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 5/5 |
| 版本 | 0.13.0-beta / versionCode 16 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.13.1-beta：修复闪退（重要）

**现象**：0.13.0 打开即崩。

**根因**：首页用 `LocalTime.now()` 配合 `"M月d日 EEEE"` 格式化日期。`LocalTime` 没有
`MonthOfYear` / `DayOfMonth` 字段，`DateTimeFormatter` 直接抛
`UnsupportedTemporalTypeException`，首页一组合就崩溃。

**修复**：
- 日期改用 `LocalDate.now()`；
- 把日期/问候逻辑抽到 `ui/home/HomeDate.kt`，并补 JVM 单元测试（`HomeDateTest`），
  验证"日期格式化不抛异常 + 问候语边界"，这类问题以后编译期就能被测试拦住。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7（新增 2 条） |
| 版本 | 0.13.1-beta / versionCode 17 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

## 0.14.0-beta：标签切换器缩放动画（T14-1 ~ T14-3）

学习 Luna 的 Stub → Grid 思路，自己实现共享元素，不依赖第三方库。

### 实现
- `BrowserController.captureStub()`：把当前 WebView 画到 720px 宽的位图，作为动画起点；
- `ui/TabSwitcherOverlay.kt`：
  - 打开时 `Animatable` 0→1（spring 0.85 / 380），stub 从全屏矩形插值到目标卡片矩形；
  - 网格同步淡入 + 轻微放大（0.96→1）；
  - 关闭时反向播放（spring 0.9 / 420），结束后回调 `onDismissed` 释放位图；
  - 目标矩形通过卡片 `boundsInWindow()` 实测，减去覆盖层原点得到本地坐标；
  - 动画开始时**锁定**目标矩形，切换标签不会让 stub 跳变；
  - 取不到矩形（例如目标卡片不在首屏）时退回屏幕中央的默认区域。
- 覆盖层打开期间 WebView 宿主保持 GONE，避免真实 View 盖住 Compose 动画。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.0-beta / versionCode 18 |
| Lint | ⚠️ 未执行 |
| 运行时 | ⚠️ 未验证 |

### 待续（T14-4 ~ T14-6）
- 下拉/上滑关闭 + 跟手；
- 拖拽排序、滑动删除、长按卡片菜单；
- 低端机降级（关闭缩放，改 200ms 淡入）。

## 0.14.1-beta：修切换器"闪 + 不触发动画"

### 两个 bug
1. **闪**：父级在点击瞬间就把 WebView 设为 GONE，而覆盖层内部还有 `mounted` 门控，
   第一帧覆盖层不渲染 → 空白一帧。
2. **不触发缩放**：动画目标依赖"卡片测量值"，用 `withTimeoutOrNull(150)` 等；
   超时后走默认矩形，且目标每帧重算会跳。更糟的是 WebView 已 GONE 时抓到的 stub 是空图，
   于是只剩网格淡入，看起来像"淡出"。

### 修法
- 去掉 `mounted` 门控：`visible || progress > 0` 就渲染，和 WebView 隐藏发生在同一帧；
- 目标矩形改为**确定性几何计算**（2 列 / 12dp 边距 / 8dp 间距 / 0.62 宽高比 / 44dp 文字区），
  不再等待测量，也不会跳；
- `captureStub()` 增加两道检查：WebView 必须 VISIBLE、位图必须是"有内容"（抽样 9 点全same/全透明则丢弃）；
- 无 stub 时仍保留网格 0.96→1 的缩放淡入，不会退化成纯淡出。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.1-beta / versionCode 19 |
| 运行时 | ⚠️ 待真机确认 |

## 0.14.2-beta：Stub 改用 PixelCopy（修复"不缩小"）

**现象**：点标签按钮后网页没有缩小，直接变网格（0.14.1 的 B 类表现）。

**根因**：WebView 是硬件渲染的，`view.draw(Canvas)` 画到软件位图上拿不到内容，
`captureStub` 永远返回 null，动画退化成网格缩放淡入。

**修复**：
- 改用 `PixelCopy.request(window, rect, bitmap, listener)`，从系统合成层拿真实画面；
- 只截 WebView 区域（`getLocationInWindow` + 宽高），与网格卡片预览一致；
- 截图是异步的：**先等位图回来，再打开面板并启动动画**（约 1 帧延迟）；
- 失败（DRM/受保护内容、rect 越界）仍回退为网格缩放淡入。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.2-beta / versionCode 20 |
| 运行时 | ⚠️ 待真机确认 |

## 0.14.3-beta：Stub 诊断 + 整窗兜底

- `captureStub` 全链路加日志（TAG = `StubCapture`）：视图不可见 / PixelCopy 返回码 / 区域截图失败 / 整窗失败；
- 区域截图失败时自动退回**整窗截图 + 按坐标裁剪**；
- 目的：确认 PixelCopy 是否真的拿不到 WebView 画面，再决定是否改用其它方案。

## 0.14.4-beta：动画兜底 + 下拉关闭（T14-4）

- **动画兜底**：截图不可用（PixelCopy 失败/受保护内容）时，用一块主题色占位 + 标题首字代替 stub，
  仍然从全屏缩到卡片，不再退化成"网格淡入"。
- **下拉关闭**：网格层支持垂直拖拽跟手，位移超过 96dp 或快速下滑即关闭；
  遮罩随拖拽变淡；**仅当网格滚动到顶部时接管手势**，避免抢走列表滚动。
- 松手未达阈值时用 `animate` 回弹到原位。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.4-beta / versionCode 22 |
| 运行时 | ⚠️ 待确认 |

## 0.14.5-beta：标签卡片长按菜单 + 滑动删除 + 低端机降级（T14-5 / T14-6）

- **长按卡片**：弹出 `MiuixSheet`，三项——关闭其他标签 / 复制链接 / 关闭此标签；
  `BrowserController.closeOtherTabs(keepId)` 只保留当前标签并清理其余 WebView。
- **左滑删除**：卡片跟手左移（最大 120dp），露出红色删除底；松手超过 60% 或点击时删除，
  未达阈值回弹。
- **低端机降级**：`ActivityManager.isLowRamDevice` 为 true 时跳过截图，
  用占位块 + `tween(180)` 短动画，避免在低端机上卡顿。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.5-beta / versionCode 23 |
| 运行时 | ⚠️ 待确认 |

## 0.14.6-beta：新标签页去推荐 + 新图标

### 新标签页（再次收敛）
- **移除常用站点宫格**，首页只剩：问候语 + 日期 → 毛玻璃搜索 → 最近关闭 → 书签；
- 无任何推荐内容，空白开始页；
- 背景仍是三层模糊光斑，内容淡入上移 320ms。

### 应用图标
- 改为 **自适应图标**（`mipmap-anydpi-v26`），背景色 `#0088FF`，前景是白色地球：
  外圈 + 经线 + 赤道/轨道 + 右上卫星点；
- Manifest 使用 `@mipmap/ic_launcher` 与 `@mipmap/ic_launcher_round`；
- 旧的手绘 `drawable/ic_launcher.xml` 已删除。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 图标 | ✅ aapt2 确认 `application-icon` 指向 mipmap |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.6-beta / versionCode 24 |

## 0.14.7-beta：修切换器覆盖不全 + 多标签跳变

1. **顶部/底部控件一直可见**：切换器原来放在 `Scaffold` 的内容区里，地址栏和底栏在 Scaffold 外层，
   永远盖不住。现在把 `TabSwitcherOverlay` 移到 `Scaffold` 之外（外层 Box），全屏覆盖，
   目标矩形加入状态栏高度补偿。
2. **超过 4 个标签概率异常**：目标卡片索引原来每帧按 `activeTabId` 重算，
   标签关闭/滚动后会跳变。现在打开瞬间**锁定索引**，整个动画期间不变；
   索引越界时钳到最后一个。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.7-beta / versionCode 25 |

## 0.14.8-beta：切换器过渡重做 + 二级页层级修复 + 导航反馈

1. **截图 → 模糊遮罩**：切换器背景铺一张全屏 stub，随进度压暗并保持 24dp 模糊，
   叠 0→32% 黑色遮罩；前景 stub 从全屏缩到卡片（spring 0.85/380 ≈ 300ms）。
2. **打断处理**：打开时不再 `snapTo(0)`，动画被打断会从当前进度继续/反向（`Animatable` 自带），
   与 Luna 的"不重置、顺着当前速度走"一致。
3. **二级页面闪屏**：书签/历史/下载/设置原来渲染在 Scaffold 内容区，会和主界面顶栏叠加；
   现在移到 Scaffold 之外全屏渲染，右滑淡入 250ms / 退出 200ms，不再闪。
4. **底部后退/前进无反应**：原来无历史时按钮 `enabled=false`，点了没反馈；
   现在始终可点，无历史时 Toast 提示"已经是第一页 / 没有可前进的页面"。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.8-beta / versionCode 26 |

## 0.14.9-beta：切换器动画重写（updateTransition）

**问题**：背景不模糊、动画硬切。

**根因**：
1. 用 `Animatable` 手动驱动，覆盖层首次组合时拿不到尺寸（`onSizeChanged` 首帧为 0），缩略块被 `screenSize > 0` 守卫跳过；
2. 每次动画前手动 `snapTo`，容易在打断/重入时把进度重置到不合预期。

**改法**：
- 改用 `updateTransition(visible)` + `animateFloat(spring(0.85, 380))`：
  首帧即为 0，打断自动反向，不需要手动 snap；
- 用 `BoxWithConstraints` 取尺寸，**首帧就可用**；
- 网格起始缩放 0.82（原来 0.96，几乎看不出动），有/无截图都保证有可见的缩放；
- 背景：截图铺满 + `blur(24.dp)`，随进度压暗并叠 0→34% 黑遮罩。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.9-beta / versionCode 27 |

## 0.14.10-beta：控件随页面收缩（Luna 同款）

- **整窗截图**：`captureStub` 改为截整个 window（含地址栏/底栏），缩进卡片时控件一起被收进去；
- **控件淡出兜底**：顶栏上移 -32dp、底栏下移 +32dp，alpha 随同一进度 1→0，
  截图失败时控件也不会"啪"地消失；
- 进度提升到 `BrowserScreen`（`updateTransition(showTabs)`），顶/底栏与切换器共用同一条 spring 曲线。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.10-beta / versionCode 28 |

## 0.14.11-beta：隐藏 about:blank + 二级页面无搜索栏

- 地址栏不再显示 `about:blank`：空白页时地址栏保持为空，显示占位提示；
- 进入书签/历史/下载/设置时，`topBar` 与 `bottomBar` 直接不渲染（`return@Scaffold`），
  二级页面用自己的 `SmallTopAppBar`，避免出现"两层标题栏 + 搜索框"。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.11-beta / versionCode 29 |

## 0.14.12-beta：修二级页面硬切

**根因（两个叠加）**：
1. `AnimatedVisibility(visible = ...)` 在**首次组合时就是 true**，Compose 不会播放进入动画，
   看起来就是"啪"地出现 → 改用 `MutableTransitionState(false)` + `visibleState`；
2. 顶/底栏用 `return@Scaffold` 直接不渲染，Scaffold 在同一帧重算布局，
   内容区高度突变，进一步放大"硬切"感 → 改为**始终渲染 + `chromeAlpha` 淡出并轻微位移**。

现在进入设置页：顶/底栏 200ms 淡出并位移，二级页面 260ms 从右侧滑入 + 淡入。

## 0.14.13-beta：过渡软化 + 标签拖拽排序

### 过渡软化（回应"遮罩太明显、动画太生硬"）
| 项 | 旧 | 新 |
|---|---|---|
| 切换器曲线 | spring(0.85, 380) | spring(0.94, 260)（更慢、几乎无过冲） |
| 遮罩 | 黑 34% | 黑 **12%**（靠背景模糊与缩放表达层次，不靠压黑） |
| 二级页面进入 | tween 260 线性 | tween 320 + FastOutSlowIn |
| 二级页面退出 | tween 220 | tween 260 + FastOutSlowIn |

### 拖拽排序
- `BrowserController.moveTab(from, to)`：调整顺序并触发重组；
- 卡片 `detectDragGesturesAfterLongPress`：长按拿起 → 跟手位移 + 放大 1.04 → 跨过单元格阈值即交换；
- 交换后重置位移基准，避免累积误差；松手回到原位。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 7/7 |
| 版本 | 0.14.13-beta / versionCode 31 |

## 0.15.0-beta：广告过滤（规则引擎 + 拦截 + 元素隐藏 + 设置项）

### 引擎（adblock/FilterEngine.kt）
支持 EasyList 子集：`||host^`、`||host/path^`（路径限定）、`@@` 例外（优先）、`/path/` 片段、`host##.sel` 站点隐藏、`##.sel` 全局隐藏；忽略注释与 `[...]` 头。
不支持：正则 `/.../`、`$` 修饰符细分、`#@#` 例外（已在代码注释里标明）。

**测试发现两个真实 bug**（已修）：
1. `@@||ads.example.com/allow^` 被解析成"整站例外"，导致该域全部放行 → 增加 `pathContains`；
2. `/adserver/` 这类以 `/` 开头的规则被丢弃 → 允许 `/` 开头的片段规则。

### 仓库（adblock/AdBlockRepository.kt）
- 内置 `assets/adblock/builtin.txt`（102 行，离线可用）；
- 4 个订阅源，主源 + jsDelivr 镜像回退，单文件上限 12MB，下载后校验特征串；
- 全部失败时保留旧规则；`blocked` 计数持久化。

### 接入
- `TabWebView.shouldInterceptRequest` → 命中返回空响应；
- 页面加载完成注入 `CosmeticInjector` 生成的一次性样式；
- 设置页新增「广告过滤」：总开关、已拦截数、更新规则、重置统计、逐源开关。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ **14/14**（FilterEngine 7 条新增） |
| 版本 | 0.15.0-beta / versionCode 32 |

## 0.15.1-beta：修复误拦正常网页

**三个成因**（叠加导致白屏 / 样式丢失）：

1. **主文档也会被判断** —— `shouldInterceptRequest` 没区分主文档，页面自身 URL 命中规则就整页空白；
2. **同站资源被拦** —— 内置规则里的 `/analytics.js`、`/gtag/js`、`/collect?v=`、`/adsbygoogle.js`
   这类宽泛片段，很容易命中网站自己的 js/css，导致页面坏掉；
3. **片段规则太泛** —— `/ad-banner/`、`/banners/ad/` 这类路径在正规站点也会出现。

**修法**：
- `shouldInterceptRequest` 里 `request.isForMainFrame` 直接 `return null`：**主文档永不拦截**；
- 新增 `FilterEngine.shouldBlock(url, includePathFragments)`：同站请求只允许域名类规则命中，
  路径片段规则仅在跨站生效；
- 内置规则删除 7 条高风险泛化路径，只保留 `/adserver/`、`/popunder/`、`/prebid` 三条。

### 验证
| 项 | 结果 |
|---|---|
| 单元测试 | ✅ **16/16**（新增主文档/同站 2 条） |
| 版本 | 0.15.1-beta / versionCode 33 |

## 0.15.2-beta：站点白名单

- `AdBlockRepository` 增加白名单（`SharedPreferences` 字符串集合）；
- 菜单新增「本站暂停过滤」：一键对该域名放行，**立即重载**生效；
- 白名单命中的站点在拦截器入口直接放行（不进规则匹配）。

## 0.16.0-beta：下载 2.0（速度/剩余/重试）

- `DownloadProgress` 增加 `speedBytesPerSec` / `etaSeconds`：按 1s 采样差分计算；
- 通知栏内容改为「文件名 · 速度 · 剩余时间」；
- 下载列表显示速度与剩余时间，失败项显示「重试」（用原 URL 重新入队）；
- 已完成项点击调用系统打开。

> 暂停/继续仍需自研下载器（DownloadManager 无公开 pause API），留到「下载 3.0」。

### 验证
| 项 | 结果 |
|---|---|
| 单元测试 | ✅ 16/16 |
| 版本 | 0.15.2 / 0.16.0，versionCode 34 / 35 |

## 0.17.0-beta：自研下载器（暂停 / 继续 / 断点续传）

替换 `DownloadManager`，改为 OkHttp + RandomAccessFile：

| 能力 | 实现 |
|---|---|
| 断点续传 | 记录 `.length()`，下次请求带 `Range: bytes=N-`；服务器不支持（非 206）则清空重下 |
| 暂停 | 取消 Job，保留已下载文件，状态置 `PAUSED` |
| 继续 | 从文件长度续传 |
| 取消 | 取消 Job 并删除临时文件 |
| 进度/速度/剩余 | 每 200ms 节流推送，1s 差分算速度 |
| 通知 | 前台服务，含「暂停 / 取消」动作，无任务 3s 后自停 |
| 打开文件 | FileProvider（`${applicationId}.fileprovider`，路径 `Download/`） |

- 临时文件命名 `dl_<id>.bin`，下载完成后仍用该文件（避免改名时的原子性问题）；
- 状态新增 `PAUSED = 5`；列表按状态显示暂停/继续/重试按钮；
- 依赖新增 `okhttp 4.12.0`；Manifest 增加 FileProvider。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 16/16 |
| 权限/组件 | ✅ 6 个权限 + `DownloadService` + FileProvider 已打包 |
| 版本 | 0.17.0-beta / versionCode 36 |

## 0.17.1-beta：修暂停 bug + 下载页重排

### 暂停异常（真 bug）
- **根因**：OkHttp 的 `execute()` 是阻塞调用，`job.cancel()` 不会中断读取，
  于是"暂停"后文件仍在增长，且 `pause()` 与协程 `catch` 双方同时改状态 → 状态错乱、续传错位。
- **修法**：
  1. 保存 `Call`，`pause()`/`cancel()` 里调 `call.cancel()` 真正打断 IO；
  2. 用 `intents` 记录用户意图（pause / cancel），`catch(IOException)` 按意图落状态，不再误判为失败；
  3. `resume()` / `retry()` 先 `join()` 上一个任务，保证同一文件只有一个写者；
  4. 循环内检查 `intents`，暂停后立刻退出写入。

### 下载页重排
- 按「进行中（N）」「已完成（N）」分组，减少长列表压迫感；
- 每行只保留**一个主操作**（暂停 / 继续 / 重试 / 删除），不再并排挤三个图标；
- 文件名 15sp 单行省略，状态 12sp 副行；进度条单独一行，百分比左、速度与剩余右对齐；
- 统一 `Spacing` 令牌（行内 12/16，卡片间距 8）。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 16/16 |
| 版本 | 0.17.1-beta / versionCode 37 |

## 0.17.2-beta：修复暂停/取消完全不生效

**真正的根因**（0.17.0 就存在，之前几版没抓到）：
`DownloadDao.updateProgress` 的 SQL 是 `WHERE dmId = :dmId`，
但自研下载器传的是**数据库行 id**，而 `dmId` 对所有新记录都是 `0`。
结果：
- 状态/进度**写不到对应记录**（`WHERE dmId = 0` 可能一次改到所有行）；
- 数据库里的状态永远是"运行中"，所以暂停看起来毫无反应；
- 取消同理，只是把记录状态乱改。

**修法**：
- SQL 改为 `WHERE id = :id`，并在注释里写明这个历史坑；
- 列表里"进行中/已暂停"的条目增加**取消**按钮（×），走 `center.cancel(id)`（同时删除临时文件），
  而不是只删数据库记录；
- 已完成/失败条目才显示"删除记录"。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 16/16 |
| 版本 | 0.17.2-beta / versionCode 38 |

## 0.18.0-beta：手势与效率

| 功能 | 实现 |
|---|---|
| 工具栏左右滑动切标签 | 底栏 `detectHorizontalDragGestures`，累计位移超 72dp 触发 `switchTab(±1)` |
| 下拉刷新 | 自研 `PullRefreshLayout`：仅在 WebView 无法继续上滑时接管手势，只移动顶部指示器 |
| 粘贴并搜索 | 地址栏尾部「粘贴」图标：取剪贴板文本 → 填入 → 直接提交 |
| 边缘前进/后退 | 未做（见下方说明） |

**为什么自研下拉刷新**：`androidx.swiperefreshlayout` 在当前环境的 Google Maven 拉不到（连接超时），
与其卡住不如自己写一个 120 行的容器，也少一个依赖。

**边缘前进/后退暂缓**：`SwipeRefreshLayout` 与边缘手势、WebView 横向滑动块（轮播图）容易互相抢事件，
需要先做手势仲裁，单独一版更稳。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 16/16 |
| 版本 | 0.18.0-beta / versionCode 39 |

## 0.18.1-beta：历史页重排 + 下拉刷新真正生效

### 下拉刷新为什么之前没反应
WebView 在开始滚动时会调用 `requestDisallowInterceptTouchEvent(true)`，
父容器从此收不到 MOVE 事件，`onInterceptTouchEvent` 形同虚设 → 看起来"没有刷新、没有回弹"。

修法：
1. 覆写 `requestDisallowInterceptTouchEvent`：**页面已在顶部时忽略子视图的请求**；
2. 下拉时同时位移子视图（0.35 系数）与指示器，手指有明确反馈；
3. 松手用 `ValueAnimator`（220/240ms + Decelerate）回弹，未达阈值也回弹；
4. `canChildScrollUp()` 逐层检查子 View（WebView 嵌在 FrameLayout 内）。

### 历史页重排
- 按「今天 / 昨天 / 更早」分组，组标题 13sp 次要色；
- 行内改为三段：标题（15sp）/ 站点（12sp）/ 时间（12sp，右对齐）；
- 行内边距 16dp、组间 12dp、卡片间距 8dp，去掉原先"标题+URL 挤一行"的布局。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 16/16 |
| 版本 | 0.18.1-beta / versionCode 40 |

## 0.19.0-beta：书签体系

| 功能 | 实现 |
|---|---|
| 文件夹 | 新增 `bookmark_folders` 表（Room v1→v2 迁移）；书签增加 `folderId` / `position` |
| 筛选 | 顶部横向 chips：全部 / 未分类 / 各文件夹 + 新建 |
| 移动 | 长按书签 → `MiuixSheet` 列出「未分类 + 各文件夹」+ 删除 |
| 导入导出 | Netscape Bookmark HTML（Chrome/Firefox/Edge/Safari 通用）；导出走 `CreateDocument`，导入走 `OpenDocument` |
| 首页模块 | 设置页新增「首页显示书签」开关，关闭后首页不显示该模块 |

### 测试
`BookmarksHtmlTest` 4 条：导出→解析数量一致、`&` 等转义往返、文件夹归属保持、无文件夹的平铺 HTML 也能导入。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ **20/20** |
| 版本 | 0.19.0-beta / versionCode 41 |

## 0.20.0-beta：应用内更新 + 底层优化

### 应用内更新
- `update/UpdateRepository`：GitHub Releases API → 比较版本号 → 下载 APK → 调起系统安装器；
- 设置页新增「更新」区：更新源仓库（`owner/repo`，**留空则不检查**）、检查更新、进度与结果状态；
- 新增 `REQUEST_INSTALL_PACKAGES` 权限；`file_paths.xml` 增加 `update/` 目录；
- 版本号比较按数字段逐位比较（`1.2.10 > 1.2.9`）。

### 底层优化
| 项 | 做法 |
|---|---|
| WebView 预热 | `Application.onCreate` 里后台低优先级线程创建并销毁一个 WebView，提前加载内核 |
| 调试开关 | `setWebContentsDebuggingEnabled(false)`（debug 包也关，防止误连） |
| 渲染优先级 | `setRenderPriority(HIGH)`，滚动时减少主线程争用 |
| 安全浏览 | API 26+ 打开 `safeBrowsingEnabled` |
| 内核信息 | 设置页显示当前 WebView 版本与提供方（`WebView.getCurrentWebViewPackage()`） |

### 关于"内置 Chromium 内核"
本版**没有**把 Chromium 打进 APK，原因与可选路线已单独说明（见回复正文）。
现方案仍是系统 WebView，通过上述预热/参数调优把启动与滚动性能提到接近上限。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 20/20 |
| 权限 | ✅ `REQUEST_INSTALL_PACKAGES` 已打包 |
| 版本 | 0.20.0-beta / versionCode 42 |

## 0.21.0-beta：GeckoView 内核接入（阶段 A，实验性）

### 做了什么
- 引入官方 `geckoview.aar`（160.0.20261104181552，本地 `app/libs/`，仅 arm64-v8a）；
- `GeckoRuntimeHolder`：Application 级 GeckoRuntime 单例；
- `GeckoPageView`：GeckoView + GeckoSession，接 Progress / Content / Navigation 三个 Delegate；
- `GeckoBrowserController`：完整实现 `BrowserController` 接口（标签、导航、长按菜单、桌面模式、
  查找、下载、白名单、持久化），主 Activity 按设置项选择引擎；
- 设置页新增「内核」开关（默认关闭，切换后提示重启）。

### 兼容性处理
| 项 | 处理 |
|---|---|
| `compileSdk` | GeckoView 要求 37.2 → 增加 `compileSdkMinor = 2` |
| ABI | `abiFilters arm64-v8a`，避免 4 ABI 同时打包 |
| 无 `evaluateJS` | `togglePageDark` 提示不支持；`evaluate` 返回 null（留待 WebExtension） |
| 前进后退 | 由 `onCanGoBack/onCanGoForward` 维护，不再同步查询 |
| 截图 | 窗口 `PixelCopy`；失败时上层回退占位块 |

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 20/20 |
| APK | 205 MB（含 Gecko 全量原生库，release + App Bundle 可分 ABI 降到 ~90MB） |
| 运行时 | ⚠️ 需真机验证 |

### 未适配（阶段 B/C）
深色模式、cosmetic 广告隐藏、viewport 强制、blob 下载 Hook —— 均依赖页面脚本，
GeckoView 160 起需通过 WebExtension content script 实现。

## 0.21.1-beta：修 Gecko 首屏崩溃

**根因**：GeckoView 按**文件路径**加载 `libxul.so` 等原生库，需要 APK 把原生库解压出来。
AGP 在 targetSdk ≥ 30 时默认 `useLegacyPackaging = false`（直接从 APK 内 mmap），
于是 Gecko 找不到库 → 打开网页即崩溃。

**修法**：
- `app/build.gradle.kts` 增加 `packaging { jniLibs { useLegacyPackaging = true } }`
  （等价于 `extractNativeLibs="true"`，但 AGP 只接受构建脚本写法）；
- `AndroidManifest` 增加 `android:largeHeap="true"`（Gecko 内存开销大）；
- `MainActivity` 启动时探测 `GeckoRuntimeHolder.runtime()`，失败自动回退 WebView 并提示，
  避免"内核不可用 → 每次启动都崩"。

验证：APK 内 `libxul.so` 存在、Manifest 里 `extractNativeLibs=true`、体积 109 MB（解压库后）。

## 0.21.2-beta：Gecko 启动诊断

`GeckoRuntime.create()` 失败会被捕获并**记录原因**，不再静默回退：

- 两条初始化路径：自定义 settings → 默认 settings；
- `GeckoDiagnostics.inspect()` 检查：
  - `nativeLibraryDir` 下 9 个必需 so（libxul / libmozglue / libnss3 / libfreebl3 / libsoftokn3 /
    libmozavcodec / libmozavutil / libgkcodecs / libplugin-container）是否都存在；
  - `assets/omni.ja` 是否可读；
  - 最后一次异常的类型与消息；
- 结果写入设置，**设置页「内核诊断」**直接显示结论（可截图或复制）；
- 启动时若不可用，仍自动回退 WebView 并提示。

## 0.21.4-beta：修 Gecko 白屏

两处渲染相关问题（都不是内核能力问题）：

1. **会话挂载顺序**：之前是 `setSession()` 先、`open(runtime)` 后。
   GeckoView 官方顺序要求 `session.open(runtime)` → `geckoView.setSession(session)` → `session.setActive(true)`，
   顺序错了会挂上会话但不出画面 → 白屏。已按官方顺序重写，并补 `setActive(true)`。
2. **下拉刷新位移内容**：`PullRefreshLayout` 下拉时给内容容器加 `translationY`，
   而 GeckoView 是 SurfaceView，父容器位移会让 Surface 与 View 位置错位 → 画面空白。
   已移除内容位移，只移动顶部指示器（WebView 下同样受益）。

另外补充 `ContentDelegate.onCrash` 回调：内容进程崩溃会记录 `GeckoInit` 日志并显示"内容进程崩溃"，不再静默白屏。

## 0.21.5-beta：Gecko 崩溃捕获 + 永久回退

- `GeckoCrashGuard`：安装 `UncaughtExceptionHandler`，把堆栈写入 `files/crash-last.txt`，
  同时**自动关闭 Gecko 开关**（下次启动直接走 WebView，不会连续崩）；
- 设置页新增「上次崩溃日志」（显示前 6 行）；
- 去掉显式 `session.setActive(true)`，避免与 GeckoView 内部生命周期竞争。

## 0.22.0-beta：放弃自研内核，转 Luna 路线

### 回退
- 删除 `browser/gecko/`（GeckoRuntimeHolder / GeckoPageView / GeckoBrowserController / 诊断 / 崩溃守卫）；
- 移除 `geckoview.aar`（244MB）与 `abiFilters`；
- 设置页删除「内核」「内核诊断」「上次崩溃日志」；APK 从 109MB 回到 **12.9MB**。

### 学 Luna 的三个机制
| 机制 | 实现 |
|---|---|
| **桥接令牌** | `js/BridgeAuth`：每次主文档加载轮换随机 16 字节 secret，注入脚本首行写入 `window.__sbBridgeSecret`；`SimpleBrowserBridge` 所有方法先校验，非法调用直接丢弃 |
| **TopRoom** | `TopRoomModule`：扫描 `position:fixed/sticky` 且贴顶的 header/nav，给 `html` 设置 `--sb-room-top` 并把 `padding-top` 加到 `body`，让页面浮层与我们的工具栏不再重叠 |
| **媒体嗅探** | `MediaSnifferModule`：扫 `__playinfo__` / `__NEXT_DATA__` / `playerConfig` 等播放器状态对象（而不是只看 `<video src>`），命中 m3u8/mp4/mpd 后经桥回传 |

注入管线仍是 `JsRegistry` + `JsInjector`（同一时机合并成一次 `evaluateJavascript`），
模块幂等标记沿用 `__sb*` 命名空间。

### 验证
| 项 | 结果 |
|---|---|
| assembleDebug | ✅ |
| 单元测试 | ✅ 20/20 |
| APK | 12.9 MB |

## 0.22.1-beta：修"上滑与下拉刷新打架"

**根因**：判断"页面是否在顶部"用的是 `View.scrollY` / `canScrollVertically(-1)`，
但多数移动站点的滚动发生在内部容器里，WebView 的 `scrollY` **恒为 0** →
父容器以为一直在顶部，于是任何下滑（包括页面上滑手势）都被下拉刷新接管。

**修法**（学 Luna 的思路：滚动状态由页面自己上报）：
1. 新增 `ScrollStateModule`：监听 `scroll`，把 `scrollY <= 2` 的布尔值经桥回传（只在变化时上报）；
2. `TabWebView.JsBridge.onScrollY(secret, atTop, y)` 校验令牌后回调；
3. 控制器维护 `tabId -> atTop`，新增 `BrowserController.isActiveTabAtTop()`；
4. `PullRefreshLayout` 用 `atTopProvider` 取代启发式判断，并且**只在顶部 + 手指向下**时拦截；
5. `requestDisallowInterceptTouchEvent`：不在顶部时**尊重 WebView**，不再强行抢占。

## 0.22.2-beta：修容器滚动 / 虚拟滚动站点的下拉刷新冲突

针对 `mihoyo.com` 这类页面：

**为什么 0.22.1 还不够**
- 这些站点滚动发生在内部容器，或干脆用 `transform` 做"虚拟滚动"（body `overflow:hidden`，
  `scrollTop` 永远 0），`window` 上收不到有用信息，于是仍被判定"在顶部"。

**v2 做法**
1. `document.addEventListener('scroll', h, true)` —— **捕获**阶段监听，任何容器滚动都能收到；
   记录最多 12 个滚动过的元素，`atTop` 要求它们 `scrollTop <= 2` 全部成立；
2. `isVirtualScroll()`：检测「根节点 `overflow-y:hidden` + 子元素带 transform / 内容高于视口」，
   判定为虚拟滚动时上报 `allowPull = 0`，**该页面直接禁用下拉刷新**（宁可没有刷新，也不能抢手势）；
3. 桥签名升级为 `onScrollY(secret, atTop, allowPull, y)`，控制器分别记录 `atTop` 与 `allowPull`；
4. `isActiveTabAtTop()` = `allowPull && atTop`。

## 0.22.3-beta：判不出滚动就不接管（终极兜底）

v2 仍然依赖"收到过 scroll 事件"，而米哈游这类页面用脚本手势/虚拟滚动，可能始终不发 scroll。
v3 改为**先判定滚动根，再决定是否允许下拉**：

1. `findScroller()`：优先 `document.scrollingElement`；否则扫描 `overflow:auto/scroll`
   且 `scrollHeight > clientHeight` 的元素，取面积最大者；
2. **找不到滚动根 → `allowPull = 0`**，该页禁用下拉刷新（不再依赖事件）；
3. 找到滚动根 → `atTop = 该根 scrollTop <= 2`；
4. `touchstart` 时重新扫描（应对懒加载改变结构）；
5. 原生兜底：`onPageFinished` 后 **2.5 秒内没收到任何上报** → 该标签禁用下拉刷新。
