# 0.72.0-beta

**S1：设计系统组件层（只建组件，未改任何页面）。**

按 `docs/DESIGN_SYSTEM.md` 方案 B（内容穿透顶栏）落地第一层。

## 新增 `ui/design/AllegroComponents.kt`

| 组件 | 规格 |
|---|---|
| `AllegroTopBar` | 56dp 高、透明背景、**底部发丝线**、左 44dp 圆钮 + 居中 20sp 标题 + 右侧 actions；内容可滚到栏下 |
| `AllegroIconButton` | 44dp 圆钮，图标 20dp，自带 `pressScale()` |
| `AllegroCard` | 24dp squircle 卡片 + 可选组标题 |
| `AllegroRow` | 60dp 行、16sp/13sp、可选前置图标 / 右侧值 / chevron；**点击与长按都由组件提供** |
| `AllegroDivider` | 1dp 发丝线，左右缩进与文案对齐 |
| `Modifier.allegroContentPadding()` | 页面内容统一内边距（顶部让出栏高 + 状态栏） |

## 令牌补充

- `TypeScale.Body = 16.sp`（`Row` 作为历史别名保留）
- `Dimens.SpacingXS/S/M/L/XL = 4/8/12/16/20`

## 为什么先只建组件

`solid`/`glass`、`AllegroSheet`、`AllegroPillButton`、`AllegroEmptyState` 还没建；
页面仍在用旧组件，**功能与 0.71.1 完全一致**（本版不改变任何界面）。

## 请验

只需确认 App 正常（无界面变化）：启动、标签面板、二级页返回。

---

# 0.71.1-beta

修 0.71.0 的回归：**长按不弹菜单** + **卡片出现诡异描边**。同一个原因。

## 原因

0.71.0 为了修正圆角，把 `Card(onClick, onLongPress, …)` 换成了 `Surface(…).clickable(onClick)`。

- `clickable` **只有单击**，长按事件被丢掉了 → 长按无反应
- 当前标签的蓝框原本画在 **内层缩略图**上；换成 Surface 后内层与外层圆角不再一致，
  于是边框在角上错位，看起来像"多了一圈奇怪的描边"（截图里那圈粉色/蓝色错位边）

## 改法

```kotlin
Surface(
    shape = shape,
    modifier = Modifier
        .pressScale()
        .then(if (active) Modifier.border(2.dp, primary, shape) else Modifier)  // 边框与外层同形状
        .combinedClickable(onClick = onSelect, onLongClick = onLongPress),      // 长按恢复
)
```

同时**删掉内层的 border** —— 边框只能有一处，否则必然错位。

## 请验

1. **长按标签能弹出浮动菜单**（本版核心）
2. 当前标签的蓝框与卡片圆角**完全贴合**，不再有多余描边
3. 卡片圆角上下一致
4. 三点菜单不透明、最近关闭不透明（0.71.0 的修复保持）

---

# 0.71.0-beta

标签面板三个问题，逐个修。

## 1. 卡片圆角未对齐

之前卡片是 `Card(...)` 包一层、缩略图又用 `Shapes.Tile` 裁一层 —— **两层圆角半径不同**，
角上就会露出白边/错位。

改为 `Surface(shape = Shapes.Tile, ...)`，卡片与缩略图**同一形状**，不再有第二层圆角。

## 2. 长按标签"硬弹出"小窗

`AnchoredMenu` 用的是 `Popup`，没有进场动画。现在补上：首次显示做 `0.92 → 1` 缩放 + 淡入，
`spring(0.9, 480)`，与菜单的落位手感一致。

## 3. 三点菜单弹出后，底部仍能看到原界面

标签面板里的两个 sheet（「标签页」溢出菜单、「最近关闭」列表）原来是**半透明玻璃**。
它们盖住的是**标签面板自己**，透出来就是"底栏文字穿帮"。

给 `MiuixSheet` 新增 `solid` 参数：为 true 时**完全不透明**、且不绘制网页快照。
标签面板内的两个 sheet 都改用 `solid = true`。

## 请验

1. 标签卡：**圆角上下一致**，当前标签的边框与卡片贴合
2. 长按标签：小窗**淡入+轻微放大**，不再硬闪
3. 点右上三点：菜单**不透明**，看不到底下的底栏文字
4. 「最近关闭」列表同样不透明
5. 网页菜单（点 ⋮）仍是半透明玻璃（那个场景要看得到网页）

---

# 0.70.1-beta

修「无法进入标签页」——2b3 引入的**绘制顺序**问题。

## 原因

2b3 把浏览器 UI 变成了 `NavDisplay` 的 Root entry，而 `BrowserNavHost` 是根 `Box` 里的**最后一个兄弟**，
于是它绘制在**所有东西之上**：

```
Box {                       // 根
   TabSwitcherOverlay(...)  // ← 在 NavDisplay 之前 → 被盖住
   PageToastHost(...)       // ← 同样被盖住
   BrowserNavHost(...)      // ← 最后绘制，整屏覆盖
}
```

点标签按钮其实**状态变了**，但面板被 `NavDisplay` 盖在下面，所以"进不去"。

## 改法

把 `TabSwitcherOverlay` 与 `PageToastHost` 移到 `BrowserNavHost` **之后**，绘制顺序回到正确层级：

```
Box {
   BrowserNavHost(...)         // 浏览器 UI + 二级页
   TabSwitcherOverlay(...)     // 面板在最上层
   PageToastHost(...)          // 提示在最上层
}
```

## 请验

1. **点标签数按钮能打开标签面板**（本版的核心）
2. 面板内：卡片、排序、最近关闭、关闭/新建都正常
3. 页内提示（如"已经是第一页"）能显示
4. 二级页返回、冷启动、网页滚动正常

---

# 0.70.0-beta

**A/2b3：浏览器 UI 成为 `NavDisplay` 的 Root entry —— A 方案完成。**

