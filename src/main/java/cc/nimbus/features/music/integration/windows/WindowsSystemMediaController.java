package cc.nimbus.features.music.integration.windows;

import cc.nimbus.features.music.integration.SystemMediaController;
import cc.nimbus.features.music.integration.SystemMediaState;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Guid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WindowsSystemMediaController implements SystemMediaController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsSystemMediaController.class);
    private static final String CLASS_SESSION_MANAGER =
            "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final Guid.GUID IID_MANAGER_STATICS =
            WinRt.guid("2050C4EE-11A0-57DE-AED7-C97C70338245");
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5L);
    private static final long POLL_INTERVAL_MILLIS = 500L;
    private static final long METADATA_INTERVAL_MILLIS = 750L;
    private static final long RETRY_INTERVAL_MILLIS = 5_000L;
    private static final long TICKS_PER_SECOND = 10_000_000L;
    private static final long WINDOWS_EPOCH_TICKS = 116_444_736_000_000_000L;

    private final AtomicReference<SystemMediaState> state =
            new AtomicReference<>(SystemMediaState.connecting());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService executor;

    private boolean apartmentInitialized;
    private WinRt.ComObject manager;
    private WinRt.ComObject session;
    private Metadata metadata = Metadata.EMPTY;
    private String sourceAppId = "";
    private long lastMetadataAt;
    private long timelineStartTicks;
    private long retryAt;
    private int failures;

    public WindowsSystemMediaController() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Nimbus-System-Media");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::cycle, 0L, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public SystemMediaState state() {
        return state.get();
    }

    @Override
    public CompletableFuture<Boolean> execute(Command command) {
        Objects.requireNonNull(command, "command");
        return submit(() -> executeCurrent(command));
    }

    @Override
    public CompletableFuture<Boolean> seek(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Seek position must be finite and non-negative"));
        }
        return submit(() -> seekCurrent(seconds));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Future<?> cleanup = executor.submit(this::cleanup);
        executor.shutdown();
        try {
            cleanup.get(3L, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Interrupted while closing Windows system media", error);
        } catch (Exception error) {
            LOGGER.debug("Timed out while closing Windows system media", error);
        } finally {
            executor.shutdownNow();
        }
    }

    private void cycle() {
        if (closed.get()) return;
        long now = System.currentTimeMillis();
        try {
            if (!apartmentInitialized) {
                WinRt.initialize();
                apartmentInitialized = true;
            }
            if (manager == null) {
                if (now < retryAt) return;
                connect();
            }
            refresh(now);
            failures = 0;
        } catch (Throwable error) {
            disconnect();
            retryAt = now + RETRY_INTERVAL_MILLIS;
            state.set(SystemMediaState.error(message(error)));
            if (failures++ == 0) LOGGER.warn("Windows system media synchronization failed; retrying later", error);
            else LOGGER.debug("Windows system media retry failed", error);
        }
    }

    private void connect() {
        state.set(SystemMediaState.connecting());
        try (WinRt.ComObject factory = WinRt.activationFactory(CLASS_SESSION_MANAGER, IID_MANAGER_STATICS);
             WinRt.ComObject operation = factory.object(6)) {
            manager = WinRt.awaitObject(operation, ASYNC_TIMEOUT);
        }
    }

    private void refresh(long now) {
        WinRt.ComObject current = manager.optionalObject(6);
        if (current == null) {
            replaceSession(null, "");
            state.set(SystemMediaState.noSession());
            return;
        }

        String source = current.string(6);
        replaceSession(current, source);
        if (metadata == Metadata.EMPTY || now - lastMetadataAt >= METADATA_INTERVAL_MILLIS) {
            metadata = readMetadata(session);
            lastMetadataAt = now;
        }

        Playback playback = readPlayback(session);
        Timeline timeline = readTimeline(session, now, playback.status());
        state.set(new SystemMediaState(
                SystemMediaState.Connection.ACTIVE,
                sourceAppId,
                metadata.title(),
                metadata.subtitle(),
                metadata.artist(),
                metadata.albumArtist(),
                metadata.albumTitle(),
                metadata.trackNumber(),
                metadata.albumTrackCount(),
                playback.status(),
                timeline.positionSeconds(),
                timeline.durationSeconds(),
                playback.controls(),
                now,
                ""
        ));
    }

    private void replaceSession(WinRt.ComObject current, String source) {
        close(session);
        session = current;
        if (!sourceAppId.equals(source)) {
            sourceAppId = source;
            metadata = Metadata.EMPTY;
            lastMetadataAt = 0L;
        }
    }

    private static Metadata readMetadata(WinRt.ComObject session) {
        try (WinRt.ComObject operation = session.object(7);
             WinRt.ComObject properties = WinRt.awaitObject(operation, ASYNC_TIMEOUT)) {
            return new Metadata(
                    properties.string(6),
                    properties.string(7),
                    properties.string(9),
                    properties.string(8),
                    properties.string(10),
                    readInt(properties, 11),
                    readInt(properties, 13)
            );
        }
    }

    private Timeline readTimeline(WinRt.ComObject session, long now, SystemMediaState.PlaybackStatus status) {
        try (WinRt.ComObject timeline = session.object(8)) {
            long start = readLong(timeline, 6);
            long end = readLong(timeline, 7);
            long position = readLong(timeline, 10);
            long updated = readLong(timeline, 11);
            timelineStartTicks = start;

            long duration = Math.max(0L, end - start);
            long relativePosition = Math.max(0L, position - start);
            if (status == SystemMediaState.PlaybackStatus.PLAYING && updated >= WINDOWS_EPOCH_TICKS) {
                long nowTicks = WINDOWS_EPOCH_TICKS + now * 10_000L;
                relativePosition += Math.max(0L, nowTicks - updated);
            }
            if (duration > 0L) relativePosition = Math.min(relativePosition, duration);
            return new Timeline(seconds(relativePosition), seconds(duration));
        }
    }

    private static Playback readPlayback(WinRt.ComObject session) {
        try (WinRt.ComObject info = session.object(9);
             WinRt.ComObject controls = info.object(6)) {
            int nativeStatus = readInt(info, 7);
            SystemMediaState.Controls capabilities = new SystemMediaState.Controls(
                    readBoolean(controls, 6),
                    readBoolean(controls, 7),
                    readBoolean(controls, 8),
                    readBoolean(controls, 12),
                    readBoolean(controls, 13),
                    readBoolean(controls, 16),
                    readBoolean(controls, 17),
                    readBoolean(controls, 18),
                    readBoolean(controls, 19),
                    readBoolean(controls, 20)
            );
            return new Playback(SystemMediaState.PlaybackStatus.fromNative(nativeStatus), capabilities);
        }
    }

    private boolean executeCurrent(Command command) {
        WinRt.ComObject current = session;
        if (current == null) return false;
        int method = switch (command) {
            case PLAY -> 10;
            case PAUSE -> 11;
            case STOP -> 12;
            case NEXT -> 16;
            case PREVIOUS -> 17;
            case TOGGLE -> 20;
        };
        try (WinRt.ComObject operation = current.object(method)) {
            return WinRt.awaitBoolean(operation, ASYNC_TIMEOUT);
        }
    }

    private boolean seekCurrent(double seconds) {
        WinRt.ComObject current = session;
        if (current == null) return false;
        long relative = Math.round(seconds * TICKS_PER_SECOND);
        long target = Math.max(0L, timelineStartTicks + relative);
        try (WinRt.ComObject operation = current.object(24, target)) {
            return WinRt.awaitBoolean(operation, ASYNC_TIMEOUT);
        }
    }

    private CompletableFuture<Boolean> submit(Task task) {
        if (closed.get()) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    result.complete(false);
                    return;
                }
                try {
                    result.complete(task.run());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private void disconnect() {
        close(session);
        close(manager);
        session = null;
        manager = null;
        sourceAppId = "";
        metadata = Metadata.EMPTY;
        lastMetadataAt = 0L;
        timelineStartTicks = 0L;
    }

    private void cleanup() {
        disconnect();
        if (apartmentInitialized) {
            WinRt.uninitialize();
            apartmentInitialized = false;
        }
    }

    private static boolean readBoolean(WinRt.ComObject object, int method) {
        Memory result = new Memory(1L);
        object.call(method, result);
        return result.getByte(0L) != 0;
    }

    private static int readInt(WinRt.ComObject object, int method) {
        Memory result = new Memory(Integer.BYTES);
        object.call(method, result);
        return result.getInt(0L);
    }

    private static long readLong(WinRt.ComObject object, int method) {
        Memory result = new Memory(Long.BYTES);
        object.call(method, result);
        return result.getLong(0L);
    }

    private static double seconds(long ticks) {
        return ticks / (double) TICKS_PER_SECOND;
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); }
        catch (Exception ignored) { }
    }

    @FunctionalInterface
    private interface Task {
        boolean run();
    }

    private record Metadata(String title, String subtitle, String artist, String albumArtist,
                            String albumTitle, int trackNumber, int albumTrackCount) {
        private static final Metadata EMPTY = new Metadata("", "", "", "", "", 0, 0);
    }

    private record Timeline(double positionSeconds, double durationSeconds) { }

    private record Playback(SystemMediaState.PlaybackStatus status, SystemMediaState.Controls controls) { }
}
