package cc.nimbus.features.music.playback;

import cc.nimbus.features.music.MusicConfig;
import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.resource.AudioCache;
import cc.nimbus.features.music.resource.TransferStage;
import cc.nimbus.features.music.resource.TransferStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class PlaybackController {
    private final MusicConfig config;
    private final AudioCache cache;
    private final ExecutorService playbackExecutor;
    private final ExecutorService ioExecutor;
    private final PlayQueue queue = new PlayQueue();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<TransferStatus> transfer = new AtomicReference<>(TransferStatus.idle());
    private final AtomicReference<TransferStatus> prefetch = new AtomicReference<>(TransferStatus.idle());
    private volatile PlaybackPhase phase = PlaybackPhase.IDLE;
    private volatile Music current;
    private volatile AudioEngine player;
    private volatile Future<?> task;
    private volatile String error = "";
    private volatile boolean cacheOnly;

    public PlaybackController(MusicConfig config, AudioCache cache, ExecutorService playbackExecutor,
                              ExecutorService ioExecutor) {
        this.config = Objects.requireNonNull(config, "config");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.playbackExecutor = Objects.requireNonNull(playbackExecutor, "playbackExecutor");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        queue.mode(config.playMode());
    }

    public synchronized void play(Music music) {
        Objects.requireNonNull(music, "music");
        queue.replace(List.of(music), 0);
        cacheOnly = false;
        restart();
    }
    public synchronized void play(List<Music> songs, int index) {
        if (songs == null || songs.isEmpty()) return;
        queue.replace(songs, Math.max(0, Math.min(index, songs.size() - 1)));
        cacheOnly = false;
        restart();
    }
    public synchronized void playCached(List<Music> songs, int index) {
        if (songs == null || songs.isEmpty()) return;
        queue.replace(songs, Math.max(0, Math.min(index, songs.size() - 1)));
        cacheOnly = true;
        restart();
    }
    public synchronized Music next() {
        Music selected = queue.next(true);
        if (selected != null) restart();
        return selected;
    }
    public synchronized Music previous() {
        Music selected = queue.previous();
        if (selected != null) restart();
        return selected;
    }
    public synchronized Music playAt(int index) {
        Music selected = queue.select(index);
        if (selected != null) restart();
        return selected;
    }
    public synchronized void stop() {
        generation.incrementAndGet();
        Future<?> running = task;
        if (running != null) running.cancel(true);
        task = null;
        closePlayer();
        current = null;
        phase = PlaybackPhase.IDLE;
        error = "";
        transfer.set(TransferStatus.idle());
        prefetch.set(TransferStatus.idle());
    }
    public synchronized void pause() {
        AudioEngine value = player;
        if (value == null) return;
        value.setPaused(true);
        phase = PlaybackPhase.PAUSED;
    }
    public synchronized void resume() {
        AudioEngine value = player;
        if (value == null) return;
        value.setPaused(false);
        phase = PlaybackPhase.PLAYING;
    }
    public synchronized void togglePause() {
        AudioEngine value = player;
        if (value == null) return;
        if (value.isPausing()) resume(); else pause();
    }
    public synchronized void seek(double seconds) {
        AudioEngine value = player;
        if (value != null) value.seekSeconds(Math.max(0d, seconds));
    }
    public synchronized void volume(float value) {
        config.volume(value);
        AudioEngine active = player;
        if (active != null) active.setVolume(config.volume());
    }
    public synchronized void rate(float value) {
        config.rate(value);
        AudioEngine active = player;
        if (active != null) active.setRate(config.rate());
    }
    public synchronized void mode(PlayMode value) {
        queue.mode(value);
        config.playMode(value);
    }

    public PlaybackState state() {
        AudioEngine active = player;
        float progress = active == null ? 0f : active.getCurrentTimeSeconds();
        Music music = current;
        PlaybackQueueState queueState = queue.snapshot();
        return new PlaybackState(phase, music, progress, music == null ? 0f : music.durationMs() / 1000f,
                config.volume(), config.rate(), queueState.index(), queueState.songs().size(),
                error, transfer.get(), prefetch.get());
    }
    public List<Music> queue() { return queue.snapshot().songs(); }
    public PlaybackQueueState queueState() { return queue.snapshot(); }
    public PlayMode mode() { return queue.mode(); }
    public float[] spectrum() {
        AudioEngine active = player;
        return active == null ? new float[0] : active.getSpectrum();
    }

    private void restart() {
        long token = generation.incrementAndGet();
        Future<?> running = task;
        if (running != null) running.cancel(true);
        closePlayer();
        phase = PlaybackPhase.PREPARING;
        error = "";
        task = playbackExecutor.submit(() -> run(token));
    }

    private void run(long token) {
        while (valid(token)) {
            Music music = queue.current();
            if (music == null) { phase = PlaybackPhase.IDLE; return; }
            current = music;
            phase = PlaybackPhase.PREPARING;
            Quality quality = config.quality();
            Path file = cacheOnly
                    ? cache.prepareCached(music, quality, transfer::set)
                    : cache.prepare(music, quality, transfer::set, () -> !valid(token));
            if (!cacheOnly && file == null && quality != Quality.STANDARD && valid(token)) {
                file = cache.prepare(music, Quality.STANDARD, transfer::set, () -> !valid(token));
            }
            if (!valid(token)) return;
            if (file == null) {
                fail(cacheOnly ? "Cached audio is unavailable"
                        : transfer.get().error().isBlank() ? "Unable to prepare audio" : transfer.get().error());
                return;
            }

            AudioEngine active = new AudioEngine(file.toFile());
            active.setVolume(config.volume());
            active.setRate(config.rate());
            player = active;
            phase = PlaybackPhase.PLAYING;
            active.play();
            boolean prefetched = false;
            while (valid(token) && !active.isFinished()) {
                if (!cacheOnly && !prefetched && config.prefetch()
                        && active.getCurrentTimeSeconds() >= music.durationMs() / 1000f * 0.9f) {
                    prefetch(token, music);
                    prefetched = true;
                }
                try { Thread.sleep(50L); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
            if (player == active) player = null;
            active.close();
            if (!valid(token)) return;
            if (queue.next(false) == null) { phase = PlaybackPhase.IDLE; return; }
        }
    }

    private void prefetch(long token, Music playing) {
        Music next = queue.peekNext();
        if (next == null || next.id() == playing.id()) return;
        ioExecutor.submit(() -> {
            Quality quality = config.quality();
            cache.prepare(next, quality, prefetch::set,
                    () -> !valid(token) || !config.prefetch() || queue.peekNext() == null || queue.peekNext().id() != next.id());
            if (valid(token)) cache.trim(config.cacheLimitBytes(), playing.id());
        });
    }

    private boolean valid(long token) { return generation.get() == token && !Thread.currentThread().isInterrupted(); }
    private synchronized void closePlayer() {
        AudioEngine active = player;
        player = null;
        if (active != null) active.close();
    }
    private void fail(String message) {
        error = message == null ? "Playback failed" : message;
        phase = PlaybackPhase.ERROR;
    }
}
