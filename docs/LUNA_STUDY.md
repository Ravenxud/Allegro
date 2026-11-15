# Luna 实现研读：搜索栏材质 / 进度条 / 标签页

> 来源：Luna APK 反编译（`com.mt.luna` + 混淆包 `defpackage`）。
> 说明：应用自身的 Compose 代码被 R8 重命名到无法按符号检索，能确认的是**结构与数据流**；
> 具体数值（弹簧参数、圆角）无法从二进制可靠还原，下文凡属推断都标了「推断」。

## 1. 搜索栏材质

| 证据 | 位置 |
|---|---|
| 内嵌了完整的 `MiuixGlass` AGSL 程序（折射/受光/内阴影/亮度曲线） | `b06.java` |
| 玻璃上下文带 **backdrop + buttonBackdrop** 两份采样层 | `p16.java`（`GlassTopAppBarContext`） |
| 页面级采样层 `pageBackdrop` | `jc2.java` |
| 面板材质 `SheetMaterial(contentAlpha, barBackdrop)` | `l3d.java` |
| 玻璃着色器与 `createBlurEffect` **串联**（shader 采样被模糊过的层） | `ng8.java` |

结论：
- 搜索栏/顶栏不是「半透明色块 + 亮边」，而是 **对捕获层跑 AGSL 折射**，模糊在着色器之前；
- 一份 backdrop 给整条栏，**每个圆形按钮再各自一份**（按钮才有"厚度"）；
- 截图里栏内是**浅色磨砂 + 深色字**，说明材质按"背景明暗"选明/暗两套。

## 2. 进度条

数据流（可确认）：

```
WebView onProgressChanged(i)
  → 只在 (i >= 100 || tab.isLoading) 时 setValue(i)      // ule.java:602
  → UI 侧读这个 Int State
```

要点：
- **回调里不跑动画**，只写一个整数状态；动画完全在 UI 层做；
- 只在必要时更新（完成或加载中），避免停顿期反复推状态；
- 因此"生硬"与"顺滑"的差别全在 UI 侧怎么消费这个整数：直接 `progress = i/100f` 会每 1% 跳一下。

（推断）Luna 的做法是双段式：已确定部分用低刚度 spring 追，未确定阶段用循环"呼吸"，
完成时补到 1.0 并淡出 —— 这是唯一直观上"顺"且不需要假进度的做法。

## 3. 标签页与快照

| 事实 | 证据 |
|---|---|
| 面板打开前先抓整窗快照 | `captureStub → requestCopy(window, rect)` |
| 快照先**降采样到 480 宽**再三次方框模糊 | `blurForSheet()` |
| 日志里每次抓图都打印 `[sheet-capture] 480x1068` | 运行日志 |
| 面板玻璃底来自这张预模糊位图，而非实时 RenderEffect | `captureForSheet` / `sheetSnapshot` |

结论：**"先抓、先糊、再动画"**。动画期间不再碰 PixelCopy，也不对整层做实时模糊。

## 4. 我们当前标签页的问题（对照）

| 现象 | 我们的实现 | 后果 |
|---|---|---|
| 动画生硬 | 有 `animate(offsetX, 0f, Snap)` 这类手动 snap，且入场无 stagger | 硬切感 |
| 模糊不稳定 | 打开时抓一次**整窗原图**，动画中做 `blur()` | 页面一变就露馅；缩放时边缘抖动 |
| 卡顿 | 1) 大图每帧缩放+模糊 2) `dragOffset` 等 state 每帧写 → 重组 3) 快照异步回调里 `setState` 才开始动画 | 首帧空/跳、掉帧 |

## 5. 重做方案（待执行）

1. **快照管线**：每标签缓存 `480 宽 + 预模糊` 位图；打开时**用缓存立即开动画**，后台补新快照后替换；
   页面 URL 变化 / 尺寸变化才失效。动画期间零 PixelCopy。
2. **动画**：所有位移/缩放在 `graphicsLayer{}` 里读 `Animatable` 的值（只重绘不重组）；
   入场按索引 stagger 24–32ms；`spring(dampingRatio = .85f, stiffness = 380f)` 统一手感。
3. **模糊**：动画期间只画预模糊位图；静止 300ms 后才允许一次刷新（沿用 sheet 的节流思路）。
4. **布局**：`LazyVerticalGrid` 固定 item 比例 + 稳定 key，避免测量抖动导致的重排。

验收标准：
- 打开/关闭面板：首帧即有内容，无空白帧；
- 连续开关 10 次：不闪、不跳、无明显掉帧；
- 页面滚动后打开：卡片内容与当前页一致（允许最多 300ms 的旧快照）。