## 结构性改动

| 之前 | 现在 |
|---|---|
| `Scaffold(...)` 与 `NavDisplay` 是兄弟，各自渲染 | `Scaffold` 整体提取为局部 `BrowserRootView()`，作为 `NavDisplay` 的 **Root entry** |
| `NavDisplay` 仅在栈深 > 1 时挂载，栈深 ≤ 1 时不渲染 | **始终挂载**：Root entry 渲染浏览器 UI，二级页 push 进来 |
| 浏览器 ↔ 二级页：挂载即出现（无转场） | 由库提供转场 |

具体做法（避免 500 行缩进出错）：
- 用**括号配对**取到完整的 `Scaffold( ... ) { ... }` 表达式（含 trailing lambda）
- 整体包进局部 `@Composable fun BrowserRootView()`（捕获原有状态，逻辑一行未改）
- `BrowserNavHost(rootContent = { BrowserRootView() })`

## 请优先验这些（这是全项目结构改动最大的一版）

1. **冷启动**：首页正常、地址栏可用
2. **进入网页 → 滚动 → 下拉刷新**
3. 菜单 → 二级页：**有转场**；返回回网页
4. 设置 → 开源许可 → 返回：回设置
5. 标签面板开合、标签切换
6. 大屏/折叠屏：侧栏 padding 是否正常
7. 输入地址时键盘弹出，页面是否被正确顶起（`imePadding`）
8. 转场期间快速连点：不穿透

**任一条异常请立刻回滚到 0.69.0**（分支 `refactor/navdisplay-rewrite` 上 0.69.0 的提交是 `08ea43b`）。

---

# 0.69.0-beta

**A/2b2：`screen` 删除，导航栈成为唯一事实源。**

## 为什么这一步是关键

前两次回归（0.68.0 启动闪退、0.68.2 顶栏透明占位）都是同一个病根：
**存在两个可独立写入的状态**（`screen` 与 `navStack`），只要有一条路径漏同步就出错。

本版把 `screen` 整个删除：

| 之前 | 现在 |
|---|---|
| `var screen by remember { … }` + `screenHistory` + 镜像 `LaunchedEffect` | **`val screen = navStack.currentSecondary() ?: BROWSER`**（纯派生） |
| 所有跳转写 `screen = X` | 统一 `pushSecondary(X)`；回浏览器 `backToBrowser()` |
| 返回靠 `backOnce()` 维护 history | `backOnce()` = `navStack.popSecondary()` |
| `NavDisplay.onBack` 需手动同步 `screen` | 栈变了，`screen` 自动跟着变 |

代码上现在只有**一个可写入口**：`navStack`。

## 删除的代码

- `screenHistory`（38 行相关逻辑）
- 镜像用的 `LaunchedEffect(screen)`
- `NavDisplay.onBack` 里的手动 `screen = …`
- 旧的 `navigate()`（语义已被 `pushSecondary` 覆盖）

## 请验

1. 冷启动正常
2. 菜单进任意二级页 → 返回：回网页
3. 设置 → 开源许可 → 返回：回设置
4. 设置 → 站点规则 → 编辑 → 返回：回站点规则
5. 从二级页返回首页：**顶栏正常**（0.68.2 的修复保持）
6. 标签面板 / 菜单 / 网页滚动正常

---

# 0.68.2-beta

修「回到首页时顶栏消失、上方留一条空白」。

## 原因

`NavDisplay` 的返回回调只做了 `navStack.popSecondary()`，**没有同步 `screen`**。

- `secondaryVisible = screen != BROWSER` 仍为 `true`
- `chromeAlpha` 的目标值停在 0 → 顶栏**透明但占位**
- 于是首页顶部出现一条 ~215px 的空白带（就是你截图里那块）

也就是说：**栈回去了，状态没回去**。

## 改法

`onBack` 里 pop 之后立即把 `screen` 退到栈顶（或 `BROWSER`）：

```kotlin
navStack.popSecondary()
screen = navStack.currentSecondary() ?: SecondaryScreen.BROWSER
```

## 请验

1. 进二级页 → 返回首页：**顶栏（地址栏）正常显示**，上方不再有空白带
2. 从二级页用系统返回键退出：同样正常
3. 首页/网页切换、标签面板、菜单：正常
4. 冷启动：正常

---

# 0.68.2-beta

修「回到首页时顶栏消失、顶部留一条空白」。

## 原因（链条）

`NavDisplay.onBack` 里我只 pop 了栈，**没有同步 `screen`**：

```
pop 栈 → 栈空了 → NavDisplay 不渲染页面（首页露出来）
        → 但 screen 仍是旧二级页 → secondaryVisible = true
        → chromeAlpha 停在 0 → 顶栏"透明但占位"
```

于是首页顶部出现一条约 200px 的空白带（截图里正是这个）。

## 改法

`onBack` 里 pop 之后立刻把 `screen` 退到新的栈顶（栈空则 `BROWSER`）：

```kotlin
navStack.popSecondary()
screen = navStack.currentSecondary() ?: SecondaryScreen.BROWSER
true
```

## 一层反思

这是**双事实源的必然代价**：`screen` 与 `navStack` 都在写，只要有一条路径只更新了其中一个，就会出现这种"状态半更新"的怪象。
2b2 时会把 `screen` 彻底删掉，只保留栈——那时这类 bug 从结构上消失。

## 请验

1. 进任意二级页 → 返回 → 回首页：**顶栏正常显示**，顶部不再空白
2. 连续进出 5 次：每次都正常
3. 二级页之间跳转、返回：语义仍正确
4. 首页搜索框、最近关闭列表布局正常

---

# 0.68.1-beta

**紧急修复：0.68.0 启动即闪退。**

## 原因

