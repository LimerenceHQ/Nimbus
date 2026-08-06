# Nimbus Design and Refactoring Guidelines

最后同步：2026-08-07。

本文档定义 Nimbus 的代码组织、接口、Manager、生命周期、错误处理和重构规范。目标不是追求抽象数量，而是让 Base 长期保持简洁、明确、可复用和容易验证。

本文档适用于人类开发者和 AI。任何“大整理”都不能以删除现有能力、猜测接口或跳过验证为代价。

## 1. 工作原则

Nimbus 的修改原则：

- 以暗猜接口为耻，以认真查阅为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以盲想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

具体含义：

1. 修改第三方或 Minecraft API 前阅读当前版本真实源码。
2. 修改公共方法前搜索全部调用者。
3. 不知道业务含义时不自行补全需求。
4. 已有能力可以组合时不新增平行接口。
5. 编译成功只是最低验证，不等于行为正确。
6. 遇到用户并发修改时保留并适配，不覆盖未知改动。

## 2. Base 的目标

一个好的 Base 应做到：

- 入口少而明确。
- 每种状态只有一个所有者。
- 生命周期能从代码结构看出来。
- 同类 Manager 使用相同模式。
- 注册位置可搜索、可审计。
- 自动化只发生在有明确规则的地方。
- 功能代码关注业务，不管理基础设施细节。
- 内部实现可以替换，公共能力不被随意删除。

Base 不应追求：

- 为每个类创建接口。
- 为每个方法创建 Manager。
- 把所有第三方 API 机械包装一遍。
- 使用反射扫描隐藏所有注册过程。
- 为未来可能出现的需求提前建立空扩展点。
- 用大量 facade 掩盖不清晰的所有权。

## 3. 包结构

当前主要职责：

```text
cc.nimbus
├─ core
│  ├─ Core
│  └─ interfaces
├─ managers
├─ features
│  ├─ client              # 全局 Client Settings、语言和主题数据对象
│  ├─ hud                 # HUD 视口、锚点和拖拽模型
│  ├─ module
│  │  ├─ api
│  │  └─ modules/<category>
│  ├─ music               # 音乐模型、provider、playback、cache 与 integration
│  └─ command
│     ├─ api
│     └─ commands
├─ event
│  ├─ component
│  ├─ handler
│  └─ impl/<domain>
├─ ui
│  └─ <product or screen domain>
├─ utils
│  └─ <technical domain>
└─ mixin
```

### 3.1 `core`

只放：

- 全局生命周期编排。
- Manager 实例访问。
- 真正跨系统的基础接口。

不放：

- Module 实现。
- UI widget。
- 配置 JSON 工具。
- 渲染算法。
- 单个 Manager 的 helper。

`core/interfaces` 不是“看起来重要的接口”收容所。只有真正属于整个客户端基础生命周期的接口才进入这里。

### 3.2 `managers`

每个 Manager 负责一个清晰领域的状态和生命周期。Manager 放在同一扁平包中，便于从目录直接看到 Base 当前拥有的全部进程服务。

Manager 内部专属的大量实现出现后，可以建立领域子包；不能把 Manager 自身散落到各功能包。

### 3.3 `features`

功能按业务领域组织：

- `api` 保存同领域稳定基础能力。
- `modules/<category>` 保存具体 Module。
- `commands` 保存具体 Command。

不要把具体功能塞进 `core` 或 `utils`。

### 3.4 `ui`

UI 专属绘制、状态和 widget 留在 UI 领域。例如 ClickGUI 的 `UiDraw`、Theme、Icons 不属于通用 Skija Renderer。

只有多个不同 UI 产品都需要并且语义一致的能力，才提升到公共 UI 或 render API。

### 3.5 `utils`

`utils` 用于技术能力，不用于隐藏业务对象。

一个 utils 类应尽量：

- 无共享可变业务状态。
- 输入输出明确。
- 不偷偷访问多个 Manager。
- 不拥有需要 shutdown 的进程资源。

一旦拥有注册表、缓存生命周期或跨功能状态，应重新评估是否属于 Manager 或某个子系统内部。

### 3.6 第三方依赖

第三方库通过 Gradle 和 Maven Central 引入，仓库不保存其源码或 native 二进制副本：

