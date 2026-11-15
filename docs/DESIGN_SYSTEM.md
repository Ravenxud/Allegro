# 设计系统收口规格（v1，冻结功能后执行）

> 目标：把"零件拼装"改成"一套系统"。**页面不允许再自己写字号、间距、圆角、壳**。
> 依据：本会话实测——84 处硬编码 `.sp`、3 个自绘壳（ScreenScaffold / GroupedCard / MiuixSheet）、
> 8 次回归全部发生在"自绘结构"上。

---

## 0. 三条硬规则（先立规则，再谈组件）

| 规则 | 判定方式（CI/脚本可查） |
|---|---|
| R1 页面不得出现裸字号 `\d+\.sp` | `grep -rn "\.sp" app/src/main/java` 只允许命中 `ui/theme/` 与组件目录 |
| R2 页面不得出现裸 `padding(` / `height(` 数值 | 只允许用 `Spacing.*` 令牌 |
| R3 页面不得自建顶层容器 | 每个页面必须以 `AllegroTopBar` + `AllegroCard` 组合，禁止 `Scaffold` 直写 |

---

## 1. 令牌层（唯一来源）

### 1.1 字号（四档，禁止第五档）

| 令牌 | 值 | 用途 |
|---|---|---|
| `Type.Title` | 20sp / SemiBold | 页面标题、标签面板标题 |
| `Type.Body` | 16sp | 行主文案、按钮文字 |
| `Type.Secondary` | 13sp | 副文案、说明、分组标题 |
| `Type.Caption` | 12sp | 网格标签、极小说明 |

> 现状问题：15/14/13/12/11 五档混用 → 收口后 **15→16、14→13、11→12**。

### 1.2 间距（六档）

| 令牌 | 值 | 用途 |
|---|---|---|
| `Spacing.xs` | 4dp | 图标与文字 |
| `Spacing.s` | 8dp | 行内元素、卡片内小间隔 |
| `Spacing.m` | 12dp | 页面左右边距、分组间距 |
| `Spacing.l` | 16dp | 卡片内边距、区块间距 |
| `Spacing.xl` | 20dp | 行首/行尾缩进 |
| `Spacing.xxl` | 32dp | 页面顶部留白 |

### 1.3 形状

| 令牌 | 值 | 用途 |
|---|---|---|
| `Shapes.Card` | 24dp squircle | 分组卡、列表卡 |
| `Shapes.Tile` | 20dp squircle | 标签卡、缩略图卡 |
| `Shapes.Panel` | 28dp（顶部两角） | 底部面板 |
| `Shapes.Floating` | 28dp 四角 | 浮动面板 / 对话框 |
| `Shapes.Capsule` | 50% | 胶囊按钮 |

### 1.4 尺寸

| 令牌 | 值 |
|---|---|
| `Size.IconChrome` | 20dp |
| `Size.IconRow` | 24dp |
| `Size.CircleButton` | 44dp |
| `Size.RowHeight` | 60dp |
| `Size.TouchMin` | 48dp |
| `Size.Hairline` | 1dp（白 8%） |

### 1.5 动效（唯一来源 `Motion`）

| 场景 | 规格 |
|---|---|
| 面板进出 | 进场 `spring(0.9, 320)`；出场 `tween(200, Accelerate)` |
| 列表增删位移 | `spring(0.9, 320)` |
| 按压 | `0.96` 缩放 + `0.9` 透明，120ms |
| 页面转场 | 由 `miuix-nav`（`NavDisplay`）提供，**不再手写** |
| 减少动画 | `Motion.reduceMotion(context)` 为真 → 全部 `tween(0)` |

---

## 2. 组件层（只有这七个，页面禁止自造）

### 2.1 `AllegroTopBar`
- 结构：左 `CircleIconButton(Back)` + 居中 `Title` + 右 `actions`
- 高度 56dp，`statusBarsPadding`
- 背景：内容穿透 + 发丝线；**不透明度由调用方声明**（默认不透明）
- 替换：`ScreenScaffold` 的顶栏部分、各页手写 `Row`

### 2.2 `AllegroCard`
- 结构：`Surface(shape = Shapes.Card)` + 可选 hairline + 可选 `header`
- 内部行通过 `AllegroRow` 排列，行间 `AllegroDivider`
- 替换：`GroupedCard`、Miuix `Card` 直用、标签卡外壳