```kotlin
LaunchedEffect(screen) {
    val route = SecondaryRoute(screen)   // ← 启动时 screen == BROWSER
    ...
}
```

`SecondaryRoute` 的 `init { require(screen != BROWSER) }` 直接抛 `IllegalArgumentException`
→ 首帧就崩，所以表现为"打开应用即闪退"。

**为什么编译期没暴露**：`require` 是运行时校验；参数在启动时恰好是非法值。

## 改法

1. 同步逻辑**先判断 `BROWSER`**，再构造 route（根页不再构造二级路由）
2. 移除 `SecondaryRoute` 里的 `require` —— 一个构造点拿到非法值就崩溃的代价太大，
   改为文档化约定 + 渲染侧对 `BROWSER` 走空分支

## 教训（已记入流程）

**不变量不要用 `require` 守在构造器里，除非所有调用点都在编译期可证合法。**
更稳的做法是让非法状态"无副作用"（渲染空），而不是让进程崩掉。

## 请验

1. 应用能正常启动（不再闪退）
2. 设置 → 开源许可 → 返回：回设置
3. 二级页之间跳转：有库自带转场
4. 二级页系统返回键、快速连点不穿透
5. 退出二级页回网页正常

---

# 0.68.0-beta

**A/2b1 落地：二级页容器换成 `NavDisplay`。**

## 结构性改动

| 之前 | 现在 |
|---|---|
| `AnimatedVisibility(visibleState = secondaryState)` + 内联 `when (lastScreen) { … }` | `BrowserNavHost`（`NavDisplay`）+ 局部函数 `RenderSecondary(routeScreen)` |
| 转场由我们手写 `slide + fade` | 转场、系统返回、边缘滑动、转场期输入拦截由 `miuix-nav` 接管 |
| `lastScreen` 记录上一页 | 删除；导航栈（`[Root, …]`）承载 |

实现要点：
- `when` 分支体**原样**搬进 `RenderSecondary`（用括号配对脚本定位，未改逻辑）
- 栈由 `screen` **单向、幂等**镜像（`LaunchedEffect(screen)`），不引入第二个可写事实源
- 栈深 ≤ 1 时不挂载 `NavDisplay`（Root entry 暂为空壳，浏览器 UI 仍在外面）

## 已知取舍（2b2 再补）

- **浏览器 → 二级页的进场动画**暂时没有（NavDisplay 是挂载即出现）；二级页**之间**的转场已由库提供
- Root entry 目前是空壳；2b2 把浏览器 UI（顶栏 + WebView + 底栏）搬进去后，才能做到"一个 NavDisplay 管所有页面"

## 请验

1. 设置 → 开源许可 → 返回：回设置（2a 的语义保留）
2. 二级页之间跳转：**有库自带转场**，不是硬切
3. 二级页里按系统返回键：能回上一页 / 回浏览器
4. 转场期间快速点击：不应穿透到 WebView 或地址栏
5. 其余功能与 0.67.0 一致

---

# 0.67.0-beta

A/2a：**修好二级页的返回语义**（在不换容器的前提下）。

## 你报的问题

设置 → 开源许可 → 返回，**直接回到网页**，而不是回设置。

原因：所有页面跳转都是 `screen = X` 直接覆盖，没有历史；`back` 一律 `screen = BROWSER`。

## 改法（2a：语义先行）

- 新增 `screenHistory`：**页内跳转**（Settings → Sync / SiteRules / ShaderSmoke / Licenses / NavPilot、SiteRules → Site）会记录来源
- 返回统一走 `backOnce()`：有历史就回上一页，没有才回浏览器
- **从浏览器进入二级页不记历史** —— 保持“从菜单进设置，返回回网页”的旧习惯
- 系统返回键同样走 `backOnce()`

## 与 NavDisplay 的关系

这一步**没有**引入 `NavDisplay`：它是可独立验证、可独立回滚的语义修正。
下一步 A/2b 再把容器换成 `BrowserNavHost`，那时只需搬运渲染，语义已经对了。

## 请验

1. 设置 → 开源许可 → **返回**：应回到**设置**（不再是网页）
2. 设置 → 站点规则 → 编辑站点 → 返回：回到**站点规则**
3. 从菜单进入设置 → 返回：回到**网页**（旧习惯保留）
4. 系统返回键：与顶栏返回行为一致
5. 连续两次返回：Licenses → Settings → 网页

---

# 0.66.1-beta

修试点页「清空栈」崩溃，并把库的硬约束写进决策记录。

## 崩溃原因（日志实证）

```
java.lang.IllegalArgumentException: NavDisplay back stack cannot be empty
    at NavDisplayKt.NavDisplay(NavDisplay.kt:1032)
```

`NavDisplay` **不允许空栈**。我原方案里「空栈 = 在浏览器主界面」的假设是错的。

## 这一条同时解释了你说的第二件事

「设置里的二级页返回会直接回到网页」——因为现在是 `replace` 语义，栈里始终只有一页，
返回没有“上一页”可回。改成 `push` 后：Settings → 开源许可 → 返回**回到 Settings**。

## 本版改动

- 试点页去掉「清空栈」，改为「弹回栈底（保留 Root）」
- `ADR-17` 写入 `DECISIONS.md`：导航栈结构定为 `[浏览器 Root, (二级页…)]`，Root 恒在栈底，任何代码不得 `clear()`
- 导航语义确定：进二级页 `push`；二级页之间 `push`；返回 `pop`；栈深为 1 时交给 Activity（网页后退 / 双击退出）

## 结算：A 方案的完整结构（下一步实施）

`BrowserScreen` 要改成 **NavDisplay 的 Root entry**：

