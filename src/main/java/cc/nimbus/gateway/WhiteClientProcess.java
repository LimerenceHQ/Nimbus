package cc.nimbus.gateway;

import cc.nimbus.features.gateway.launch.WhiteClientCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/** Owns the authorised white-client process used as the real server side. */
public final class WhiteClientProcess implements AutoCloseable {
    private final Process process;
    private final CompletableFuture<Integer> exited;

    private WhiteClientProcess(Process process) {
        this.process = process;
        this.exited = process.onExit().thenApply(Process::exitValue);
        Thread output = new Thread(() -> drainOutput(process), "nimbus-white-client-output");
        output.setDaemon(true);
        output.start();
    }

    public static WhiteClientProcess launch(Path executable, List<String> arguments,
                                            Map<String, String> environment) throws IOException {
        Objects.requireNonNull(executable, "executable");
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        if (arguments != null) command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (environment != null) builder.environment().putAll(environment);
        return new WhiteClientProcess(builder.start());
    }

    public static WhiteClientProcess launch(WhiteClientCommand launchCommand) throws IOException {
        Objects.requireNonNull(launchCommand, "launchCommand");
        writeLaunchDiagnostics(launchCommand);
        ProcessBuilder builder = new ProcessBuilder(launchCommand.command())
                .directory(launchCommand.workingDirectory().toFile())
                .redirectErrorStream(true);
        return new WhiteClientProcess(builder.start());
    }

    /** Keeps the exact argument vector available when a manifest silently
     * ignores quick-connect options (Forge/NetEase launchers are sensitive to
     * argument spelling). Secrets are never included in this file. */
    private static void writeLaunchDiagnostics(WhiteClientCommand launchCommand) {
        try {
            Path file = Path.of(System.getProperty("user.dir", "."))
                    .resolve("config/nimbus/gateway/last-launch-command.txt");
            java.nio.file.Files.createDirectories(file.getParent());
            java.util.List<String> values = launchCommand.command();
            java.nio.file.Files.writeString(file, String.join(System.lineSeparator(), values));
        } catch (Exception ignored) {
            // Diagnostics must never prevent the white client from launching.
        }
    }

    private static void drainOutput(Process process) {
        try {
            try (var input = process.getInputStream()) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
        } catch (IOException ignored) {
        }
    }

    public Process process() {
        return process;
    }

    public long pid() {
        return process.pid();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Completes when the NEL-style white-client process exits. */
    public CompletableFuture<Integer> exited() {
        return exited;
    }

    @Override
    public void close() {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
