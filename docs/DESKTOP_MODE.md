# 桌面版模式（Request Desktop Site）技术方案

> 状态：**0.30.0 已实现**（主文档代理 + 全局开关 + 站点覆盖）。
> 目标：**任意站点**开启桌面版后，稳定拿到桌面布局，不白屏、不循环、不丢登录态。
> 结论先行：只改 UA 永远做不到，必须由 App **自己抓主文档**并伪造完整的 Client Hints。

---

## 1. 现状与根因

现在「桌面版」只做了一件事：`WebSettings.setUserAgentString(桌面 UA)`。

问题在于现代站点判定设备类型有**两套依据**：

| 依据 | 谁发送 | 我们能不能改 |
|---|---|---|
| `User-Agent` | WebView 网络栈 | ✅ `setUserAgentString` |
| `Sec-CH-UA-Mobile` / `Sec-CH-UA-Platform` 等 Client Hints | WebView 网络栈（Chromium 元数据） | ❌ **WebView 没有 API** |

B 站的现象就是两边判断不一致导致的 302 死循环：

```
m.bilibili.com  --(UA 是桌面)-->  302  www.bilibili.com
www.bilibili.com --(hint 是手机)--> 302  m.bilibili.com    ← 每 300ms 一轮
```

其它站点表现更隐蔽：不循环，但把你重定向回 `m.`，于是"开了桌面版还是手机页"。

`WebViewClient.shouldInterceptRequest` 只能**读**请求，不能改写出站请求头；
Chromium 命令行开关在非 root 设备上不可用；`setUserAgentMetadata` 属于 embedder 内部 API，未暴露。

**结论：只要请求仍由 WebView 发出，hints 就一定错。**

---

## 2. 候选方案对比

| 方案 | 做法 | 优点 | 致命问题 | 结论 |
|---|---|---|---|---|
| A. 只改 UA + JS 指纹 | 现状 + 覆盖 `navigator.userAgentData` | 零成本 | 服务端 302 改不了 | 只作为补丁 |
| B. 单文档代理 | 主文档由 App 用 OkHttp 抓，伪造全部头，再喂给 WebView | 完全控制请求头；只处理主文档，工作量可控 | 需处理 Cookie 同步、相对路径、POST | **选定** |
| C. 全局 MITM 代理 | `VpnService` + 本地 CA，改写所有请求 | 彻底 | 要装 CA、要 VPN 授权、破坏 HTTPS 信任、耗电 | 不做 |
| D. 桌面侧渲染 | 服务端/PC 渲染后截图 | 100% 桌面版 | 需要服务器或 PC，违背"本机"定位 | 不做 |

---

## 3. 选定方案：单文档桌面代理（DesktopDocumentProxy）

### 3.1 触发条件

按优先级取第一个命中：

1. 站点设置为「强制桌面」→ 开
2. 站点设置为「强制移动」→ 关
3. 标签页工具栏的桌面开关（临时）
4. 全局设置「始终请求桌面版」→ 开
5. 默认 → 关

### 3.2 请求流程

```
WebView 要加载主文档 (GET, http/https)
        │
        ▼
shouldInterceptRequest(isForMainFrame && 桌面模式 && GET)
        │
        ├─ 命中 30s 缓存 → 直接返回缓存
        │
        ▼
DesktopFetcher.fetch(url)
   头：User-Agent: <桌面 UA，可切 macOS/Windows>
       Sec-CH-UA-Mobile: ?0
       Sec-CH-UA-Platform: "macOS"
       Sec-CH-UA: "Chromium";v="123", ...
       Accept / Accept-Language / Referer: 取原请求
       Cookie: CookieManager.getCookie(url)
        │
        ├─ 手动跟随重定向（最多 5 跳）
        │     ├─ 若目标 host 与本页同站且是 m./www 互换 → 允许 1 次，
        │     │   第 2 次直接停止（打破循环），用当前 HTML 渲染
        │     └─ 每跳都同步 Set-Cookie 到 CookieManager
        │
        ▼
返回 WebResourceResponse(html)
        │
        ▼
后处理：若最终 URL ≠ 请求 URL，注入 <base href="最终URL">
        （否则页面里的相对路径会全部 404，地址栏却是另一个域名）
```

**要点：一旦由我们抓取，重定向链就掌握在我们手里，物理上不可能无限循环。**

### 3.3 关键实现点