| 现状 | 重写后 |
|---|---|
| 一个大 Box：顶栏 + WebView + 底栏 + 二级页浮层 | `NavDisplay(stack)` 的 Root entry = 顶栏 + WebView + 底栏 |
| 二级页用 `when(screen)` + `AnimatedVisibility` | 二级页是 push 进来的 entry，转场/返回/输入拦截由库管 |
| `screen` 变量是事实源 | `backStack` 是唯一事实源，`screen` 从栈顶派生 |
| 旧 `BackHandler(enabled = screen != BROWSER)` | 删除，返回交给 `NavDisplay.onBack` |

## 请验

1. 试点页点「弹回栈底」：不崩溃，回到 Root
2. Settings → 开源许可 → 返回：**目前仍回网页**（旧行为未改，A 方案实施后才变）
3. 其它功能与 0.66.0 一致

---

# 0.66.0-beta

NavDisplay 重写 **第 1 步：在试点页验证契约**（不动生产导航）。

## 背景

一份诊断意见指出我原方案的关键漏洞：把 `AnimatedVisibility` 与 `NavDisplay` 并存当"安全中间态"是
**假安全**——两套转场同时驱动同一批页面，旧 `BackHandler` 还会和 `NavDisplay.onBack` 抢返回。
该意见已采纳，重写改为「原子切换 + 唯一事实源」。

## 本版新增（试点页）

| 项 | 用途 |
|---|---|
| 栈深显示 | 直观看到 `backStack.size` 与栈顶，空栈时显示「等价于在浏览器主界面」 |
| replace 成详情页 | 验证二级页之间跳转**不叠栈**（对应 Settings → Sync 这类页内跳转） |
| 清空栈（空栈） | 验证 `NavDisplay` 对**空栈**不崩、不残留、不白屏 |

## 为什么先做这个

正式切换后，`BROWSER` 对应「空栈」。空栈行为如果没在试点验证过，就要等**上线后才第一次被验证**。

## 请验

1. 进入「设置 → 诊断 → 转场实验」
2. 「栈深」显示 1，栈顶为 `PilotRoot`
3. 点「清空栈」：页面变成空白（只剩外壳），**不崩溃、不残留旧页面**
4. 点「replace 成详情页」：栈深仍为 1（不叠栈），标题变详情页
5. push 后再按系统返回：栈深递减，回到空栈后停在空白

---

# 0.65.0-beta

动画专项收尾两件（C6 / C10）。Coil **未引入**，原因见文末。

## C6 页内提示（`PageToastHost`）

新增应用内提示浮层，替代系统 Toast：

- 从底部升起 + 淡入（`tween(Standard)`），2 秒后淡出
- 胶囊形状、`surface` 95%，跟随主题
- 先接两处：工具栏「已经是第一页 / 没有可前进的页面」

为什么不直接用 Toast：Android 11+ 的 Toast 是系统窗口，样式跟不了 App、位置固定、还会被输入法顶走。

## C10 历史页空态交叉淡入

在「空态 ↔ 列表」之间加 `Crossfade(Standard)`。上一次（0.60）做这个时把 `grouped` 的作用域挤坏了，
这次是**整段替换 + 括号配对检查**，编译通过。

## 为什么没装 Coil

原计划 0.65 引入 Coil 3，核对后放弃：我们的图片全部来自 WebView 回调的 `Bitmap`
（favicon、标签预览），**没有 URL 源**；Coil 的强项（URL → 图片 + 缓存 + crossfade）在这批数据上退化为
"Bitmap → 同一张 Bitmap"，淡入还得手写。收益≈0，代价 +300KB 与一条依赖登记。

## 进度

动画专项 **24 / 26**：仅剩 C5（元素高亮框，需改 JS）与 C2（星标，缺行内收藏入口）。

## 请验

1. 在首页按「后退」：底部出现**应用内胶囊提示**（不是系统 Toast），2 秒后消失
2. 历史页清空数据：空态与列表之间有**交叉淡入**
3. 其它功能与 0.64.1 一致

---

# Allegro 版本说明（近期）

> 只保留最近一段的版本记录；更早的历史在 git tag 与 GitHub Releases 里可查（`v0.22.3-beta` … `v0.54.2-beta`）。

# 0.64.1-beta

修试点页的两个显示问题（与 `NavDisplay` 无关，是我漏了外壳）。

## 现象

- 试点页底下透出首页（搜索框、最近关闭列表）
- 顶部文字压到状态栏上

## 原因

试点页是**裸的 `NavDisplay`**，没有套 `ScreenScaffold`：
- 少了不透明底色 → 首页从底下透出来
- 少了 `statusBarsPadding` + 顶栏高度 → 文字顶到状态栏

这跟 0.44 那次"二级页透出一级页"是**同一类错误**（换掉外壳时丢了它隐式提供的底色与内边距）。

## 改法

`ScreenScaffold(title = "转场实验", onBack = onBack) { inner -> NavDisplay(modifier = inner, ...) }`
标题与返回交给外壳，`NavDisplay` 只负责内部转场。

## 请验

1. 试点页**不再透出首页**，顶部不再压状态栏
2. push 详情页有转场；系统返回 / 边缘右滑按预期
3. 退出试点回设置正常

---

# 0.64.0-beta

**推翻 0.63.1 的结论：`miuix-nav` 可用。** 之前是我用错了 API。

## 我错在哪

0.63.1 我调用的是 `NavDisplay(backStack, entryProvider = entryProvider { ... })`，
而 `entryProvider` 与那个重载在源码里都是 `internal`，于是判定"库未公开 API"。

查源码（`miuix-nav/src/commonMain/.../NavDisplay.kt`）后确认：**有三个重载，前两个是 public**：

```kotlin
@Composable
fun NavDisplay(backStack: NavBackStack, modifier, onBack, transition, effects,
               content: NavEntryBuilder.() -> Unit)   // ← 公共，DSL 形式
```

`entryProvider` 只是它内部的辅助函数，**外部不应调用**。改用 DSL 后编译通过。

## 第二个阻塞的解决