- 坐标在 `build.gradle` 声明，版本在 `gradle.properties` 统一管理。
- 修改调用前读取锁定版本对应的官方源码 JAR 或上游源码，确认真实 API 和所有权语义。
- 可以在正确作用域使用公开类型，但不把 Nimbus 业务逻辑写进第三方包。
- 不为适配 Nimbus 风格 fork、重排或重命名第三方源码。
- 新增依赖时更新 `README.md` 的“第三方依赖”章节，并检查最终 Fabric JAR 的嵌套依赖。

## 4. Manager 设计规范

### 4.1 创建条件

创建新 Manager 前必须回答：

1. 它拥有什么状态或资源？
2. 谁需要共享它？
3. 它何时 init，何时 shutdown？
4. 现有 Manager 为什么不能承担？
5. 不创建 Manager 会产生什么真实重复或耦合？

无法回答时，不创建。

### 4.2 统一形态

Manager 应优先使用：

```java
public final class ExampleManager implements InstanceManager {
    private final Dependency dependency;

    public ExampleManager(Dependency dependency) {
        this.dependency = Objects.requireNonNull(dependency, "dependency");
    }

    @Override
    public void init() {
    }

    @Override
    public void shutdown() {
    }
}
```

规范：

- 默认 `final`。
- 依赖构造器注入。
- 不使用全局静态 `INSTANCE`；实例由 Core 管。
- init 不承担与生命周期无关的业务操作。
- shutdown 只关闭自己拥有的资源。
- 注册和注销成对。
- 失败后不留下半初始化状态。
- 集合查询返回 snapshot。

### 4.3 初始化顺序

依赖方后启动、先关闭：

```text
A 被 B 依赖
init:     A -> B
shutdown: B -> A
```

Manager init 期间不通过 Core getter 偷取尚未提交的依赖。Core 的事务式初始化要求依赖在构造器中明确传入。

### 4.4 注册策略

Manager、Module、Command 当前使用显式注册。显式注册不是“不够自动”，而是把有限、稳定的系统组成放在可审计位置。

自动化适合规则明确且对象已经存在的场景，例如 Module Setting 字段自动发现。

选择标准：

- 数量有限、启动顺序重要：显式注册。
- 子对象已经声明为字段、规则稳定：自动发现。
- 需要扫描 classpath、依赖加载顺序或产生隐式副作用：通常拒绝。

## 5. 接口抽取规范

接口用于表达稳定能力或替换边界，不是装饰。

适合抽接口：

- 至少有两个真实实现。
- 调用方只需要稳定能力，不应知道具体类。
- 生命周期或资源所有权需要统一协议。
- 第三方实现需要被隔离。
- 接口能明显降低跨包耦合。

不适合抽接口：

- 只有一个实现且没有替换需求。
- 接口与实现方法完全一一复制，没有调用方收益。
- 为未来猜测的实现预留。
- 只为了让目录里出现 `interfaces`。
- 接口隐藏了重要所有权信息。

`InstanceManager` 是合理接口，因为 Core 对所有 Manager 执行统一 init/shutdown。每个具体 Manager 不需要再创建 `IExampleManager`。

抽接口前先搜索现有接口和 helper，避免创造同义能力。

## 6. 自动注册规范

自动注册不能删除现有好用能力。

当前 Module Setting 规则：

- 普通 Setting 声明为实例字段。
- `BasicModule.getSettings()` 自动反射发现。
- `@Exclude` 明确排除。
- `addSetting()` 只保留给动态 Setting。

错误重构：

```java
public Example() {
    addSettings(a, b, c);
}
```

如果字段已经能自动发现，这会增加重复和遗漏风险。

设计新的自动注册时必须提供：

- 确定的发现范围。
- 确定的顺序。
- 重复检测。
- 错误行为。
- 缓存失效规则。
- 对动态对象的逃生口。

## 7. API 设计

### 7.1 入口

每个子系统应有一个明显根入口：

```java
Core.INSTANCE.getModuleManager()
Core.INSTANCE.getConfigManager()
Core.INSTANCE.getRenderManager().skija()
```

不要同时提供三种同义获取方式，例如静态 INSTANCE、Core getter 和 ServiceLocator。

### 7.2 方法语义

方法名应表达所有权和结果：

- `getModules()` 返回 snapshot。
- `registerModule()` 修改 Manager registry。
- `releaseTarget()` 释放可重建 GPU target。
- `close()` 表示对象生命周期结束。
- `saveConfig()` 返回是否成功。

若实现与名称不符，优先修正语义，而不是靠注释长期解释。

### 7.3 返回值

