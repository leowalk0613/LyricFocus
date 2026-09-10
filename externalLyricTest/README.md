# 外部歌词测试 App（ExternalLyricTest）

第三方接入 **参考实现**：按协议正确处理 `loading` / `seq`，避免切歌残留。

## 做什么

- Manifest 声明 `com.leowalk.LyricFocus.EXTERNAL_LYRIC` → `content://com.leowalk.ExternalLyricTest.lyric`
- 导出 Provider：`putlyric` / `putlyricfd` / `settings`
- [`LyricReceiveEngine`](src/main/java/com/leowalk/ExternalLyricTest/LyricReceiveEngine.kt)：接收状态机（可单测）
  - 过期 `seq` → DROP
  - `loading=true` / 异曲 / 空时间轴 → 立刻清空歌词行
  - 轻量换行保留上次全量 `ctx` 行数元数据

## 构建安装

```bash
./gradlew :externalLyricTest:installDebug
./gradlew :externalLyricTest:test
```

## 验证步骤

1. 安装本 App 与 LyricFocus（外部渲染开启）
2. 播放能出词的歌 → 应出现 `putlyricfd`，随后换行 `putlyric`
3. **快速切歌** → 主行应先显示「loading 清空 · 等待新词」；日志可出现 `DROP stale seq=...`
4. 短按「清除日志」只清文本；**长按**才重置会话 seq

logcat 过滤：`ExternalLyricTest`、`ExternalLyricProtocol`、`LyricService`
