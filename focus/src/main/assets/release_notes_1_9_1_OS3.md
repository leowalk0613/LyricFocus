# V1.9.1(OS3)

> ## ⚠️ 重要提醒
> **本包为 HyperOS 3.x 专用。**
> **HyperOS 4 用户请使用 OS4 专用包，请勿安装本版！**

## 修复
- **OS3 焦点通知退化为普通通知**：`FocusMainHook` 在 SystemUI 冷启动读不到开关时，不再把缺键的 remote prefs 默认成 `false` 而跳过全部 hook；查询失败按「焦点开启」处理，保留 `canShowFocus` / `canCustomFocus` 白名单绕过（对齐 1.8.9 行为）
- SystemUI 侧 `refreshSettings` 不再 `reloadFromDisk`；aodchange 30 秒同步广播不再误触发样式重置

## 保留（来自 1.9.0）
- aodchange 外部渲染模式与焦点通知总开关
- 更新对话框三渠道、焦点防闪烁、AOD 去重等

## 版本号
- `1.9.1(OS3)`（versionCode 33）
