package cc.nimbus.features.music.integration;

import cc.nimbus.features.music.integration.windows.WindowsSystemMediaController;
import com.sun.jna.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class SystemMediaControllers {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemMediaControllers.class);
    private static final SystemMediaController UNSUPPORTED = new SystemMediaController() {
        @Override public SystemMediaState state() { return SystemMediaState.unsupported(); }
        @Override public CompletableFuture<Boolean> execute(Command command) {
            Objects.requireNonNull(command, "command");
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletableFuture<Boolean> seek(double seconds) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public void close() { }
    };

    private SystemMediaControllers() { }

    public static SystemMediaController create() {
        if (!Platform.isWindows()) return UNSUPPORTED;
        try {
            return new WindowsSystemMediaController();
        } catch (LinkageError | RuntimeException error) {
            LOGGER.warn("Unable to create the Windows system media controller", error);
            return UNSUPPORTED;
        }
    }
}
