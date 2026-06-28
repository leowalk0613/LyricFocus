<div align="center">

# LyricFocus

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

在小米 HyperOS 上于**锁屏、AOD（息屏显示）、通知栏**展示同步歌词。  
通过 LSPosed 注入 SystemUI，使用 HyperOS **焦点通知**（`miui.focus.*`）渲染歌词，可选超级岛。

包名：`com.leowalk.LyricFocus`

</div>

---

## 📋 目录

- [功能概览](#功能概览)- [系统要求](#系统要求)- [架构与数据流](#架构与数据流)- [项目结构](#项目结构)- [安装与配置](#安装与配置)- [设置项](#设置项)
- [歌词源](#歌词源)- [进程间通信](#进程间通信)- [Xposed Hook](#xposed-hook)- [依赖](#依赖)
- [版本更新](#版本更新)- [调试与反馈](#调试与反馈)- [已知限制](#已知限制)- [致谢](#致谢)- [许可证](#许可证)

---

## 功能概览

- **Material 3 界面**：跟随系统壁纸 **Monet 动态取色**（Android 12+ Material You）
- **实时歌词**：`NotificationListenerService` 绑定 MediaSession，监听播放进度与元数据
- **多歌词源**：网易云音乐、QQ 音乐（自动链式回退或指定单一源）
- **焦点通知**：锁屏 / AOD 使用 `miui.focus.rv`、`miui.focus.rvAod` 自定义 RemoteViews，支持 updatable 会话续期
- **超级岛（可选，默认关闭）**：开启走 `IslandApi`；关闭时注入 `dismissIsland`，避免 HyperOS 3 生成默认小岛
- **应用白名单**：可选仅对指定音乐 App 的 MediaSession 响应
- **歌词提前量**：可调同步偏移（默认提前 1300 ms）
- **Root 重启 SystemUI**：设置页一键重启系统界面，Hook 变更后快速生效
- **统一通知渠道**：后台服务通知合并为单一渠道，前台通知显示当前播放状态
- **关于界面**：包含软件信息、GitHub 链接、酷安作者链接、系统要求、致谢与开源许可证
- **LSPosed 日志查看**：应用内选择日志文件/ZIP压缩包，自动筛选 LyricFocus 相关日志，支持一键复制

---

## 系统要求

| 项目 | 要求 |
|------|------|
| 系统 | **小米 HyperOS 3.0 + **（验证环境：HyperOS 3.0.x，如 `3.0.302.0.WNCCNXM`） |
| Android | API **31+**，`targetSdk 34` |
| 框架 | **LSPosed**（或兼容 Xposed 实现），API 82+ |
| LSPosed 作用域 | `com.android.systemui`（系统界面）、`com.miui.aod`（息屏与锁屏编辑） |
| 权限 | 通知访问、发送通知、网络、前台服务 |
| 可选 | Root（Magisk / KernelSU）— 应用内重启 SystemUI、查看 LSPosed 日志 |

### 额外使用条件

**焦点通知白名单**：需移除系统级焦点通知白名单限制，否则 LyricFocus 无法正常显示歌词。可通过以下 LSPosed 模块实现：

| 模块 | 说明 | 链接 |
|------|------|------|
| **HyperIsland** | 移除焦点通知白名单，支持自定义超级岛内容 | https://github.com/1812z/HyperIsland |
| **HyperCeiler** | HyperOS 系统增强模块，包含焦点通知相关功能 | https://github.com/ReChronoRain/HyperCeiler |

> 若未安装上述模块，焦点通知可能无法显示或被系统拦截。

---

## 架构与数据流

App 进程拉歌词，SystemUI 进程发焦点通知：

```mermaid
flowchart LR
    subgraph App["com.leowalk.LyricFocus"]
        MMS[MusicMonitorService]
        LS[LyricService]
        LM[LyricManager]
        MMS -->|MediaSession| LS
        LS --> LM
        LS -->|Broadcast| SYSUI
    end

    subgraph SystemUI["com.android.systemui"]
        HOOK[SystemUIHyperFocusHook]
        STYLE[HyperFocusLyricStyle]
        HOOK --> STYLE
        STYLE -->|FocusApi.sendDiyFocus| FN[锁屏 / AOD / 岛]
    end

    subgraph AOD["com.miui.aod"]
        AODH[AodFocusPluginHook]
    end

    SYSUI --> FN
    AODH -.-> FN
```

1. `MusicMonitorService` 获取活跃 `MediaController`，回调 `LyricService`
2. `LyricService` 拉取歌词，100 ms ticker + Alarm 兜底推进行号
3. 向 SystemUI 广播 `UPDATE_LYRIC` / `LYRIC_DATA`（见 [进程间通信](#进程间通信)）
4. `SystemUIHyperFocusHook` 调用 `HyperFocusLyricStyle.postFocusNotification()`
5. SystemUI 就绪后发 `REQUEST_RESYNC`，`FocusResyncReceiver` 重推状态

---

## 项目结构

```
LyricFocus/
├── settings.gradle
├── build.gradle
└── focus/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/xposed_init          → FocusMainHook
        ├── java/com/leowalk/LyricFocus/
        │   ├── MainActivity.kt
        │   ├── AboutActivity.kt        → 关于界面（软件信息、日志查看）
        │   ├── AppWhitelistActivity.kt
        │   ├── FocusPreferences.kt
        │   ├── lyric/                  # 网易云 / QQ、LRC 解析
        │   ├── service/                # MediaSession、歌词服务、通知管理
        │   ├── notification/           # HyperFocusLyricStyle
        │   ├── receiver/               # FocusResyncReceiver
        │   ├── util/                   # RootHelper（含日志读取）
        │   └── xposed/                 # SystemUI / AOD Hook
        └── res/layout/                 # focus_lyric_lock / aod / island / about
```

---

## 安装与配置

### 前置条件

开始之前请确认：

| 项目 | 说明 |
|------|------|
| 设备 | 小米 / Redmi，已升级 **HyperOS**（建议 HyperOS 2 / 3，焦点通知完整） |
| Android | **12 及以上**（`minSdk 31`） |
| Bootloader | 已解锁（安装 LSPosed 所需） |
| LSPosed | 已通过 Magisk / KernelSU 等模块安装并启用 |
| 网络 | 拉取歌词需联网（网易云 / QQ 音乐 API） |
| Root（推荐） | 非必须，但 Hook 变更后可在应用内一键重启 SystemUI、查看 LSPosed 日志 |

> 已在 HyperOS **3.0.302.0.WNCCNXM** 环境验证。其他 HyperOS 版本若焦点通知 API 有差异，可能需要适配。

---

### 方式一：下载 Release APK（推荐）

1. 在 [Releases](../../releases) 页面下载最新 `focus-*.apk`（或 `focus-debug.apk`）
2. 将 APK 传到手机，在系统设置中允许「安装未知来源应用」
3. 点击 APK 完成安装
4. 继续下方 [LSPosed 配置](#lsposed-配置) 与 [应用权限](#应用权限)

---

### 方式二：自行编译

**环境要求**：JDK 17、Android SDK（`compileSdk 34`）、Gradle（随仓库 Wrapper）

```bash
# 克隆仓库
git clone https://github.com/leowalk0613/LyricFocus.git
cd LyricFocus

# 编译 Debug APK
./gradlew :focus:assembleDebug
```

产物路径：

```
focus/build/outputs/apk/debug/focus-debug.apk
```

**Android Studio**：打开项目根目录 → Sync Gradle → 选择运行配置 **`focus`** → Run。

安装到已连接设备：

```bash
adb install -r focus/build/outputs/apk/debug/focus-debug.apk
```

---

### LSPosed 配置

1. 打开 **LSPosed 管理器**
2. 进入 **模块** 列表，找到并启用 **LyricFocus**
3. 点击模块名称进入 **作用域**，勾选以下应用（缺一不可）：
   - **系统界面**（`com.android.systemui`）
   - **息屏与锁屏编辑**（`com.miui.aod`）
4. 保存后 **重启 SystemUI** 使 Hook 生效，任选其一：
   - 打开 LyricFocus → **重启系统界面**（需 Root，推荐）
   - LSPosed 日志页面对 SystemUI 执行重启
   - 直接 **重启手机**（最稳妥）

> 若只勾选 SystemUI 而未勾选 `com.miui.aod`，AOD 焦点歌词可能无法显示或权限校验失败。

---

### 应用权限

首次打开 LyricFocus 后，在 **权限设置** 卡片中逐项完成：

| 权限 | 如何开启 | 用途 |
|------|----------|------|
| **通知访问** | 点击「授权」→ 系统列表中找到 LyricFocus 并开启 | 读取 MediaSession，获取歌曲信息与播放进度 |
| **发送通知** | Android 13+ 点击「授权」允许；若已拒绝则跳转应用通知设置 | 前台服务通知、可选通知栏歌词 |
| **Root**（可选） | Magisk / KernelSU 授予 LyricFocus Root | 应用内重启 SystemUI、查看 LSPosed 日志 |

**HyperOS 额外建议**：

- **设置 → 应用 → LyricFocus → 省电策略** 设为「无限制」，避免后台被杀
- **设置 → 通知与状态栏 → 通知管理 → LyricFocus** 允许通知、关闭「静默」
- 若使用白名单，确认目标音乐 App 未被「应用双开」隔离到不同用户空间

---

### 首次使用步骤

按顺序操作，可减少「装了但没歌词」的情况：

1. **安装 APK** 并完成 [LSPosed 配置](#lsposed-配置)
2. **Root 重启 SystemUI**（或重启手机）
3. 打开 **LyricFocus**，授予 **通知访问** 与 **发送通知**
4. 确认 **服务状态** 显示为「运行中」
5. 打开任意音乐 App **播放歌曲**（带歌词 metadata 更易命中）
6. **锁屏** 或 **息屏（AOD）**，应看到焦点通知区域的歌词随播放更新
7. （可选）开启 **超级岛显示歌词**；默认关闭，仅在锁屏 / AOD / 焦点区更新

**歌词源**：默认 `auto`（先网易云后 QQ）。可在设置中切换；界面会显示当前歌曲与命中来源。

**同步偏移**：歌词偏慢向右拖，偏快向左拖（默认提前 1300 ms）。

---

### 验证清单

| 检查项 | 预期结果 |
|--------|----------|
| LSPosed 模块状态 | LyricFocus 已启用，作用域含 SystemUI + AOD |
| 通知访问 | 设置页显示「已授权」 |
| 服务状态 | 「运行中」 |
| 播放音乐 | 「歌词获取源」下方出现歌曲名与来源命中 |
| 锁屏 | 焦点通知区显示当前歌词行 |
| 换行 | 歌词随进度切换，无明显长时间卡住 |

---

### 卸载

1. LSPosed 中 **关闭** LyricFocus 模块
2. **重启 SystemUI** 或重启手机（避免残留 Hook 行为）
3. 系统设置中 **卸载** LyricFocus
4. （可选）在「通知访问」设置中确认 LyricFocus 条目已消失

---

## 设置项

偏好文件：`lyric_focus_prefs`（`FocusPreferences.kt`）

| 设置 | 键 | 默认 | 说明 |
|------|-----|------|------|
| 焦点通知歌词 | `focus_lyric_enabled` | 开 | 总开关 |
| 通知栏显示 | `show_in_notification_shade` | 关 | 下滑通知栏是否显示 |
| 超级岛显示歌词 | `show_on_super_island` | 关 | 开启用 `IslandApi`；关闭则 `dismissIsland` |
| 应用白名单 | `app_whitelist_enabled` | 关 | 限制 MediaSession 包名 |
| 歌词获取源 | `lyric_source` | `auto` | `auto` / `netease` / `qq` |
| 歌词提前量 | `sync_advance_ms` | `1300` | -1000 ~ 3000 ms |
| AOD 保活间隔 | `aod_keepalive_sec` | `9` | 受焦点会话 ~9s 系统上限约束 |

---

## 歌词源

| 源 | Provider | API |
|----|----------|-----|
| 网易云 | `NetEaseLyricProvider` | `music.163.com/api/...` |
| QQ 音乐 | `QQMusicLyricProvider` | `c.y.qq.com/...` |

- `auto`：先网易云，失败再 QQ
- 按 MediaSession 的**标题 + 艺术家**搜词；播放器不限于上述两家（如 Spotify，只要 API 能搜到）

`LrcParser` 支持标准 LRC、`[mm:ss:cc]` 网易格式、翻译合并、跳过作词/作曲行。

---

## 进程间通信

**App → SystemUI**（`setPackage("com.android.systemui")`）：

| Action | 说明 |
|--------|------|
| `com.leowalk.LyricFocus.action.UPDATE_LYRIC` | 当前行、第二行、播放状态 |
| `com.leowalk.LyricFocus.action.LYRIC_DATA` | LRC JSON、position、offset |
| `com.leowalk.LyricFocus.action.PLAYBACK_STATE` | 播放/暂停 |
| `com.leowalk.LyricFocus.action.SETTINGS_CHANGED` | 设置变更 |

**SystemUI → App**：

| Action | 说明 |
|--------|------|
| `com.leowalk.LyricFocus.action.REQUEST_RESYNC` | SystemUI 就绪，请求重推 |

---

## Xposed Hook

入口：`com.leowalk.LyricFocus.xposed.FocusMainHook`

| 作用域 | 主要类 | 职责 |
|--------|--------|------|
| `com.android.systemui` | `SystemUIHyperFocusHook` | 广播、焦点通知、权限 bypass |
| | `SystemUIPluginHook` | 焦点/AOD 插件 ClassLoader bypass |
| | `FocusIslandSuppressHook` | 关闭超级岛兜底 |
| | `FocusAntiFlickerHook` | 换行防闪烁 |
| `com.miui.aod` | `AodFocusPluginHook` | AOD 进程焦点权限 bypass |

焦点通知构建见 `HyperFocusLyricStyle.kt`：`FocusApi.sendDiyFocus()`、渠道 `channel_id_focusNotifLyrics`。

---

## 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| [HyperFocusApi](https://github.com/ghhccghk/HyperFocusApi) | 2.0 | `miui.focus` 参数封装 |
| [Xposed API](https://api.xposed.info/) | 82 | LSPosed Hook 接口 |
| OkHttp | 4.12.0 | 歌词 HTTP |
| AndroidX / Material / Coroutines | 见 `focus/build.gradle` | UI、MediaSession |

---

## 版本更新

### v1.1.0

- **统一通知渠道**：后台服务通知合并为单一 `lyric_service` 渠道
- **关于界面**：添加软件详细信息、GitHub 链接、酷安作者链接、系统要求、致谢与开源许可证
- **LSPosed 日志查看**：应用内选择日志文件/ZIP压缩包，自动筛选 LyricFocus 相关日志，支持一键复制
- **包名变更**：改为 `com.leowalk.LyricFocus`

---

## 调试与反馈

### 日志标签

| Tag | 来源 |
|-----|------|
| `LyricFocus_Xposed` | Xposed 入口 |
| `SystemUIHyperFocusHook` | SystemUI Hook |
| `LyricService` | 歌词服务 |
| `MusicMonitorService` | 媒体监控服务 |

### 查看 LSPosed 日志

#### 方法一：应用内查看（推荐）

1. 打开 **LSPosed 管理器**
2. 点击右上角菜单 → **保存日志**（会导出为 zip 压缩包）
3. 打开 **LyricFocus** → 点击底部导航「关于」
4. 在「日志查看指南」下方点击 **选择日志文件筛选**
5. 选择刚才导出的 zip 文件
6. 应用会自动筛选并显示 LyricFocus 相关日志
7. 如需反馈问题，点击 **复制日志** 将日志复制到剪贴板

#### 方法二：ADB 命令

```bash
# 查看所有 LyricFocus 相关日志
adb logcat | findstr "LyricFocus"

# 保存日志到文件
adb logcat -d > logcat.txt
```

### 日志文件说明

LSPosed 导出的 zip 压缩包包含以下文件：

| 文件 | 说明 |
|------|------|
| `startup.log` | 模块启动日志 |
| `error.log` | 错误日志（排查 crash 首选） |
| `logcat.log` | 完整系统日志 |

### 反馈问题

遇到问题时，请按以下步骤操作：

1. **确认问题场景**：记录问题发生的具体条件（如：播放某首歌时、锁屏时、AOD 时等）
2. **保存日志**：按照「查看 LSPosed 日志」方法一保存日志
3. **复制日志**：在应用内日志查看器中点击「复制日志」
4. **选择反馈渠道**：
   - **GitHub Issues**：[https://github.com/leowalk0613/LyricFocus/issues](https://github.com/leowalk0613/LyricFocus/issues)
   - **酷安作者**：[https://www.coolapk.com/u/551303](https://www.coolapk.com/u/551303)
5. **描述问题**：详细描述问题现象，并附上复制的日志内容

### 常见问题排查

**Q：LSPosed 已启用，但完全没有歌词**

- 确认作用域是否同时勾选 `com.android.systemui` 与 `com.miui.aod`
- Root 重启 SystemUI 或重启手机
- 检查通知访问是否已授权
- 在应用内日志查看器中过滤 `LyricFocus_Xposed`、`SystemUIHyperFocusHook` 是否有报错

**Q：锁屏有歌词，AOD 没有**

- 确认 `com.miui.aod` 在作用域内
- 系统设置中 AOD 已开启且支持焦点通知样式
- HyperOS 焦点 updatable 会话约 **9 秒**超时，AOD 依赖周期性续期；若仍异常可查看 `LyricService` 日志

**Q：有通知但没有歌词文本 / 只显示占位**

- 当前歌曲可能在网易云 / QQ API 未搜到，尝试切换歌词源
- 检查网络与 DNS；部分区域需稳定网络访问 `music.163.com` / `c.y.qq.com`

**Q：歌词有时不会自动刷新**

- 已修复：添加了 5 秒定时健康检查，自动检测并刷新歌词状态

**Q：Hook 或设置改了不生效**

- 使用应用内 **重启系统界面**（需 Root）
- 或重启手机

**Q：非小米 / 非 HyperOS 能否使用？**

- **不能**。本应用依赖 HyperOS 焦点通知（`miui.focus.*`）与 SystemUI Hook，其他 ROM 不支持。

---

## 已知限制

- 仅适用于小米 HyperOS 焦点通知，其他 ROM 不可用
- 系统大版本升级可能导致 Hook 类名变化，需适配
- 焦点 updatable 会话有约 9s 系统超时，AOD 靠周期性 notify 续期
- 歌词准确度取决于 API 搜索与 LRC 质量

---

## 致谢

感谢下列项目提供框架、依赖与实现参考。

### 焦点通知

- **[HyperCeiler](https://github.com/ReChronoRain/HyperCeiler)** — 焦点歌词、`MusicBaseHook` / `FocusNotifLyric` 思路；渠道 ID、插件 ClassLoader bypass、防闪烁等见 `HyperFocusLyricStyle`、`SystemUIHyperFocusHook`、`SystemUIPluginHook`、`FocusAntiFlickerHook`
- **[FocusNotifLyric](https://github.com/ghhccghk/FocusNotifLyric)**（[wuyou-123](https://github.com/wuyou-123)）— 焦点歌词上游原型，已并入 HyperCeiler
- **[HyperFocusApi](https://github.com/ghhccghk/HyperFocusApi)** — Gradle 依赖；Demo：[HyperFocusNotifDemo](https://github.com/ghhccghk/HyperFocusNotifDemo)

### 框架与库

- **[LSPosed](https://github.com/LSPosed/LSPosed)** · **[XposedBridge](https://github.com/rovo89/XposedBridge)** · [AndroidX](https://github.com/androidx/androidx) · [OkHttp](https://github.com/square/okhttp) · [Kotlin](https://github.com/JetBrains/kotlin)

### 同生态

- [Lyric-Getter](https://github.com/xiaowine/Lyric-Getter) / [Lyric-Getter-Api](https://github.com/xiaowine/Lyric-Getter-Api) — FocusNotifLyric 常配合的歌词方案；本仓库歌词走网易云/QQ Provider
- [HookTool](https://github.com/HChenX/HookTool) · [Cemiuiler](https://github.com/ReChronoRain/Cemiuiler)

歌词 Web API 版权归网易云、QQ 音乐各自平台所有。

---

## 许可证

MIT License

## 📷 效果图

### 主设置界面

<img width="600" height="1335" alt="Screenshot_2026-06-28-14-48-57-708_com leowalk LyricFocus" src="https://github.com/user-attachments/assets/c0bfa278-05a8-4c40-9df2-6e976fba5ac7" />

### 锁屏歌词（桌面状态）

<img width="600" height="1335" alt="Screenshot_2026-06-28-14-49-07-693_com miui home" src="https://github.com/user-attachments/assets/cdb04b77-c0f3-407d-8a69-b15e32045b8f" />

### 锁屏歌词（AOD 息屏）

<img width="600" height="1335" alt="Screenshot_2026-06-28-14-49-32-311_lockscreen" src="https://github.com/user-attachments/assets/cb6881be-789d-48a6-acf7-60ad6e5ee669" />

---
