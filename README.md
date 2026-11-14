# Allegro

轻量、安静、数据留在本机的 Android 浏览器。

- **小**：release 包约 1.9 MB，无统计 SDK、无账号体系、无推送
- **快**：标签页 WebView 池化复用，切换不重载
- **安静**：广告过滤（内置规则 + 可订阅）、隐私报告、元素选择器
- **能读**：阅读模式、系统 TTS 朗读、离线页（自包含单文件 HTML，断网可看）
- **按站点做主**：每个域名单独控制 JavaScript / 图片 / 桌面版 / 广告过滤
- **数据是你的**：书签、文件夹、站点设置、偏好可通过任意 WebDAV 同步

## 安装

从 [Releases](https://github.com/Ravenxud/Allegro/releases) 下载最新 APK 直接安装。

- 系统要求：Android 8.0+（minSdk 26）
- 当前为 beta，使用 debug 或自签名包，覆盖安装会保留数据
- 包名：`com.ravenxud.allegro`

## 构建

```bash
# 环境：JDK 17 + Gradle 9.9.1 + AGP 9.5.1 + compileSdk 37
./gradlew assembleDebug          # 13 MB，可调试
./gradlew assembleRelease        # 1.9 MB，R8 + 资源收缩
./gradlew testDebugUnitTest      # 67 条单测
./gradlew lintDebug              # 0 error 才能合并
```

若在 aarch64 Linux 上 `aapt2` 无法执行，需要 qemu shim：

```bash
gradle -Pandroid.aapt2FromMavenOverride=/opt/aapt2shim/aapt2 assembleDebug
```

发布流程见 `scripts/release.sh`（打 tag → 建 Release → 上传 APK → 回读校验）。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/HANDOFF.md](docs/HANDOFF.md) | 交接说明：代码地图、架构约定、踩过的坑、下一步 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层与模块职责 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | 技术决策记录（ADR） |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 迭代路线 |
| [docs/DESKTOP_MODE.md](docs/DESKTOP_MODE.md) | 桌面版模式的技术方案 |
| [docs/ANIMATION.md](docs/ANIMATION.md) | 动画专项方案（令牌、26 项规格、帧率预算） |
| [docs/TESTING.md](docs/TESTING.md) | 真机验证清单 |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | 逐版本变更 |

## 已知限制

- 桌面版模式受 WebView 限制（无法修改 Client Hints），方案见 `docs/DESKTOP_MODE.md`
- WebDAV 密码以明文存于本机 SharedPreferences，请使用应用专用密码
- 无账号体系，跨设备同步依赖你自己的 WebDAV

## 许可

个人项目，未附带开源许可证文件；如需引用请先开 issue 沟通。
