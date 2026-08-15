# V1.9.1 更新

> ## ⚠️ 重要提醒
> **本版本仅支持 HyperOS4 / Android 17（验证环境：Xiaomi HyperOS 4.0.0.18）。**
> **HyperOS3 及以下用户请勿更新！** 本版针对 HyperOS4 做了大量适配，未在 HyperOS3 上验证，可能无法正常显示焦点歌词。

## ⚠️ HyperOS4 / Android 17 适配（重点）

本版本针对 **HyperOS4（Android 17）** 做了系统性适配，此前在 HyperOS3 上正常的功能在 HyperOS4 上会失效或异常，现已全部修复：

- **焦点通知认证**：HyperOS4 新增 `SignatureChecker.checkSignatures` 签名校验，未 Hook 时焦点通知认证失败（onAuthFailed）被系统移除。新增 `XmsfAuthHook` Hook 该校验强制返回成功，认证通过（onAuthSuccess），系统正确识别 `focusType=CUSTOM|PARAMS`，焦点歌词正常显示
- **防闪烁 Hook 重写**：HyperOS4 移除了 `FocusedNotifPromptView / FocusedNotifPromptController`，原防闪烁方案失效。改为 Hook `StatusBarFocusNotifUtils.needAnim` 返回 false，压制焦点通知更新动画
- **岛抑制 Hook 重写**：HyperOS4 岛显示判定集中在 `DynamicIslandController`，通过 `hasCustomFocusView(StatusBarNotification)` 检查 `miui.focus.rv` 决定是否显示岛。改为 Hook 该方法返回 false，抑制 LyricFocus 焦点通知的岛显示
- **AOD 状态检测**：HyperOS4 下 `PowerManager.isInteractive()` 在 AOD 模式返回 true，导致「仅 AOD 显示多行」误判。改为 Hook `MiuiDozeService.onDreamingStarted()/onDreamingStopped()` 并读取 `AodFocusControllerV2.mAodStart` 字段，准确判断 AOD 状态
- **RemoteViews 新 API**：Android 17 移除 `setViewLayoutParams` / `MATCH_PARENT`，多行歌词区域高度改用 `setViewLayoutHeight(viewId, height, COMPLEX_UNIT_DIP)` 新 API

## 新增
- 焦点通知背景支持专辑取色（独立于 Monet / 文字取色），支持透明度调节
- 通知高度滑块：调节多行歌词区域高度（200-450dp，步长 10dp，默认 400dp）

## 修复
- 歌词样式持续生效：修复样式设置仅对第一行生效、30 秒后样式重置的问题（内容变更检测 + 强制刷新 + 样式广播先于歌词推送）
- 焦点通知卡片圆角裁剪：背景色不再溢出圆角（锁屏 / AOD / 岛 / 多行共 5 个布局统一 24dp 圆角 + clipToOutline）
- 样式设置稳定性：修复 Fragment 生命周期崩溃、广播无限循环、Monet 背景取色开关失效
- AOD 多行歌词：修复仅显示一行的问题
- 多行歌词无翻译时回退纯原文显示
- aodchange 模式：通过系统属性同步配置，SystemUI / AOD 进程正确跳过 hook 注入，避免模块冲突

## 优化
- 删除样式预览窗口功能
- AOD 多行歌词布局：固定高度区域（AOD 200-450dp / 锁屏 180-360dp 可调）、顶格显示、行距调整（组内 4dp / 组间 28dp）、固定显示 10 行（5 组原文+翻译）
- 多行模式性能：缓存歌词 JSON 解析与多行窗口构建，消除掉帧
- 主界面开关切换不再自动重启 SystemUI（hook 运行时门卫兜底）
- 关于页改版：项目信息与联系作者超链接化（GitHub / Gitee / 123网盘），按钮排成一排
