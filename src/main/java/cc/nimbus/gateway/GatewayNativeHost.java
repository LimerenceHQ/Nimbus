package cc.nimbus.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Starts the native injector and consumes its line-delimited JSON status. */
public final class GatewayNativeHost implements AutoCloseable {
    private final Process process;
    private final CompletableFuture<GatewayState> ready = new CompletableFuture<>();
    private final Consumer<GatewayState> listener;

    private GatewayNativeHost(Process process, Consumer<GatewayState> listener) {
        this.process = process;
        this.listener = listener == null ? ignored -> {} : listener;
        Thread reader = new Thread(this::readOutput, "nimbus-gateway-status");
        reader.setDaemon(true);
        reader.start();
    }

    public static GatewayNativeHost start(long whiteClientPid, int port,
                                          Consumer<GatewayState> listener) throws IOException {
        if (whiteClientPid <= 0) throw new IllegalArgumentException("whiteClientPid must be positive");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid loopback port");
        Path host = locateBinary("nimbus-gateway-host.exe");
        Path bridge = locateBinary("nimbus-gateway-bridge.dll");
        Process process = new ProcessBuilder(host.toString(), "--pid", Long.toString(whiteClientPid),
                "--dll", bridge.toString(), "--port", Integer.toString(port))
                .redirectErrorStream(true)
                .start();
        return new GatewayNativeHost(process, listener);
    }

    public CompletableFuture<GatewayState> ready() {
        return ready;
    }

    public Process process() {
        return process;
    }

    private void readOutput() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = lines.readLine()) != null) {
                GatewayState state = parse(line);
                if (state == null) continue;
                listener.accept(state);
                if (state.isFailure()) {
                    ready.completeExceptionally(new GatewayFailure(
                            state.detail() == null || state.detail().isBlank() ? "native gateway failed" : state.detail()));
                } else if (state.isReady()) {
                    ready.complete(state);
                }
            }
            int exit = process.waitFor();
            if (!ready.isDone()) {
                ready.completeExceptionally(new GatewayFailure(
                        "gateway host exited before bridge readiness with code " + exit
                ));
            }
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            ready.completeExceptionally(new GatewayFailure("unable to read gateway status", error));
        }
    }

    private static GatewayState parse(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            GatewayStage stage = GatewayStage.parse(json.has("event") ? json.get("event").getAsString() : null);
            long pid = json.has("pid") ? json.get("pid").getAsLong() : 0;
            int port = json.has("port") ? json.get("port").getAsInt() : 0;
            String detail = json.has("detail") ? json.get("detail").getAsString() : "";
            return new GatewayState(stage, pid, port, detail);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path locateBinary(String fileName) throws IOException {
        String configured = System.getProperty("nimbus.gateway.dir");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured).resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        Path gameDir = Path.of(System.getProperty("user.dir", "."));
        Path[] candidates = {
                gameDir.resolve("nimbus").resolve("gateway").resolve(fileName),
                gameDir.resolve("build").resolve("gateway-msvc").resolve(fileName),
                gameDir.resolve("build").resolve("gateway-native").resolve(fileName),
                gameDir.resolve("..").resolve("build").resolve("gateway-msvc").resolve(fileName),
                gameDir.resolve("..").resolve("build").resolve("gateway-native").resolve(fileName)
        };
        for (Path candidate : candidates) if (Files.isRegularFile(candidate) && Files.size(candidate) > 0) return candidate.toAbsolutePath().normalize();
        String resource = "/nimbus/gateway/" + fileName;
        try (var input = GatewayNativeHost.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("missing Nimbus Gateway binary: " + fileName);
            Path dir = Files.createTempDirectory("nimbus-gateway-");
            Path extracted = dir.resolve(fileName);
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            return extracted;
        }
    }

    @Override
    public void close() {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
