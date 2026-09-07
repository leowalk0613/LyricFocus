# V1.9.2(OS3) 更新

> ## ⚠️ 重要提醒
> **本包为 HyperOS 3.x 专用（versionName `1.9.2(OS3)`）。**
> **HyperOS 4 用户请使用 [OS4 专用包](https://github.com/leowalk0613/LyricFocus/releases/tag/v1.9.2-OS4)，请勿安装本版！**
> **请勿交叉安装渠道包：OS3 ↔ OS4 焦点 API 不同，错装会导致焦点通知异常或退化为普通通知。**

## 新增
- 外部歌词推送协议：开启外部渲染后只推送歌词；第三方声明 `EXTERNAL_LYRIC` 即可接入（兼容 aodchange / musiclockscreen）
- 自研配套：[Aodchange](https://github.com/leowalk0613/Aodchange)（OS3 万象息屏）、[HyperLockMusic](https://github.com/leowalk0613/HyperLockMusic)（OS4 音乐锁屏）
- QQ 音乐源支持原文 + 官方翻译
- 自动源按播放器包名匹配（QQ/小米同源，网易优先网易）
- 焦点通知背景支持专辑取色（独立于 Monet / 文字取色），支持透明度调节
- 通知高度滑块：调节多行歌词区域高度（200–450dp）
- 可选 Hook `com.xiaomi.xmsf` 焦点认证（`XmsfAuthHook`，包名级，失败自动跳过）

## 修复（含 OS3 焦点通知）
- **OS3 焦点通知退化为普通通知**：`FocusMainHook` 缺键 / 查询失败（null）≠ 关闭；仅明确关闭时跳过 hook，保留 `canShowFocus` / `canCustomFocus`（boolean，`miui.systemui`）白名单绕过
- SystemUI 侧 `refreshSettings` 仅经 ContentProvider 刷新焦点总开关，不 `reloadFromDisk`；纯 aodchange 同步广播早退，避免样式被刷回默认
- 息屏 cancel+repost / 歌词置顶于媒体通知（逻辑移植，仍使用 OS3 `SystemUIApplication` 与 boolean focus API）
- 歌词样式持续生效、AOD 多行仅一行、无翻译回退纯原文等 1.9.1 修复一并带入

## 优化
- 多行歌词槽位扩展至 24，当前行强调；当前行翻译淡化色
- 多行字号下限 15sp；欢迎页改版；关于页项目/联系超链接化
- 主界面「外部渲染（纯推送）」文案；更新资源名规范化支持 `1.9.2(OS3)` → `release_notes_1_9_2_OS3.md`
- 删除样式预览窗口；样式设置页清理

## 刻意未移植（OS4 专用）
- `canShowFocusState` / `SystemUIApplicationImpl` / `com.miui.systemui` 包名重命名
- HyperOS4 `SignatureChecker` 强制绕过、防闪烁/岛抑制的 OS4 重写（OS3 仍用 `FocusedNotifPrompt*` / `miui.systemui...DynamicIslandController`）

## 版本号
- `1.9.2(OS3)`（versionCode 34）