库以 **JVM 21** 编译，工程原来是 17。已装 JDK 21 并把
`sourceCompatibility / targetCompatibility / jvmTarget` 全部升到 **21**。

## 本版内容

- 新增 `NavPilotScreen`：`NavDisplay` + 两个试点路由，验证转场 / 系统返回 / 边缘手势
- 设置 → 诊断 →「转场实验（miuix-nav）」进入
- 依赖：`miuix-nav 0.9.4` 重新接入

**这是试点，不参与生产导航**；验证通过后再把二级页整体迁过去。

## 请验

1. 设置 → 诊断 → 转场实验：能进入
2. 点「push 一个详情页」：**有转场动画**（不是硬切）
3. 详情页按系统返回键：能回到试点首页
4. 从屏幕左缘右滑：观察是否跟手返回
5. 退出试点后，App 其余功能与 0.63.1 一致

---

# 0.63.1-beta

**miuix-nav 迁移试点：失败，已回滚。** 这版只回滚，功能与 0.63.0 一致。

## 试了什么

写了一个独立试点页（`NavPilotScreen`，两个假路由），用 `NavDisplay + NavController`
验证转场与返回，然后编译。

## 两个否决性阻塞

1. **API 是 internal**
   ```
   Cannot access 'fun entryProvider(...)': it is internal in file
   Cannot access 'fun NavDisplay(backStack, entryProvider, ...)': it is internal in file
   ```
   javap 里看着是 `public`，但 Kotlin 侧标了 `internal` —— **库方尚未公开对外 API**。
   能用的只有 `NavController` / `NavKey` 这类数据层，`NavDisplay` 这个渲染入口拿不到。

2. **JVM target 不兼容**
   ```
   Cannot inline bytecode built with JVM target 21 into bytecode built with JVM target 17
   ```
   该库用 **JVM 21** 编译，本工程是 **JVM 17**。

## 结论

`miuix-nav 0.9.4` 目前**不可用**：要么等它公开 API，要么我们升到 JDK 21 + jvmTarget 21
（仍解决不了 internal 问题，所以升级也没有意义）。

已回滚：删除试点页、移除依赖与路由入口。

## 保留的成果

- `miuix-squircle`（0.62 已生效，继续保留）
- `AllegroIcons` 单一图标入口（0.63 已生效）

## 请验

本版应与 0.63.0 **完全一致**（无功能变更）。

---

# 0.63.0-beta

图标体系收敛：**全站唯一图标入口 `AllegroIcons`**。

## 做了什么

19 个文件里的 `MiuixIcons.X` 全部改为 `AllegroIcons.X`，图标实现集中在
`ui/theme/AllegroIcons.kt` 一处，并定义三个尺寸档位：

| 档位 | 尺寸 | 用途 |
|---|---|---|
| `Chrome` | 20dp | 工具栏、圆形按钮 |
| `Row` | 24dp | 列表行、菜单行 |
| `Large` | 32dp | 空态、强调入口 |

## 为什么这样做，而不是直接换图标库

我上一版说要换 **Tabler Icons**，但这版我**没有引**：

1. 我无法在线核实 Tabler 的 Compose 构件坐标与维护状态（搜索返回的都是无关结果）
2. 更关键的是：**在不做真机目检的前提下批量换 19 个文件的图标，风险远大于收益**——这正是本会话连续出回归的模式

所以先做**结构收敛**：所有调用点统一到一处。将来要换 Tabler（或任何图标集），
只需改 `AllegroIcons.kt` 的实现，**调用点零改动**。这才是"统一"的实际含义。

## 请验

1. 全站图标外观应与 0.62.0 **完全一致**（本版只改引用路径，未换图标资源）
2. 工具栏、菜单、列表、设置页均正常显示
3. 若有某一处图标**变空/不显示**，说明该图标没有登记进 `AllegroIcons`——把页面名发我，我补一条

---

# 0.62.0-beta

外观统一第一刀：**超椭圆圆角**（用 `miuix-squircle`，不再手写圆角曲线）。

## 改动

| 项 | 之前 | 现在 |
|---|---|---|
| 圆角形状 | `RoundedCornerShape(24/20/28dp)`（标准圆弧） | **超椭圆（squircle）**：由 `LocalSquircleEnabled` 全局开启，Miuix 的 `Card` / `Surface` / `Sheet` 自动生效 |
| 行高 | 52dp（SheetRow）与 60dp（SettingRow）并存 | 统一 **60dp**（`Dimens.RowHeight`） |

超椭圆和普通圆角的差别在**拐角的曲率过渡**：普通圆弧在切点处曲率突变，看起来"方一点"；
squircle 的曲率连续，观感更接近 Luna / iOS。这是 4dp 级别的差别，但整屏一起看很明显。

## 实现方式

```kotlin
CompositionLocalProvider(LocalSquircleEnabled provides true) { BrowserScreen(...) }
```

一个组合局部量，全 App 生效；不需要在每个 `Modifier.clip()` 处改形状。

## 请验

1. 设置页卡片、菜单面板、标签卡：**拐角是否更"饱满"**（不是标准圆弧）
2. 菜单/长按面板：行高变高一点（52 → 60），点击区域更大
3. 全局无视觉错位（超椭圆可能导致 1px 级边缘差异，若某处圆角看起来"缺角"请告我具体页面）

---

# 0.62.0-beta

外观统一第一步：**全局超椭圆圆角** + 行高收敛。

## 1. 超椭圆（squircle）

引入 `miuix-squircle`，在 `MainActivity` 的根组合处：

```kotlin
CompositionLocalProvider(LocalSquircleEnabled provides true) { ... }
```

这一个局部量让 **Miuix 的 Card / Surface / Sheet / Dialog 全部改用超椭圆圆角** ——
比我们之前逐个 `RoundedCornerShape(24.dp)` 更接近 Luna 的形状手感（直角过渡更"整"）。

