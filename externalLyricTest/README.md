# 外部歌词测试 App（ExternalLyricTest）

用于验证 LyricFocus「外部渲染（纯推送）」是否能发现并推送到第三方 ContentProvider。

## 做什么

- Manifest 声明 `com.leowalk.LyricFocus.EXTERNAL_LYRIC` → `content://com.leowalk.ExternalLyricTest.lyric`
- 导出 Provider，实现 `putlyric` / `putlyricfd` / `settings`
- 界面实时显示方法名、歌名、当前行、ctx 行数，并记推送日志

## 构建安装

```bash
./gradlew :externalLyricTest:installDebug
```

或 Android Studio 选 `externalLyricTest` 运行。

## 验证步骤

1. 安装本 App 与 LyricFocus
2. LyricFocus 打开 **「外部渲染（纯推送）」**
3. 确保 LyricFocus 有通知访问权限，播放一首能出词的歌
4. 打开本 App，期望：
   - 首次加载出现 **`putlyricfd`**（带 `ctx.lines`）
   - 换行出现 **`putlyric`**（无 ctx）
   - 切歌瞬间可能出现空 `l/s` 的清空推送
5. logcat 过滤：`ExternalLyricTest`、`ExternalLyricProtocol`

若一直无数据：改外部渲染开关或重启 LyricFocus 以触发 `invalidateDiscovery`；确认本 App 已安装且 Provider authority 可解析。
