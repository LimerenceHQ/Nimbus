# Nimbus Core and Manager Guide

最后同步：2026-08-16。

本文档说明 Nimbus Core、Manager、Module、Setting、Command、Config 和 EventBus 的当前结构与注册方式。它既是开发手册，也是 AI 修改这些基础设施时的约束。

文档描述当前真实实现。修改注册方式或生命周期后，必须同步更新本文档。

## 1. 总体结构

```text
Nimbus.onInitializeClient()
└─ Core.INSTANCE.init(configDir)
   ├─ RenderManager
   ├─ RotationManager
   ├─ ModuleManager
   ├─ NotificationManager
   ├─ TargetManager
   ├─ HudManager
   ├─ ConfigManager
   ├─ I18nManager
   ├─ AccountManager
   ├─ MusicManager
   └─ CommandManager
```

`Core` 只负责：

- 创建 Manager。
- 按依赖顺序初始化。
- 初始化失败时回滚已启动 Manager。
- 按逆序 shutdown。
- 提供初始化状态检查后的 getter。

`Core` 不负责：

- Module、Command 或 Account 的业务逻辑。
- 配置 JSON 细节。
- 渲染实现。
- 扫描 classpath 自动发现 Manager。
- 保存全局工具方法。

## 2. `InstanceManager`

所有进程级 Manager 实现：

```java
public interface InstanceManager extends InstanceAccess {
    void init();
    void shutdown();
}
```

`InstanceAccess` 只提供：

```java
default Core core() {
    return Core.INSTANCE;
}
```

不要为没有生命周期的普通工具类实现 `InstanceManager`。

适合成为 Manager 的对象通常满足至少一项：

- 拥有跨功能共享的注册表。
- 持有进程级资源。
- 负责持久化状态。
- 需要明确 init/shutdown。
- 协调多个功能之间的公共服务。

不适合成为 Manager：

- 纯计算工具。
- 单一 Module 的私有状态。
- 只有一个静态方法的 helper。
- 没有所有权和生命周期的 facade。

## 3. Core 初始化事务

当前启动顺序：

```text
1. RenderManager
2. RotationManager
3. ModuleManager
4. NotificationManager
5. TargetManager
6. HudManager
7. ConfigManager
8. I18nManager
9. AccountManager
10. MusicManager
11. CommandManager
```

shutdown 使用相反顺序：

```text
1. CommandManager
2. MusicManager
3. AccountManager
4. I18nManager
5. ConfigManager
6. HudManager
7. TargetManager
8. NotificationManager
9. ModuleManager
10. RotationManager
11. RenderManager
```

Core 先用局部变量创建全部 Manager，全部成功后才提交到字段：

```java
RenderManager renders = new RenderManager();
RotationManager rotations = new RotationManager(Minecraft.getInstance());
ModuleManager modules = new ModuleManager();
TargetManager targets = new TargetManager(Minecraft.getInstance(), modules);
ConfigManager configs = new ConfigManager(nimbusDirectory, modules);

start(renders, started);
start(rotations, started);
start(modules, started);
start(targets, started);
start(configs, started);

renderManager = renders;
rotationManager = rotations;
moduleManager = modules;
targetManager = targets;
configManager = configs;
initialized = true;
configs.applyDeferredModuleStates();
```

重要限制：Manager 的 `init()` 执行期间，Core getter 尚未指向这些局部 Manager。

`ConfigManager.init()` 在这一阶段只解析配置并恢复 Setting、按键、隐藏状态等不依赖
Core getter 的数据。配置中的 Module enabled 状态会暂存，等全部 Manager 成功启动、
Core 字段完成提交且 `initialized == true` 后，由 `applyDeferredModuleStates()` 统一恢复。
运行期间手动加载命名配置仍会立即应用 enabled 状态。

错误方式：

```java
public void init() {
    Core.INSTANCE.getModuleManager(); // 可能尚未提交，直接抛异常
}
```

正确方式是构造器注入依赖：

```java
public final class ExampleManager implements InstanceManager {
    private final ModuleManager modules;

    public ExampleManager(ModuleManager modules) {
        this.modules = Objects.requireNonNull(modules, "modules");
    }
}
```

