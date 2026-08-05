package cc.nimbus.features.music.integration;

import java.util.concurrent.CompletableFuture;

public interface SystemMediaController extends AutoCloseable {
    SystemMediaState state();

    CompletableFuture<Boolean> execute(Command command);

    CompletableFuture<Boolean> seek(double seconds);

    @Override
    void close();

    enum Command {
        PLAY,
        PAUSE,
        STOP,
        TOGGLE,
        NEXT,
        PREVIOUS
    }
}
