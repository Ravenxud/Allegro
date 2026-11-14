# 0.28.2-beta

飞书"回新标签页"仍未解决，这一版不猜了：**把导航事件写成可导出的日志**，同时补三处兜底。

## 新增

- **诊断日志**：`filesDir/diag.log`，记录 `page-start / page-finish / main-frame-error /
  blank-suppressed / renderer-gone / new-window / open-tab / close-tab / new-intent`，
  上限 256KB 自动轮转。入口：**设置 → 诊断 → 导出诊断日志**（走系统分享，可直接发我）。
  只记 URL 与错误码，不记页面内容与账号信息。

## 兜底修复

- `window.close()` 不再有任何效果（之前可能把主窗口关掉 → 看起来"页面自己没了"）
- `ERR_ABORTED / ERR_UNSUPPORTED_SCHEME / ERR_REDIRECT_LOOP` 不再替换成错误页：
  SPA 重定向时这类中断是正常的，替换反而把好页面冲掉
- `window.open` 的探针 WebView 改到 post 里销毁，避免在回调中销毁打断 opener 的渲染进程

## 怎么用

1. 打开飞书，复现"回新标签页"
2. 设置 → 诊断 → 导出诊断日志 → 把内容发我
3. 顺手确认一下 设置 → 关于 → 版本号 是 `0.28.2-beta`（之前几次可能装的是旧包）

---

# 0.28.1-beta

修 0.28.0 上你报的 6 个问题。

## 修复

| # | 问题 | 原因 / 处理 |
|---|---|---|
| 1 | 打开飞书这类站点直接回到新标签页 | 三个来源一起挡：①站点重定向/渲染进程重启会上报 `about:blank`，被写进 `tab.url` 后界面就切回首页 → 已加"真实 URL 优先"保护；②`window.open` 未实现，弹窗类站点（飞书/微信）点了没反应 → 已支持并在后台开新标签；③渲染进程被系统回收后页面变空白 → 已实现销毁重建并重新加载 |
| 2 | 设置里切换搜索引擎的选项消失 | 0.28 改名时那段 `RadioButtonPreference` 被误删，已补回（百度 / Bing / Google） |
| 3 | 点网页内输入框不弹键盘 | WebView 抢不到焦点：Compose 侧输入框占着焦点时，网页输入框拿不到 IME。现在 `onTouchEvent` 里主动 `requestFocus()`，并显式设置 `adjustResize`。**这条最需要你真机确认** |
| 4 | 标签页管理页显示 `about:blank` | 改为显示「新标签页」，副标题不再显示实现细节 |
| 5 | 长按链接时 URL 太长 | 面板标题改为单行省略号，不再撑满整屏 |
| 6 | 地址栏右侧按钮意义不明 | 加载中曾显示 ✕（像"关闭标签页"）。现在固定显示刷新图标，加载中点击=停止加载，语义唯一 |

## 诊断

飞书那类问题现在有日志可循：

```bash
adb logcat -s BrowserNav
# 忽略 about:blank 覆盖，保留 https://www.feishu.cn/...
# window.open -> https://...
# 渲染进程被回收，重建 WebView（tab=...）
```

## 已知未验证

- #1 的三个修复里，只有"日志能证明命中了哪条"是真机才能确认的，我这边只能保证编译与逻辑；
- #3 键盘问题若仍复现，请录屏并说明机型/输入法，我按"Compose 焦点 + IME insets"方向继续改。

---

# 0.28.0-beta

阶段 A 第二步：**发布工程化**。这一版几乎没有新功能，但让"能不能正式发出去"从"不知道"变成"可以"。

## 变更

- **包名改为 `com.ravenxud.allegro`**（原 `com.example.simplebrowser`）。
  ⚠️ applicationId 变了，**必须卸载旧版再安装**，旧版的本地数据不会自动迁移。