初始化失败时，Core 只逆序关闭已经成功启动的 Manager，并把后续 shutdown 异常作为 suppressed error 保留。Manager 不应吞掉阻止系统正常启动的错误。

## 4. 新增 Manager

### 4.1 基础模板

```java
package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;

public final class ExampleManager implements InstanceManager {
    private boolean initialized;

    @Override
    public synchronized void init() {
        if (initialized) return;
        try {
            // Allocate resources and register listeners.
            initialized = true;
        } catch (RuntimeException error) {
            shutdown();
            throw error;
        }
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) return;
        try {
            // Save or close owned resources.
        } finally {
            // Unregister listeners and clear state.
            initialized = false;
        }
    }
}
```

模板不是要求每个 Manager 都复制 `initialized`；应根据真实生命周期选择。但必须满足：

- `init()` 失败后不留下半初始化状态。
- `shutdown()` 释放自己拥有的资源。
- EventBus 注册和注销成对。
- 不关闭其他 Manager 拥有的对象。
- 对外集合返回不可变 snapshot。

### 4.2 注册到 Core

新增 Manager 需要显式完成：

1. 在 `Core` 添加字段。
2. 在 `Core.init()` 创建局部实例。
3. 按依赖顺序调用 `start(...)`。
4. 全部成功后赋值到字段。
5. 加入 `shutdown()` 列表；列表仍按启动顺序书写，由 Core 逆序关闭。
6. 添加使用 `requireInitialized(...)` 的 getter。

示例：

```java
private ExampleManager exampleManager;

public ExampleManager getExampleManager() {
    return requireInitialized(exampleManager, "ExampleManager");
}
```

不要把 Manager 放进静态 initializer，也不要在其他 Manager 字段初始化时从 Core 获取它。

## 5. 当前 Manager 职责

| Manager | 职责 | 主要依赖 |
|---|---|---|
| `RenderManager` | 持有 `SkijaRenderer`，初始化和关闭渲染子系统 | 无 |
| `RotationManager` | Rotation 请求裁决、平滑恢复、服务端角度、MovementFix、RayCast/模型使用的活动角度 | Minecraft client、EventBus |
| `ModuleManager` | Module 注册、索引、按键映射和自动启用 | EventBus |
| `NotificationManager` | Client/Game 通知队列、生命周期与公共 post API；渲染由 `ui.notification` 表现层负责 | NotificationRenderer、EventBus |
| `TargetManager` | 每 Tick 构建不可变候选快照和范围缓存，集中应用 Target、Teams、AntiBot 过滤 | ModuleManager、Minecraft client、EventBus |
| `HudManager` | HUD 元素注册、编辑状态和拖拽布局 | ModuleManager、RenderManager |
| `ConfigManager` | 命名 Module 配置、全局 Client Settings、自动保存和配置文件切换 | ModuleManager、EventBus |
| `I18nManager` | Nimbus 独立语言选择、翻译加载、英文回退和参数格式化 | ClientSettings、Minecraft LanguageManager |
| `AccountManager` | Alt 账户列表、最后账户、登录与账户文件 | Minecraft client |
| `MusicManager` | 网易云账号、歌曲数据、播放队列、音频/图片缓存、播放器和系统媒体控制生命周期 | EventBus、Minecraft client |
| `CommandManager` | Command 注册、执行、建议和聊天拦截 | EventBus |

Manager 之间只能通过明确依赖协作。不要把所有 Manager 互相注入，也不要让 Core 变成业务中转站。

Rotation 请求由调用方对象作为 owner 提交。Manager 每 Tick 选择最高优先级请求，同优先级采用最后提交者；调用方禁用时必须按 owner 取消。没有活动请求时，Manager 平滑恢复到玩家真实视角，并在世界切换、玩家为空和 shutdown 时清空请求与临时旋转状态。

TargetManager 不暴露可变集合。Combat Module 只能通过 `getCandidates()`、`getTargets(range)` 和 `isTarget(entity)` 使用统一目标语义，不能自行复制世界扫描和 Teams/AntiBot 判断。

## 6. Module 注册

### 6.1 创建 Module

