# 交接说明（给下一个 AI / 开发者）

> 读完这一份就能接手开发。项目：**Allegro**（代码内部名 SimpleBrowser）—— 参考 Luna 的极简 Android 浏览器。
> 最后更新：0.26.0-beta（versionCode 58）

---

## 1. 一句话状态

浏览核心、标签页 2.0、下载器、广告过滤、书签、设置、JS 注入管线、手势、应用内更新、
阅读模式/朗读/分享/用户脚本、离线页可打开（MHTML→HTML）、隐私报告 + 元素选择器、
**站点设置（JS/图片/桌面版 UA/广告过滤/清数据）** 均已完成并可编译；
阶段一还剩「WebDAV 同步」「大屏/标签组/HLS/无障碍」；动画与外观专项未开始。

## 2. 仓库与构建

| 项 | 值 |
|---|---|
| 远程仓库 | https://github.com/Ravenxud/Allegro（`main`） |
| 本地路径 | `/workspace/SimpleBrowser` |
| APK 目录 | `/workspace/apk`（只保留最近 3 个版本） |
| 分支 | 只有 `main`，无 feature 分支 |
| CI | `.github/workflows/build.yml`：push 触发，跑单测 + `assembleDebug`，上传 artifact |

### 构建环境（必须严格对齐，否则必挂）

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | AGP 9 最低要求 |
| Gradle | **9.9.1** | 8.x 无法运行 AGP 9 |
| AGP | **9.5.1** | 9.x 内置 Kotlin 需关闭：`android.builtInKotlin=false` |
| Kotlin | **2.4.21** | 与 Compose 1.12.2、MIUIX 0.9.4 匹配 |
| compileSdk | **37** | MIUIX 0.9.4 强制要求 37（36 会报 AAR metadata 错误） |
| minSdk | 26 | |
| 关键依赖 | Compose 1.12.2、MIUIX 0.9.4（ui/preference/icons）、Room 2.8.5、OkHttp 4.12.0 | |

```bash
# 标准构建
cd /workspace/SimpleBrowser
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest

# 若在 aarch64 Linux 且 aapt2 无法执行（x86_64 only），需 qemu shim：
gradle -Pandroid.aapt2FromMavenOverride=/opt/aapt2shim/aapt2 assembleDebug
```

> ⚠️ **常见坑**：`compileSdkMinor` 只在需要 GeckoView 时加过，现已移除；不要为了"更新"随意升 AGP 9.6+/Compose 1.13，会牵动 Kotlin 与 MIUIX 全链路。

## 3. 代码地图（82 个 Kotlin 文件）

```
browser/     引擎层：BrowserController(接口) / WebViewBrowserController(实现) /
             TabWebView(WebView 子类+桥) / WebViewPool(最多 4 个常驻) /
             BrowserState / ErrorPage / MenuActions / UrlParser
js/          注入管线：JsModule(接口) / JsRegistry / JsInjector / BridgeAuth(令牌)
             modules/: BridgePrelude、Viewport、PageDark、BlobDownload、
                       MediaSniffer、TopRoom、ScrollState、UserScript
data/        Room：6 实体 + 5 DAO + 4 Repository；SettingsRepository(SharedPreferences)；
             UserScriptStore(JSON 索引)；BookmarksHtml(导入导出)；SearchEngine/ThemeMode
reader/      ReaderExtractor(Readability+后处理+拼页) / OfflinePageStore /
             ReadAloudController(系统 TTS)
download/    DownloadCenter(自研 OkHttp 下载，断点续传/暂停/继续) /
             DownloadService(前台通知) / DownloadActionReceiver / DownloadModels
adblock/     FilterEngine(规则解析/匹配) / AdBlockRepository(多源+镜像+白名单) /
             CosmeticInjector
action/      BrowserAction(接口) / ActionRegistry / BuiltInActions(7 个)
update/      UpdateRepository(GitHub Releases 检查+下载+安装)
ui/          BrowserScreen(主界面+覆盖层调度) / TabSwitcherOverlay(标签网格+缩放动画) /
             HomeScreen / Overlays(AppMenu/ContextMenu) / PullRefreshLayout(自研下拉刷新) /
             screens/ 12 个二级页 / components/(MiuixSheet/EmptyState) / theme/(BrowserTheme/Tokens)
di/          ServiceLocator(手写 DI，替代 Hilt)
```

## 4. 四个必须遵守的架构约定

1. **UI 不许碰 WebView**：一切经过 `BrowserController`，便于替换引擎（虽然已决定不换）。
2. **状态单一来源**：`BrowserController.state: StateFlow<BrowserState>`，UI 只订阅。
3. **JS 模块必须幂等**：用 `window.__sb*` 标记；新增脚本 = 实现 `JsModule` + 在 `JsRegistry.default()` 注册一行。
4. **原生桥必须校验令牌**：`BridgeAuth.isValid(secret)`，页面每次加载轮换；`BridgePreludeModule` 把 `window.__sbXxx` 映射到 `SimpleBrowserBridge`。

## 5. 五个扩展点（新增功能只改一个文件的地方）

| 扩展点 | 新增方式 |
|---|---|
| JS 注入脚本 | 实现 `JsModule` → `JsRegistry` 注册 |
| 菜单动作 | 实现 `BrowserAction` → `ActionRegistry.builtIn` 加一行 |
| 二级页面 | `SecondaryScreen` 加枚举 + `BrowserScreen` 加 `when` 分支 + `ScreenScaffold` 新页 |
| 数据表 | Room 实体 + DAO + 版本号 +1 + `Migration`（**历史坑见下**） |
| 站点级开关 | `SiteSettings` 加字段 → `SiteSettingsCodec` 编解码 → `applySiteSettings()` 落到 WebView → 页面加开关 |
| 用户脚本 | 用户自带，无需改代码 |

