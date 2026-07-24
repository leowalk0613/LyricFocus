## v1.8.4

### 锁屏切歌性能优化

- **消除掉帧**：锁屏切歌时不再执行 cancel+repost 通知循环，改为原地更新，杜绝闪烁和掉帧
- 仅在进入 AOD 息屏时保留 cancel+repost 以确保焦点通知置顶
- 添加 600ms 防抖避免切歌/屏状态变化时频繁重建通知

### 致谢页面更新

- 致谢内容与 README 对齐，补全所有项目链接（HyperCeiler、FocusNotifLyric、HyperFocusApi、LSPosed、XposedBridge、AndroidX、OkHttp、Kotlin、Lyric-Getter/Api、SuperLyricApi、Lyricon、LyricInfo、HookTool、Cemiuiler）
- 每个条目均为可点击的链接，跳转对应 GitHub 仓库

### 歌词源切换 UI 重设计

- 全新卡片式布局，每个源带图标 + 标题 + 描述 + 圆形选择指示器
- 选中卡片有主色调描边高亮
- 选中后自动展开详细信息，无需手动展开/收起

### 多行模式颜色修正

- 当前行的翻译行改用淡色显示，与其他翻译行视觉一致，不再使用强调色
- Monet 取色下当前行强调色通过 `ensureContrast` 对背景计算对比度，解决双白/双黑色调不可读问题

### LyricInfo / Lyricon / SuperLyric 切歌稳定性

- LyricInfo：增加歌名匹配校验，不匹配时 500ms 后重试；`onMetadataChanged` 切歌时立即清空旧歌词
- Lyricon / SuperLyric：Bridge 回调增加 `currentTitle` 校验，切歌后旧订阅的延迟回调不再覆盖新歌
- Bridge `stop()` 中提前置空 callback，避免异步回调竞态

### 样式细节优化

- 日语原文歌词在第二行时字号缩小 12%，视觉更协调
- 预览界面背景应用 16dp 圆角，与卡片风格统一

### 版本号

- `1.8.4` (versionCode 24)