- **R8 混淆 + 资源收缩**：release 包从 13 MB 降到 **1.9 MB**，规则见 `app/proguard-rules.pro`。
- **release 签名**：本机 `keystore.properties` 存在时用正式签名，否则退回 debug 签名（供 CI 验证 R8）。
- **Lint 进 CI**：`lintDebug` 成为必过步骤。首跑暴露 10 个 error，全部修掉：
  - 2 个线程约束：其中一个是**真 bug** —— 后台线程创建 WebView 必然抛异常，被 `runCatching` 静默吞掉，
    也就是说"启动预热"从来没生效过，已删除；
  - 8 个 Compose 资源误用 `LocalContext`，改用 `LocalResources.current`。
- CI 增加 `assembleRelease` 产物，R8 一旦收缩出错会在 CI 暴露。

## 踩坑记录（AGP 9）

release 第一次构建出来的 APK **没有 AndroidManifest.xml 和 res**，Gradle 却报 BUILD SUCCESSFUL，
`aapt2 dump packagename` 直接说 "could not identify format of APK"。原因是 AGP 9 在
`android.newDsl=false` 下的资源优化步骤产出了空资源包；关闭 `android.enableResourceOptimizations`
后恢复正常。这条已写进 HANDOFF，**以后每次升 AGP 都要复验 release APK 能否被 aapt2 解析**。

## 请验

**先装 release 包（1.9MB）**——`Allegro-0.28.0-beta-release.apk`，专治 R8 裁剪：

| # | 场景 | 期望 |
|---|---|---|
| R1 | 冷启动进首页 | 不闪退 |
| R2 | 打开网页 → 长按链接 | 菜单能弹 |
| R3 | 阅读模式 / 朗读 | 可用（JS 桥方法名未被混淆） |
| R4 | 保存离线页 → 打开 | 正常 |
| R5 | 切换主题 / 搜索引擎 | 生效（枚举 valueOf 未被裁） |
| R6 | WebDAV 同步一次 | 正常 |
| R7 | 下载文件并安装 | 调起安装器（authority = `com.ravenxud.allegro.fileprovider`） |

R1–R7 全过 → 下个版本起 Release 就只发 release 包（1.9MB）。
若 release 包有异常，先用下面的 debug 包（13MB）对比，把崩溃栈发我。

---

# 0.27.2-beta

阶段 A 第一小步：把**应用内更新**这条链路修对。之前它其实从未生效。

## 修复

- **检查更新永远提示「已是最新」**：`UpdateRepository` 调的是 GitHub 的 `releases/latest`，
  而该接口**会跳过 prerelease**，本项目所有版本号又都是 `0.x.y-beta` —— 结果从来没检查到过更新。
  现在改为拉 `/releases` 列表，自己按语义化版本挑选（`ReleaseFeed` + `pickLatestRelease`）。
- **版本比较改为数值比较**：`0.9.0` 与 `0.10.0` 按字典序是反的；现在按数字段比较，
  并遵守 semver「正式版 > 同号预发布版」（`0.28.0` > `0.28.0-beta`）。
- **当前版本号解析不了时不提示更新**（保守策略），避免奇怪 tag 骚扰用户。
- 更新源默认为 `Ravenxud/Allegro`，开箱即可检查更新（清空则关闭检查）。
- 菜单里「用户脚本」和「本站设置」用了同一个齿轮图标，前者改为编辑图标。

## 质量

- 单测 58 → **64 条**：新增 `ReleaseVersionTest`（解析、数值比较、预发布定序、异常 tag）。

## 请验

1. 设置 → 关于 → 检查更新 → 应提示 **0.27.2-beta** 可更新（不再是「已是最新」）。
2. 下载并安装 → 版本号变成 0.27.2-beta。
3. 再点一次检查更新 → 显示「已是最新」。
4. 主菜单：用户脚本与本站设置不再是同一个图标。

---

# 0.27.1-beta

修 0.27.0 的两个真机问题。

## 修复

- **菜单里的「设置」看不见了**：底部面板的行数从 13 涨到 18（本站设置/隐私报告/选择元素等），
  而 `MiuixSheet` 是个不可滚动的 `Column`，屏幕矮一点时最底下的「设置」直接被裁掉。
  现在面板限高（72% 屏高）并可滚动，所有行都能到达。
