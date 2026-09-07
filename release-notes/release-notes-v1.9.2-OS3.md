# V1.9.2(OS3)

> ## ⚠️ 重要提醒
> **本包为 HyperOS 3.x 专用（`1.9.2(OS3)`）。**
> **HyperOS 4 用户请安装 [V1.9.2(OS4)](https://github.com/leowalk0613/LyricFocus/releases/tag/v1.9.2-OS4)，请勿使用本版！**
> **请勿将本包安装到 HyperOS 4；亦勿将 OS4 包安装到 HyperOS 3。**

HyperOS3 专用包：同步主线 1.9.1 / 1.9.2 功能，保留 OS3 焦点 API 与焦点通知总开关修复。

## 新增
- 外部歌词推送协议（`EXTERNAL_LYRIC`）；配套 [Aodchange](https://github.com/leowalk0613/Aodchange) / [HyperLockMusic](https://github.com/leowalk0613/HyperLockMusic)
- QQ 音乐原文 + 官方翻译；自动源按播放器包名匹配
- 专辑取色背景、多行高度滑块、多行 24 槽 + 当前行强调
- 可选 xmsf 焦点认证 Hook

## 修复
- OS3：remote prefs 缺键不为关闭；ContentProvider 刷新焦点开关；aodchange 同步早退
- 息屏 cancel+repost 置顶；样式持续生效等 1.9.1 修复

## 说明
- 不含 OS4 的 `canShowFocusState` / `SystemUIApplicationImpl` / SignatureChecker 等适配
- HyperOS4 请安装 OS4 专用包
