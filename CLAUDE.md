# AGENTS.md

## 发布流程（更新上传）

当用户说"更新上传"、"发布新版本"、"上传GitHub"或类似指令时，自动执行以下步骤：

1. **构建 Release APK**：`./gradlew :focus:assembleRelease`
2. **复制 APK** 到 `D:\Download\LyricFocus.vX.X.X-release.apk`
3. **git add -A && git commit** 提交信息格式：`vX.X.X: 简短描述`
4. **git tag -f vX.X.X** 打标签（⚠ 用 `-f` 覆盖已存在的本地标签）
5. **git push origin main --tags**
6. **gh release create vX.X.X**：
   - `--title "vX.X.X - 简短描述"`
   - `--notes-file release-notes/release-notes-vX.X.X.md`
   - 附加 release APK 文件
7. 返回 Release URL

### 注意事项
- release APK 路径：`focus\build\outputs\apk\release\LyricFocus.vX.X.X.apk`
- debug APK 路径：`focus\build\outputs\apk\debug\LyricFocus.vX.X.X.apk`
- Release 编译耗时较长（含 R8 混淆），正常约 2~3 分钟
- 推送时 `--tags` 可能因旧标签冲突报错，忽略警告，只关注 main 和新 tag 是否推送成功
