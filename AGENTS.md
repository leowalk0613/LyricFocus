# AGENTS.md

## 发布流程（更新上传）

当用户说"更新上传"、"发布新版本"、"上传GitHub"或类似指令时，自动执行以下步骤：

1. **双渠道升版**：OS4（`D:\work\LyricFocus` / `main`）与 OS3（`D:\work\LyricFocus-OS3` / `hyperos3`）同步升到同一版号
   - `versionName` **仍区分渠道**：`X.Y.Z(OS4)` / `X.Y.Z(OS3)`
   - `versionCode` 各自递增（可不同数值，但须比上一版大）
2. **构建两侧 Release APK**：分别在两渠道执行 `./gradlew :focus:assembleRelease`
3. **复制 APK** 到 `D:\Download\`：
   - `LyricFocus.vX.Y.Z(OS4)-release.apk`
   - `LyricFocus.vX.Y.Z(OS3)-release.apk`
4. **分别提交并推送**：
   - OS4：`main`，提交信息 `vX.Y.Z(OS4): 简短描述`
   - OS3：`hyperos3`，提交信息 `vX.Y.Z(OS3): 简短描述`
5. **统一打标签**（只打一个）：`git tag -f vX.Y.Z`（在 `main` 上；⚠ 用 `-f` 覆盖已存在的本地标签）
6. **推送分支 + 仅推送本次 tag**（⚠ **禁止** `git push --tags`，否则会把本地残留的旧标签重新推上远端）：
   - `git push origin main`
   - `git push origin hyperos3`
   - `git push origin vX.Y.Z`（若远端已有同名 tag 需先删：`git push origin :refs/tags/vX.Y.Z` 再推）
7. **`gh release create vX.Y.Z`**（**OS3/OS4 共用同一个 Release，不再分 `vX.Y.Z-OS3` / `vX.Y.Z-OS4`**）：
   - `--title "VX.Y.Z"`（大写 V + 版本号，不附加渠道或描述）
   - `--notes-file release-notes/release-notes-vX.Y.Z.md`（一份说明，内含两渠道注意点）
   - **同时附加** OS4 与 OS3 两个 release APK
8. 返回 Release URL
9. 等待用户确认后，再触发 Gitee 同步。

### 用户偏好
- Release 标题只用 `V1.9.3` 格式，不加描述文字、不加 `(OS3)`/`(OS4)`
- **自 1.9.3 起**：GitHub / Gitee 上 **一个 tag + 一个 Release 挂两个 APK**；`versionName` 仍为 `1.9.3(OS3)` / `1.9.3(OS4)` 以便安装与更新日志区分
- 发布前必须等用户测试通过并明确允许
- **必须**为各渠道创建应用内更新日志：
  - OS4：`focus/src/main/assets/release_notes_x_y_z_OS4.md`
  - OS3：`focus/src/main/assets/release_notes_x_y_z_OS3.md`
  -（`UpdateChecker.normalizeVersionForAsset`：`1.9.3(OS4)` → `1_9_3_OS4`）
- 每次版本必须更新 **一份** 共用说明：`release-notes/release-notes-vX.Y.Z.md`

### 发布前检查清单 ⚠
开始推送前，确认以下全部完成：
1. OS4 / OS3 的 `focus/build.gradle`：`versionCode`、`versionName`（含渠道后缀）已更新
2. OS4 / OS3 的 `strings.xml` 中 `app_version` 已更新
3. `release_notes_x_y_z_OS4.md` 与 `release_notes_x_y_z_OS3.md` 已创建
4. `release-notes/release-notes-vX.Y.Z.md` 已创建（一份，写清两渠道）
5. 两侧 Release APK 均已构建成功

### 注意事项
- release APK 路径：`focus\build\outputs\apk\release\LyricFocus.vX.Y.Z(OSx).apk`
- debug APK 路径：`focus\build\outputs\apk\debug\LyricFocus.vX.Y.Z(OSx).apk`
- Release 编译含 R8 混淆 + 资源收缩，正常约 2~3 分钟，APK 约 6MB
- **禁止**使用 `git push --tags` / `git push origin --tags`：会恢复用户已在远端删除的历史标签
- 仅推送当前版本标签：`git push origin vX.Y.Z`；若冲突则先 `git push origin :refs/tags/vX.Y.Z` 再推
- Gitee 同步由 GitHub Actions 自动完成（需已配置 `GITEE_TOKEN` secret）

## OS3 / OS4 双渠道同步 ⚠

- **OS4**：`D:\work\LyricFocus`（`main`）
- **OS3**：`D:\work\LyricFocus-OS3`（`hyperos3` worktree）

### 禁止整文件覆盖的渠道差异代码
同步功能时 **禁止** 把下列文件从 OS4 整份 `Copy-Item` 到 OS3（或反过来），否则会搞挂焦点通知：

| 文件 | 原因 |
|------|------|
| `SystemUIHyperFocusHook.kt` | HyperOS3 / HyperOS4 SystemUI 类名与焦点权限 API 不同 |
| 其它带明显 `HyperOS3` / `HyperOS4` 注释的 hook | 渠道专用 |

**正确做法**：在目标渠道保留原文件，只手工/补丁合并与渠道无关的业务改动（如多行歌词逻辑）。

### SystemUIHyperFocusHook 关键差异（勿混用）

| 项 | OS3 | OS4 |
|----|-----|-----|
| Application 入口 | `com.android.systemui.SystemUIApplication` | `com.android.systemui.application.impl.SystemUIApplicationImpl` |
| 焦点权限绕过 | `miui.systemui.notification.NotificationSettingsManager` 的 `canShowFocus` / `canCustomFocus`（返回 **boolean**） | `com.miui.systemui.notification.NotificationSettingsManager` 的 `canShowFocusState` / `canShowFocusStateApp`（返回 **int**，放行值 `1`） |
| 签名检查 | 无 OS4 那套 `SignatureChecker` 强制 hook | 需 hook `SignatureChecker.checkSignatures` |
| `refreshSettings` | 强制 `pinAboveMedia = true` + `syncFocusPinState()` | 不在此处强行 pin |

### OS3 专用偏好
- `FocusPreferences.ensureFocusEnabledDefault()`：首次安装 prefs 无键时默认打开焦点通知；`LyricFocusApp` 会调用。OS4 无此逻辑。同步 `FocusPreferences.kt` 时若覆盖 OS3，**必须**把该方法补回。

### 可安全同步（渠道无关）的示例
歌词源 / ID 解析、设置页 UI、样式 snapshot 字段、多行窗口业务逻辑等——同步前仍应用 `diff` 确认无渠道专用改动。
