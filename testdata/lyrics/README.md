将本目录中的 `.lrc` 文件复制到手机后，在应用内：

1. 歌词源 → **本地 LRC 文件**
2. 点 **配置** → **选择文件夹**，选中存放歌词的目录（如「下载」）

## 示例文件

| 文件名 | 对应歌曲 |
|--------|----------|
| `告白气球 - 周杰伦.lrc` | 告白气球 / 周杰伦 |
| `p.h. - SEVENTHLINKS.lrc` | p.h. / SEVENTHLINKS |
| `SEVENTHLINKS v flower - p.h..lrc` | p.h. / SEVENTHLINKS/v flower（歌手在前） |

## ADB 推送

```bash
adb push testdata/lyrics/. /sdcard/Download/
```

然后在应用内选择「下载」文件夹即可。
