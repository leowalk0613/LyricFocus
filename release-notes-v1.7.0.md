## v1.7.0

### 新功能

- **多行歌词当前行高亮**：多行歌词模式下当前播放行字号增大 20% 并使用专辑主打色高亮；非当前播放行统一用默认白/黑色（不再受专辑取色干扰）
- **AOD 实时刷新重构**：从原来的「每次换行 cancel+notify」改为仅首次息屏绑定时 cancel+notify，后续换行全走 `notify()` 原地更新 + Hook 强制 AOD 路由；锁屏不再有焦点通知闪烁

### 优化

- **移除防闪烁 Hook**：`FocusAntiFlickerHook` 已删除——新的原地更新方案不再触发焦点通知入场动画，无需手动压制
- **通知更新模式**：锁屏 / AOD 歌词换行不再重建通知会话，仅更新通知内 RemoteViews 内容

### 说明

- AOD 实时刷新依赖 Hook `AodFocusControllerV2$3.onAdd()` 强制 `enableAlert=true`，使每次歌词更新都能通过 DozeService 送达息屏进程
- 多行歌词模式下当前行高亮颜色首选专辑主打色，降级为通知文字取色，再降级为默认主题色
- 现有样式设置项无需调整，多行高亮自动生效

### 版本号

- `1.7.0`（versionCode 19）

## 安装说明

- 下载 `LyricFocus.v1.7.0.apk` 安装
- 若曾安装 Debug 版，请先卸载再安装
- 需要 LSPosed，作用域：`com.android.systemui`、`com.miui.aod`
- 安装后请在 LSPosed 中重新勾选上述作用域并**重启 SystemUI**