- 编程错误，例如重复注册，立即抛异常。
- 允许不存在的查找可返回 `null` 或 `Optional`，同一领域保持一致。
- 用户输入错误返回明确失败状态和消息。
- 不返回内部可变集合。
- 不用 `null` 表示多个互相冲突的状态。

### 7.4 能力保留

重构公共 API 前建立能力清单：

- 当前方法有哪些调用者？
- 是否支持自动发现、可见性、缓存失效或动态注册？
- 新设计如何覆盖全部能力？
- 删除的是冗余实现还是业务能力？

不能因为新 API 看起来更短就删除已有的完整能力。

## 8. 生命周期和资源所有权

每个需要关闭的对象必须能回答：

- 谁创建？
- 谁保存？
- 谁关闭？
- 关闭后是否可重建？
- 是否允许多个消费者共享？

推荐层级：

| 生命周期 | 示例 | 所有者 |
|---|---|---|
| 进程级 | Manager、Renderer、字体注册表 | Core/Manager |
| 世界级 | 只在世界存在时有效的缓存 | 对应 world service |
| Screen 级 | framebuffer target、UI 输入状态 | Screen/生命周期适配器 |
| Frame 级 | Shader、MaskFilter、临时 Path | Frame scope |
| 代码块级 | Paint、ImageFilter | try-with-resources |

不要依赖 GC 及时释放 GPU 或 native 资源。也不要把所有小对象强行做成复杂引用计数；进程级小资源可以由 Manager shutdown 统一处理。

共享资源不能由借用者关闭。独占资源必须有明确 close 边界。

## 9. 配置设计

配置系统保持一个 `ConfigManager`，不要为每种 Setting 或页面创建 Manager。

规范：

- Module/Setting 是配置数据来源。
- Setting 字段自动发现。
- 状态变化通过 listener 标记 dirty。
- 延迟自动保存，避免每次拖动滑块都写磁盘。
- 临时文件写入后原子替换。
- 加载期间禁止递归标 dirty。
- 单个损坏条目尽量隔离。
- 配置名称必须防止路径穿越。
- Account/token 使用独立存储领域。

不默认添加：

- 未被要求的 legacy migration。
- 多套 Serializer facade。
- 每种 Setting 一个文件。
- 隐式修改用户旧文件的迁移逻辑。

需要迁移时必须有明确旧格式、版本、回滚和验证方案。

## 10. 事件设计

- Event 表示已经存在的真实时机或状态变化。
- 不为调用一个方法而绕一圈 EventBus。
- Module listener 跟随 enabled 生命周期。
- Manager listener 跟随 Manager 生命周期。
- Event handler 保持短小，把复杂业务委托给领域对象。
- Cancelable 事件的取消语义必须明确。
- 不在构造器中注册未完成对象。
- 不保存短生命周期 Event 对象供以后使用。

高频事件中的代码应避免：

- 每次反射。
- 无界集合增长。
- 磁盘 IO。
- 阻塞网络请求。
- 重复构造相同索引。

## 11. 并发和线程

默认先确认线程边界，不猜“应该没问题”。

- Minecraft/GL 状态在 render thread 操作。
- Minecraft User 或 GUI 状态通过 client execute/schedule 回主线程。
- 网络认证可在专用后台线程执行。
- Manager 共享可变集合使用明确同步边界。
- 回调跨线程时传递不可变 snapshot。
- 不把事件、Canvas、Frame 或 GL object 跨线程保存。

`volatile` 不能代替复合状态同步。多个索引必须在同一临界区一起更新。

## 12. 缓存和性能

创建缓存前回答：

1. 计算是否真的昂贵？
2. key 是否稳定且正确？
3. 是否有上限？
4. 淘汰时谁关闭 value？
5. 资源 reload 或 shutdown 如何清理？

禁止：

- 无界 native/GPU 缓存。
- 在 Module 中复制 Renderer 的图片或字体缓存。
- 用缓存掩盖每帧错误构造完整业务模型。
- 以性能名义保留未测量的复杂度。

优先优化：

- 移出高频循环的反射和索引构建。
- 复用明确的 Manager registry。
- 使用不可变 snapshot 传入渲染 callback。
- 对 native 缓存设置上限并在淘汰时 close。

## 13. 错误处理

### 13.1 Fail fast

适用于：

- 缺少 `@ModuleInfo`。
- 重复 Module class/name/alias。
- 重复 Command name/alias。
- 未注册字体。
- Core getter 在初始化前使用。
- 平台或 Backend 不支持。

