## v1.8.6

### 新功能
- **AI 歌词翻译独立卡片**：移至歌词获取源页面，开关控制显隐，支持内联配置（Base URL / API Key / Model / 目标语言 / 强制翻译全部行 / 连通性检测），无需弹窗
- **AI Token 消耗统计**：自动累计每次 API 调用的 Token 用量，卡片内实时显示
- **欢迎引导页重构**：三步向导式引导（软件介绍 → 必要权限 → 其他权限 + 须知），带圆点指示器和上一步/下一步按钮
- 🥚

### 改进
- **多彩取色更鲜艳**：强调色按饱和度选取（不再按亮度），`blendSecondary` 稀释比降低，对比度阈值下调
- **预览取色与实机统一**：预览和实机共用 `resolveLockScreenColors` 取色函数，色彩模式 / Monet / 文字取色 / 手动色全部统一
- **多行预览重复歌词修复**：非交错模式下索引从 `i/2` 改为 `i`，每行不再重复显示
- **多行日语字体缩小**：`applyMultiLineStyle` 和预览同步 ×0.88 缩放
- **样式卡片顺序**：万象息屏开启时 AOD 卡片前置、锁屏卡片后置；关闭时恢复
- **向上滑动闪动修复**：AppBarLayout 添加 `liftOnScroll="false"`
- **多行模式字号互斥**：开启多行后锁屏字号滑块自动失效，由多行字号接管
- **预览自动收起**：多行模式下向上滑动超过 400px 自动折叠预览卡片，动画 350ms
- **焦点通知背景**：预览背景跟随手动设置（黑/白/灰），取色模式下跟随提取色

### 修复
- `extractDistinctColors` 相同色值崩溃修复
- "仅 AOD 显示多行"不再干预预览取色和背景

### API 变更
- `HyperFocusLyricStyle.resolveLockScreenColors()`：新增共享取色入口，实机和预览统一调用
- `AlbumColorExtractor.extractDistinctColors()`：强调色改为饱和度最高
- `FocusPreferences`：新增 `isAiLyricEnabled` / `setAiLyricEnabled` / `getAiTotalTokens` / `addAiTokens`
