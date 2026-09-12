package cc.nimbus.features.proxy;

import cc.nimbus.managers.ProxyManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class NativeProcessScanner {
    private static final String RESOURCE = "/assets/nimbus/native/windows-x86_64/nimbus_controller.dll";
    private static volatile boolean loaded;
    private NativeProcessScanner() {}

    public record Snapshot(ProxyManager.TargetProcess target, int queryError) {}

    public static boolean supported() {
        String arch = System.getProperty("os.arch", "");
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                && (arch.equalsIgnoreCase("amd64") || arch.equalsIgnoreCase("x86_64"));
    }

    public static long[] discover(Path directory) throws IOException {
        return ensureLoaded(directory) ? discoverNative() : new long[0];
    }

    public static Optional<Snapshot> inspect(long pid, Path directory) throws IOException {
        if (pid <= 0 || pid > 0xffff_ffffL) throw new IOException("PID 超出 Windows 进程号范围");
        if (!ensureLoaded(directory)) return Optional.empty();
        String[] data = inspectNative(pid);
        return data == null ? Optional.empty() : Optional.of(parseSnapshot(pid, data));
    }

    public static Snapshot parseSnapshot(long pid, String[] data) throws IOException {
        try {
            if (data.length != 3 || data[0] == null || data[0].isBlank()) throw new IllegalArgumentException();
            long ticks = Long.parseLong(data[1]);
            int error = Integer.parseInt(data[2]);
            if (ticks < 0 || error < 0) throw new IllegalArgumentException();
            Instant started = ticks == 0 ? Instant.EPOCH : Instant.ofEpochSecond(
                    ticks / 10_000_000L - 11_644_473_600L, (ticks % 10_000_000L) * 100L);
            return new Snapshot(new ProxyManager.TargetProcess(pid, started, data[0]), error);
        } catch (RuntimeException invalid) {
            throw new IOException("进程扫描模块返回的数据无效", invalid);
        }
    }

    private static synchronized boolean ensureLoaded(Path directory) throws IOException {
        if (loaded) return true;
        if (directory == null || !supported()) return false;
        try (InputStream input = NativeProcessScanner.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Nimbus 未打包只读进程扫描 DLL，请重新构建");
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0) throw new IOException("进程扫描 DLL 资源为空");
            Path dir = directory.toAbsolutePath().resolve("native");
            Files.createDirectories(dir);
            Path dll = dir.resolve(UUID.randomUUID().toString().replace("-", "") + ".dll");
            Path temp = Files.createTempFile(dir, "scanner-", ".tmp");
            try {
                Files.write(temp, bytes);
                try { Files.move(temp, dll, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, dll, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            System.load(dll.toString());
            loaded = true;
            return true;
        } catch (LinkageError error) {
            throw new IOException("进程扫描模块加载失败：" + error.getMessage(), error);
        }
    }
    public static int runElevated(Path directory,Path java,String[] arguments,Path workingDirectory)throws IOException{
        if(!ensureLoaded(directory))throw new IOException("管理员加载模块未就绪");
        return runElevatedNative(java.toString(),arguments,workingDirectory.toString());
    }
    private static native int runElevatedNative(String java,String[] arguments,String directory)throws IOException;
    private static native long[] discoverNative() throws IOException;
    private static native String[] inspectNative(long pid) throws IOException;
}
