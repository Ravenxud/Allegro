# 架构说明

## 分层

```
UI (Compose + MIUIX)
  ├─ BrowserScreen          主界面：地址栏 / 内容 / 底栏 / 覆盖层调度
  ├─ TabSwitcherOverlay     标签网格 + 缩放动画（PixelCopy 截图作为 stub）
  ├─ HomeScreen             新标签页
  ├─ screens/*              12 个二级页（书签/历史/下载/设置/离线/阅读/脚本…）
  └─ components/            MiuixSheet（统一底部面板）/ EmptyState
        │  只订阅 StateFlow，只调用 BrowserController
        ▼
BrowserController（接口，唯一入口）
  └─ WebViewBrowserController
       ├─ TabWebView       WebView 子类：回调 + JsBridge(@JavascriptInterface)
       ├─ WebViewPool      标签⇄WebView，最多 4 常驻，溢出 saveState 冻结
       ├─ JsInjector       组装脚本 → evaluateJavascript
       ├─ MenuActions      复制/分享/下载
       └─ OfflinePageStore / ReaderExtractor / ReadAloudController
        │
        ▼
data（Room 6 实体 / SharedPreferences / JSON 索引）
```

## 关键数据流

**页面加载**
```
TabWebView.loadUrl
  → onPageStarted  → state.progress=0 / BridgeAuth.rotate()
                   → JsInjector DOC_START（Prelude/Viewport）
  → onProgress     → state.progress
  → onPageFinished → JsInjector DOC_END（PageDark/Blob/MediaSniffer/TopRoom/Scroll/UserScript）
                   → historyRepository.record
                   → scheduleScrollFallback（2.5s 无上报则禁用下拉刷新）
```

**JS 桥**
```
页面脚本 → window.__sbXxx(...)          （由 BridgePreludeModule 定义）
         → SimpleBrowserBridge.onXxx(secret, ...)   （@JavascriptInterface）
         → BridgeAuth.isValid(secret) 校验
         → TabWebView.Callbacks → Controller
```

**下载**
```
网页/长按 → DownloadCenter.enqueue
  → Room 插入(running) → OkHttp + Range 写 dl_<id>.bin
  → 200ms 节流 publish 进度（speed/eta 1s 差分）
  → DownloadService 前台通知（暂停/取消）
  → 完成/失败/暂停写回 Room
```

**阅读模式**
```
Controller.openReader
  → 注入 Readability.js（90KB）
  → 60ms 后注入提取脚本：Readability.parse → 后处理(链接/图片/结构) → 拼页(最多3页) → JSON
  → onReaderResult → ReaderExtractor.readerHtml → ReaderScreen(WebView 渲染)
```

## 数据库（Room v2）

| 表 | 用途 | 关键列 |
|---|---|---|
| `tabs` | 标签持久化 | id, url, title, favicon, position, state(BLOB) |
| `closed_tabs` | 撤销关闭 | url, title, closedAt（保留 20 条） |
| `history` | 历史 | url(PK), title, lastVisitedAt |
| `bookmarks` | 书签 | url(unique), folderId, position |
| `bookmark_folders` | 书签文件夹 | id(UUID), name, position |
| `downloads` | 下载 | id, dmId, url, fileName, status, bytesSoFar, totalBytes |

> 迁移：v1→v2 增加 `bookmarks.folderId/position` 与 `bookmark_folders` 表（见 `AppDatabase.MIGRATION_1_2`）。
> 所有实体预留 `updatedAt` / `deleted`，为 WebDAV 同步准备。

## 主题与设计令牌

- 配色：`MiuixTheme(lightColorScheme()/darkColorScheme())`；`ThemeMode` 三态存 SharedPreferences。
- 令牌：`ui/theme/Tokens.kt`（Spacing/Radius/IconSize），新代码禁止裸 dp。
- 图标：全部 `MiuixIcons.*`；仅 `ic_launcher`、`ic_download` 为 drawable 资源。

## 并发与生命周期

- 控制器持有 `CoroutineScope(SupervisorJob + Main.immediate)`；IO 用 `Dispatchers.IO` 包裹。
- `release()` 会 `persistNow()` + `pool.clear()` + `scope.cancel()`。
- 覆盖层打开时把 WebView 宿主设为 `GONE`（否则 AndroidView 会盖住 Compose 浮层）。
