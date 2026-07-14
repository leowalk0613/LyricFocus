## v1.6.0

### 新功能

- **歌词焦点通知永久置顶**：新增 `FocusPinAboveHook`，在焦点通知数据层与视图层将歌词卡片置顶，覆盖 HyperIsland 转换通知、倒计时焦点通知等场景；`pinAboveMedia` 默认始终开启
- **本地 LRC 歌词源**：新增「本地 LRC 文件」模式；应用内通过系统文件夹选择器（SAF）授权读取 `.lrc`；支持「歌名 - 歌手」/「歌手 - 歌名」文件名智能匹配；支持三语 LRC（同时间戳或续行的原文 / 翻译 / 读音合并为一条，第二行显示翻译与读音）
- **AI 翻译歌词源**：新增「AI 翻译（在线 + 翻译）」模式，兼容 OpenAI Chat Completions API；先拉取在线歌词，再为无译文歌曲生成翻译（需配置 API Key）

### 修复

- **QQ 音乐歌词源失效**：搜索迁移至 `u.y.qq.com/cgi-bin/musicu.fcg`（旧 `client_search_cp` 已失效）；歌词接口改用 `i.y.qq.com` 明文拉取，保留 base64 回退；支持双搜索 payload 回退
- **网易云极短标题误匹配**：修复 `p.h.` 等带点短标题误匹配为同艺人其他歌曲（如「針」）；增加艺术家优先搜索词、标题零分排除、已知单曲 ID 兜底（1449599572）
- **歌词通知被其他焦点通知压在下方**：修复仅调整通知栈子 View 无法影响锁屏焦点卡片排序的问题；针对 `FocusedNotifPromptController` 及插件进程补 hook

### 优化

- **焦点置顶 hook 稳定性**：缩小 `FocusedNotifPromptController` hook 范围，防重入，安全处理不可变列表，移除 hook 内 refresh 避免异常刷屏
- **三语 LRC 解析**：`LrcParser` 合并同时间戳多行，支持无时间戳续行与 `|` / `/` 行内分隔；按假名 / 汉字 / 罗马音自动分类
- **内置更新日志**：应用内点击版本按钮可查看当前版本说明（`assets/release_notes_1_6_0.md`），无需等待远程 Release
- **万象息屏标题图标文案**：「显示标题图标」说明去掉「或手动选择」，改为「支持自动识别」
- **歌词搜索关键词**：`LyricSearchHelper` 增加艺术家优先、拆分组合艺人、尾点变体等搜索词策略

### 版本号

- `1.6.0`（versionCode 16）

## 安装说明

- 下载 `LyricFocus.v1.6.0.apk` 安装
- 若曾安装 Debug 版，请先卸载再安装
- 需要 LSPosed，作用域：`com.android.systemui`、`com.miui.aod`
- 安装后请在 LSPosed 中重新勾选上述作用域并**重启 SystemUI**

## 本地 LRC 快速上手

1. 将 `.lrc` 文件放入手机任意文件夹（如「下载」）
2. 歌词源 → **本地 LRC 文件** → **配置** → **选择文件夹**
3. 播放对应歌曲即可
