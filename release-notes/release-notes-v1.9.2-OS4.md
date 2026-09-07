# V1.9.2(OS4)

> ## ⚠️ 重要提醒
> **本包为 HyperOS4 / Android 17 专用（验证环境：Xiaomi HyperOS 4.0.0.18）。**
> **HyperOS3 用户请使用 OS3 专用包，请勿安装本版！**

## 新增
- **外部歌词推送协议**：开启外部渲染后本 App 仅搜词/对齐/推送；第三方用 Manifest `EXTERNAL_LYRIC` meta-data 声明即可接入，无需改 LyricFocus（见 README / `docs/external-lyric-protocol.md`）
- QQ 音乐源支持原文 + 官方翻译（明文 LRC 合并；QRC 无译时自动补翻译）
- 自动歌词源按播放器包名匹配：网易云优先网易；QQ 音乐 / 小米音乐同源优先 QQ；其余先 QQ 再网易

## 优化
- 外部渲染文案改为「纯推送」，兼容 aodchange、musiclockscreen 及声明协议的第三方模块
- 多端推送按 endpoint 独立去重，避免一端消费全量后另一端收不到 `ctx.lines`
- 多行模式字号下限调整为 15sp（不低于未播行）

## 版本号
- `1.9.2(OS4)`（versionCode 32）