- **保存离线页报「保存失败」**：`WebView.saveWebArchive` 在页面含跨域 iframe 或体量过大时
  **返回 false 而不抛异常**，旧代码只判断"文件是否存在"，一失败就整条链路终止。
  现在两种失败都会降级到 **DOM 快照**（抓 `outerHTML` + 注入 `<base href>`），
  提示语改为「已保存（简化版：图片/样式可能缺失）」——保存动作永远有产物。
  同时把 MHTML 解析放宽：支持 LF 换行、无根 MIME 头、缺少 `Content-Type` 的 HTML 段。

## 质量

- 单测 54 → **58 条**，其中新增一个**真实 Chromium 快照**回归用例：
  用 `scripts/capture_mhtml.py`（CDP `Page.captureSnapshot`）抓的 MHTML 作为 fixture，
  与 `WebView.saveWebArchive` 同一套生成器 —— 专门防"合成报文全绿、真机失败"。
  该报文里包含 quoted-printable 中文、`cid:` 内联样式表、外部 CSS 与 PNG，全部能正确内联。

## 诊断

保存离线页时会打 `OfflinePage` 日志：

```bash
adb logcat -s OfflinePage
# saveWebArchive=true size=183402 url=https://...
# 离线页已保存 xxx.html 251338B simple=false
```

如果仍是「保存失败」，把这几行日志给我就能定位是取报文失败还是写文件失败。

## 请验

1. 主菜单往下滑 → 能看到并进入「设置」。
2. 之前保存失败的页面上重试「保存离线页」→ 出现"已保存（简化版）"且列表里能打开。
3. 普通图文页保存 → 仍是完整版（提示不带"简化版"）。
4. 深色模式 + 小屏机型上重看菜单最底一行是否可达。

---

# 0.27.0-beta

第六个 beta：**WebDAV 同步**。换手机、多设备之间同步书签与站点设置，不经过任何第三方服务。

## 新增

- **设置 → WebDAV 同步**：填服务器地址 / 账号 / 应用密码 / 远端目录 → 测试连接 → 上传或下载
  - 兼容坚果云、Nextcloud、群晖、自建 nginx-dav（只用到 PROPFIND / MKCOL / PUT / GET）
  - 目录不存在会自动创建；远端没有备份时提示先上传
  - 状态行显示上次同步时间；失败时显示原因（如「认证失败（401）」）
- **同步内容**：书签 + 书签文件夹 + 站点设置 + 主题/搜索引擎/首页书签开关
  - 不含标签页（各设备自己的）、历史与下载记录（隐私与体量）
- **整包覆盖，方向由你选**：上传 = 本机覆盖远端；下载 = 远端覆盖本机（书签按 url upsert，不会重复）

## 工程

- 新增 `util/MiniJson`（约 100 行、零依赖）：`org.json` 在 JVM 单测里是抛 `Stub!` 的空壳，
  用它等于同步逻辑完全不可测；自写之后备份文件的往返一致性可以断言
- 新增 `sync/SyncPayload`（版本化 JSON，`schema` 不兼容时直接拒绝）、`sync/WebDavConfig`（URL 拼接/中文目录编码/Basic 认证）、
  `sync/WebDavClient`、`sync/SyncRepository`（配置持久化 + 两个方向 + 合并）
- 单元测试 35 → **54 条**，其中 3 条是**真连 WebDAV** 的集成测试
  （本机起了台最小 WebDAV 服务器验证：上传→下载内容逐字节一致、404 返回 null、错密码报「认证失败」）；
  未设置 `ALLEGRO_DAV_URL` 时自动跳过，CI 不受影响
- 文档：HANDOFF（含集成测试跑法、明文密码等已知问题）/ TESTING / DECISIONS(ADR-10) / RELEASE_NOTES

## 安装

`Allegro-0.27.0-beta-debug.apk`（Android 8.0+，debug 签名，仅测试用）。

## 请验这几条

1. 用坚果云/Nextcloud 填好地址与**应用密码** → 「测试连接」→ 连接正常。
2. 「上传本机数据」→ 在网页端能看到 `Allegro/allegro-sync.json`。
3. 清除 App 数据（或换设备）→ 「从远端恢复」→ 书签、文件夹、站点设置、主题都回来了。
4. 密码故意填错 → 提示「认证失败」，本地数据不变。
5. 远端目录不存在时直接点上传 → 自动建目录并成功。

