## v1.8.2

### 新功能：样式预览

样式设置页新增固定在标题栏下方的实时预览区：
- 默认展开，点击「样式预览」标题栏可折叠
- 滚动设置时预览始终可见
- 锁屏样式预览：隐藏歌名，深色/黑色/白色背景，双行/多行歌词+翻译
- 万象息屏 AOD 预览：纯黑背景，AOD 文本+标题图标+宽度模拟
- 所有样式改动即时反映到预览区
- 播放歌曲时预览同步显示当前歌词和翻译（多行模式亦然）

### 颜色取色统一

预览区颜色逻辑与通知实际颜色完全一致：
- Monet 动态取色：复用 `AlbumColorExtractor.ensureContrast` + `blendSecondary`
- 通知文字取色：复用 `AlbumColorExtractor.resolveTextColors`
- 背景色跟随 Mon et / 手动设置同步变化

### AOD 标题图标预览

- 图标根据当前播放的音乐软件自动切换（网易云/QQ/酷狗/Spotify 等）
- 图标大小跟随标题图标尺寸 slider 实时缩放
- 图标开关即时显示/隐藏

### 性能优化

- Handler 轮询间隔从 100ms 降至 250ms，主线程压力减半
- `updateForegroundNotification` 增加状态去重，无变化时不再调用 `notify()`
- `LAYOUT_REFLOW_DEBOUNCE` 从 5000ms 降到 2000ms，通知栏滑动更流畅

### 置顶策略重构

- **通知中心（解锁状态）**：仅靠 `PRIORITY_MAX + sortKey=0` 保持最高优先级，不做额外 View 操作
- **AOD/锁屏**：进入 AOD 时 cancel + repost 焦点通知置顶
- **切歌**：切换歌曲时 cancel + repost 置顶
- 移除全部 FocusPinAboveHook ViewGroup/List 重型操作钩子（-1063 行），彻底消除通知栏操作卡顿

### 版本号

- `1.8.2` (versionCode 22)