## 6. 已知问题与技术债（接手先看这段）

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | ~~离线页只能看列表，不能打开~~ | 0.25.0 已解决 | MHTML 转自包含 HTML（见 ADR-8）；**转换对 JS 动态渲染/视频不适用** |
| 2 | **翻译功能未实现**（用户明确要求跳过） | 无 | 若要做，抽象 `IPageTranslator`，默认腾讯/微软 |
| 3 | **下拉刷新对虚拟滚动站点仍可能误判** | 已三次返工 | 当前策略：判不出滚动根就 `allowPull=false`；计划改为设置项默认关闭 |
| 4 | 阅读模式对 SPA 提取率不稳 | 部分站点失败 | 已加 `charThreshold=200`，可再补站点规则 |
| 5 | 用户脚本不支持 `@require`/`GM_registerMenuCommand`/跨域 XHR | 兼容性有限 | 只承诺 GM 子集 |
| 6 | Lint 从未成功跑通（环境缺 `lint-gradle` 下载） | 未知静态问题 | 在 GitHub Actions 里补 lint job |
| 7 | release 构建 + 混淆未验证 | 发布阻塞 | 0.29 阶段处理 |
| 8 | 包名仍是 `com.example.simplebrowser` | 与 Allegro 品牌不符 | 改包名会牵动 FileProvider authorities，建议 1.0 前一次改完 |

## 7. 踩过的坑（写下来避免重复踩）

1. **GeckoView 路线已放弃**：连踩「库未解压 → runtime 重复创建 → 白屏（会话挂载顺序/Surface 位移）→ 崩溃」四坑，APK +100MB。**不要再试**。
2. **Room DAO 列名**：`updateProgress` 曾写成 `WHERE dmId = :dmId`，而自研下载器传的是行 `id`，导致暂停/取消全部静默失效。**改表先核对列名**。
3. **KDoc 里的 `*/`**：注释写 `*://*.example.com/*` 会提前闭合注释块，导致语法错误。通配符路径用行注释。
4. **下拉刷新**：WebView 的 `scrollY` 在多数 H5 恒为 0；且会调 `requestDisallowInterceptTouchEvent(true)` 抢手势。正确做法是让**页面自己上报**滚动根与 `atTop`。
5. **MIUIX 强制 compileSdk 37**，降到 36 会 AAR metadata 报错。
6. **CI 的 SDK 包名**是 `platforms;android-37.0`（带 `.0`），不是 `android-37`。
7. **`AnimatedVisibility(visible=…)` 首次组合即 true 不播动画**，二级页要用 `MutableTransitionState`。
8. **WebView 打不开 MHTML**：`saveWebArchive` 的产物是 multipart/related，直接 `file://` 加载只会显示乱码文本。
   必须转成自包含 HTML；且 Chrome/WebView 写出的 HTML 里资源是**相对路径**，靠 Content-Location 整串替换匹配不上，
   要按 `<base>`/页面 URL 解析后再替换（见 `MhtmlArchive` 与 ADR-8）。
9. **隐私报告的域名榜落盘不能复用 Top N**：早期实现把「已落盘 Top 20」当全量合并写回，
   每次刷盘都会丢掉 20 名以外的域名。必须读全量再合并。
10. **WebView 的 settings 是全局的**：`javaScriptEnabled`/`blockNetworkImage`/`userAgentString` 属于 WebView 实例，
    而本项目一个 WebView 复用于多个标签页。所以站点设置必须在**每次导航（onPageStarted）重设一遍**，
    只改一次会被下一个站点的页面覆盖。

## 8. 测试现状

```
app/src/test/java/.../UrlParserTest      5 条  URL/搜索解析
app/src/test/java/.../FilterEngineTest   9 条  广告规则匹配/例外/同站
app/src/test/java/.../BookmarksHtmlTest  4 条  书签 HTML 往返
app/src/test/java/.../HomeDateTest       2 条  日期/问候
app/src/test/java/.../MhtmlArchiveTest   9 条  MHTML 解析/相对引用/CSS 内联/QP 解码
app/src/test/java/.../SiteSettingsTest   6 条  编解码/域名继承链/摘要
共 35 条，全绿
```

真机验证清单见 `docs/TESTING.md`。**没有 instrumentation 测试，也没有截图测试**。

## 9. 下一步任务（按优先级）

1. ~~离线页可打开~~ / ~~隐私报告 + 元素选择器~~（0.25.0 完成）
2. ~~站点设置页~~（0.26.0 完成；Cookie 只做了「清本站数据」，未做逐站开关）
3. WebDAV 同步（1 个版本）
4. 大屏 / 标签组 / HLS 下载 / 无障碍（1 个版本）
5. 动画专项 26 项（1 个版本，见 `ROADMAP.md`）
6. 外观专项 → 稳定性与发布准备 → 1.0.0

## 10. 与用户协作的偏好（重要）

- 该用户**重视真机可验**：每版都要给出「请验这几条」清单。
- 交付**必须能编译**，且要如实说明「未验证的部分」，不接受"假装完成"。
- 版本号规则：`0.x.y-beta`，每版 `versionCode +1`；APK 命名 `Allegro-<version>-debug.apk`。
- 提交信息用中文，前缀 `feat:` / `fix:` / `chore:` / `ci:`。
