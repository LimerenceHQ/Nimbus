# Nimbus 开发文档索引

最后同步：2026-08-07。

本文档目录只记录稳定的架构、公共 API、资源所有权和开发约束。具体 Module 的外观、动画、页面内容和临时测试功能以源码为准，不在架构文档中逐项追踪。

| 文档 | 适用范围 |
|---|---|
| `core-manager-guide.md` | Core、Manager、Module、Setting、Config、I18N、Command 与 EventBus |
| `skija-architecture.md` | Skija 入口、Layer、坐标、文本、图片、后处理和 native 资源生命周期 |
| `hud-draggable-guide.md` | HUD 归一化位置、统一视口缩放、四区锚点、拖拽与配置持久化 |
| `clickgui-music-guide.md` | ClickGUI 工具窗口与 Music UI 的表现层边界、状态和异步规则 |
| `design-guidelines.md` | 包结构、所有权、线程、错误处理、重构与代码审查规范 |
| `skill/AGENTS.md` | 人类或 AI 修改项目时必须遵守的最低工作契约 |

阅读顺序：

1. 先读与任务直接相关的文档。
2. 再检查当前源码、构造器、注册点和所有调用者。
3. 涉及 Minecraft、Fabric、Skija 或 native API 时，以当前依赖版本的真实源码为准。
4. 文档与代码不一致时，同一次修改中校正文档；不要根据文档虚构不存在的 API。

文档不应包含：

- 单个 HUD Module 的具体动画参数。
- 单个 Music 页面或视觉样式的逐项说明。
- 临时测试 Module、截图问题或一次性调试结论。
- 尚未实现但没有明确标注的未来计划。
