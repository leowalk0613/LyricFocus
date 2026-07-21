## v1.8.0

### ⚠️ 重要：LSPosed 框架要求

**此版本已完整迁移至 Modern Xposed API（LSPosed 2.0 / API 102），必须使用 LSPosed 2.0 或更高版本！**

- 旧版 LSPosed（API 82）不再兼容，请务必将 LSPosed 框架更新至 2.0 版本后再安装此版本
- 使用 `META-INF/xposed/module.prop` 声明 API 版本（minApiVersion=102, targetApiVersion=102）
- 入口继承 `XposedModule`，所有 Hook 使用 `module.hook(method).intercept { chain -> }` 拦截器链
- `XSharedPreferences` 替换为 `getRemotePreferences()`
- 移除所有 `de.robv.android.xposed` legacy 依赖

### 新功能

- **万象息屏仅显示歌词**：开启万象息屏模式后播放音乐时，AOD 自动隐藏其他焦点通知和普通通知，仅展示歌词通知（`customAodLayout + isPlaying + isAodActive`）
- **AOD 过渡动画保护**：Hook `MiuiFocusNotification2.transitionTo`，锁屏↔AOD 切换时保存并恢复歌词视图，避免闪现空白
- **Monet 图标适配**：应用图标背景随壁纸 Monet 动态取色（Android 13+）

### 优化

- **通知刷新统一**：万象息屏和锁屏 AOD 统一使用 updatable notify-only，不再 cancel+notify（移除动画）
- **窗口高度调整**：主页和样式设置底部 padding 从 24dp 增加到 80dp
- **歌词置顶增强**：Hook `FocusedNotifPromptView` 数据方法 + `ShadeListBuilder` 通知变更入口，播放时歌词通知确保置顶

### 移除

- **自定义字体功能**：因 RemoteViews 限制，暂不支持（后续版本可能重新添加）

### 版本号

- `1.8.0`（versionCode 20）

## 安装说明

- 下载 `LyricFocus.v1.8.0.apk` 安装
- **必须先更新 LSPosed 至 2.0（API 102），旧版 LSPosed 无法使用！**
- 若曾安装旧版本，请先卸载再安装
- 需要 LSPosed 2.0，作用域：`com.android.systemui`、`com.miui.aod`
- 安装后请在 LSPosed 中重新勾选上述作用域并**重启 SystemUI**
