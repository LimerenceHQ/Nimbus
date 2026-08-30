package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.features.gateway.account.GatewayAccountStore;
import cc.nimbus.features.gateway.install.WhiteClientResourceInstaller;
import cc.nimbus.features.gateway.install.GatewayJavaRuntimeInstaller;
import cc.nimbus.features.gateway.launch.GatewayJoinAuthenticator;
import cc.nimbus.features.gateway.launch.NeteaseYggdrasilAuthenticator;
import cc.nimbus.features.gateway.launch.GatewayLaunchServices;
import cc.nimbus.features.gateway.launch.GatewayLaunchTarget;
import cc.nimbus.features.gateway.launch.WhiteClientCommand;
import cc.nimbus.features.gateway.launch.WhiteClientCommandBuilder;
import cc.nimbus.features.gateway.launch.WhiteClientLaunchParameters;
import cc.nimbus.features.gateway.launch.WhiteClientRuntimeLayout;
import cc.nimbus.features.gateway.model.BujiIslandSnapshot;
import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayCaptcha;
import cc.nimbus.features.gateway.model.GatewayDevice;
import cc.nimbus.features.gateway.model.WhiteClientManifest;
import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import cc.nimbus.features.gateway.net.Channel4399Service;
import cc.nimbus.features.gateway.net.GatewayProtocolException;
import cc.nimbus.features.gateway.net.NeteaseGatewayService;
import cc.nimbus.gateway.GatewaySession;
import cc.nimbus.gateway.GatewayState;
import cc.nimbus.gateway.WhiteClientProcess;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/** Owns NetEase accounts, single-server queries and the active Nimbus Gateway session. */
public final class GatewayManager implements InstanceManager {
    private final Path directory;
    private final Minecraft minecraft;
    private final List<GatewayAccount> accounts = new ArrayList<>();

    private GatewayAccountStore accountStore;
    private NeteaseGatewayService netease;
    private Channel4399Service channel4399;
    private WhiteClientResourceInstaller resourceInstaller;
    private GatewayJavaRuntimeInstaller javaInstaller;
    private ExecutorService executor;
    private GatewayDevice device;
    private GatewayAccount selectedAccount;
    private GatewaySession session;
    private long operationGeneration;
    private boolean initialized;

    public GatewayManager(Path directory) {
        this(directory, Minecraft.getInstance());
    }

