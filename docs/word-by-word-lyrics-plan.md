# 逐字歌词实现规划

## 概述

在现有行级歌词基础上，增加词级高亮显示。支持两种模式：
- **真逐字**：LRC 文件内含 `<MM:SS.CC>` 逐字时间标签时，精确高亮
- **伪逐字**：无词级源时，按行进度 + 字符数均分估算当前词位置

---

## 一、数据模型改动

### 1.1 新增 `WordTime` 数据类

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/lyric/LyricLine.kt`

```kotlin
data class LyricLine(
    val time: Long,
    val text: String,
    val translation: String? = null,
    val reading: String? = null,
    val words: List<WordTime>? = null  // 新增：null 表示无词级源，走伪逐字
) {
    companion object {
        val EMPTY = LyricLine(0, "")
    }

    fun secondaryText(): String? {
        return listOfNotNull(
            translation?.takeIf { it.isNotBlank() },
            reading?.takeIf { it.isNotBlank() }
        ).joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** 行内有词级定时则为 true */
    val hasWordTiming: Boolean get() = words != null && words!!.isNotEmpty()
}

/**
 * @param relativeMs 词相对于行首的偏移（毫秒），首个词始终为 0
 * @param text      词的文本内容
 */
data class WordTime(
    val relativeMs: Long,
    val text: String
)
```

### 1.2 `LyricInfo.toJson()` 序列化词信息

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/lyric/LyricInfo.kt`

在现有 `toJson()` 中，如果 `line.words` 不为空，追加一个 `words` JSONArray：

```json
{
  "time": 18210,
  "text": "Hello world this is a lyric",
  "words": [
    {"r": 0,    "t": "Hello"},
    {"r": 520,  "t": "world"},
    {"r": 980,  "t": "this"},
    {"r": 1300, "t": "is"},
    {"r": 1500, "t": "a"},
    {"r": 1700, "t": "lyric"}
  ]
}
```

其中 `r` = relativeMs，`t` = text。仅在 `line.hasWordTiming` 为 true 时才输出该数组。

### 1.3 SystemUI 侧 `LyricLineData` 同步更新

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/xposed/hook/systemui/SystemUIHyperFocusHook.kt`

内部 `LyricLineData`（第 127 行）增加 `words` 字段，并在 JSON 解析时反序列化。

```kotlin
private data class LyricLineData(
    val time: Long,
    val text: String,
    val translation: String? = null,
    val reading: String? = null,
    val words: List<WordTimeData>? = null
) {
    data class WordTimeData(
        val relativeMs: Long,
        val text: String
    )

    fun secondaryText(): String? { ... }  // 不变

    val hasWordTiming: Boolean get() = words != null && words!!.isNotEmpty()
}
```

---

## 二、LRC 解析器改动

### 2.1 解析逐字时间标签

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/lyric/LrcParser.kt`

现有第 48 行已定义正则，但 `stripTimeTags()`（第 50 行）直接将其删除。需要改为**先提取再剥离**。

```kotlin
/** 逐字/单词时间码：[word-level] 格式如 <00:16.440>、<00:16:44> */
private val WORD_TIME_PATTERN = Regex("<\\d{1,2}:\\d{2}[.:]\\d{2,3}>")

/** 从包含词级标签的行中提取 word timings */
private fun parseWordTimings(
    rawLine: String,
    lineTimeMs: Long
): List<WordTime>? {
    val matches = WORD_TIME_PATTERN.findAll(rawLine).toList()
    if (matches.isEmpty()) return null

    // 将行分割为「时间标签 + 文本片段」
    val parts = rawLine.split(WORD_TIME_PATTERN)
    val words = mutableListOf<WordTime>()

    // 跳过第一个空 split（要是有前导标签），收集时间标记间的文本
    var textIndex = 0
    for (match in matches) {
        // 标签后的文本片段
        textIndex++
        val textFragment = parts.getOrElse(textIndex) { "" }.trim()
        if (textFragment.isNotEmpty()) {
            val tagMs = parseWordTagToMs(match.value)
            val relativeMs = tagMs - lineTimeMs
            words.add(WordTime(relativeMs.coerceAtLeast(0), textFragment))
        }
    }
    return if (words.isNotEmpty()) words else null
}

/** 解析 <MM:SS.CC> 或 <MM:SS:CC> 为绝对毫秒 */
private fun parseWordTagToMs(tag: String): Long {
    val cleaned = tag.removeSurrounding("<", ">")
        .replace(":", ".")  // 统一 . 分隔
    val parts = cleaned.split(".")
    val minutes = parts.getOrElse(0) { "0" }.toLongOrNull() ?: 0
    val seconds = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0
    val centis  = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0
    return minutes * 60_000 + seconds * 1_000 + centis * 10
}
```

### 2.2 修改 RawEntry 及 buildLyricLines 流程

将 `RawEntry` 增加原始文本字段（带词标签），在构建 `LyricLine` 时传入解析出的 word timings。

```kotlin
private data class RawEntry(
    val time: Long,
    val text: String,
    val rawText: String  // 新增：保留含词标签的原始行文本
)
```

在 `buildLyricLines()` 中：
```kotlin
rawEntries.add(RawEntry(time, text, trimmedLine))  // 保留原始行
```

在 `mergeMultilingualGroups()` 中构造 `LyricLine` 时：
```kotlin
val wordTimings = parseWordTimings(entries[index].rawText, time)
result.add(
    LyricLine(
        time = time,
        text = parts.original,
        translation = parts.translation,
        reading = parts.reading,
        words = wordTimings
    )
)
```

### 2.3 注意：翻译/注音行不做逐字

`parseWordTimings` 只对**原始歌词行**解析。三语合并时，`parts.original` 对应的 `rawEntry.rawText` 才可能携带词标签；翻译和注音行忽略 word timings。

---

## 三、伪逐字模式（无词级源时的估算）

### 3.1 核心算法

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/lyric/LyricInfo.kt` 或新工具函数

当 `line.words` 为 null 时，根据行内文本和行进度估算当前词：

```kotlin
/**
 * 伪逐字：按字符数均分行时长，估算当前词索引。
 *
 * @param line      当前歌词行
 * @param progress  行内进度 0f..1f（使用已有的 getLineProgress()）
 * @return 当前词索引，-1 表示无法确定
 */
fun estimateCurrentWordIndex(line: LyricLine, progress: Float): Int {
    if (line.text.isBlank()) return -1

    // 按空白字符分词（中文字符每个单独算一个词，英文按空白分）
    val words = splitLyricWords(line.text)
    if (words.isEmpty()) return -1

    // 按字符数加权：每个词的"时长比例" = 词字符数 / 总字符数
    val totalChars = words.sumOf { it.length }.toFloat()
    if (totalChars <= 0f) return -1

    var accumulated = 0f
    for ((index, word) in words.withIndex()) {
        accumulated += word.length / totalChars
        if (progress <= accumulated) return index
    }
    return words.lastIndex  // 进度 1.0 时返回最后一个词
}

/**
 * 中文逐字符切分，英文/数字按空白分词。
 * 例："Hello 世界 test" → ["Hello", "世", "界", "test"]
 */
fun splitLyricWords(text: String): List<String> {
    val result = mutableListOf<String>()
    var currentWord = StringBuilder()

    for (ch in text) {
        if (ch.isWhitespace()) {
            if (currentWord.isNotEmpty()) {
                result.add(currentWord.toString())
                currentWord = StringBuilder()
            }
            result.add(" ")  // 空白也算一个"词"
        } else if (ch in '\u4E00'..'\u9FFF' ||
                   ch in '\u3040'..'\u309F' ||
                   ch in '\u30A0'..'\u30FF') {
            // CJK 字符单独成词
            if (currentWord.isNotEmpty()) {
                result.add(currentWord.toString())
                currentWord = StringBuilder()
            }
            result.add(ch.toString())
        } else {
            // 拉丁/数字字符累积
            currentWord.append(ch)
        }
    }
    if (currentWord.isNotEmpty()) result.add(currentWord.toString())
    return result
}
```

### 3.2 词级 progress 计算

给 `LyricLine` 增加一个根据 progress 获取当前词的方法：

```kotlin
/**
 * @param progress 行内进度 0f..1f
 * @return 当前词索引（-1 表示无词或无法确定）和词文本
 */
fun getCurrentWord(progress: Float, lineProgress: Float): Pair<Int, String>? {
    val effectiveProgress = progress  // 0f..1f
    val index: Int
    if (hasWordTiming) {
        // 真逐字：按相对时间找
        val nextLineTime = /* 需要外部传入行时长 */ 0L
        val lineDurationMs = 5000L  // fallback
        val elapsedInLine = (effectiveProgress * lineDurationMs).toLong()
        index = words!!.indexOfLast { it.relativeMs <= elapsedInLine }
            .coerceAtLeast(0)
    } else {
        // 伪逐字：按字符数估算
        index = estimateCurrentWordIndex(this, effectiveProgress)
    }
    if (index < 0) return null
    val splitWords = splitLyricWords(text)
    return index to splitWords.getOrElse(index) { "" }
}
```

---

## 四、渲染改动

### 4.1 `HyperFocusLyricStyle.applyLyricStyle()` — 双行模式

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/notification/HyperFocusLyricStyle.kt`

新增参数 `activeWordIndex: Int = -1`，当 >= 0 时高亮该词。

**高亮逻辑**：

```kotlin
/**
 * 构建带词高亮的 HTML 文本。
 *
 * @param fullText      整行文本
 * @param activeIndex   当前词在 splitLyricWords 中的索引，-1 表示不高亮
 * @param normalColor   普通文字颜色（#RRGGBB）
 * @param highlightColor 高亮颜色（#RRGGBB）
 * @return HTML 字符串，可直接传入 Html.fromHtml()
 */
fun buildWordHighlightedHtml(
    fullText: String,
    activeIndex: Int,
    normalColor: String,
    highlightColor: String
): String {
    if (activeIndex < 0) {
        return "<font color=\"$normalColor\">${fullText.htmlEscape()}</font>"
    }

    val words = splitLyricWords(fullText)
    if (words.isEmpty() || activeIndex >= words.size) {
        return "<font color=\"$normalColor\">${fullText.htmlEscape()}</font>"
    }

    val sb = StringBuilder()
    for ((i, word) in words.withIndex()) {
        val escaped = word.htmlEscape()
        if (i == activeIndex) {
            sb.append("<font color=\"$highlightColor\">$escaped</font>")
        } else {
            sb.append("<font color=\"$normalColor\">$escaped</font>")
        }
    }
    return sb.toString()
}

/** 将 & < > 等转义为 HTML entities */
private fun String.htmlEscape(): String {
    return this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
```

**在 `applyLyricStyle()` 第 786 行**，将：

```kotlin
views.setTextViewText(R.id.focuslyric, primaryText)
```

替换为：

```kotlin
val styledLyric = if (activeWordIndex >= 0) {
    val normalStr = String.format("#%06X", 0xFFFFFF and blendSecondaryTextColor(style.colorPrimary))
    val highlightStr = String.format("#%06X", 0xFFFFFF and style.colorPrimary)
    Html.fromHtml(
        buildWordHighlightedHtml(primaryText, activeWordIndex, normalStr, highlightStr),
        Html.FROM_HTML_MODE_LEGACY
    )
} else {
    primaryText  // 不启用逐字时走原逻辑，零性能损耗
}
views.setTextViewText(R.id.focuslyric, styledLyric)
```

### 4.2 多行模式 — 当前行词高亮

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/notification/HyperFocusLyricStyle.kt`

多行模式构建方法（约第 1284 行循环处），对 `isCurrentLine == true` 的槽位，同样用 HTML 构建带词高亮的文本：

```kotlin
if (isCurrentLine && activeWordIndex >= 0) {
    val styled = Html.fromHtml(
        buildWordHighlightedHtml(displayText, activeWordIndex, normalStr, highlightStr),
        Html.FROM_HTML_MODE_LEGACY
    )
    views.setTextViewText(viewId, styled)
} else {
    views.setTextViewText(viewId, displayText)
}
```

### 4.3 `FocusContent` 增加字段

`FocusContent` data class（第 91 行）增加：

```kotlin
data class FocusContent(
    val songTitle: String,
    val artist: String,
    val lyricText: String,
    val secondLineText: String,
    val lineTranslation: String? = null,
    val musicPackage: String = "",
    val multiLine: MultiLineWindow? = null,
    // 新增：当前词索引，-1 表示不启用逐字
    val activeWordIndex: Int = -1
)
```

---

## 五、SystemUI Hook 侧词级定时驱动

### 5.1 广播协议扩展

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/service/LyricService.kt`  
**文件**：`focus/src/main/java/com/leowalk/LyricFocus/xposed/hook/systemui/SystemUIHyperFocusHook.kt`

**`ACTION_LYRIC_DATA` 广播**（全量数据）增加：

```kotlin
const val EXTRA_LYRIC_JSON = "lyric_json"  // 现有，但需增加 words 数组
const val EXTRA_LYRIC_HAS_WORD_TIMING = "lyric_has_word_timing"  // 新增：boolean
```

**`ACTION_UPDATE_LYRIC` 广播**（增量更新）增加：

```kotlin
const val EXTRA_WORD_INDEX = "word_index"  // 新增：int，-1 表示不启用
```

### 5.2 SystemUI Hook — 本地词索引计算

`SystemUIHyperFocusHook` 已有 `currentPosition` 和 `lyricLines`，可本地计算词索引，无需 App 每次广播。

```kotlin
/**
 * 根据当前播放位置和行内进度，计算当前词索引。
 * @param lineIndex 当前歌词行索引（由 getCurrentLineIndex 算出）
 * @return 词索引，-1 表示无法确定
 */
private fun getCurrentWordIndex(lineIndex: Int): Int {
    if (lineIndex < 0 || lineIndex >= lyricLines.size) return -1
    val line = lyricLines[lineIndex]
    if (line.text.isBlank()) return -1

    // 获取行内进度
    val adjustedPosition = currentPosition + lyricOffset + syncAdvanceMs
    val lineTime = line.time
    val nextTime = lyricLines.getOrNull(lineIndex + 1)?.time ?: (lineTime + 5000L)
    val lineDuration = (nextTime - lineTime).coerceAtLeast(1000L)
    val progress = ((adjustedPosition - lineTime).toFloat() / lineDuration).coerceIn(0f, 1f)

    if (line.hasWordTiming) {
        // 真逐字：按相对时间匹配
        val elapsedInLine = (progress * lineDuration).toLong()
        return line.words!!.indexOfLast { it.relativeMs <= elapsedInLine }
            .coerceAtLeast(0)
    }

    // 伪逐字：按字符数估算
    val words = splitLyricWords(line.text)
    if (words.isEmpty()) return -1
    val totalChars = words.sumOf { it.length }.toFloat()
    var accumulated = 0f
    for ((i, word) in words.withIndex()) {
        accumulated += word.length / totalChars
        if (progress <= accumulated) return i
    }
    return words.lastIndex
}
```

### 5.3 更新时机

在 `postFocusUpdate()` 中，计算 `activeWordIndex` 并传入 `FocusContent`：

```kotlin
private fun postFocusUpdate(mode: FocusRefreshMode, force: Boolean = false) {
    val lineIndex = getCurrentLineIndex(currentPosition)
    val wordIndex = if (enableWordHighlight) getCurrentWordIndex(lineIndex) else -1
    
    HyperFocusLyricStyle.postFocusNotification(
        systemContext = systemUIContext!!,
        notificationManager = notificationManager!!,
        content = FocusContent(
            songTitle = currentTitle,
            artist = currentArtist,
            lyricText = currentLyricText,
            secondLineText = currentSecondLine,
            lineTranslation = currentLineTranslation,
            musicPackage = musicPackage,
            multiLine = buildMultiLineWindow(),
            activeWordIndex = wordIndex
        ),
        ...
    )
}
```

**更新频率**：仍然复用现有的 `scheduleNextUpdate()` 机制（250ms 间隔），词索引的计算是 O(n) 的纯内存操作，无额外开销。

---

## 六、性能数据

| 项目 | 改动前 | 改动后 | 增量 |
|------|--------|--------|------|
| 广播频率 | 250ms | 250ms | 0 |
| 单次广播 payload | ~200B | ~300B（多传 word_index int） | ~100B |
| RemoteViews 序列化大小 | ~5KB | ~6KB（Spanned 多几个 ForegroundColorSpan） | ~1KB |
| 行级 CPU | O(log n) 二分查找 | O(log n) 二分 + O(w) 词索引计算（w ≤ 20） | 可忽略 |
| 内存 | lyrics JSON + ~3KB | lyrics JSON + words 数组（约 200B/行） | ~10KB/首歌 |

---

## 七、用户偏好开关

**文件**：`focus/src/main/java/com/leowalk/LyricFocus/FocusPreferences.kt`

新增配置项：

```kotlin
const val KEY_WORD_BY_WORD = "word_by_word_highlight"
const val DEFAULT_WORD_BY_WORD = false  // 默认关闭，保留原体验

fun isWordByWordEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_WORD_BY_WORD, DEFAULT_WORD_BY_WORD)
}

fun setWordByWordEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_WORD_BY_WORD, enabled).apply()
    // 广播样式变更
    broadcastSettingsChanged(context)
}
```

`enableWordHighlight` 从 `FocusStyleSnapshot` 中读取，与现有颜色/大小配置一并跨进程同步。

---

## 八、文件改动清单

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `lyric/LyricLine.kt` | +20 行 | 新增 `WordTime`、`words` 字段、辅助属性 |
| `lyric/LyricInfo.kt` | +10 行 | `toJson()` 序列化 words |
| `lyric/LrcParser.kt` | +50 行 | `parseWordTimings()`、修改 `RawEntry` 和构建流程 |
| `lyric/WordUtils.kt` | +40 行 | 新文件：`splitLyricWords()`、`estimateCurrentWordIndex()` |
| `notification/HyperFocusLyricStyle.kt` | +40 行 | `buildWordHighlightedHtml()`、修改 `applyLyricStyle()` 和 `FocusContent` |
| `xposed/.../SystemUIHyperFocusHook.kt` | +50 行 | `LyricLineData.WordTimeData`、`getCurrentWordIndex()`、JSON 解析 words、传入 `activeWordIndex` |
| `service/LyricService.kt` | +5 行 | 广播中增加 `EXTRA_LYRIC_HAS_WORD_TIMING` |
| `FocusPreferences.kt` | +15 行 | 新增开关配置 |
| `FocusStyleSnapshot.kt` | +5 行 | 跨进程同步 `enableWordHighlight` |
| Layout 文件 | 0 | 不需要改动 |
| 测试 | +80 行 | `LrcParser` 逐字解析测试、`splitLyricWords` 测试 |

**总预估**：约 315 行新增代码，零行删除（`stripTimeTags` 改为新方法调用）。

---

## 九、风险与注意事项

1. **RemoteViews Spanned 跨进程序列化**  
   理论上 Android 6+ 的 `TextUtils.writeToParcel` 完整支持 `ParcelableSpan`（`ForegroundColorSpan` 实现了该接口）。如果 HyperOS 的焦点通知框架有自定义序列化路径导致失败，需要 fallback 到 SystemUI hook 侧直接对已渲染的 TextView 调用 `setText(SpannableString)`。

2. **HTML 转义**  
   歌词中可能包含 `<`、`>`、`&` 字符（极少见但存在），`htmlEscape()` 必须覆盖这些。同时确保 `Html.fromHtml()` 正确还原。

3. **颜色编码**  
   HTML `<font color="#RRGGBB">` 要求严格 6 位十六进制。`String.format("#%06X", ...)` 可满足。

4. **切换开关时的平滑降级**  
   当 `enableWordHighlight = false` 时，所有代码路径必须走回原来的 `setText(plainString)` 逻辑，零性能损耗和零视觉差异。

5. **多行模式下非当前行不高亮**  
   只对 `currentLineSlot` 对应的行做词高亮，其余行保持静态纯色。

6. **中英混合分词**  
   `splitLyricWords()` 是伪逐字效果的关键，必须正确切分 CJK 单字符词、拉丁多字符词、标点符号。

7. **AOD 模式**  
   由于 AOD 更新频率较低（保活间隔最长 9 秒），逐字高亮在 AOD 上可能无法实时刷新。此时建议关闭词高亮（或降级为仅锁屏生效）。
