# AGENTS.md

## 发布流程（更新上传）

当用户说"更新上传"、"发布新版本"、"上传GitHub"或类似指令时，自动执行以下步骤：

1. **构建 Release APK**：`./gradlew :focus:assembleRelease`
2. **复制 APK** 到 `D:\Download\LyricFocus.vX.X.X-release.apk`
3. **git add -A && git commit** 提交信息格式：`vX.X.X: 简短描述`
4. **git tag -f vX.X.X** 打标签（⚠ 用 `-f` 覆盖已存在的本地标签）
5. **git push origin main --tags**
6. **gh release create vX.X.X**：
   - `--title "VX.X.X"`（大写 V + 版本号，不附加描述）
   - `--notes-file release-notes/release-notes-vX.X.X.md`
   - 附加 release APK 文件
7. 返回 Release URL
8. 等待用户确认后，再触发 Gitee 同步。

### 用户偏好
- Release 标题只用 `V1.8.8` 格式，不加描述文字
- 发布前必须等用户测试通过并明确允许
- **必须**创建应用内更新日志 `focus/src/main/assets/release_notes_x_x_x.md`（点号分隔版本号）
- 每次版本必须更新 release-notes 目录下的 `release-notes-vX.X.X.md`

### 发布前检查清单 ⚠
开始推送前，确认以下三项全部完成：
1. `focus/build.gradle`：`versionCode` 和 `versionName` 已更新
2. `focus/src/main/assets/release_notes_x_x_x.md` 已创建并写入内容
3. `release-notes/release-notes-vX.X.X.md` 已创建并写入内容



### 注意事项
- release APK 路径：`focus\build\outputs\apk\release\LyricFocus.vX.X.X.apk`
- debug APK 路径：`focus\build\outputs\apk\debug\LyricFocus.vX.X.X.apk`
- Release 编译含 R8 混淆 + 资源收缩，正常约 2~3 分钟，APK 约 6MB
- 推送时 `--tags` 可能因旧标签冲突报错，拒绝的 tag 用 `git push origin :refs/tags/xxx` 删除远端后重新 `git push origin vX.X.X`
- Gitee 同步由 GitHub Actions 自动完成（需已配置 `GITEE_TOKEN` secret）