    GatewayManager(Path directory, Minecraft minecraft) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public synchronized void init() {
        if (initialized) return;
        GatewayAccountStore store = new GatewayAccountStore(directory.resolve("accounts"));
        try {
            store.init();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to initialize Nimbus Gateway account storage", error);
        }
        GatewayAccountStore.Snapshot snapshot = store.loadAccounts();
        accountStore = store;
        netease = new NeteaseGatewayService();
        channel4399 = new Channel4399Service();
        resourceInstaller = new WhiteClientResourceInstaller(directory.resolve("runtime"));
        javaInstaller = new GatewayJavaRuntimeInstaller(directory);
        executor = Executors.newSingleThreadExecutor(new GatewayThreadFactory());
        device = store.loadDevice();
        accounts.clear();
        accounts.addAll(snapshot.accounts());
        selectedAccount = findAccount(snapshot.lastUserId());
        if (selectedAccount == null) selectedAccount = accounts.stream().findFirst().orElse(null);
        operationGeneration = 0;
        session = null;
        initialized = true;
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) return;
        initialized = false;
        operationGeneration++;
        if (session != null) {
            session.close();
            session = null;
        }
        if (executor != null) executor.shutdownNow();
        executor = null;
        netease = null;
        channel4399 = null;
        resourceInstaller = null;
        javaInstaller = null;
        accountStore = null;
        device = null;
        selectedAccount = null;
        accounts.clear();
    }

    public synchronized List<GatewayAccount> accounts() {
        requireInitialized();
        return List.copyOf(accounts);
    }

    public synchronized GatewayAccount selectedAccount() {
        requireInitialized();
        return selectedAccount;
    }

    /** Password contents are copied for the background request and cleared from the supplied array. */
    public CompletableFuture<GatewayAccount> loginWithEmail(String email, char[] password) {
        Objects.requireNonNull(password, "password");
        char[] secret = Arrays.copyOf(password, password.length);
        Arrays.fill(password, '\0');
        return submit(() -> {
            try {
                GatewayDevice activeDevice;
                synchronized (this) {
                    activeDevice = device;
                }
                if (activeDevice == null) {
                    activeDevice = netease.initializeDevice(null);
                    accountStore.saveDevice(activeDevice);
                }
                GatewayAccount account = netease.loginWithEmail(activeDevice, email, secret);
                List<GatewayAccount> updated;
                synchronized (this) {
                    updated = upsert(accounts, account);
                }
                accountStore.saveAccounts(updated, account.userId());
                return new AccountMutation(account, activeDevice, updated);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }).thenApply(mutation -> {
            synchronized (this) {
                device = mutation.device();
                accounts.clear();
                accounts.addAll(mutation.accounts());
                selectedAccount = mutation.account();
            }
            return mutation.account();
        });
    }

    /** Imports the X19 SAuth JSON called a cookie by NEL clients. */
    public CompletableFuture<GatewayAccount> loginWithCookie(char[] cookie) {
        Objects.requireNonNull(cookie, "cookie");
        char[] secret = Arrays.copyOf(cookie, cookie.length);
        Arrays.fill(cookie, '\0');
        return submit(() -> {
            try {
                GatewayAccount account = netease.loginWithCookie(new String(secret));
                GatewayDevice activeDevice;
                List<GatewayAccount> updated;
                synchronized (this) {
                    activeDevice = device;
                    updated = upsert(accounts, account);
                }
                accountStore.saveAccounts(updated, account.userId());
                return new AccountMutation(account, activeDevice, updated);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }).thenApply(this::applyAccountMutation);
    }

    public CompletableFuture<GatewayCaptcha> request4399Captcha() {
        synchronized (this) {
            requireInitialized();
        }
        return submit(channel4399::requestCaptcha);
    }

    /** Password contents are cleared after the 4399 channel has produced an X19 SAuth credential. */
    public CompletableFuture<GatewayAccount> loginWith4399(
            String username,
            char[] password,
            String captchaSessionId,
            String captcha
    ) {
        Objects.requireNonNull(password, "password");
        char[] secret = Arrays.copyOf(password, password.length);
        Arrays.fill(password, '\0');
        return submit(() -> {
            try {
                GatewayAccount account = netease.loginWithSAuth(
                        channel4399.login(username, secret, captchaSessionId, captcha)
                );
                GatewayDevice activeDevice;
                List<GatewayAccount> updated;
                synchronized (this) {
                    activeDevice = device;
                    updated = upsert(accounts, account);
                }
                accountStore.saveAccounts(updated, account.userId());
                return new AccountMutation(account, activeDevice, updated);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }).thenApply(this::applyAccountMutation);
    }

    public CompletableFuture<GatewayAccount> refreshAccount(String userId) {
        GatewayAccount account;
        GatewayDevice activeDevice;
        synchronized (this) {
            requireInitialized();
            account = findRequiredAccount(userId);
            activeDevice = device;
        }
        if (activeDevice == null && account.sAuthJson().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("网易 MPay 设备尚未初始化"));
        }
        return submit(() -> {
            GatewayAccount refreshed = netease.refresh(activeDevice, account);
            List<GatewayAccount> updated;
            synchronized (this) {
                updated = upsert(accounts, refreshed);
            }
            accountStore.saveAccounts(updated, refreshed.userId());
            return new AccountMutation(refreshed, activeDevice, updated);
        }).thenApply(mutation -> {
            synchronized (this) {
                accounts.clear();
                accounts.addAll(mutation.accounts());
                selectedAccount = mutation.account();
            }
            return mutation.account();
        });
    }

    public CompletableFuture<GatewayAccount> selectAccount(String userId) {
        GatewayAccount account;
        List<GatewayAccount> snapshot;
        synchronized (this) {
            requireInitialized();
            account = findRequiredAccount(userId);
            snapshot = List.copyOf(accounts);
        }
        return submit(() -> {
            accountStore.saveAccounts(snapshot, account.userId());
            return account;
        }).thenApply(selected -> {
            synchronized (this) {
                selectedAccount = selected;
            }
            return selected;
        });
    }

    public CompletableFuture<Boolean> removeAccount(String userId) {
        List<GatewayAccount> updated;
        GatewayAccount nextSelected;
        synchronized (this) {
            requireInitialized();
            GatewayAccount existing = findAccount(userId);
            if (existing == null) return CompletableFuture.completedFuture(false);
            updated = accounts.stream().filter(account -> !account.userId().equals(userId)).toList();
            nextSelected = selectedAccount != null && selectedAccount.userId().equals(userId)
                    ? updated.stream().findFirst().orElse(null)
                    : selectedAccount;
        }
        GatewayAccount finalNextSelected = nextSelected;
        return submit(() -> {
            accountStore.saveAccounts(updated, finalNextSelected == null ? "" : finalNextSelected.userId());
            return true;
        }).thenApply(removed -> {
            synchronized (this) {
                accounts.clear();
                accounts.addAll(updated);
                selectedAccount = finalNextSelected;
            }
            return removed;
        });
    }

    public CompletableFuture<BujiIslandSnapshot> loadBujiIsland() {
        GatewayAccount account;
        synchronized (this) {
            requireInitialized();
            if (selectedAccount == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("请先选择网易账号"));
            }
            account = selectedAccount;
        }
        return submit(() -> netease.loadBujiIsland(account));
    }

    public CompletableFuture<Void> createBujiIslandCharacter(String roleName) {
        GatewayAccount account;
        synchronized (this) {
            requireInitialized();
            if (selectedAccount == null) return CompletableFuture.failedFuture(new IllegalStateException("请先登录网易账号"));
            account = selectedAccount;
        }
        return submit(() -> {
            netease.createBujiIslandCharacter(account, roleName);
            return null;
        });
    }

    public CompletableFuture<Void> notifyGameStart() {
        GatewayAccount account;
        synchronized (this) {
            requireInitialized();
            if (selectedAccount == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("请先选择网易账号"));
            }
            account = selectedAccount;
        }
        return submit(() -> {
            netease.notifyGameStart(account);
            return null;
        });
    }

    public CompletableFuture<WhiteClientManifest> loadWhiteClientManifest() {
        GatewayAccount account;
        synchronized (this) {
            requireInitialized();
            if (selectedAccount == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("请先选择网易账号"));
            }
            account = selectedAccount;
        }
        return submit(() -> netease.loadWhiteClientManifest(account));
    }

    /** Downloads and verifies the manifest archives into the role-independent Gateway cache. */
    public CompletableFuture<cc.nimbus.features.gateway.model.WhiteClientInstallation> prepareWhiteClient(
            WhiteClientManifest manifest
    ) {
        Objects.requireNonNull(manifest, "manifest");
        synchronized (this) {
            requireInitialized();
        }
        return submit(() -> resourceInstaller.prepare(manifest));
    }

    public CompletableFuture<String> loadLauncherVersion() {
        synchronized (this) {
            requireInitialized();
        }
        return submit(() -> netease.launcherVersion());
    }

    /** Resolves the Java 17 runtime location used by NEL-style launchers. */
    public synchronized Path resolveJava17Executable() {
        requireInitialized();
        String configured = System.getProperty("nimbus.gateway.java", "");
        List<Path> roots = new ArrayList<>();
        if (!configured.isBlank()) roots.add(Path.of(configured));
        roots.add(directory.resolve("Java").resolve("jdk17"));
        roots.add(directory.resolve("java").resolve("jdk17"));
        Path working = Path.of(System.getProperty("user.dir", "."));
        roots.add(working.resolve(".game_cache").resolve("Java").resolve("jdk17"));
        roots.add(working.resolve("Java").resolve("jdk17"));
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) roots.add(Path.of(javaHome));
        roots.add(Path.of(System.getProperty("java.home", ".")));
        for (Path root : roots) {
            Path candidate = root;
            if (Files.isRegularFile(candidate)) {
                if (isJava17(candidate)) return candidate;
                continue;
            }
            for (String name : List.of("javaw.exe", "java.exe")) {
                candidate = root.resolve("bin").resolve(name);
                if (Files.isRegularFile(candidate) && isJava17(candidate)) return candidate;
            }
        }
        return null;
    }

    /** Ensures the managed NEL-style Java 17 runtime exists, downloading it when needed. */
    public CompletableFuture<Path> prepareJava17() {
        synchronized (this) { requireInitialized(); }
        return submit(() -> javaInstaller.prepare());
    }

    private static boolean isJava17(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
            return process.exitValue() == 0 && (output.contains("version \"17") || output.contains("openjdk 17"));
        } catch (Exception ignored) { return false; }
    }

    /** Creates the official NetEase Yggdrasil callback used by the white client's AuthSock. */
    public synchronized GatewayJoinAuthenticator createJoinAuthenticator(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation
    ) throws GatewayProtocolException {
        requireInitialized();
        if (target == null || installation == null) throw new IllegalArgumentException("launch target and installation are required");
        return new NeteaseYggdrasilAuthenticator(target, installation);
    }

    /** Temporary entry point retained until the full white-client launcher replaces it. */
    public synchronized GatewaySession startBujiIsland(
            Path executable,
            List<String> arguments,
            Map<String, String> environment,
            Consumer<GatewayState> listener
    ) throws IOException {
        requireInitialized();
        if (session != null) throw new IllegalStateException("Nimbus Gateway session already active");
        WhiteClientProcess white = WhiteClientProcess.launch(executable, arguments, environment);
        try {
            GatewaySession started = GatewaySession.start(white, listener);
            session = started;
            started.ready().whenComplete((state, error) -> {
                if (error == null) return;
                synchronized (GatewayManager.this) {
                    if (session == started) {
                        started.close();
                        session = null;
                    }
                }
            });
            return started;
        } catch (IOException | RuntimeException error) {
            white.close();
            throw error;
        }
    }

    /**
     * Starts the NetEase white client using the official launcher ordering:
     * AuthSock/RPC first, launch arguments second, Java process third, then
     * attach the native A/B bridge and expose the loopback endpoint to Nimbus.
     */
    public synchronized GatewaySession startBujiIsland(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation,
            Path javaExecutable,
            int memoryMegabytes,
            GatewayJoinAuthenticator authenticator,
            Consumer<GatewayState> listener
    ) throws IOException, GatewayProtocolException {
        return startBujiIsland(target, installation, javaExecutable, memoryMegabytes, authenticator, listener, true);
    }

    /** Launches the white client without starting the native injector. */
    public synchronized GatewaySession startBujiIslandWhiteOnly(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation,
            Path javaExecutable,
            int memoryMegabytes,
            GatewayJoinAuthenticator authenticator,
            Consumer<GatewayState> listener
    ) throws IOException, GatewayProtocolException {
        return startBujiIsland(target, installation, javaExecutable, memoryMegabytes, authenticator, listener, false);
    }

    private synchronized GatewaySession startBujiIsland(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation,
            Path javaExecutable,
            int memoryMegabytes,
            GatewayJoinAuthenticator authenticator,
            Consumer<GatewayState> listener,
            boolean nativeBridge
    ) throws IOException, GatewayProtocolException {
        requireInitialized();
        WhiteClientRuntimeLayout layout = resourceInstaller.assembleRuntime(installation, target);
        return startBujiIsland(
                target,
                installation,
                javaExecutable,
                layout.gameBaseMinecraft(),
                layout.runtimeMinecraft(),
                layout.assetsDirectory(),
                layout.nativesDirectory(),
                memoryMegabytes,
                authenticator,
                listener,
                nativeBridge
        );
    }

    public synchronized GatewaySession startBujiIsland(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation,
            Path javaExecutable,
            Path gameBaseMinecraft,
            Path runtimeMinecraft,
            Path assetsDirectory,
            Path nativesDirectory,
            int memoryMegabytes,
            GatewayJoinAuthenticator authenticator,
            Consumer<GatewayState> listener
    ) throws IOException, GatewayProtocolException {
        return startBujiIsland(target, installation, javaExecutable, gameBaseMinecraft, runtimeMinecraft,
                assetsDirectory, nativesDirectory, memoryMegabytes, authenticator, listener, true);
    }

    private synchronized GatewaySession startBujiIsland(
            GatewayLaunchTarget target,
            WhiteClientInstallation installation,
            Path javaExecutable,
            Path gameBaseMinecraft,
            Path runtimeMinecraft,
            Path assetsDirectory,
            Path nativesDirectory,
            int memoryMegabytes,
            GatewayJoinAuthenticator authenticator,
            Consumer<GatewayState> listener,
            boolean nativeBridge
    ) throws IOException, GatewayProtocolException {
        requireInitialized();
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(installation, "installation");
        Objects.requireNonNull(javaExecutable, "javaExecutable");
        Objects.requireNonNull(gameBaseMinecraft, "gameBaseMinecraft");
        Objects.requireNonNull(runtimeMinecraft, "runtimeMinecraft");
        Objects.requireNonNull(assetsDirectory, "assetsDirectory");
        Objects.requireNonNull(nativesDirectory, "nativesDirectory");
        Objects.requireNonNull(authenticator, "authenticator");
        if (session != null) throw new IllegalStateException("Nimbus Gateway session already active");

        GatewayLaunchServices services = GatewayLaunchServices.start(target, authenticator);
        WhiteClientProcess white = null;
        try {
            // NEL calls its interconnection GameStart after the launcher-side
            // services are ready and before the Java client is allowed to join.
            netease.notifyGameStart(target.account());
            String launcherVersion = netease.launcherVersion();
            WhiteClientLaunchParameters parameters = WhiteClientLaunchParameters.create(
                    target,
                    services,
                    launcherVersion,
                    runtimeMinecraft,
                    assetsDirectory,
                    nativesDirectory,
                    memoryMegabytes
            );
            WhiteClientCommand command = WhiteClientCommandBuilder.build(
                    javaExecutable,
                    gameBaseMinecraft,
                    runtimeMinecraft,
                    target.server().gameVersion(),
                    parameters
            );
            white = WhiteClientProcess.launch(command);
            GatewaySession started = nativeBridge
                    ? GatewaySession.start(white, services, listener)
                    : GatewaySession.startWhiteOnly(white, services, listener);
            session = started;
            GatewaySession active = started;
            started.ready().whenComplete((state, error) -> {
                if (error == null) return;
                synchronized (GatewayManager.this) {
                    if (session == active) session = null;
                }
            });
            return started;
        } catch (IOException | RuntimeException | GatewayProtocolException error) {
            if (white != null) white.close();
            services.close();
            throw error;
        }
    }

    public synchronized GatewaySession session() {
        requireInitialized();
        return session;
    }

    public synchronized void closeSession() {
        requireInitialized();
        if (session == null) return;
        session.close();
        session = null;
    }

    private <T> CompletableFuture<T> submit(CheckedSupplier<T> operation) {
        ExecutorService activeExecutor;
        long generation;
        synchronized (this) {
            requireInitialized();
            activeExecutor = executor;
            generation = ++operationGeneration;
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        activeExecutor.execute(() -> {
            T value = null;
            Throwable failure = null;
            try {
                value = operation.get();
            } catch (Exception error) {
                failure = error;
            }
            T completedValue = value;
            Throwable completedFailure = failure;
            minecraft.execute(() -> {
                synchronized (GatewayManager.this) {
                    if (!initialized || generation != operationGeneration) {
                        result.cancel(false);
                        return;
                    }
                }
                if (completedFailure == null) result.complete(completedValue);
                else result.completeExceptionally(completedFailure);
            });
        });
        return result;
    }

    private synchronized GatewayAccount findRequiredAccount(String userId) {
        GatewayAccount account = findAccount(userId);
        if (account == null) throw new IllegalArgumentException("Unknown Nimbus Gateway account: " + userId);
        return account;
    }

    private GatewayAccount findAccount(String userId) {
        if (userId == null || userId.isBlank()) return null;
        return accounts.stream().filter(account -> account.userId().equals(userId)).findFirst().orElse(null);
    }

    private static List<GatewayAccount> upsert(List<GatewayAccount> source, GatewayAccount account) {
        List<GatewayAccount> result = new ArrayList<>(source.size() + 1);
        for (GatewayAccount existing : source) {
            if (!existing.userId().equals(account.userId())) result.add(existing);
        }
        result.add(account);
        return List.copyOf(result);
    }

    private GatewayAccount applyAccountMutation(AccountMutation mutation) {
        synchronized (this) {
            device = mutation.device();
            accounts.clear();
            accounts.addAll(mutation.accounts());
            selectedAccount = mutation.account();
        }
        return mutation.account();
    }

    private void requireInitialized() {
        if (!initialized) throw new IllegalStateException("GatewayManager is not initialized");
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private record AccountMutation(GatewayAccount account, GatewayDevice device, List<GatewayAccount> accounts) {
        private AccountMutation {
            accounts = List.copyOf(accounts);
        }
    }

    private static final class GatewayThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "nimbus-gateway-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
