## v1.8.7

### 修复
- **尝试修复某些 HyperOS 3.0 版本焦点通知不显示**：`getModuleContext()` 在 Android 14+ 上，App 安装/更新后 data 目录未创建时 `createPackageContext` 会抛异常，现在加入 `mkdirs` 重试兜底
- **`SystemUIPluginHook` 插件类名适配**：放宽匹配 `FocusNotification` 以兼容 HyperOS 3.0 欧版 ROM 新接口 `FocusNotificationContent`
- **补齐 `param_v2` 协议**：新增 `ensureParamV2()` 确保 `miui.focus.param` 在所有情况下都包含 `param_v2`

### 新增
- **AI 功能升级**（替代旧 "AI 翻译"）：翻译与润色两个独立功能
  - **翻译**：独立开关 + 强制翻译全部行；仅影响 `LyricLine.translation`
  - **润色**：独立开关；统一标点、修正错别字、清理元信息；不改变原文，结果存入 `LyricLine.polished`
  - **文件缓存**：翻译/润色结果持久化到 `cacheDir/AiLyricCache/`，含启用开关，支持查看缓存大小与清除
  - AI 功能不再作为独立歌词源，改为增强功能卡片
- **多行预览动画**：展开/收起 300ms 高度动画；标题栏拖拽跟随手指；回顶自动展开

### 优化
- 关闭多行模式时立即展开预览；仅下滑触发收起，回滚回顶自动展开
- AI 翻译关闭后 "强制翻译全部行" 置灰

---

**版本号**：`1.8.7` (versionCode 27)