## 2. 行高收敛

`SheetRow` 的 52dp → **60dp**（`Dimens.RowHeight`），与设置页 `SettingRow` 一致。
此前全项目并存 52 / 60 / 64 三套行高，是"规范不统一"的直接来源之一。

## 3. 说明

- 我们自己用 `Shapes.Card/Tile/Panel` 裁切的容器（如 `GroupedCard`、标签卡）**不会自动变超椭圆**，
  下一版再逐处换成 Miuix `Surface(shape=…)` 或 `SquircleBackground`，避免一次改动过大。
- 图标集统一（Tabler）尚未做：需要 20+ 文件的映射替换，单独一版。

## 请验

1. 设置页卡片、菜单面板、对话框的圆角：**应比之前更"方中带圆"**（超椭圆特征）
2. 菜单/列表面板行高比之前略高，文字更透气
3. 其余功能与 0.61.0 一致

---

# 0.61.0-beta

**验证性版本**：接入 `miuix-nav` 依赖，不改任何界面。目的是先确认"能不能用"，再谈"怎么用"。

## 验证结论（三条都过）

| 项 | 结果 |
|---|---|
| 依赖可解析 | ✅ `top.yukonga.miuix.kmp:miuix-nav:0.9.4` |
| 与现有 Compose 冲突 | ✅ 无。它依赖 **JetBrains Compose**（`org.jetbrains.compose.foundation`），与本工程用的 `org.jetbrains.compose` 插件同族；`navigationevent` 统一到 1.1.2 |
| 编译 | ✅ `compileDebugKotlin` 通过（依赖已在 classpath，未调用） |

## 它到底提供什么（读 AAR 得到）

| 包 | 内容 |
|---|---|
| `nav.core` | `NavController`、`NavBackStack`、`NavDisplay`、`LocalNavTransitionScope` |
| `nav.gesture` | **`NavSwipeDismiss`**（边缘滑动返回）、**`NavPredictiveBackDriver`**（预测性返回驱动）、`NavSwipeArbitrator`（手势仲裁） |
| `nav.transition` | 页面转场（与 miuix 动效令牌一致） |

## 这意味着什么

它不是"零散小组件"，而是**自成一套的导航容器**：`NavController + NavDisplay`。
接入方式只有一种：**把我们现在手写的 `screen` 状态机换成 `NavDisplay`**，才能同时拿到
转场、滑动返回、预测性返回、转场期输入拦截（`blockInput`）。

本版只装了依赖，**没有替换任何页面**，所以功能与 0.60.0 完全一致。

## 请验

1. App 能正常启动、各页面可用（应当与 0.60.0 无差别）
2. 设置 → 开源许可：`miuix` 条目仍在（依赖清单未变）
3. 若启动异常，说明依赖引入影响了运行时——请把 diag.log 发我

---

# 0.60.0-beta

头部收敛 + 动画专项收尾（能做的都做了）。

## 1. 标签面板头部收敛（解决"设计规范不统一"）

之前头部是「标题 + 最近关闭胶囊 + 排序胶囊 + 圆钮」四个元素挤在一行，窄屏还会截断成「按打…」。

现在：**标题 + 一个圆形溢出按钮**，菜单里两项：

| 菜单项 | 行为 |
|---|---|
| 最近关闭的标签页 | 打开列表面板 |
| 排列方式 | 显示当前档位（按打开时间 / 按标题 / 按网站），点击循环 |

与设置页、主菜单的"少量控件 + 圆钮"规范一致。

## 2. 动画专项新增

| # | 项 | 说明 |
|---|---|---|
| **低端机降级** | 并入 `Motion.reduceMotion`：`isLowRamDevice` 时位移类动效压到 Instant |
| **C10 空态交叉淡入** | 书签页在「空态 ↔ 列表」之间 `Crossfade(Standard)` |

## 3. 仍未做的 4 项（不是遗漏，是缺前置）

| # | 卡在哪 |
|---|---|
| C2 星标弹动 | 「收藏」只有菜单项，没有可动画的行内入口 |
| C5 元素选择器高亮 | 高亮框由 JS 绘制，需改 JS 注入脚本 |
| C6 页内提示 | 需要新建 Snackbar 宿主（下版做） |
| C8 搜索建议逐项 | 建议列表功能尚未实现 |

历史页的空态交叉淡入**撤回了**：那次插入把 `grouped` 的作用域挤坏，编译不通过。
按 ADR-16，这类改动下次整段重写而不是插字符串。

## 请验

1. 标签面板头部：**标题 + 一个圆点按钮**，不再有胶囊
2. 点圆钮：出现「最近关闭的标签页 / 排列方式（当前：按打开时间）」
3. 点「排列方式」：档位循环切换，头部菜单显示当前档
4. 书签页在「有数据 ↔ 空」之间切换时有淡入淡出
5. 其余与 0.59.0 一致

---

# 0.59.0-beta

日志导出改成**三条独立路径**（你之前遇到过"日志导不出来"）。

## 问题

原来只有一条路：`ACTION_SEND` + `FileProvider` 拉起系统分享面板。
很多精简 ROM 会裁掉文件分享目标，一点就没反应——这是"无法导出"的常见原因。

## 现在（设置 → 诊断）

| 行 | 行为 |
|---|---|
| **分享日志文件** | 系统分享面板；**调不起时自动退化为复制到剪贴板**并提示 |
| **复制日志到剪贴板** | 不依赖分享面板，最可靠 |
| **保存到「下载」目录** | 生成 `allegro-diag-时间戳.log`（走 MediaStore，Android 10+ 免存储权限） |
| **清空日志** | 导出后清理 |

分享时补了 `clipData`（部分接收方只认 ClipData 才拿到读权限）。

## 请验

