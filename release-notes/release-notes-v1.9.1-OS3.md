# V1.9.1(OS3)

> ## ⚠️ 重要提醒
> **本包为 HyperOS 3.x 专用。**
> **HyperOS 4 用户请使用 OS4 专用包，请勿安装本版！**

## 修复
- **OS3 焦点通知退化为普通通知**：SystemUI 冷启动读开关失败时不再误判为关闭并跳过 hook，恢复 `canShowFocus` 白名单绕过（对齐 1.8.9）
- aodchange 周期同步不再把 SystemUI 侧样式覆盖回默认

## 保留（来自 1.9.0）
- aodchange 外部渲染、焦点通知总开关、更新三渠道、防闪烁、AOD 去重

## 版本号
- `1.9.1(OS3)`（versionCode 33）
