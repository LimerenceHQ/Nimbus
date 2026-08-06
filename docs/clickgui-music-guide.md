# ClickGUI 与 Nimbus Music

最后同步：2026-08-07。

本文只记录 ClickGUI 与 Music 表现层的稳定边界、输入/异步规则和资源所有权，不记录某个页面的具体外观、动画或临时功能。网络、播放器、配置持久化和 Skija 原生资源规则分别以 `MusicManager`、`ConfigManager` 与 `docs/skija-architecture.md` 为准。

## 1. List ClickGUI

`ClickGuiScreen` 继承 `AbstractSkijaScreen`，并建立统一逻辑坐标系。小窗口会整体缩放，所有鼠标坐标必须执行同一逆变换。

`ClickGuiView` 固定渲染 Combat、Move、Player、Render、Exploit、HUD 六列：

- 左键启停 Module。
- 右键在当前列内展开 Setting；同列只保留一个展开项。
- 中键进入按键监听，Escape 取消，Backspace/Delete 清除绑定。
- 每列独立滚动；不可见 Module 和 Setting 不创建可点击命中区。
- Search 只过滤 Module 列表，不修改 Module registry。

Setting 继续由 `BasicModule` 自动发现。`SettingControls` 只负责展示和输入，不允许新增手工 `addSettings(...)` 链路。

## 2. 工具窗口

右侧栏打开 Search、Music、Config 和 Client Settings。同一时间只存在一个工具窗口；切换时旧页面向左退出，新页面从右侧进入，外框在两种目标尺寸间插值。页面实现统一遵循 `ToolPage`。

Config 的选择操作不隐式加载。Load、Save、Create、Delete 与 Open Folder 都是明确命令。Client Settings 复用 `SettingControls`，没有第二套 Setting 表单系统。

数据所有权保持分离：

| 数据 | 所有者 | 文件 |
|---|---|---|
| Module、Setting、HUD 位置 | `ConfigManager` | `configs/*.json` |
| 全局视觉设置 | `ConfigManager` | `client.json` |
| 音乐 Cookie、播放与缓存设置 | `MusicManager` | `music/music.json` |
| 离线歌曲清单 | `AudioCache`（由 `MusicManager` 拥有） | `music/cache/index.json` |

## 3. Music 展示层

`MusicTool` 只组合 `MusicManager` 的公开能力。页面路由属于展示状态，不拥有网易云客户端、线程池或播放器。路由名称和页面数量可以变化，但数据所有权不能进入 `ui.clickgui`。

`MusicDataState` 保存窗口打开期间的页面快照，并用 route、playback 和 owner generation 验证异步结果。结果统一调度回 Minecraft 主线程；离开页面或关闭窗口后，迟到结果直接丢弃。二维码离开登录页或切换到短信登录后必须取消轮询。页面关闭不会停止 `MusicManager` 的播放、网络线程或缓存。

播放条读取 `PlaybackState` 和 `PlaybackQueueState`。队列点击只调用 `MusicManager.playAt(index)`，不得根据搜索列表猜测随机模式下的真实索引。

页面需要图片、色板或模糊效果时，只能借用 `MusicManager`/`ArtworkCache` 提供的数据，并通过 `SkijaEffects` 创建帧资源。页面不得持久保存 `ImageFilter`、Canvas 或 `SkijaFrame`；全局 Post Processing 关闭时不得绕过配置自行创建另一套后处理。

## 4. 图片与缓存

封面和二维码最终通过 `frame.images().encoded(key, bytes)` 解码。返回的 Skija `Image` 由 RenderManager 的有界 LRU 拥有，UI 只在当前帧借用，不关闭、不跨帧保存。

`ArtworkCache` 是 MusicManager 拥有的编码图片与色板缓存。UI 可以在窗口打开期间保存不可变 `Artwork` 引用，但不能建立第二套 native Image 缓存。

`AudioCache` 只在完整音频准备成功后更新 `index.json`。离线播放必须从 `CachedSong.music()` 构建队列并调用 `playCached(...)`；cache-only 播放路径不能回退到 `NeteaseApi`。

## 5. 字体

UI 使用 `NimbusFonts` 语义角色：

- `DISPLAY`：品牌和窗口标题。
- `BODY` / `BODY_MEDIUM` / `BODY_STRONG`：Module、Setting、按钮和数字。
- `CONTENT` / `CONTENT_MEDIUM`：歌曲、歌手、专辑与歌词。

中英文混排由 `TextFactory` 自动分 run。宽度测量、绘制、输入光标和选区必须使用同一 Skija 文本测量入口，不能用 Minecraft 字体宽度校正 Skija 文本。

## 6. 验证

除 `compileJava`、完整 `build` 和 `git diff --check` 外，客户端至少验证：

- 1280×720、1920×1080 与高分辨率下六列缩放和命中一致。
- RShift、Escape、Search、滚动、全部 Setting 类型与中键绑定。
- 三个工具窗口快速切换、重复开关和 resize 不留下焦点或异步状态。
- 页面切换、登录轮询和搜索等异步结果在关闭或 route generation 改变后会被丢弃。
- 断网播放缓存时不访问网易云元数据或音频 URL，UI 关闭不终止 MusicManager 当前播放。
