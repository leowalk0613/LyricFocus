# 外部歌词推送协议（LyricFocus External Lyric）

开启「外部渲染」后，LyricFocus **只负责搜词、对齐与推送**，自身焦点通知 / AOD 歌词不再输出。第三方模块按本协议声明即可接收，无需改 LyricFocus 源码。

协议版本：`v = 1`（JSON 顶层字段）。

## 1. 声明接入

在接收方 `AndroidManifest.xml` 的 `<application>` 内添加：

```xml
<meta-data
    android:name="com.leowalk.LyricFocus.EXTERNAL_LYRIC"
    android:value="content://your.package.authority" />
```

- `android:name` 必须为 `com.leowalk.LyricFocus.EXTERNAL_LYRIC`
- `android:value` 为你的 ContentProvider 的 content URI（authority 根路径即可）
- LyricFocus 启动/设置变更时扫描已安装应用的 meta-data；Provider 未安装或 authority 无效则跳过

内置兼容（可不声明 meta-data）：

- `content://com.leowalk.aodchange.notifications`
- `content://com.leowalk.musiclockscreen.lyric`

若同时声明了与内置相同的 URI，只会推送一次。

## 2. ContentProvider 方法

| method | extras | 说明 |
|--------|--------|------|
| `putlyric` | `n`：JSON 字符串 | **轻量推送**（换行）。无全量 `ctx.lines` |
| `putlyricfd` | `fd`：`ParcelFileDescriptor` | **全量推送**（切歌 / 歌词加载）。读文件得到完整 JSON |
| `settings`（可选） | 返回 Bundle，`n` 为 settings JSON | 可读 `lyric_advance_ms`（毫秒），用于同步提前量 |

示例（Kotlin）：

```kotlin
override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    return when (method) {
        "putlyric" -> {
            val json = extras?.getString("n") ?: return null
            onLyricUpdate(JSONObject(json))
            null
        }
        "putlyricfd" -> {
            val pfd = extras?.getParcelable<ParcelFileDescriptor>("fd") ?: return null
            pfd.use { fd ->
                val text = FileInputStream(fd.fileDescriptor).bufferedReader().readText()
                onLyricUpdate(JSONObject(text))
            }
            null
        }
        "settings" -> Bundle().apply {
            putString("n", JSONObject().put("lyric_advance_ms", 200).toString())
        }
        else -> null
    }
}
```

Provider 需在 Manifest 中导出（或按你的安全策略配置权限），保证 LyricFocus 进程可 `call`。

## 3. JSON 字段

### 顶层（轻量 / 全量共用）

| 字段 | 类型 | 说明 |
|------|------|------|
| `v` | int | 协议版本，当前为 `1` |
| `l` | string | 当前行原文 |
| `s` | string | 副行（下一句或翻译，取决于推送侧组装） |
| `t` | long | 当前行时间戳（ms） |
| `title` | string | 歌名 |
| `artist` | string | 歌手 |
| `pkg` | string | 可选，播放器包名 |
| `playing` | boolean | 是否在播放 |
| `ctx` | object | **仅全量**时出现，见下 |

切歌清空时：`l`/`s` 为空字符串，`t = 0`，无 `ctx`。

### `ctx`（全量）

```json
{
  "idx": 12,
  "lines": [
    { "t": "原文", "tm": 32000, "r": "翻译", "isCur": false }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `idx` | 当前行下标；前奏未到第一句时为 `-1` |
| `lines[].t` | 原文 |
| `lines[].tm` | 行时间戳 ms |
| `lines[].r` | 翻译（可为空） |
| `lines[].isCur` | 是否当前行 |

未知字段请忽略，便于后续扩展。

## 4. 推送时机

- **全量 `putlyricfd`**：歌词内容变化（切歌加载、换源等）时，带完整 `ctx.lines`
- **轻量 `putlyric`**：换行时，只带 `l/s/t/title/artist/...`，接收方用缓存的 `lines` + 本地进度渲染多行
- **清空**：切歌瞬间先 `putlyric` 空数据，避免残留

## 5. 与 LyricFocus 的关系

| LyricFocus 侧 | 行为 |
|---------------|------|
| 外部渲染 **关** | 走自有焦点 / AOD 路径，不向外部推送 |
| 外部渲染 **开** | 关闭焦点歌词输出；向所有已发现 endpoint 推送 |

实现参考：`focus/.../service/ExternalLyricProtocol.kt`。