```java
@ModuleInfo(
        name = "Example",
        displayName = "Example",
        description = "Does one clear thing.",
        category = ModuleCategory.Player,
        keyBind = 0,
        enableOnStartUp = false
)
public final class Example extends BasicModule {
    public final BooleanSetting option = new BooleanSetting("Option", true);

    @EventTarget
    private void onTick(EventTick event) {
        // This listener is active only while the module is enabled.
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
```

`@ModuleInfo` 必须存在。缺少注解时 `BasicModule` 构造器会立即失败。

### 6.2 注册 Module

当前 Module 使用显式注册，不扫描 classpath：

```java
registerModules(
        new ExampleModule(),
        new AnotherModule()
);
```

在 `ModuleManager.init()` 的 `registerModules(...)` 中加入新实例。

显式注册的优点：

- 启动顺序可见。
- 构造失败位置明确。
- 不依赖包扫描和反射类加载。
- 重复 class、name 或 alias 会立即失败。

`ModuleManager` 自动建立：

- `Class -> Module`。
- `Category -> Modules`。
- `name/alias -> Module`，忽略大小写。
- `key -> Modules`。

不要在其他位置维护第二套 Module 列表。

### 6.3 Module EventBus 生命周期

`BasicModule.setEnabled(true)` 自动执行：

```text
EventBus.register(module)
onEnable()
notify config change
```

`setEnabled(false)` 自动执行：

```text
EventBus.unregister(module)
onDisable()
notify config change
```

因此普通 Module 不需要在 `onEnable/onDisable` 手动注册或注销自身。

启停是事务性的：

- `onEnable()` 失败时，Module 恢复为 disabled，注销已经注册的监听器，并执行必要的 `onDisable()` 清理。
- `onDisable()` 失败时，EventBus 注销和配置变更通知仍会继续执行；多个失败通过 suppressed exception 保留。
- `ClickGui` 这类在 `onEnable()` 中立即关闭自身的一次性 Module 仍受支持；启用成功后只有当前仍为 enabled 才发送常规变更通知。
- Module 自己不得捕获启停异常后留下半启用状态，也不得再次手动维护自己的 EventBus 注册。

## 7. Setting 自动发现

Module 中的非静态 `Setting<?>` 字段会自动发现：

```java
public final BooleanSetting enabled = new BooleanSetting("Enabled", true);
public final NumberSetting range = new NumberSetting("Range", 3, 1, 6, 0.1);
public final ModeSetting mode = new ModeSetting("Mode", new String[]{"A", "B"}, "A");
```

不需要：

```java
addSettings(enabled, range, mode);
```

自动发现规则：

- 遍历从 `BasicModule` 到具体 Module 的类层级。
- 接受所有非静态、类型继承 `Setting` 的字段。
- 使用声明顺序形成 UI/配置顺序。
- 同一个 Setting 实例只加入一次。
- 标注 `@Exclude` 的字段跳过。
- 结果缓存为不可变 List。

`addSetting(...)` 只用于无法作为字段声明的动态 Setting。普通 Module 不应使用它替代字段自动发现。

Setting 变化会通过 `bindStateListeners(...)` 通知 ModuleManager 的 change listener；ConfigManager 正是通过这条链路自动标记 dirty。

Setting 构造器中的 `name` 是稳定配置键，不得传入翻译结果：

```java
public final BooleanSetting showSuffix = new BooleanSetting("Show suffix", true);
```

`BasicModule` 在自动发现 Setting 时同时绑定翻译作用域。显示层使用 `Setting.getTranslationKey()`，Mode 和 MultiSelect 选项使用 `getOptionTranslationKey(...)`。例如：

```text
nimbus.module.array_list.setting.show_suffix
nimbus.client.setting.blur.option.gaussian
```

多个动态 Setting 共用同一组选项时，可在加入 Module 前通过
`bindOptionTranslationScope(...)` 绑定共享选项作用域，避免为每个动态 Setting 重复维护相同翻译。

配置仍保存 `Show suffix` 和 `Gaussian`，切换语言不会改变配置身份或业务判断。

新增 Setting 类型时，至少检查：

- `Setting.normalize(...)`。
- UI 是否支持编辑。
- `ConfigManager.writeSetting(...)`。
- `ConfigManager.deserializeSettings(...)`。
- Change listener 是否会触发。

## 8. ConfigManager

### 8.1 保存路径

Fabric config directory 下：

