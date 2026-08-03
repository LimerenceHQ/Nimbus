package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.input.EventKeyPress;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.music.MusicConfig;
import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.internal.net.NeteaseHttp;
import cc.nimbus.features.music.lyrics.Lyrics;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.CachedSong;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.model.User;
import cc.nimbus.features.music.playback.PlayMode;
import cc.nimbus.features.music.playback.PlaybackQueueState;
import cc.nimbus.features.music.playback.PlaybackController;
import cc.nimbus.features.music.playback.PlaybackState;
import cc.nimbus.features.music.provider.NeteaseApi;
import cc.nimbus.features.music.resource.ArtworkCache;
import cc.nimbus.features.music.resource.Artwork;
import cc.nimbus.features.music.resource.AudioCache;
import cc.nimbus.features.music.resource.QrCodeImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MusicManager implements InstanceManager {
    private static final long CONFIG_SAVE_DELAY_MILLIS = 800L;
    private static final long SMS_RESEND_MILLIS = 60_000L;
    private static final long SMS_EXPIRE_MILLIS = 10 * 60_000L;
    private final Path directory;
    private MusicConfig config;
    private NeteaseApi api;
    private AudioCache audioCache;
    private ArtworkCache artworkCache;
    private PlaybackController playback;
    private ExecutorService ioExecutor;
    private ExecutorService playbackExecutor;
    private volatile User profile;
    private volatile List<Playlist> playlists = List.of();
    private volatile Set<Long> likedIds = Set.of();
    private volatile NeteaseApi.QrLogin activeQr;
    private volatile SmsSession activeSms;
    private volatile boolean initialized;
    private volatile boolean configDirty;
    private volatile long configDirtySince;

    public MusicManager(Path directory) { this.directory = Objects.requireNonNull(directory, "directory"); }

    @Override
    public synchronized void init() {
        if (initialized) return;
        try {
            Files.createDirectories(directory);
            config = new MusicConfig(directory.resolve("music.json"));
            config.load();
            api = new NeteaseApi(new NeteaseHttp());
            api.cookie(config.cookie());
            audioCache = new AudioCache(directory.resolve("cache"), api);
            audioCache.init();
            artworkCache = new ArtworkCache();
            ioExecutor = Executors.newFixedThreadPool(3, threads("Nimbus-Music-IO"));
            playbackExecutor = Executors.newSingleThreadExecutor(threads("Nimbus-Music-Playback"));
            playback = new PlaybackController(config, audioCache, playbackExecutor, ioExecutor);
            initialized = true;
            EventBus.register(this);
            if (!config.cookie().isBlank()) refreshAccount();
        } catch (RuntimeException error) {
            shutdownPartial();
            throw error;
        } catch (Exception error) {
            shutdownPartial();
            throw new IllegalStateException("Unable to initialize MusicManager", error);
        }
    }

    @Override
    public synchronized void shutdown() {
        if (!initialized) return;
        initialized = false;
        try {
            if (playback != null) playback.stop();
            if (config != null) config.save();
        } finally {
            shutdownPartial();
        }
    }

    public CompletableFuture<List<Music>> searchSongs(String keyword, int limit) {
        String value = requireText(keyword, "keyword");
        return async(() -> api().searchSongs(value, limit));
    }
    public CompletableFuture<List<Playlist>> searchPlaylists(String keyword, int limit) {
        String value = requireText(keyword, "keyword");
        return async(() -> api().searchPlaylists(value, limit));
    }
    public CompletableFuture<Music> song(long id) { return async(() -> api().song(requireId(id))); }
    public CompletableFuture<Playlist> playlist(long id) { return async(() -> api().playlist(requireId(id))); }
    public CompletableFuture<List<Music>> playlistSongs(long id) { return async(() -> api().playlistSongs(requireId(id))); }
    public CompletableFuture<List<Playlist>> dailyRecommendedPlaylists() { return async(() -> api().dailyRecommendedPlaylists()); }
    public CompletableFuture<List<Music>> dailyRecommendedSongs() { return async(() -> api().dailyRecommendedSongs()); }
    public CompletableFuture<List<Playlist>> personalizedPlaylists(int limit) {
        return async(() -> api().personalizedPlaylists(limit));
    }
    public CompletableFuture<List<Playlist>> featuredPlaylists(int limit) {
        return async(() -> api().featuredPlaylists(limit));
    }
    public CompletableFuture<List<Music>> intelligentSongs(long songId, long playlistId, long startMusicId, int count) {
        return async(() -> api().intelligentSongs(requireId(songId), requireId(playlistId), requireId(startMusicId), count));
    }
    public CompletableFuture<List<Music>> playIntelligent(long songId, long playlistId, int count) {
        long seedId = requireId(songId);
        long sourceId = requireId(playlistId);
        return async(() -> {
            Music seed = api().song(seedId);
            if (seed == null) throw new IllegalStateException("Music not found: " + seedId);
            LinkedHashSet<Music> queue = new LinkedHashSet<>();
            queue.add(seed);
            queue.addAll(api().intelligentSongs(seedId, sourceId, seedId, count));
            return List.copyOf(queue);
        }).thenApply(queue -> {
            playback().play(queue, 0);
            return queue;
        });
    }
    public CompletableFuture<Lyrics> lyrics(long id) { return async(() -> api().lyrics(requireId(id))); }

    public CompletableFuture<User> refreshAccount() {
        return async(this::loadAccount);
    }
    public User profile() { return profile; }
    public List<Playlist> accountPlaylists() { return playlists; }
    public Set<Long> likedIds() { return likedIds; }
    public CompletableFuture<List<Music>> likedSongs(int limit) {
        return async(() -> {
            List<Long> ids = likedIds.stream().limit(Math.max(1, limit)).toList();
            return api().songs(ids);
        });
    }
    public CompletableFuture<Boolean> like(long musicId, boolean liked) {
        return async(() -> {
            boolean changed = api().like(requireId(musicId), liked);
            if (changed) {
                LinkedHashSet<Long> updated = new LinkedHashSet<>(likedIds);
                if (liked) updated.add(musicId); else updated.remove(musicId);
                likedIds = Set.copyOf(updated);
            }
            return changed;
        });
    }
    public CompletableFuture<Boolean> editPlaylist(long playlistId, long musicId, boolean add) {
        return async(() -> api().editPlaylist(requireId(playlistId), requireId(musicId), add));
    }

    public CompletableFuture<NeteaseApi.QrLogin> requestQrLogin() {
        return async(() -> {
            NeteaseApi.QrLogin login = api().createQrLogin();
            activeQr = login;
            if (login != null) {
                try {
                    Files.write(directory.resolve("login-qr.png"), QrCodeImage.png(login.url(), 320));
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Unable to save login QR code", error);
                }
            }
            return login;
        });
    }
    public CompletableFuture<NeteaseApi.QrStatus> checkQrLogin() {
        NeteaseApi.QrLogin login = activeQr;
        if (login == null) return CompletableFuture.failedFuture(new IllegalStateException("No active QR login"));
        return async(() -> {
            NeteaseApi.QrStatus status = api().checkQrLogin(login.key());
            if (status.code() == 803 && !status.cookie().isBlank()) {
                completeLogin(status.cookie());
                activeQr = null;
            }
            return status;
        });
    }
    public NeteaseApi.QrLogin activeQr() { return activeQr; }
    public Path qrImage() { return directory.resolve("login-qr.png"); }
    public CompletableFuture<byte[]> qrImageBytes() {
        return async(() -> {
            try {
                return Files.isRegularFile(qrImage()) ? Files.readAllBytes(qrImage()) : new byte[0];
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Unable to read login QR code", error);
            }
        });
    }
    public synchronized void cancelLogin() {
        activeQr = null;
        activeSms = null;
        try { Files.deleteIfExists(qrImage()); } catch (Exception ignored) {}
    }

    public synchronized CompletableFuture<NeteaseApi.SmsStatus> requestSmsLogin(String phone, String countryCode) {
        requireInitialized();
        String normalizedPhone = digits(phone, "phone", 5, 15);
        String normalizedCountry = digits(countryCode == null || countryCode.isBlank() ? "86" : countryCode,
                "country code", 1, 4);
        long now = System.currentTimeMillis();
        SmsSession previous = activeSms;
        if (previous != null && now - previous.sentAt() < SMS_RESEND_MILLIS) {
            long seconds = (SMS_RESEND_MILLIS - (now - previous.sentAt()) + 999L) / 1000L;
            return CompletableFuture.failedFuture(new IllegalStateException("Please wait " + seconds + " seconds before resending"));
        }
        SmsSession session = new SmsSession(normalizedPhone, normalizedCountry, now);
        activeSms = session;
        return async(() -> api().sendSms(session.phone(), session.countryCode())).whenComplete((status, error) -> {
            if (error != null || status == null || !status.successful()) clearSms(session);
        });
    }
    public CompletableFuture<NeteaseApi.SmsLogin> completeSmsLogin(String captcha) {
        SmsSession session = activeSms;
        if (session == null) return CompletableFuture.failedFuture(new IllegalStateException("No active SMS login"));
        if (System.currentTimeMillis() - session.sentAt() > SMS_EXPIRE_MILLIS) {
            clearSms(session);
            return CompletableFuture.failedFuture(new IllegalStateException("SMS login expired"));
        }
        String normalizedCaptcha = digits(captcha, "captcha", 4, 8);
        return async(() -> {
            NeteaseApi.SmsLogin result = api().loginWithSms(session.phone(), session.countryCode(), normalizedCaptcha);
            if (result.successful()) {
                completeLogin(result.cookie());
                clearSms(session);
            }
            return result;
        });
    }
    public synchronized void logout() {
        requireInitialized();
        playback.stop();
        api.cookie("");
        config.cookie("");
        config.save();
        profile = null;
        playlists = List.of();
        likedIds = Set.of();
        activeQr = null;
        activeSms = null;
        try { Files.deleteIfExists(qrImage()); } catch (Exception ignored) {}
    }

    public CompletableFuture<Music> play(long id) {
        return song(id).thenApply(music -> {
            if (music == null) throw new IllegalStateException("Music not found: " + id);
            playback().play(music);
            return music;
        });
    }
    public void play(Music music) { playback().play(music); }
    public void play(List<Music> songs, int index) { playback().play(songs, index); }
    public List<CachedSong> cachedSongs() {
        requireInitialized();
        return audioCache.cachedSongs();
    }
    public boolean isCached(long musicId) {
        requireInitialized();
        return musicId > 0L && audioCache.cachedSong(musicId) != null;
    }
    public CompletableFuture<CachedSong> cache(Music music) {
        Objects.requireNonNull(music, "music");
        return async(() -> {
            Path prepared = audioCache.prepare(music, config().quality(), ignored -> {}, () -> false);
            CachedSong cached = audioCache.cachedSong(music.id());
            if (prepared == null || cached == null) throw new IllegalStateException("Unable to cache " + music.name());
            return cached;
        });
    }
    public CompletableFuture<Boolean> removeCached(long musicId) {
        long id = requireId(musicId);
        long keep = state().music() == null ? -1L : state().music().id();
        return async(() -> audioCache.remove(id, keep));
    }
    public void playCached(List<Long> musicIds, int index) {
        requireInitialized();
        if (musicIds == null || musicIds.isEmpty()) return;
        long selectedId = musicIds.get(Math.clamp(index, 0, musicIds.size() - 1));
        List<Music> songs = musicIds.stream()
                .map(audioCache::cachedSong)
                .filter(Objects::nonNull)
                .map(CachedSong::music)
                .toList();
        if (songs.isEmpty()) throw new IllegalArgumentException("No cached songs are available");
        int selectedIndex = 0;
        for (int candidate = 0; candidate < songs.size(); candidate++) {
            if (songs.get(candidate).id() == selectedId) {
                selectedIndex = candidate;
                break;
            }
        }
        playback().playCached(songs, selectedIndex);
    }
    public CompletableFuture<List<Music>> playPlaylist(long id, int index) {
        return playlistSongs(id).thenApply(songs -> {
            if (songs.isEmpty()) throw new IllegalStateException("Playlist is empty: " + id);
            playback().play(songs, index);
            return songs;
        });
    }
    public Music next() { return playback().next(); }
    public Music previous() { return playback().previous(); }
    public Music playAt(int index) { return playback().playAt(index); }
    public void stop() { playback().stop(); }
    public void pause() { playback().pause(); }
    public void resume() { playback().resume(); }
    public void togglePause() { playback().togglePause(); }
    public void seek(double seconds) { playback().seek(seconds); }
    public PlaybackState state() { return playback().state(); }
    public List<Music> queue() { return playback().queue(); }
    public PlaybackQueueState queueState() { return playback().queueState(); }
    public float[] spectrum() { return playback().spectrum(); }

    public synchronized void volume(float value) { playback().volume(value); markConfigDirty(); }
    public synchronized void rate(float value) { playback().rate(value); markConfigDirty(); }
    public synchronized void mode(PlayMode value) { playback().mode(value); markConfigDirty(); }
    public synchronized void quality(Quality value) { config().quality(value); markConfigDirty(); }
    public synchronized void prefetch(boolean value) { config().prefetch(value); markConfigDirty(); }
    public synchronized void dynamicBackground(boolean value) { config().dynamicBackground(value); markConfigDirty(); }
    public synchronized void cacheLimitBytes(long value) { config().cacheLimitBytes(value); markConfigDirty(); }
    public MusicConfig config() { requireInitialized(); return config; }

    public CompletableFuture<byte[]> cover(Music music, int size) {
        Objects.requireNonNull(music, "music");
        int bounded = Math.max(32, Math.min(2048, size));
        return artwork().load("music:" + music.id() + ':' + bounded, music.coverUrl(bounded), io());
    }
    public CompletableFuture<Artwork> artwork(Music music, int size) {
        Objects.requireNonNull(music, "music");
        int bounded = Math.max(32, Math.min(2048, size));
        return artwork().loadArtwork("music-artwork:" + music.id() + ':' + bounded,
                music.coverUrl(bounded), io());
    }
    public CompletableFuture<byte[]> cover(Playlist playlist, int size) {
        Objects.requireNonNull(playlist, "playlist");
        int bounded = Math.max(32, Math.min(2048, size));
        String separator = playlist.coverUrl().contains("?") ? "&" : "?";
        return artwork().load("playlist:" + playlist.id() + ':' + bounded,
                playlist.coverUrl() + separator + "param=" + bounded + 'y' + bounded, io());
    }
    public CompletableFuture<byte[]> avatar(User user, int size) {
        Objects.requireNonNull(user, "user");
        int bounded = Math.max(32, Math.min(1024, size));
        String separator = user.avatarUrl().contains("?") ? "&" : "?";
        return artwork().load("user:" + user.id() + ':' + bounded,
                user.avatarUrl() + separator + "param=" + bounded + 'y' + bounded, io());
    }

    public long cacheSizeBytes() { requireInitialized(); return audioCache.sizeBytes(); }
    public CompletableFuture<Long> clearCache() {
        long keep = state().music() == null ? -1L : state().music().id();
        return async(() -> audioCache.clear(keep));
    }
    public CompletableFuture<Long> trimCache() {
        long keep = state().music() == null ? -1L : state().music().id();
        return async(() -> audioCache.trim(config().cacheLimitBytes(), keep));
    }
    public Path directory() { return directory; }

    public synchronized void hotkey(MusicConfig.Hotkey action, int... keys) {
        config().hotkey(action, keys);
        markConfigDirty();
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.POST || !configDirty) return;
        if (System.currentTimeMillis() - configDirtySince >= CONFIG_SAVE_DELAY_MILLIS) flushConfig();
    }

    @EventTarget
    public void onKey(EventKeyPress event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !acceptsHotkeys()) return;
        if (matches(event.getKey(), config().hotkey(MusicConfig.Hotkey.PREVIOUS))) previous();
        if (matches(event.getKey(), config().hotkey(MusicConfig.Hotkey.NEXT))) next();
        if (matches(event.getKey(), config().hotkey(MusicConfig.Hotkey.PAUSE))) togglePause();
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            requireInitialized();
            return task.get();
        }, io());
    }
    private ExecutorService io() { requireInitialized(); return ioExecutor; }
    private NeteaseApi api() { requireInitialized(); return api; }
    private PlaybackController playback() { requireInitialized(); return playback; }
    private ArtworkCache artwork() { requireInitialized(); return artworkCache; }
    private void requireInitialized() { if (!initialized) throw new IllegalStateException("MusicManager is not initialized"); }
    private static long requireId(long value) { if (value <= 0L) throw new IllegalArgumentException("ID must be positive"); return value; }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " cannot be empty");
        return value.trim();
    }
    private static String digits(String value, String name, int minimum, int maximum) {
        if (value == null) throw new IllegalArgumentException(name + " cannot be empty");
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isDigit(current)) result.append(current);
            else if (!Character.isWhitespace(current) && current != '+' && current != '-' && current != '(' && current != ')') {
                throw new IllegalArgumentException("Invalid " + name);
            }
        }
        if (result.length() < minimum || result.length() > maximum) throw new IllegalArgumentException("Invalid " + name);
        return result.toString();
    }
    private static ThreadFactory threads(String name) {
        AtomicInteger index = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, name + '-' + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
    private synchronized void markConfigDirty() {
        configDirty = true;
        configDirtySince = System.currentTimeMillis();
    }
    private synchronized void flushConfig() {
        if (!configDirty || config == null) return;
        config.save();
        configDirty = false;
    }
    private static boolean acceptsHotkeys() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null;
    }
    private static boolean matches(int pressed, int[] keys) {
        if (keys.length == 0 || java.util.Arrays.stream(keys).noneMatch(key -> key == pressed)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().handle();
        return java.util.Arrays.stream(keys).allMatch(key -> key == pressed || GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS);
    }
    private User loadAccount() {
        AccountData account = fetchAccount();
        applyAccount(account);
        return account.user();
    }
    private AccountData fetchAccount() {
        User user = api().profile();
        if (user == null) return new AccountData(null, List.of(), Set.of());
        return new AccountData(user, api().userPlaylists(user.id(), 100), Set.copyOf(api().likedIds(user.id())));
    }
    private void applyAccount(AccountData account) {
        profile = account.user();
        playlists = account.playlists();
        likedIds = account.likedIds();
    }
    private User completeLogin(String loginCookie) {
        String value = requireText(loginCookie, "cookie");
        String previousApiCookie = api().cookie();
        String previousConfigCookie = config().cookie();
        api().cookie(value);
        try {
            AccountData account = fetchAccount();
            if (account.user() == null) throw new IllegalStateException("Netease login did not return an account");
            config().cookie(value);
            config().save();
            applyAccount(account);
            activeQr = null;
            activeSms = null;
            return account.user();
        } catch (RuntimeException error) {
            api().cookie(previousApiCookie);
            config().cookie(previousConfigCookie);
            throw error;
        }
    }
    private synchronized void clearSms(SmsSession session) {
        if (activeSms == session) activeSms = null;
    }
    private void shutdownPartial() {
        initialized = false;
        EventBus.unregister(this);
        if (playbackExecutor != null) playbackExecutor.shutdownNow();
        if (ioExecutor != null) ioExecutor.shutdownNow();
        if (artworkCache != null) artworkCache.close();
        if (audioCache != null) audioCache.close();
        if (api != null) api.close();
        playback = null;
        audioCache = null;
        artworkCache = null;
        api = null;
        config = null;
        ioExecutor = null;
        playbackExecutor = null;
        profile = null;
        playlists = List.of();
        likedIds = Set.of();
        activeQr = null;
        activeSms = null;
    }

    private record SmsSession(String phone, String countryCode, long sentAt) {}
    private record AccountData(User user, List<Playlist> playlists, Set<Long> likedIds) {
        private AccountData {
            playlists = List.copyOf(playlists);
            likedIds = Set.copyOf(likedIds);
        }
    }
}