> 说明：同步是整包覆盖，没有自动合并；两台设备都改过数据时，后同步的一方会覆盖前者。
> 密码目前明文存在本机 SharedPreferences 里（见 HANDOFF 已知问题 9），请使用应用专用密码。

---

# 0.26.0-beta

第五个 beta：补上**站点设置**——把"这个站要不要跑 JS / 要不要图 / 要不要桌面版 / 要不要过滤"交给用户按域名决定。

## 新增

- **站点设置页**（菜单 → 本站设置）
  - **JavaScript**：关掉后整站的脚本不执行，连本项目的注入脚本也一起跳过（否则会"没反应还不知道为什么"）
  - **加载图片**：关掉省流量，图片位置留空
  - **桌面版网页**：用桌面 UA 请求，专治被强制跳移动站的站点；与工具栏临时切换的桌面模式共存（任一开启即生效）
  - **本站广告过滤**：与菜单里的快捷开关是同一个真值（复用广告白名单，不另存一份）
  - **清除本站数据**：删该域 Cookie（API 29+ 连带该源的 WebStorage），其它站点登录状态不受影响
  - **已配置的站点**列表：只显示改过的站点，一行摘要「JS 关 · 图片关 · 桌面版」，可单条恢复默认
- **域名继承**：`m.news.example.com` 的配置会继承 `example.com`；精确匹配优先，不做 Public Suffix List

## 工程

- 新增 `data/site/SiteSettings`（纯 Kotlin 编解码 + 域名继承链，可单测）与 `SiteSettingsRepository`（SharedPreferences，只存非默认值）
- 关键实现点：WebView 的 `settings` 是**实例级全局**，而本项目一个 WebView 会复用给多个标签页 →
  站点设置必须在每次导航的 `onPageStarted` 重新落一遍（已写进 HANDOFF 坑 10）
- 单元测试 29 → **35 条**（新增 `SiteSettingsTest` 6 条：编解码、缺字段回退、继承链、继承优先级、摘要）
- 文档：HANDOFF / TESTING / DECISIONS(ADR-9) / RELEASE_NOTES

## 安装

`Allegro-0.26.0-beta-debug.apk`（Android 8.0+，debug 签名，仅测试用）。

## 请验这几条

1. 某站点 → 菜单「本站设置」→ 关 JavaScript → 页面重载后脚本不执行（静态内容还在）；再开回来恢复。
2. 关「加载图片」→ 重载 → 图片位置留空、明显省流量。
3. 开「桌面版网页」→ 重载 → 桌面布局；子域（m. 开头）也生效。
4. 关「本站广告过滤」→ 该站广告出现；其它站点仍被过滤。
5. 「清除本站数据」→ 该站退出登录，另一个站点仍登录。
6. 回到列表看「已配置的站点」→ 摘要正确；恢复默认后该条消失。

---

# 0.25.0-beta

第四个 beta 里程碑：把「隐私」这条主线补齐，并解决阶段一最扎眼的功能残废——离线页只能存不能看。

## 新增

- **离线页可打开**：`saveWebArchive` 存出来的是 MHTML，而 Android WebView 根本不能渲染 MHTML
  （`file://x.mhtml` 只会显示乱码文本）。新增 `MhtmlArchive`：解析 multipart/related，
  把主 HTML 抽出来、其余资源（图片/CSS/字体）内联成 `data:` URI，落成单文件 `.html` 再打开。
  - 相对路径会按页面 URL / `<base>` 解析后再替换；
  - CSS 里的 `url(...)` 一并内联，避免"样式在、图全丢"；
  - 单资源 ≤512KB、总计 ≤8MB，超出部分保持原链接；
  - 旧版本存的 `.mhtml` 首次打开时惰性转换、就地替换索引；
  - 地址栏继续显示原网址，而不是 `file:///…` 路径。
- **隐私报告**（菜单 → 隐私报告）：本机拦截总数、覆盖域名数、按域名 Top 20、清空统计。
  数据全在本机，不发任何请求。