```text
nimbus/
├─ client.json
├─ configs/
│  ├─ default.json
│  └─ <profile>.json
├─ accounts/
   ├─ accounts.json
│  └─ last.json
└─ music/
   ├─ music.json
   └─ cache/
      └─ index.json
```

Client Settings、Module 配置与 Account 配置是独立领域：

- `ConfigManager` 管 `client.json`，保存 Language、Client Name、Style、Accent 和完整后处理参数。
- `ConfigManager` 管 `configs/*.json`。
- `AccountManager` 管 `accounts/accounts.json` 和 `accounts/last.json`。

命名 Config 不包含 Client Settings，切换命名 Config 不改变全局视觉。不要把账户 token 或音乐 cookie 混入 Module config。

### 8.2 自动保存链路

```text
Module enabled/key/hidden change
or Setting.setValue(...)
  -> Module changeListener
    -> ConfigManager.markDirty()
      -> EventTick.POST after 800 ms
        -> saveConfig(currentConfig)
```

Client Settings 使用独立 dirty 状态和同样的 800 ms 延迟保存链路，目标文件固定为 `nimbus/client.json`。两个 dirty 状态不能合并，否则保存命名 Config 会意外改变全局设置的持久化语义。

加载配置期间 `loading == true`，Setting 变化不会再次标 dirty。

保存使用同目录临时文件并优先原子替换：

```text
profile.json.tmp
  -> ATOMIC_MOVE / fallback REPLACE_EXISTING
  -> profile.json
```

不要改成直接覆盖目标文件，否则中断写入可能损坏唯一配置。

### 8.3 当前保存内容

每个 Module 保存：

- `enabled`
- `key`
- `hidden`
- `settings`
- `draggables`：每个 `Draggable` 的归一化 `x`、`y` 和组件 `scale`

当前反序列化支持：

- `BooleanSetting`
- `NumberSetting`
- `ModeSetting`
- `ColorSetting`
- `KeySetting`（以 `{ "type": "KEYBOARD|MOUSE|NONE", "code": number }` 保存）
- `FoldSetting`
- `TextSetting`
- `MultiSelectSetting`

配置读取按 Module 和 Setting 名称忽略大小写。损坏的单个条目会记录错误并跳过，不应让整个文件中其他正常条目失效。

配置名称只允许 sanitize 后不发生变化的安全字符；不要把未经检查的输入拼进 Path。

### 8.4 I18N 与语言切换

Nimbus 使用 `I18nManager` 独立于 Minecraft 切换语言，选项为 Follow Game、English 和 Simplified Chinese。选择保存在 `client.json`，修改后下一帧生效，不需要重启或重开 ClickGUI。

语言资源位于：

```text
assets/nimbus/lang/en_us.json
assets/nimbus/lang/zh_cn.json
```

`en_us` 是完整基线；当前语言缺少 key 时先回退英文，再回退调用处提供的原始文案。Manager 复用 Minecraft 的 `Language.loadFromJson(...)` 解析标准语言 JSON，不维护另一种文件格式。

Skija 显示层通过 `UiContext.translate(...)` 获取字符串。原版 Widget 在 Nimbus 独立语言模式下使用：

```java
Component.literal(Core.INSTANCE.getI18nManager().get(key, fallback));
```

不要使用 `Component.translatable(...)` 读取 Nimbus 独立语言；它始终服从 Minecraft 当前语言。歌名、歌手、歌词、用户名等服务端内容不经过本地翻译。

### 8.5 ClickGUI 与 Client Settings

`ClickGui` Module 持有进程级 `ClickGuiSession`，默认绑定为右 Shift。Session 只保存六列滚动、各列展开项、当前工具窗口和 Music 路由，不写入配置，也不升级为 Manager。关闭 Screen 时清除搜索、输入焦点、绑定监听、登录敏感字段、确认状态和异步页面数据。

`ClickGuiScreen` 继承 `AbstractSkijaScreen`；`ClickGuiView` 负责六列布局、统一逻辑坐标和输入分发。六列固定为 Combat、Move、Player、Render、Exploit、HUD。设置控件统一由 `SettingControls` 渲染，Config、Client Settings 和 Module 不能各自复制 Setting 表单。