这些错误继续运行只会在更远处产生难以理解的 NPE。

### 13.2 可恢复错误

适用于：

- 用户输入错误。
- 单个配置条目损坏。
- 可选资源不存在。
- 登录失败。

应返回失败状态或显示明确消息，不能静默假装成功。

### 13.3 日志

错误信息包含：

- 操作对象。
- 路径、name 或类型。
- 原始异常原因。

不要只输出 `Failed`，也不要用大量正常日志淹没真正错误。

## 14. 代码整洁度

- 删除没有调用者、没有扩展契约的历史方法。
- 不保留空 `beginFrame/endFrame` 或空 TODO 协议。
- 简单流程保持线性，避免深层包装。
- 注释解释原因、边界和所有权，不复述代码。
- 同类 Manager 的命名、init/shutdown 和 snapshot 方式保持一致。
- 避免静态可变业务状态。
- 对重复逻辑先判断是否真的同一语义，再抽取。
- 一个类承担一个可描述的职责，不机械追求极小类。

删除代码前必须区分：

- 无调用者的冗余。
- 反射、注解或框架隐式调用。
- 用户未来明确依赖的公共能力。

仅凭文本搜索没有调用者，不足以删除反射入口。

## 15. 重构流程

### 15.1 调查

1. 查看 `git status`，识别用户并发修改。
2. 搜索定义、构造位置和所有调用者。
3. 阅读接口实际源码，不猜第三方行为。
4. 记录当前能力和不变量。
5. 区分当前问题、历史问题和未来设想。

### 15.2 设计

1. 明确所有者和生命周期。
2. 明确公共 API 与 internal 边界。
3. 优先复用现有接口。
4. 只增加能消除真实复杂度的抽象。
5. 行为不明确或业务选择会改变结果时，先向人类确认。

### 15.3 修改

1. 小步编辑。
2. 保留现有能力。
3. 每次修改后检查并发变化。
4. 不顺便格式化或重排无关文件。
5. 不覆盖用户未提交修改。

### 15.4 验证

至少：

```powershell
.\gradlew.bat compileJava --console=plain
git diff --check
```

然后按风险验证：

- 注册：重复、缺失、顺序和注销。
- 配置：首次创建、加载、自动保存、损坏项。
- UI：重复打开、返回、缩放和输入。
- 渲染：HUD、Screen、resize、资源关闭。
- Command：执行、引号、Tab suggestion。

当前项目没有必要为了每次窄重构临时创建测试目录，但绝不能因此跳过验证。适合自动化且会长期维护的逻辑，应在与用户确认测试策略后添加正式测试。

## 16. 新代码审查清单

### 架构

- 文件是否位于正确领域？
- 是否出现新的全局入口？
- 是否绕过 Manager 或 Renderer？
- 是否重复已有能力？
- 接口是否有真实调用者？

### 生命周期

- 创建者、所有者、关闭者是否明确？
- 失败路径是否释放已创建资源？
- listener 是否成对注销？
- 集合是否有界或能清理？

### 行为

- 自动注册是否仍工作？
- 配置是否能保存新 Setting？
- 旧方法能力是否被保留？
- 错误是否在正确边界暴露？

### 可读性

- 方法名是否反映真实语义？
- 是否存在无用 wrapper？
- 是否能减少状态和分支？
- 注释是否解释了必要原因？

### 验证

- 是否查阅真实 API？
- 是否搜索所有调用点？
- 是否编译？
- 是否检查 diff？
- 是否说明无法自动验证的部分？

## 17. 文档维护

以下修改必须同步文档：

- 新增或删除 Manager。
- 改变 Core init/shutdown 顺序。
- 改变 Module、Setting 或 Command 注册方式。
- 改变配置路径或格式。
- 改变 Skija 公共入口、坐标、所有权或生命周期。
- 新增跨功能公共接口。
- 改变全局 Client Settings、I18N、HUD 视口或 MusicManager 的所有权边界。

文档不能把未来计划写成当前接口。未来设计必须标明“尚未实现”，避免 AI 根据不存在的类型编写代码。
文档只追踪稳定架构和公共契约，不追踪单个 Module 的动画参数、单个页面的视觉细节或临时测试功能。

相关文档：

- `docs/skija-architecture.md`
- `docs/core-manager-guide.md`
- `docs/README.md`