- **元素选择器**（菜单 → 选择元素隐藏）：进入拾取模式，悬停高亮、点击生成 CSS 选择器并加入本地隐藏规则。
  选择器生成优先用稳定 id / 非哈希类名，必要时补 `:nth-of-type()`；`Esc` 或再点一次菜单退出。
- **本地隐藏规则管理**：隐私报告页列出所有自定义规则，可单条删除或全部清除，改完即时生效（不需要重启）。

## 修复

- **隐私统计会丢数据**：早期实现把「已落盘的 Top 20」当全量合并写回，每次刷盘都会把 20 名以外的域名抹掉。
  现在读全量合并，并改为每 24 次落盘 + 退到后台时落盘。
- 新增本地隐藏规则后必须重启才生效 → 改为 `FilterEngine.withExtraCosmetic()` 叠加，增删规则即时生效且不重新解析整套规则。
- `TabWebView.init()` 里 `allowFileAccess` 与 `cacheMode` 各写了两遍（历史遗留），已去除重复。

## 工程

- 单元测试 20 → **29 条**：新增 `MhtmlArchiveTest`（MHTML 解析、相对引用解析、CSS 内联、quoted-printable 解码）、
  `FilterEngineTest` 补本地规则叠加用例。全绿。
- 文档：`docs/HANDOFF.md`（状态/坑/下一步）、`docs/TESTING.md`（新增 3 条验证项）、
  `docs/DECISIONS.md` 新增 ADR-8（为什么用 MHTML 转 HTML，以及踩到的相对路径坑）。
- 体积：debug APK 13MB（未变）。

## 安装

`Allegro-0.25.0-beta-debug.apk`（Android 8.0+，debug 签名，仅测试用）。

## 请验这几条

1. 打开一个图文多的网页 → 菜单「保存离线页」→ 断网 → 离线页面列表点它 → **正文 + 图片都在**，地址栏是原网址。
2. 旧版存过的离线页（升级前保存的）点开 → 等 1–2 秒 → 能正常显示。
3. 菜单「选择元素隐藏」→ 点页面上的广告块 → 元素消失 + 提示；刷新页面后仍然隐藏。
4. 隐私报告：总数与域名榜随浏览增长；「清空统计」归零；规则列表可单删 / 全清。
5. 保存离线页时切到别的标签再回来 → 不闪退、列表里能看到新条目。

---

# 0.22.3-beta

第三个 beta 里程碑。本版把此前验证过的能力收敛成一个可日常使用的浏览器，
并完成 GitHub 仓库、CI 与发布流程的搭建。

## 新增 / 修复

- **广告过滤**：修复误拦正常网页（主文档永不拦截；同站请求忽略路径片段规则）；
  新增站点白名单（菜单「本站暂停过滤」一键放行并重载）
- **下载器**：修复暂停/取消不生效（DAO 按行 id 更新而非 dmId）；列表增加取消按钮；
  支持断点续传、速度与剩余时间、失败重试
- **书签**：文件夹、移动、Netscape HTML 导入导出（与 Chrome/Firefox 互通）
- **标签页**：拖拽排序；缩放动画 + 背景模糊过渡；下拉关闭；长按菜单；左滑删除；低端机降级
- **JS 注入管线**：viewport 归一化、网页深色（仅改画布）、blob 下载、媒体嗅探、TopRoom 让位、
  滚动状态上报；全部模块幂等，并引入**桥接令牌**防止恶意页面调用原生
- **手势**：工具栏左右滑动切标签、下拉刷新（含容器滚动 / 虚拟滚动判定，判不出即禁用）
- **设置**：主题三态、搜索引擎、首页书签开关、广告过滤面板、应用内更新
- **应用名**：显示名改为 **Allegro**（包名暂留 `com.example.simplebrowser`）

## 工程

- 单元测试 20 条（URL 解析、广告规则、书签 HTML 往返、首页日期）
- 移除实验性 GeckoView 内核（体积 109MB → 12.9MB）
- 新增 GitHub Actions：push 时跑单测 + 构建 debug APK 并上传产物

## 安装

下载 `Allegro-0.22.3-beta-debug.apk` 直接安装（Android 8.0+，arm64/arm32/x86_64 通用）。

> 这是 **debug 签名**包，仅用于测试；正式版会使用 release 签名。