Music 工具窗口只调用 `MusicManager`。网络、播放器、队列、缓存和账号状态不进入 `ui.clickgui`。异步结果必须调度回 Minecraft 主线程，并通过页面 generation 丢弃关闭后的迟到结果。

## 9. Command 注册

### 9.1 创建 Command

```java
public final class ExampleCommand extends BasicCommand {
    public ExampleCommand() {
        super("example", "Example command", "ex");
    }

    @Override
    public void execute(String[] args) {
        // Validate argument count and report useful errors.
    }

    @Override
    public List<String> suggest(String[] args) {
        return List.of("first", "second");
    }
}
```

### 9.2 注册 Command

在 `CommandManager.init()` 显式添加：

```java
register(new ExampleCommand());
```

Manager 会：

- 校验空 name/alias。
- 拒绝重复 name/alias。
- 建立忽略大小写的索引。
- 按 command name 排序。
- 拦截以 `.` 开头的聊天消息。
- 根据当前 token 位置请求 command suggestion。

参数解析使用 `CommandLine.parse(...)`，支持空格、单双引号和反斜杠转义。不要在每个 Command 再用 `split(" ")`。

## 10. EventBus 注册

方法监听器：

```java
@EventTarget(priority = Priority.Normal)
private void onTick(EventTick event) {
}
```

字段监听器：

```java
@EventTarget
private final Listener<EventTick> tick = event -> {
};
```

有效监听器必须：

- 返回 `void`。
- 只有一个参数。
- 参数继承 `Cancelable`。
- 使用具体事件 class；EventBus 按 `event.getClass()` 精确分发。

Manager 在 `init()` 注册自身，在 `shutdown()` 注销。Module 由 enabled 状态自动管理。

事件被取消后，后续 listener 不再执行。Priority 数值越小越先执行：

```text
Highest -> High -> Normal -> Low -> Lowest
```

不要在 constructor 中注册 EventBus；对象可能尚未完成构造，也没有明确注销边界。

当前异常策略：

- 方法监听器和字段监听器使用同一分发包装。
- 注册过程中任意监听器失败，会撤销该 subscriber 已经注册的全部监听器。
- 普通 `RuntimeException` 会记录事件类型、subscriber 身份和处理方法，然后继续分发后续 listener。
- `Error` 视为致命错误并继续抛出；`InterruptedException` 会恢复线程中断标记。
- EventBus 按事件的实际 class 精确分发，不自动向父事件类型冒泡。

## 11. AccountManager

AccountManager 管理账户列表和最后使用账户：

```java
AccountManager accounts = Core.INSTANCE.getAccountManager();
accounts.addAccount(account);
accounts.removeAccount(account);
accounts.setLastAccount(account);
accounts.login(account, callback, status);
```

规则：

- `getAccounts()` 返回不可变 snapshot。
- 身份由 account type 和忽略大小写的 username 共同确定。
- Microsoft 登录在专用线程进行。
- Minecraft User 替换通过 client execute 回到 Minecraft 线程。
- 不实现未被要求的 legacy file migration。
- token 和账户数据只由 AccountManager 持久化。
- 离线账户 UUID 使用 `OfflinePlayer:<username>` 生成稳定 name-based UUID。
- Token/Microsoft 账户必须从服务端取得并验证真实 UUID；缺失或非法 UUID 会明确登录失败，不能退回所有账户共用的固定 UUID。
- OAuth 本地 callback server 在成功、失败、取消和浏览器启动失败后都必须关闭；重复登录先取消旧流程。

## 12. RenderManager

RenderManager 是 Skija 的唯一进程入口：

```java
SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
```

功能不得创建自己的 Renderer。详细渲染规则见 `docs/skija-architecture.md`。

## 13. MusicManager

音乐子系统的唯一进程入口是：

```java
MusicManager music = Core.INSTANCE.getMusicManager();
```

功能代码只通过 `MusicManager` 搜索、播放、控制队列、读取歌词、管理账号和获取封面。不要直接实例化 `NeteaseApi`、`AudioCache`、`ArtworkCache` 或 `PlaybackController`；这些对象由 `MusicManager` 创建、共享并在 shutdown 时统一释放。

音乐数据位于 Fabric config directory 下：

```text
nimbus/music/
├─ music.json
├─ login-qr.png
└─ cache/
   └─ index.json
```