1. 设置 → 诊断：看到四行
2. 点「保存到下载」→ 提示已保存 → 用文件管理器能在「下载」里找到 `allegro-diag-*.log`
3. 点「复制日志」→ 粘贴到任意输入框，内容应以 `MM-dd HH:mm:ss.SSS [事件]` 开头
4. 点「清空日志」后再导出 → 提示"暂无日志"

---

# 0.58.0-beta

还债版：**把说错的地方改对，把踩过的坑写死**。无新功能，界面仅「开源许可」页分组变化。

## 1. 合规页纠错（事实错误）

`ThirdPartyRegistry` 原来用 `vendored: Boolean`，把「读过它的代码」和「复制了它的代码」混为一谈。
0.54 删掉两个 vendored 文件后，页面仍写着"直接复用了 UI Kit 的源码"——**与事实不符**。

改为三态 `EntryStatus`：

| 状态 | 含义 |
|---|---|
| `VENDORED` | 复制了源码（合规上最需要显式声明） |
| `REFERENCE` | 参考思路/设计，未复制代码 |
| `DEPENDENCY` | 依赖库 |

许可页相应分成 **复用源码 / 参考致谢 / 依赖库** 三组。当前无 `VENDORED` 条目，UI Kit 与 KernelSU 归入 `REFERENCE`。

## 2. 文档同步（此前全是过期数字）

| 位置 | 之前 | 现在 |
|---|---|---|
| README | minSdk 26 / Android 8.0+ | **minSdk 33 / Android 13+** |
| README | 67 条单测 | **107 个用例** |
| README | release 1.9 MB | **3.6 MB** |

## 3. DECISIONS 新增 5 条（并作废旧规则）

- **ADR-12** 覆盖层不得隐藏 WebView —— **作废 ADR-9**（那条正是玻璃/背景动画连坏三个版本的根源）
- **ADR-13** 动画值不得在组合期读取
- **ADR-14** 浮层组件必须"真机三验"（能打开 / 能输入 / 能关闭），且不得放进 `verticalScroll` 或 `weight` 容器
- **ADR-15** 改性能代码不得顺手删除守卫
- **ADR-16** 同一文件连续修补 >2 次，整段重写

## 4. 清理

删除已空的 `ui/components/vendor/` 目录。

## 请验

1. 设置 → 关于 → 开源许可：**三组**（复用源码 / 参考致谢 / 依赖库），"复用源码"下没有条目
2. 其余功能与 0.57.2 一致（本版未改业务逻辑）

---

# 0.57.2-beta

修「点最近关闭没有反应」——**0.57.1 里那个面板被我误删了**。

## 原因

0.57.1 把 `MiuixSheet` 从 `TabGrid` 的 Column 里移出来时，删除区间取错了尾部，
把**整个「最近关闭」面板一起删掉了**，只留下 `showRecent = true` 这个状态和一个按钮。
所以点胶囊后什么都没发生（0.57.0 里能看到列表，是因为那时面板还在）。

## 改法

把面板重新插到外层 `Box`（`BoxWithConstraints` 闭合之前），并补：

- 过滤掉 `about:blank` 这类噪音条目，最多取 5 条
- 恢复时写一行 `[restore-closed] <url>` 日志，便于下次定位

## 请验

1. 关掉 1–2 个真实网页 → 打开标签面板 → 点「最近关闭」：**应弹出列表**
2. 点其中一条：面板收起，**新标签打开该网页**
3. 列表里不应再出现 `about:blank`
4. 卡片仍正常显示（0.57.1 的修复保留）

---

# 0.57.1-beta

## 修「标签页整片消失」

**根因**：我把「最近关闭」的 `MiuixSheet` 放进了 `TabGrid` 的 `Column` 里。
`MiuixSheet` 根节点是 `fillMaxSize()` 的全屏容器，作为 Column 子项会抢走全部高度，
把带 `weight(1f)` 的网格压成 **0 高** —— 于是表头和底栏都在、**卡片全不见**。

**改法**：把它移到外层 `Box`（网格的兄弟节点），面板不再参与网格布局。

## 顺带

- 头部三个控件统一：两个胶囊 + **圆形关闭按钮**（原来是裸 `IconButton`，尺寸和基线不齐）
- 去掉卡片上的 `Modifier.animateItem()`（它给每张卡加独立动画层，与本卡自己的 `graphicsLayer` 叠加，属不必要的风险）
- 打开面板时 `scrollToItem(0)`，避免复用上次滚动位置

## 请验

1. 打开标签面板：**卡片正常显示**（不再是空白 + 模糊首页）
2. 头部：`标签（3）` + 「最近关闭」胶囊 + 排序胶囊 + 圆形关闭按钮，基线齐平
3. 点「最近关闭」：底部弹出列表，点一条在新标签打开
4. 关掉面板再打开：网格从顶部开始，不会停在中间

---

# 0.57.0-beta

标签页第二版：**最近关闭** + 复制链接反馈（动画 C3）。

## 新增

| 项 | 说明 |
|---|---|
| **最近关闭** | 标签面板头部新增「最近关闭」胶囊，点开列出最近 5 个关闭的标签（标题 + 域名），点一条即在新标签打开 |
| **C3 复制反馈** | 长按标签菜单里点「复制链接」→ 该行图标变 **✓**、文案变「已复制」，600ms 后复原；**菜单不再自动关闭**（方便连续复制） |

数据来自现有 `closed_tabs` 表（`controller.recentClosed(5)`），打开面板时读取一次。

## 说明

原计划里的「长按菜单改浮动面板」**没有做**：当前长按菜单已经是锚定在卡片上的 `AnchoredMenu`（0.53 已落地），
再改一轮只是形态微调，收益低于风险。这一版的预算放在了更有用的「最近关闭」上。

## 请验