### 2.3 `AllegroRow`
- 60dp 高、`Spacing.xl` 行首、`Spacing.l` 行尾
- 参数：`title / summary? / value? / leadingIcon? / trailing`（默认 chevron）
- 自带 `pressScale()` + `clickable`（**不许页面自己加 clickable**）
- 替换：`SettingRow`、`SheetRow`、`BasicComponent` 直用

### 2.4 `AllegroSheet`
- 底部面板：`Shapes.Panel`、拖拽条、标题、右侧动作、内容可滚
- 背景两档：`Glass`（背后是网页，走快照模糊）/ `Solid`（盖住自身界面，纯不透明）
- 替换：`MiuixSheet` 全部用法

### 2.5 `AllegroIconButton` / 2.6 `AllegroPillButton`
- 44dp 圆钮（图标 20dp）／胶囊（高 40dp，文字 `Type.Body`）
- 自带按压反馈与无障碍标签
- 替换：`CircleIconButton`、`PillButton`、散落的 `IconButton`

### 2.7 `AllegroEmptyState`
- 图标 32dp + `Type.Body` 标题 + `Type.Secondary` 说明
- 与列表之间用 `Crossfade(Standard)` 切换（由组件内部处理）

---

## 3. 页面映射（14 个二级页 + 首页 + 标签面板）

| 页面 | 现状 | 收口后 |
|---|---|---|
| 设置 | 自绘 SectionLabel + GroupedCard + 混用 Miuix | `AllegroTopBar` + 4×`AllegroCard` + `AllegroRow` |
| 书签 / 历史 / 下载 | `ScreenScaffold` + 自绘 Card 行 | `AllegroTopBar` + `AllegroCard`/`AllegroRow` |
| 站点设置 / 站点规则 / 用户脚本 | 同上 + `SheetRow` | 同上 |
| 离线页 / 标签组 / WebDAV / 隐私报告 / 着色器 | 同上 | 同上 |
| 开源许可 | 三组卡片 | `AllegroCard` ×3，条目 `AllegroRow` |
| 转场实验（试点） | `ScreenScaffold` + 裸 NavDisplay | 保留，作为 NavDisplay 的对照页 |
| 首页 | 手写搜索框 + 卡片 + 最近关闭 | `AllegroCard`；搜索框抽 `AllegroSearchField` |
| 标签面板 | 自绘头部 + `Surface` 卡 + 两个 sheet | `AllegroTopBar`（标题+1 圆钮）+ `AllegroCard` 变体（Tile）+ `AllegroSheet(Solid)` |

**删除清单**：`ScreenScaffold.kt`、`GroupedCard.kt`（并入 `AllegroCard`）、`MiuixSheet.kt`（并入 `AllegroSheet`）、`PillButton`/`CircleIconButton`（并入 `Allegro*`）。

---

## 4. 执行顺序（每步单独截图验收）

| 步 | 内容 | 产出 |
|---|---|---|
| S1 | 建 `ui/design/` 七个组件 + 令牌迁移；不改任何页面 | 编译通过，页面仍用旧组件 |
| S2 | 设置页迁到新组件 | 一张对照截图 |
| S3 | 书签/历史/下载（列表三页） | 三张 |
| S4 | 其余八页（批量，机械替换） | 每页一张 |
| S5 | 首页 + 标签面板 | 两张 |
| S6 | 删除旧组件；跑 R1–R3 脚本；出包 | 脚本 0 命中 |

估时：S1 半天，S2–S5 各半天，S6 半天 ≈ **3 天**。

---

## 5. 验收清单（每页都要过）

- [ ] 顶栏：左圆钮 / 居中标题 / 右侧动作，基线一致
- [ ] 字号只有四档，且与相邻页一致
- [ ] 页边距 12dp，卡片内边距 16/20dp，行高 60dp
- [ ] 卡片圆角 24dp，内部元素无第二层圆角
- [ ] 按压有 0.96 反馈，长按有对应动作（不许"换组件丢事件"）
- [ ] 返回：顶栏 / 系统键 / 预测性返回三种入口行为一致
- [ ] 深色模式下无对比度问题
- [ ] `TalkBack` 能读出每组标题与每行文案