- `music.json` 保存 cookie、音质、音量、倍速、播放模式、预下载、缓存上限和组合键。
- `login-qr.png` 仅在二维码登录请求后生成，退出登录时删除。
- `cache/` 保存下载或转换后的音频；`index.json` 原子记录歌曲快照、可用质量、总大小和最后使用时间。
- `cachedSongs()` 只枚举索引中仍有有效 WAV 的项目；`playCached(...)` 使用缓存快照构建 cache-only 队列，不请求歌曲元数据或音频 URL。
- 网易云请求、文件 IO 和播放分别使用 Manager 拥有的线程资源；调用者不得自行关闭这些资源。
- 系统媒体控制通过 `systemMediaState()`、`systemMedia(...)` 和 `systemMediaSeek(...)` 暴露；平台不支持时由 `SystemMediaControllers` 返回安全的 unsupported controller，UI 不得直接调用 WinRT/JNA。
- `MusicManager.shutdown()` 关闭系统媒体 controller、播放器 executor、IO executor、ArtworkCache 和 AudioCache。
- 当前 `.music` 指令是功能验证入口，不应复制一套音乐状态到 Command 内。

常用异步入口：

```java
music.dailyRecommendedSongs();
music.dailyRecommendedPlaylists();
music.personalizedPlaylists(12);
music.featuredPlaylists(40);
music.intelligentSongs(songId, playlistId, startMusicId, count);
music.playIntelligent(songId, playlistId, count);
music.cachedSongs();
music.cache(song);
music.removeCached(songId);
music.playCached(songIds, startIndex);
music.requestSmsLogin(phone, countryCode);
music.completeSmsLogin(captcha);
```

短信登录的手机号、国家码和验证码会话只存在于内存，不写入配置。二维码与短信登录共用 Cookie 验证和账号资料刷新流程；只有账号验证成功后才持久化 Cookie。

## 14. 对外 API 约定

Manager 对外 API 应遵循：

- 查找失败且允许不存在时返回 `null` 或 `Optional`，保持同一 Manager 内一致。
- 非法注册、重复 name/class 属于编程错误，立即抛异常。
- 用户输入导致的失败返回明确状态并输出可理解消息。
- 集合返回 `List.copyOf(...)`，不暴露可变内部集合。
- 修改操作集中在 Manager，不允许调用者直接修改 registry。
- 需要线程安全的共享状态使用同步边界，不让半更新索引泄漏。

## 15. AI 修改检查表

新增 Manager 前：

- 是否真的拥有共享状态或生命周期？
- 是否可以放在现有 Manager？
- 依赖是否能构造器注入？
- init 失败是否可回滚？
- shutdown 顺序是否正确？

新增 Module 前：

- 是否有 `@ModuleInfo`？
- 是否加入 `ModuleManager.init()`？
- Setting 是否声明为字段以便自动发现？
- 是否错误地手动注册 EventBus？
- 新 Setting 类型是否支持 config/UI？

新增 Command 前：

- 是否复用 `CommandLine.parse()`？
- 是否加入 `CommandManager.init()`？
- alias 是否冲突？
- suggestion 是否按当前参数位置工作？

修改 Core 前：

- 是否保持局部创建、成功后提交？
- 是否保持依赖启动顺序？
- 是否加入逆序 shutdown？
- 是否避免 Manager init 期间调用未提交 Core getter？

## 16. 验证

至少执行：

```powershell
.\gradlew.bat compileJava --console=plain
git diff --check
```

涉及生命周期或注册时人工验证：

- Core 重复 init/shutdown 不产生残留 listener。
- Manager init 失败能关闭已启动 Manager。
- Module name、alias 和 class 冲突会明确失败。
- Module enable/disable 正确注册和注销事件。
- Setting 变化会触发延迟自动保存。
- default config 首次启动能创建，后续能加载。
- 配置写入中断不会破坏原文件。
- Command 执行、引号解析和 Tab suggestion 正常。
- Account 保存、删除和最后账户一致。
- MusicManager 搜索、登录、播放控制和缓存操作能通过 `.music` 指令执行。
- MusicManager shutdown 后停止播放、保存配置并关闭自有线程和 HTTP 资源。
- RenderManager shutdown 后没有重复关闭错误。
