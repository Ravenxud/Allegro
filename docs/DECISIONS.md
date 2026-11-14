# 技术决策记录（ADR 摘要）

## ADR-1 使用系统 WebView，不自研内核
- 背景：需要页面渲染、JS 执行、下载、截图。
- 决策：系统 WebView + 重度 JS 注入（对齐 Luna 路线）。
- 备选：GeckoView（已实测，见 ADR-2）。
- 后果：APK 12–13MB；内核版本依赖厂商；但生命周期、截图、注入全部可用。

## ADR-2 放弃 GeckoView 实验
- 过程：接入 geckoview 160 → 库未解压崩溃 → runtime 重复创建 → 白屏 → 闪退。
- 根因：`useLegacyPackaging` 需为 true；GeckoView 160 移除 `evaluateJS`（注入能力需重写为 WebExtension）；SurfaceView 与父容器位移冲突。
- 结论：代价（+100MB、架构重写）远超收益，**不再尝试**。

## ADR-3 JS 桥使用一次性令牌
- 做法：每次主文档加载 `BridgeAuth.rotate()`，注入脚本首行写入 `window.__sbBridgeSecret`，桥方法先校验。
- 理由：`addJavascriptInterface` 对页面完全敞开，令牌能挡住"恶意页面直接调原生"。
- 局限：同页面内脚本仍可读令牌（等同 Tampermonkey 模型）。

## ADR-4 JS 注入采用模块注册表 + 前置桥
- 结构：`JsModule(id, injectAt, priority, js())` + `JsRegistry` + `JsInjector`。
- 关键：`BridgePreludeModule` 在 DOC_START 定义 `window.__sbReader/__sbPageText/__sbScrollY/...`，
  映射到 `SimpleBrowserBridge`。**缺了这层会出现"调用了不存在的函数"的静默失败**（阅读模式曾因此失效）。

## ADR-5 下拉刷新由页面上报滚动状态
- 问题：WebView `scrollY` 在多数 H5 恒为 0；WebView 会 `requestDisallowInterceptTouchEvent(true)` 抢手势。
- 决策：`ScrollStateModule` 找"滚动根"（`scrollingElement` 或面积最大的可滚动容器），上报 `atTop` 与 `allowPull`；
  找不到滚动根（虚拟滚动）→ `allowPull=false`；原生 2.5s 无上报 → 也禁用。
- 后果：虚拟滚动站点（如米哈游官网）没有下拉刷新，属刻意取舍。

## ADR-6 自研下载器（OkHttp + Range）
- 理由：`DownloadManager` 无公开 pause API，且无法精细控制进度/速度。
- 关键实现：`RandomAccessFile` 追加写；暂停 = 记录意图 + `call.cancel()`；恢复先 `join()` 上一个任务避免双写。
- 坑：DAO 曾按 `dmId` 更新，导致状态写不进去（应为行 `id`）。

## ADR-7 书签/离线/用户脚本用 JSON 索引而非新表
- 理由：这些数据量小、结构灵活，避免频繁 Room 迁移。
- 例外：书签因为需要文件夹与排序，用了 Room v2 迁移。

## ADR-8 统一底部面板（MiuixSheet）
- 主菜单、长按菜单、字体选择共用同一组件：遮罩 12%、顶圆角 28dp、拖拽条、分组标题、行内图标。
- 教训：早期每个菜单各写一套 `Surface + Card`，观感割裂。

## ADR-9 覆盖层必须隐藏 WebView
- 问题：`AndroidView`（WebView）绘制层级高于 Compose，菜单会被网页盖住（表现为"点了没反应"）。
- 解法：覆盖层可见时把 WebView 宿主设 `GONE`。

## ADR-10 二级页动画用 MutableTransitionState
- 问题：`AnimatedVisibility(visible=…)` 首次组合即 true 时不播动画 → 硬切。
- 解法：`MutableTransitionState(false)` + 目标态驱动；配合顶/底栏 `chromeAlpha` 淡出而非直接移除（避免布局跳变）。

## ADR-8 离线页用「MHTML → 自包含 HTML」而不是直接存 HTML
- 背景：`WebView.saveWebArchive` 是唯一能拿到「当前渲染结果 + 子资源」的 API，产物是 MHTML。
- 问题：Android WebView **不能渲染 MHTML**；`file://x.mhtml` 会当纯文本显示（功能等于半残）。
- 备选：
  1. 只存 `outerHTML`：丢样式表与图片，离线页只剩骨架；
  2. 用 `loadDataWithBaseURL` 注入 MHTML：子资源仍拿不到；
  3. 自研 MHTML 解析并内联资源（选定）。
- 决策：`MhtmlArchive` 解析 multipart/related → 主 HTML + 各 part 转 `data:` URI（单 part ≤512KB、总 ≤8MB）。
  关键细节：真实 MHTML 的 HTML 里资源是**相对路径**，必须按页面 URL / `<base>` 解析后再替换；
  CSS 里的 `url(...)` 也要先内联，否则内联了样式表仍然断图。
- 后果：离线页体积约为原 MHTML 的 1.3 倍（base64 膨胀 33%），但可离线打开、可单文件分享；
  旧版本存的 `.mhtml` 首次打开时惰性转换并就地替换索引。
- 局限：转换发生在保存之后，JS 动态渲染的内容、`<iframe>` 内部页面、视频流不在内联范围。

## ADR-9 站点设置：域名继承链 + 只存非默认值 + 广告过滤复用白名单
- 背景：需要按站点控制 JS / 图片 / 桌面版 UA / 广告过滤，且不能把"全默认"存成一堆死数据。
- 决策：
  1. 存储用 SharedPreferences（key = 域名，value = `js=1;img=1;ua=0`），**等于默认值就删除该 key**，
     "已配置站点"列表因此等于存储里剩下的 key；
  2. 匹配用继承链：`m.news.example.com` → `news.example.com` → `example.com`，精确优先，
     不到二级后缀即停（不引入 Public Suffix List，见 `SiteSettingsCodec.hostCandidates`）；
  3. 广告过滤**不另存一份**，直接读写 `AdBlockRepository` 的站点白名单——否则两处真值必然打架；
  4. WebView 设置是实例级全局，必须每次 `onPageStarted` 重设（见 HANDOFF 坑 10）。
- 后果：改设置后需 reload 才生效（页面上已明确如此）；JS 关闭时跳过全部 JS 注入（含元素隐藏与选择器），
  避免"没反应又不知道原因"。
- 局限：Cookie 只能"清除本站数据"（`CookieManager` 无逐站开关）；`WebStorage.deleteOrigin` 需要 API 29+，
  更低版本只能清 Cookie。