1. 标签面板头部：出现「最近关闭」胶囊
2. 关掉 2 个标签 → 点该胶囊：能看到这两条，点一条即在新标签打开
3. 长按某个标签 → 点「复制链接」：图标变 ✓、文案变「已复制」，菜单**不关闭**，600ms 后复原
4. 排序、拖拽、开合动画仍正常

---

# 0.56.0-beta

标签页收尾（方案 A：非「按打开时间」时禁用拖拽重排）。

## 新增

| 项 | 说明 |
|---|---|
| **标签排序** | 头部新增胶囊按钮，点击循环 **按打开时间 → 按标题 → 按网站** |
| **排序与拖拽互斥** | 只有「按打开时间」时允许长按拖动重排；其余两档拖拽被禁用（`pointerInput` 直接 return），避免"拖完立刻被排序打回原位" |
| **标签卡按压反馈** | 补上 `pressScale()`（0.96 缩放 + 0.9 透明），与其它卡片一致 |

排序**只影响展示顺序**，不改 `state.tabs` 底层顺序，也不动 WebView 池索引——所以切换排序不会重载页面。

## 关于「大屏 TabsRail 性能治理」

本版**没有改**：实际读代码后确认 `TabsRail.kt` 里**没有任何动画或 `progress` 读取**，
是一张普通的 `LazyColumn`，不存在标签面板那种"每帧重组"的问题。原计划里的这一项属于误判，已撤销。

## 请验

1. 标签面板头部右侧出现胶囊：显示「按打开时间」
2. 点一下变「按标题」，卡片按标题字母序排列；再点变「按网站」
3. 在「按标题」/「按网站」下**长按卡片不会拖动**
4. 切回「按打开时间」：可正常长按拖拽
5. 按压缩放：手指按住卡片有轻微内缩

---

# 0.55.1-beta

修你反馈的两条。

## 1. 二级页「有概率硬切」→ 背景被瞬间摘掉

`overlayActive` 里含 `screen != BROWSER`，所以**打开二级页那一刻 WebView 被设成 `GONE`**，
背景瞬间消失——A1/A2 的缩放与压暗根本没机会被看见，观感就是"有时候硬切"。
（"有时候"是因为点得快时你看到的是页面入场动画，点得慢时看到的是背景消失。）

已改为：二级页**不隐藏 WebView**（二级页自身不透明，遮得住）。现在应该稳定看到
"背景后缩 6% + 压暗 8%" → 页面滑入 → 返回反向。

## 2. 「减少动画」不再依赖开发者选项

新增应用内开关：**设置 → 首页设置 → 减少动画**（说明：关闭位移类动效，只保留淡入淡出）。
判定逻辑：**应用内开关 OR 系统 `ANIMATOR_DURATION_SCALE == 0`**，
所以既不用进开发者选项，也能跟随系统无障碍设置。

## 请验

1. 首页 → 点菜单进设置：背后网页**明显后缩并变暗**，不再硬切
2. 连续快速进出 5 次：每次都有缩放过渡（不应偶发瞬切）
3. 设置 → 首页设置 → 打开「减少动画」→ 再开菜单/进二级页：**变瞬时**（只淡入）
4. 关闭该开关后动画恢复

---

# 0.55.0-beta

动画专项一次推进两批（原计划 0.59 / 0.60 / 0.61）。**本轮完成 10 项，另 7 项因依赖 WebView/JS 或需要新组件，明确留到下一轮。**

## 本轮完成（10）

| # | 项 | 实现 |
|---|---|---|
| **B3** | 下载进度插值 | `animateFloatAsState(tween(Instant))`，进度不再跳变 |
| **B9** | 隐私报告数字滚动 | `animateIntAsState(tween(Standard))` |
| **B6** | 标签拖拽让位 | 标签卡加 `Modifier.animateItem()`，重排/关闭时其余卡片平滑补位 |
| **A1** | 二级页进入 | 背景网页 `1 → 0.94` 缩放（`spring(0.9, 320)`） |
| **A2** | 二级页返回 | 同一过渡反向回弹 |
| **A1/A2 压暗** | 背景 8% 随进度压暗，`drawBehind` 内读进度（不额外重组） |
| **Reduce Motion** | 读 `ANIMATOR_DURATION_SCALE`；为 0 时面板进场与标签面板进度压到 `tween(0)` |
| B2 | 列表新增 | 由 `animateItem` 的 fadeIn 覆盖，已生效（书签/历史） |
| C1 | 按压反馈 | 0.47 已做，本轮回归验证 |
| C4 | 下拉刷新 | 已有，回归验证 |

## 本轮**未做**（7 项 + 1 项降级）

| # | 项 | 为什么留 |
|---|---|---|
| C2 | 收藏星标弹动 | 需要先决定「收藏」入口放哪（目前只有菜单项） |
| C3 | 复制链接 ✓ | 需要菜单项内联状态，等长按菜单改浮动面板时一起做 |
| C5 | 元素选择器高亮 | 高亮框由 JS 绘制，需改 JS 侧 |
| C6 | 页内提示 | 需要新建 Snackbar 宿主组件，单独一版 |
| C7 | 桌面版切换值 | 现在值是文本，交叉淡入价值低 |
| C8 | 搜索建议逐项 | 当前建议列表未做逐项动画，需重构列表 |
| C10 | 空态切换 | 需要 `Crossfade` 包裹各列表空态 |
| 横切 | 低端机降级 | 只在标签面板有 `simplify`，扩展需逐页评估 |

## 请验

1. 下载页：进度条**平滑推进**，不再一格一格跳
2. 隐私报告：数字**滚动**而不是瞬间变化
3. 标签面板拖拽重排 / 关闭：其余卡片**平滑补位**
4. 打开设置等二级页：背后网页**轻微后缩并变暗**，返回时回弹
5. 开发者选项把「动画时长缩放」设为关闭 → 面板出现变**瞬时**（只淡入）

---

