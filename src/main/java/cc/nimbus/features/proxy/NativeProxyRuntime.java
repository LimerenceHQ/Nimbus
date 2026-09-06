package cc.nimbus.features.proxy;

import cc.nimbus.managers.ProxyManager;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Locale;

public final class NativeProxyRuntime {
    private NativeProxyRuntime() {}

    public static void inject(ProxyManager.TargetProcess target, Path library, int port) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "");
        if (!os.startsWith("windows") || !(arch.equals("amd64") || arch.equals("x86_64")))
            throw new IOException("A 端 DLL 需要 Windows x64；连接已加载的 A 不依赖本地 DLL");
        ProxyManager.validateTarget(target);
        Class<?> vmClass;
        try { vmClass = Class.forName("com.sun.tools.attach.VirtualMachine"); }
        catch (ClassNotFoundException | LinkageError error) {
            throw new IOException("Nimbus 的 Java 运行时缺少 jdk.attach，请选择包含该模块的 Java 21+ 运行时", error);
        }
        Object vm = null;
        Exception failure = null;
        try {
            vm = vmClass.getMethod("attach", String.class).invoke(null, Long.toString(target.pid()));
            vmClass.getMethod("loadAgentPath", String.class, String.class)
                    .invoke(vm, library.toAbsolutePath().toString(), "port=" + port);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            failure = stageFailure(target.pid(), vm != null, cause);
            throw failure;
        } finally {
            if (vm != null) {
                try { vmClass.getMethod("detach").invoke(vm); }
                catch (ReflectiveOperationException error) {
                    if (failure != null) failure.addSuppressed(error);
                    else throw new IOException("A 已加载，但 Attach 会话释放失败；请检查 A 后使用直接连接", error);
                }
            }
        }
    }
    public static IOException stageFailure(long pid, boolean attached, Throwable cause) {
        String phase = attached ? "DLL 加载/初始化" : "Attach（尚未加载 DLL）";
        return new IOException("A 端 " + phase + "失败，PID " + pid + "：" + describeFailure(cause), cause);
    }
    private static String describeFailure(Throwable cause) {
        if (cause.getClass().getName().equals("com.sun.tools.attach.AgentInitializationException")) {
            try {
                int code = (int) cause.getClass().getMethod("returnValue").invoke(cause);
                String detail = switch (code) {
                    case 10 -> "监听端口参数无效";
                    case 11 -> "A 的 JNI/JVMTI 环境初始化失败";
                    case 12 -> "A 尚未加载兼容的 Minecraft 类；请确认目标进程和映射";
                    case 13 -> "A 的 Netty 转发处理器初始化失败";
                    case 14 -> "A 的 Connection Hook 初始化失败";
                    case 15 -> "A 监听器启动失败；请检查端口占用和日志";
                    case 16 -> "A 已在其他端口监听；请使用原端口直接连接";
                    default -> "A 原生初始化异常";
                };
                return detail + " (code " + code + ")";
            } catch (ReflectiveOperationException ignored) { }
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
