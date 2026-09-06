# Nimbus B 端控制与连接

## 运行模型

Nimbus 是 B 端控制器和客户端，选中的独立 Minecraft Java 进程是 A 端。A 端加载 `nimbus_proxy.dll` 并监听 loopback，Nimbus 通过 Minecraft 连接界面连接 `127.0.0.1:25565`。

- Nimbus 不在本地加载 A 端代理 DLL，也不在启动时创建监听器。
- 普通权限通过 JDK Attach 将 DLL 加载到 A；管理员模式通过一次性 UAC helper 完成同一 Attach 操作。
- `nimbus_controller.dll` 只负责 B 端的 Windows 进程发现和管理员启动。
- A 端监听器的生命周期属于 A 进程；关闭 B 不会卸载 A 的代理。
- 一个 A 端监听器同时接受一个 B 端连接。
- Tab 处理保留 Nimbus 自己的身份和数据路径，不使用 SakuraTool 的 UUID 镜像逻辑。

## 使用入口

1. 启动独立的 Minecraft 客户端作为 A，并进入服务器或世界。
2. 启动 Nimbus，在主菜单打开 `Proxy / 连接 A`。
3. 选择扫描到的 A 进程，或手动输入 PID；确认监听端口，默认值为 `25565`。
4. 普通模式使用加载并连接；需要提升权限时切换管理员模式并确认 UAC。
5. A 已经加载代理时，可以使用直接连接，不会重复 Attach。

实际登录结果由 Minecraft 连接界面显示，控制器不会仅凭本地请求把状态标记为已连接。

## 构建

Windows 默认构建并打包两个原生 DLL 和 Attach helper：

```powershell
.\gradlew.bat build --console=plain
```

可以通过 `-PcmakeExecutable=...` 指定 CMake，或使用 `-PbundleNativeProxy=false` 构建不含原生资源的 B 端客户端。

主要产物：

- `build/native/proxy/Release/nimbus_proxy.dll`
- `build/native/controller/Release/nimbus_controller.dll`
- `build/libs/nimbus-1.0.jar`

原生资源由 Gradle 在构建目录生成并打入 JAR，不写回 `src/main/resources`。

## 运行条件

- Windows x64。
- B 端运行时需要包含 `jdk.attach` 的 Java 21 或更高版本。
- A、B 必须运行在同一台 Windows 主机上。
- A 端 Minecraft 版本和映射必须与原生模块支持的类名、方法描述符匹配。

常见错误码：`10` 表示端口参数错误，`11` 表示 JNI/JVMTI 初始化失败，`12` 表示 Minecraft 类尚未就绪，`13` 表示 Netty 转发初始化失败，`14` 表示 Connection hook 失败，`15` 表示监听器绑定失败，`16` 表示 A 已在其他端口运行。

A 端日志优先写入 `%APPDATA%\\.minecraft\\nimbus-proxy.log`，无法写入时回退到临时目录。