| 点 | 做法 | 不这么做会怎样 |
|---|---|---|
| Client Hints | 我们自己发 `Sec-CH-UA-Mobile: ?0` + `Platform: "macOS"` | 服务端仍判为手机 |
| Cookie 同步 | 出站 `CookieManager.getCookie`；入站解析全部 `Set-Cookie` 写回 | 登录态丢失，页面变未登录 |
| 相对路径 | 最终 URL 与请求 URL 不同时注入 `<base href>` | 图片/CSS 全挂 |
| POST 不代理 | 表单提交、上传、OAuth 回调交给 WebView 原路径 | 重放 POST 会丢 body，登录直接坏 |
| 失败回退 | 超时/TLS/非 2xx/非 HTML → 返回 null，让 WebView 正常加载 | 一个站点失败就整站打不开 |
| 缓存 | LRU，30 秒，键 = URL + UA | 每次刷新多一跳，体感变慢 |
| 只做主文档 | 子资源、XHR/fetch 不动 | 全量代理等于自建 MITM，成本爆炸 |
| 熔断兜底 | 保留现有「6 秒互跳 ≥5 次」保护 | 极端站点仍有循环 |

### 3.4 安全边界

- 仅在 http/https、仅主文档、仅 GET、仅桌面模式开启时启用
- 不落盘页面内容；诊断日志只记录 URL 与状态码（`desktop-fetch-*`）
- 不修改页面脚本（除已有的注入模块）

---

## 4. 测试策略（可在本机构建/单测覆盖）

1. **请求头构造**（纯函数）：`DesktopHeaders.build(url, desktopUa, cookies, accept)` → 断言三件套 hint 都在
2. **重定向策略**（纯函数）：输入一串 `Location`，断言"允许一次 m./www 互换、第二次停止"
3. **Set-Cookie 解析**：多值、`Expires` 含逗号、`Path`/`Domain`/`SameSite` 场景
4. **base 注入**：最终 URL 不同才注入，已有 `<base>` 时不重复
5. **集成测试**：本地起一台 mock 服务器 —— `Sec-CH-UA-Mobile: ?1` 时 302 到 `/m`，
   `?0` 时返回桌面页；断言 `DesktopFetcher` 拿到的是桌面页且没有 302 循环
   （与现有 `scripts/dav_server.py` 同一套做法，用环境变量 gate，CI 自动跳过）
6. **真机验收**：B 站 / 知乎 / 豆瓣 / 微博 / 淘宝 各开一次桌面版

---

## 5. 迭代编排

| 版本 | 内容 | 工量 | 可验证性 |
|---|---|---|---|
| **0.30.0** | 代理引擎 + 全局"始终桌面版"开关 + 站点级覆盖 + 失败回退 + 诊断日志 | 3–4 天 | 单测 + mock 服务器集成测试；真机看效果 |
| **0.30.1** | 桌面视口调优（1280 宽 + 缩放）、UA 选择（macOS/Windows）、登录流程兜底提示 | 1–2 天 | 真机 |
| **0.31** | 站点规则管理页（强制桌面/强制移动/默认 + 导入导出），替代零散的开关 | 2 天 | 单测 + 真机 |

> 这版之后，原计划的「标签组」顺延到 0.32，「动画专项」0.33，「HLS/无障碍」0.34。

---

## 6. 验收标准

- [ ] B 站开桌面版：**一次加载即停在桌面布局**，无 302 循环（诊断日志无 `redirect-loop`）
- [ ] 知乎/豆瓣/微博桌面版布局正确，图片样式完整（base 注入生效）
- [ ] 登录流程可用：能在桌面版页面输入账号并提交（POST 走 WebView 路径）
- [ ] 失败站点（如强制 App 跳转的）自动回退，**不白屏**
- [ ] 主文档首字节延迟增加 < 300ms；缓存命中 < 50ms
- [ ] 关闭桌面版后行为与现在完全一致

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 部分站点按 UA 给不同 HTML，桌面 HTML 依赖桌面专用 JS/CSS | 样式错乱 | base 注入 + 失败自动回退 + 一键切回移动版 |
| Cookie 同步不完整（`SameSite=None`、HttpOnly 多域名） | 登录态丢失 | 尽量写回；失败时提示"桌面版可能导致登录异常" |
| 页面用 `Referer` 做防盗链 | 图片 403 | 透传原请求 Referer |
| SPA 首屏后由 JS 再跳 `m.` | 又回移动版 | 已有的 `DesktopHintsModule` 覆盖前端指纹 |
| 大文件/PDF 主文档被代理 | 内存压力 | 只代理 `Content-Type: text/html` 且 < 3MB，其余回退 |

---

## 8. 明确不做

- 不做 VPN/MITM 全局代理（要装证书、破坏信任链）
- 不做"云端渲染桌面版"（需要服务器，违背本机定位）
- 不代理 POST/上传/流媒体（风险远大于收益）
