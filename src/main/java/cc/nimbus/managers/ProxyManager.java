package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.features.proxy.ElevatedProxyRuntime;
import cc.nimbus.features.proxy.NativeProcessScanner;
import cc.nimbus.features.proxy.NativeProxyRuntime;
import cc.nimbus.features.proxy.ProxyPayload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProxyManager implements InstanceManager {
    public enum State { IDLE, ATTACHING, READY, CONNECT_REQUESTED, FAILED, STOPPED }
    public record Status(State state, String detail) {}
    public record TargetProcess(long pid, Instant startedAt, String executable) {
        public TargetProcess {
            if (pid <= 0) throw new IllegalArgumentException("Invalid target PID");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(executable, "executable");
        }
        public boolean hasKnownStartTime() { return !Instant.EPOCH.equals(startedAt); }
        public static TargetProcess fromPid(long pid) throws IOException {
            return targetFromPid(pid, null);
        }
        public String label() { return executable + " (PID " + pid + ")"; }
    }
    public record Endpoint(String host, int port) {
        public Endpoint {
            Objects.requireNonNull(host, "host");
            validatePort(port);
        }
        public String address() { return host + ":" + port; }
    }

    @FunctionalInterface interface TargetLoader { void load(TargetProcess target, Path payload, int port) throws Exception; }
    @FunctionalInterface interface PayloadSource { Path extract() throws IOException; }
    @FunctionalInterface interface TargetValidator { void validate(TargetProcess target) throws IOException; }

    @FunctionalInterface interface TargetResolver { TargetProcess resolve(long pid) throws IOException; }
    @FunctionalInterface private interface TargetRequest { TargetProcess resolve() throws IOException; }

    private final TargetResolver resolver;
    private final TargetLoader loader;
    private final PayloadSource payload;
    private final TargetValidator validator;
    private final Path nimbusDirectory;
    private ExecutorService worker;
    private CompletableFuture<Endpoint> pending;
    private long generation;
    private volatile Status status = new Status(State.STOPPED, "B 端控制器尚未初始化");

    public ProxyManager(Path nimbusDirectory) {
        this(NativeProxyRuntime::inject, () -> ProxyPayload.extractBundled(nimbusDirectory),
                target -> validateTarget(target, nimbusDirectory),
                pid -> targetFromPid(pid, nimbusDirectory), nimbusDirectory);
    }

    ProxyManager(TargetLoader loader, PayloadSource payload, TargetValidator validator) {
        this(loader, payload, validator, TargetProcess::fromPid);
    }

    ProxyManager(TargetLoader loader, PayloadSource payload, TargetValidator validator, TargetResolver resolver) {
        this(loader, payload, validator, resolver, null);
    }

    private ProxyManager(TargetLoader loader, PayloadSource payload, TargetValidator validator,
                         TargetResolver resolver, Path nimbusDirectory) {
        this.nimbusDirectory = nimbusDirectory;
        this.resolver = Objects.requireNonNull(resolver);
        this.loader = Objects.requireNonNull(loader);
        this.payload = Objects.requireNonNull(payload);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override public synchronized void init() {
        if (worker != null) return;
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Nimbus-ProxyController");
            thread.setDaemon(true);
            return thread;
        });
        generation++;
        status = new Status(State.IDLE, "Nimbus 是 B 端；输入 A 的 PID，或直接连接已加载的 A");
    }

    public Status status() { return status; }
    public State state() { return status.state(); }
    public boolean isBusy() { return state() == State.ATTACHING; }

    public List<TargetProcess> targets() {
        long self = ProcessHandle.current().pid();
        if (nimbusDirectory != null && NativeProcessScanner.supported()) {
            try {
                var targets = new java.util.ArrayList<TargetProcess>();
                for (long pid : NativeProcessScanner.discover(nimbusDirectory)) {
                    if (pid == self) continue;
                    var info = NativeProcessScanner.inspect(pid, nimbusDirectory);
                    if (info.isPresent() && isJavaExecutable(info.get().target().executable()))
                        targets.add(info.get().target());
                }
                return List.copyOf(targets);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
        try (var processes = ProcessHandle.allProcesses()) {
            return processes.filter(p -> p.pid() != self && p.isAlive()).flatMap(p -> {
                try { return discoveredTarget(p.pid(), p.info()).stream(); }
                catch (SecurityException error) { return java.util.stream.Stream.empty(); }
            }).sorted(Comparator.comparingLong(TargetProcess::pid)).toList();
        }
    }

    public CompletableFuture<Endpoint> preparePid(long pid, int port) {
        if (pid <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("请输入大于 0 的 PID"));
        return submit(pid, port, () -> resolver.resolve(pid));
    }

    public static long parsePid(String text) {
        String value = text == null ? "" : text.strip();
        try {
            if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) throw new NumberFormatException();
            long pid = Long.parseLong(value);
            if (pid <= 0) throw new NumberFormatException();
            return pid;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("请输入有效的 A 进程 PID（正整数）");
        }
    }

    public CompletableFuture<Endpoint> prepare(TargetProcess target, int port) {
        Objects.requireNonNull(target, "target");
        return submit(target.pid(), port, () -> target);
    }

    public CompletableFuture<Endpoint> preparePidElevated(long pid,int port) {
        if(pid<=0)return CompletableFuture.failedFuture(new IllegalArgumentException("请输入有效 PID"));
        return submit(pid,port,()->resolver.resolve(pid),true);
    }
    public CompletableFuture<Endpoint> prepareElevated(TargetProcess target,int port) {
        Objects.requireNonNull(target);return submit(target.pid(),port,()->target,true);
    }
    private CompletableFuture<Endpoint> submit(long pid,int port,TargetRequest request){return submit(pid,port,request,false);}
    private synchronized CompletableFuture<Endpoint> submit(long pid, int port, TargetRequest request,boolean elevated) {
        validatePort(port);
        if (worker == null) return CompletableFuture.failedFuture(new IllegalStateException("B 端控制器已停止"));
        if (isBusy()) return CompletableFuture.failedFuture(new IllegalStateException("已有 A 端加载任务正在执行"));
        if (pid == ProcessHandle.current().pid())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Nimbus 是 B 端，请选择另一个 A 进程"));
        long attempt = ++generation;
        var result = new CompletableFuture<Endpoint>();
        pending = result;
        status = new Status(State.ATTACHING, elevated ? "请确认 Windows 管理员授权；仅提升加载助手，PID "+pid : "正在向 A (PID " + pid + ") 加载转发模块");
        worker.execute(() -> {
            try {
                TargetProcess target = request.resolve();
                if (target.pid() != pid) throw new IOException("A 进程标识已变化，请重新输入 PID");
                validator.validate(target);
                Path library = payload.extract();
                validator.validate(target);
                synchronized (this) {
                    if (attempt != generation) return;
                }
                if(elevated)ElevatedProxyRuntime.inject(target,library,port,nimbusDirectory);
                else loader.load(target, library, port);
                synchronized (this) {
                    if (attempt != generation) return;
                    status = new Status(State.READY, "A 的监听器已就绪：127.0.0.1:" + port);
                    pending = null;
                }
                result.complete(new Endpoint("127.0.0.1", port));
            } catch (Exception | LinkageError error) {
                synchronized (this) {
                    if (attempt != generation) return;
                    status = new Status(State.FAILED, message(error));
                    pending = null;
                }
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public synchronized Endpoint requestConnection(int port) {
        if (worker == null || isBusy()) throw new IllegalStateException("请等待 A 端加载完成");
        Endpoint endpoint = new Endpoint("127.0.0.1", port);
        status = new Status(State.CONNECT_REQUESTED, "已请求 B → A 连接；登录结果由 Minecraft 连接界面显示");
        return endpoint;
    }

    @Override public synchronized void shutdown() {
        generation++;
        if (pending != null) pending.completeExceptionally(new IllegalStateException("B 端控制器已关闭"));
        pending = null;
        if (worker != null) worker.shutdownNow();
        worker = null;
        status = new Status(State.STOPPED, "B 端控制器已关闭；A 进程保持独立运行");
    }

    public static void validateTarget(TargetProcess target) throws IOException {
        validateTargetShape(target);
    }

    private static void validateTarget(TargetProcess target, Path directory) throws IOException {
        validateTargetShape(target);
        TargetProcess current = targetFromPid(target.pid(), directory);
        if (target.hasKnownStartTime() && !target.startedAt().equals(current.startedAt()))
            throw new IOException("A 进程身份已变化或启动时间读取失败，请重新输入 PID");
        if (!current.executable().equals("<PID 已指定>") && !isJavaExecutable(current.executable()))
            throw new IOException("A 必须是 Java 客户端进程，当前为 " + current.executable());
    }

    private static void validateTargetShape(TargetProcess target) throws IOException {
        Objects.requireNonNull(target, "target");
        if (target.pid() > 0xffff_ffffL) throw new IOException("PID 超出 Windows 进程号范围");
        if (target.pid() == ProcessHandle.current().pid()) throw new IOException("请选择独立于 Nimbus 的 A 进程");
        if (!target.executable().equals("<PID 已指定>") && !isJavaExecutable(target.executable()))
            throw new IOException("A 必须是 Java 客户端进程，当前为 " + target.executable());
    }

    static TargetProcess targetFromPid(long pid, Path directory) throws IOException {
        if (pid <= 0) throw new IllegalArgumentException("请输入大于 0 的 PID");
        if (NativeProcessScanner.supported()) {
            var nativeInfo = NativeProcessScanner.inspect(pid, directory);
            if (nativeInfo.isPresent()) return nativeInfo.get().target();
        }
        ProcessHandle process = ProcessHandle.of(pid).orElseThrow(() ->
                new IOException("当前查询未返回 PID " + pid + "；进程可能已退出或查询权限不足"));
        if (!process.isAlive()) throw new IOException("A 进程已退出：PID " + pid);
        return snapshot(pid, process.info());
    }

    private static TargetProcess snapshot(long pid, ProcessHandle.Info info) {
        return new TargetProcess(pid, info.startInstant().orElse(Instant.EPOCH),
                executableName(info).orElse("<PID 已指定>"));
    }

    static Optional<TargetProcess> discoveredTarget(long pid, ProcessHandle.Info info) {
        if (!executableName(info).filter(ProxyManager::isJavaExecutable).isPresent()) return Optional.empty();
        return Optional.of(snapshot(pid, info));
    }

    static void validateMetadata(TargetProcess target, ProcessHandle.Info info) throws IOException {
        if (target.hasKnownStartTime() && !info.startInstant().filter(target.startedAt()::equals).isPresent())
            throw new IOException("A 进程身份已变化或启动时间读取失败，请重新输入 PID");
        Optional<String> executable = executableName(info);
        if (executable.isPresent() && !isJavaExecutable(executable.get()))
            throw new IOException("A 必须是 Java 客户端进程，当前为 " + executable.get());
    }

    private static Optional<String> executableName(ProcessHandle.Info info) {
        Optional<String> command = info.command().filter(value -> !value.isBlank());
        if (command.isPresent()) return Optional.of(fileName(command.get()));
        return info.commandLine().filter(value -> !value.isBlank()).flatMap(line -> {
            String value = line.strip();
            String token;
            if (value.startsWith("\"")) {
                int end = value.indexOf('"', 1);
                if (end <= 1) return Optional.empty();
                token = value.substring(1, end);
            } else {
                int end = 0;
                while (end < value.length() && !Character.isWhitespace(value.charAt(end))) end++;
                token = value.substring(0, end);
            }
            return token.isBlank() ? Optional.empty() : Optional.of(fileName(token));
        });
    }

    private static String fileName(String command) {
        String normalized = command.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static boolean isJavaExecutable(String name) {
        return name.equalsIgnoreCase("java.exe") || name.equalsIgnoreCase("javaw.exe") || name.equals("java");
    }
    private static void validatePort(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("端口范围为 1–65535");
    }
    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
