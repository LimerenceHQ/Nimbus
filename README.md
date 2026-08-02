# Nimbus

Nimbus 是一个面向 Minecraft Fabric 客户端的开发中项目。当前工作是建立一套简洁、明确且容易长期维护的客户端：统一 客户端 写法、集中配置管理、保留可靠的自动发现能力。

> Nimbus 仍处于开发阶段，内部结构和公开 API 可能继续调整。目前通过 Gradle 引入的 Skija native 仅包含 Windows x64 版本。

## 当前基础能力

- 事务式 Core 启动：Manager 按依赖顺序初始化，失败时逆序回滚。
- 统一 Manager 生命周期：通过 `InstanceManager` 使用一致的 `init()` / `shutdown()` 协议。
- Module 系统：显式注册 Module，并自动发现继承层级中的 `Setting<?>` 字段。
- 配置系统：单一 `ConfigManager`、延迟自动保存、临时文件写入与原子替换。
- Account 系统：账户列表和最后使用账户拥有独立的存储边界。
- Command 系统：集中注册命令、别名和补全逻辑。
- EventBus：支持优先级、取消语义以及与 Module 启用状态绑定的监听生命周期。
- Skija 渲染子系统：统一 HUD、Screen、文本、图片、SVG、效果和后处理入口。
- UI 基础：包含菜单、账户界面以及仍在迭代中的 ClickGUI 实现。

当前仓库中包含 HUD、Render 类 Module 和一组基础客户端命令。这些实现同时承担对 Base API 的实际使用与验证。

## 环境要求

| 组件 | 当前版本 |
|---|---|
| Minecraft | `26.2` |
| Fabric Loader | `0.19.3` 或更高 |
| Fabric API | `0.156.0+26.2` |
| Java | `25` 或更高 |
| Gradle | Wrapper `9.5.1` |
| Skija | `0.143.17`，Windows x64 native |

版本以 [`gradle.properties`](gradle.properties) 和 [`fabric.mod.json`](src/main/resources/fabric.mod.json) 为准。

## 构建与运行

项目提供 Gradle Wrapper，无需单独安装 Gradle。Windows PowerShell：

```powershell
.\gradlew.bat compileJava --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat runClient --console=plain
```

构建产物位于 `build/libs/`。首次构建需要能够下载 Minecraft、Fabric 和 Gradle 依赖。

Skija、JLayer、jFLAC 和 QR Code Generator 均由 Maven Central 解析并嵌入构建产物，仓库不保存这些项目的源码或 native 二进制副本。版本统一声明在 `gradle.properties`。

## 项目结构

```text
src/main/java/cc/nimbus
├─ core                 # 全局生命周期编排与基础接口
├─ managers             # 进程级服务及其所有权边界
├─ features
│  ├─ module            # Module API 与分类实现
│  └─ command           # Command API 与具体命令
├─ event                # EventBus、处理器与领域事件
├─ ui                   # 菜单、账户界面、ClickGUI 与 Screen
├─ utils                # 无业务所有权的技术能力
└─ mixin                # Minecraft 渲染与客户端接入点
```

核心启动关系：

```text
Nimbus.onInitializeClient()
└─ Core.INSTANCE.init(configDir)
   ├─ RenderManager
   ├─ ModuleManager
   ├─ ConfigManager
   ├─ AccountManager
   └─ CommandManager
```

关闭顺序与启动顺序相反。Manager 初始化期间需要共享依赖时，应使用构造器注入，不能通过尚未提交完成的 Core getter 获取。

## 配置存储

Nimbus 在 Fabric config directory 下使用单一项目目录：

```text
nimbus/
├─ configs/
│  ├─ default.json
│  └─ <profile>.json
└─ accounts/
   ├─ accounts.json
   └─ last.json
```

Module 配置由 `ConfigManager` 管理；账户和 token 数据由 `AccountManager` 管理。两个领域不会写入同一配置文件。

## 开发文档

- [Core 与 Manager 使用指南](docs/core-manager-guide.md)：Manager、Module、Setting、Config、Command 和 EventBus 的注册与使用。
- [HUD 与 Skija 架构](docs/skija-architecture.md)：渲染入口、Layer、坐标、文本、SVG、缓存和资源生命周期。
- [HUD 拖拽系统](docs/hud-draggable-guide.md)：自动注册、四区锚点、缩放、编辑和配置持久化。
- [设计与重构规范](docs/design-guidelines.md)：包结构、接口抽取、代码卫生、错误处理和验证要求。
- [项目开发契约](docs/skill/AGENTS.md)：AI 修改本项目时必须遵守的最低规则。

修改代码前应先阅读对应文档，再以当前源码和依赖真实 API 为最终依据。文档与实现不一致时，应在同一次修改中校正两者。

## 设计原则

- 认真查阅接口，不暗猜第三方或 Minecraft 行为。
- 业务含义不明确时先确认，不自行补全需求。
- 优先复用已有能力，不为形式整齐制造平行接口。
- 保留自动发现、生命周期和已有公共能力，不以缩短代码为理由降级功能。
- 所有权、线程边界和释放责任必须能够从代码中判断。
- 编译只是最低验证；配置、渲染、输入和补全等行为需要针对性检查。
- 重构保持范围明确，不覆盖用户并行修改，不顺手整理无关代码。

## 第三方依赖

下列依赖在 Gradle 构建期间从 Maven Central 解析，其源码和 native 二进制文件不存放在本仓库中。实际版本以 `gradle.properties` 为准。

| 依赖 | 用途 | 许可证 | 上游项目 |
|---|---|---|---|
| `io.github.humbleui:skija-shared` / `skija-windows-x64` | Skia Java 绑定和 Windows x64 native 运行库 | Apache License 2.0 | [HumbleUI/Skija](https://github.com/humbleui/skija) |
| `io.github.humbleui:types` | Skija 使用的几何类型 | Apache License 2.0 | [HumbleUI/Types](https://github.com/humbleui/types) |
| `javazoom:jlayer` | MP3 解码和 WAV 转换 | GNU LGPL | [JavaLayer](http://www.javazoom.net/javalayer/javalayer.html) |
| `org.jflac:jflac-codec` | FLAC 解码和 WAV 转换 | GNU LGPL 2.1 | [jFLAC](https://github.com/nguillaumin/jflac) |
| `io.nayuki:qrcodegen` | 音乐登录二维码生成 | MIT License | [QR Code Generator](https://github.com/nayuki/QR-Code-generator) |

这些库及其权利归各自作者所有，并按各自许可证分发。Nimbus 不修改其包内实现；Fabric 模组中嵌入的依赖 JAR 保留上游元数据，完整许可证和声明以对应上游项目为准。

## License

Copyright (c) 2026. All rights reserved. 详见 [`LICENSE.txt`](LICENSE.txt)。
