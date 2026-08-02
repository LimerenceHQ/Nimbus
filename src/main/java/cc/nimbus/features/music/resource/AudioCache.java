package cc.nimbus.features.music.resource;

import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.CachedSong;
import cc.nimbus.features.music.provider.NeteaseApi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javazoom.jl.converter.Converter;
import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;
import org.jflac.util.WavWriter;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AudioCache implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    private final Path indexFile;
    private final NeteaseApi api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<Long, ReentrantLock> songLocks = new ConcurrentHashMap<>();
    private final Map<Long, CachedSong> cachedSongs = new LinkedHashMap<>();

    public AudioCache(Path directory, NeteaseApi api) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.indexFile = directory.resolve("index.json");
        this.api = Objects.requireNonNull(api, "api");
    }

    public void init() {
        try {
            Files.createDirectories(directory);
            loadIndex();
            reconcileIndex();
        }
        catch (Exception error) { throw new IllegalStateException("Unable to create music cache", error); }
    }

    public Path prepare(Music music, Quality quality, Consumer<TransferStatus> status, BooleanSupplier cancelled) {
        Objects.requireNonNull(music, "music");
        Objects.requireNonNull(quality, "quality");
        Consumer<TransferStatus> listener = status == null ? ignored -> {} : status;
        BooleanSupplier cancellation = cancelled == null ? () -> false : cancelled;
        ReentrantLock lock = songLocks.computeIfAbsent(music.id(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            checkCancelled(cancellation);
            listener.accept(value(music, quality, TransferStage.PREPARING, 0L, -1L, 0L, ""));
            Path songDirectory = directory.resolve(Long.toString(music.id()));
            Files.createDirectories(songDirectory);
            Path wav = songDirectory.resolve(quality.apiValue() + ".wav");
            if (validWav(wav)) {
                touch(wav);
                updateIndex(music, songDirectory);
                listener.accept(value(music, quality, TransferStage.READY, 0L, -1L, 0L, ""));
                return wav;
            }

            Path raw = findRaw(songDirectory, quality);
            if (raw == null) raw = download(music, quality, songDirectory, listener, cancellation);
            checkCancelled(cancellation);
            AudioType type = detect(raw);
            if (type != AudioType.FLAC && type != AudioType.MP3 && type != AudioType.WAV) {
                throw new IllegalStateException("Unsupported audio format: " + type.name().toLowerCase());
            }

            listener.accept(value(music, quality, TransferStage.CONVERTING, 0L, -1L, 0L, ""));
            Path temporary = wav.resolveSibling(wav.getFileName() + ".part");
            Files.deleteIfExists(temporary);
            try {
                if (type == AudioType.FLAC) flac(raw, temporary);
                else if (type == AudioType.MP3) new Converter().convert(raw.toString(), temporary.toString());
                else Files.copy(raw, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (!validWav(temporary)) throw new IllegalStateException("Decoder produced an invalid WAV file");
                move(temporary, wav);
            } finally {
                Files.deleteIfExists(temporary);
            }
            listener.accept(value(music, quality, TransferStage.READY, 0L, -1L, 0L, ""));
            updateIndex(music, songDirectory);
            return wav;
        } catch (Cancelled ignored) {
            listener.accept(TransferStatus.idle());
            return null;
        } catch (Exception error) {
            listener.accept(value(music, quality, TransferStage.ERROR, 0L, -1L, 0L, message(error)));
            return null;
        } finally {
            lock.unlock();
        }
    }

    public Path prepareCached(Music music, Consumer<TransferStatus> status) {
        return prepareCached(music, null, status);
    }

    public Path prepareCached(Music music, Quality preferred, Consumer<TransferStatus> status) {
        Objects.requireNonNull(music, "music");
        Consumer<TransferStatus> listener = status == null ? ignored -> {} : status;
        ReentrantLock lock = songLocks.computeIfAbsent(music.id(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            CachedSong cached = cachedSong(music.id());
            if (cached == null) return null;
            Path songDirectory = directory.resolve(Long.toString(music.id()));
            List<Quality> qualities = new ArrayList<>(cached.qualities());
            qualities.sort(Comparator.comparingInt(Quality::ordinal).reversed());
            if (preferred != null && qualities.remove(preferred)) qualities.addFirst(preferred);
            for (Quality quality : qualities) {
                Path wav = songDirectory.resolve(quality.apiValue() + ".wav");
                if (!validWav(wav)) continue;
                touch(wav);
                updateIndex(music, songDirectory);
                listener.accept(value(music, quality, TransferStage.READY, 0L, Files.size(wav), 0L, ""));
                return wav;
            }
            removeIndex(music.id());
            return null;
        } catch (Exception error) {
            listener.accept(value(music, Quality.STANDARD, TransferStage.ERROR, 0L, -1L, 0L, message(error)));
            return null;
        } finally {
            lock.unlock();
        }
    }

    public synchronized List<CachedSong> cachedSongs() {
        return cachedSongs.values().stream()
                .sorted(Comparator.comparingLong(CachedSong::lastUsedAt).reversed())
                .toList();
    }

    public synchronized CachedSong cachedSong(long musicId) {
        return cachedSongs.get(musicId);
    }

    public boolean remove(long musicId, long keepMusicId) {
        if (musicId <= 0L || musicId == keepMusicId) return false;
        ReentrantLock lock = songLocks.computeIfAbsent(musicId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) return false;
        try {
            delete(directory.resolve(Long.toString(musicId)));
            removeIndex(musicId);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    public long sizeBytes() {
        try { return size(directory); } catch (Exception ignored) { return 0L; }
    }

    public long clear(long keepMusicId) {
        long before = sizeBytes();
        for (Path child : songDirectories()) {
            long id = id(child);
            if (id == keepMusicId) continue;
            ReentrantLock lock = songLocks.computeIfAbsent(id, ignored -> new ReentrantLock());
            if (!lock.tryLock()) continue;
            try {
                delete(child);
                removeIndex(id);
            } catch (Exception ignored) {} finally { lock.unlock(); }
        }
        return Math.max(0L, before - sizeBytes());
    }

    public long trim(long limitBytes, long keepMusicId) {
        long total = sizeBytes();
        if (total <= limitBytes) return 0L;
        List<Path> songs = new ArrayList<>(songDirectories());
        songs.sort(Comparator.comparingLong(AudioCache::modified));
        long freed = 0L;
        for (Path song : songs) {
            if (total - freed <= limitBytes) break;
            long id = id(song);
            if (id == keepMusicId) continue;
            ReentrantLock lock = songLocks.computeIfAbsent(id, ignored -> new ReentrantLock());
            if (!lock.tryLock()) continue;
            try {
                long bytes = size(song);
                delete(song);
                removeIndex(id);
                freed += bytes;
            } catch (Exception ignored) {
            } finally {
                lock.unlock();
            }
        }
        return freed;
    }

    private Path download(Music music, Quality quality, Path songDirectory, Consumer<TransferStatus> listener,
                          BooleanSupplier cancelled) throws Exception {
        NeteaseApi.AudioUrl audio = api.audioUrl(music.id(), quality);
        if (audio == null || audio.url() == null || audio.url().isBlank()) throw new IllegalStateException("No playable URL");
        Path part = songDirectory.resolve(quality.apiValue() + ".download.part");
        Files.deleteIfExists(part);
        listener.accept(value(music, quality, TransferStage.DOWNLOADING, 0L, audio.sizeBytes(), 0L, ""));
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(audio.url()))
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "Nimbus-Music")
                    .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("HTTP " + response.statusCode());
            long total = response.headers().firstValueAsLong("content-length").orElse(audio.sizeBytes());
            long bytes = 0L;
            long lastBytes = 0L;
            long lastUpdate = System.nanoTime();
            long speed = 0L;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = response.body(); var output = Files.newOutputStream(part)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    checkCancelled(cancelled);
                    output.write(buffer, 0, read);
                    bytes += read;
                    long now = System.nanoTime();
                    if (now - lastUpdate >= 200_000_000L) {
                        long elapsed = now - lastUpdate;
                        long instant = (bytes - lastBytes) * 1_000_000_000L / Math.max(1L, elapsed);
                        speed = speed == 0L ? instant : (speed * 3L + instant) / 4L;
                        listener.accept(value(music, quality, TransferStage.DOWNLOADING, bytes, total, speed, ""));
                        lastUpdate = now;
                        lastBytes = bytes;
                    }
                }
            }
            long elapsed = Math.max(1L, System.nanoTime() - started);
            listener.accept(value(music, quality, TransferStage.DOWNLOADING, bytes, total,
                    bytes * 1_000_000_000L / elapsed, ""));
            AudioType type = detect(part);
            if (type.extension == null) throw new IllegalStateException("Downloaded file is not supported audio");
            Path raw = songDirectory.resolve(quality.apiValue() + "." + type.extension);
            move(part, raw);
            return raw;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static void flac(Path input, Path output) throws Exception {
        try (BufferedInputStream source = new BufferedInputStream(new FileInputStream(input.toFile()));
             FileOutputStream destination = new FileOutputStream(output.toFile())) {
            FLACDecoder decoder = new FLACDecoder(source);
            StreamInfo info = decoder.readStreamInfo();
            decoder.readMetadata(info);
            WavWriter writer = new WavWriter(destination, info);
            writer.writeHeader();
            decoder.addPCMProcessor(new PCMProcessor() {
                @Override public void processStreamInfo(StreamInfo ignored) {}
                @Override public void processPCM(ByteData pcm) {
                    try { writer.writePCM(pcm); }
                    catch (Exception error) { throw new PcmWriteFailure(error); }
                }
            });
            decoder.decodeFrames();
        } catch (PcmWriteFailure failure) {
            throw new IllegalStateException("Unable to write decoded FLAC", failure.getCause());
        }
    }

    private static Path findRaw(Path directory, Quality quality) throws Exception {
        try (var files = Files.list(directory)) {
            String prefix = quality.apiValue() + ".";
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> !path.getFileName().toString().endsWith(".wav"))
                    .filter(path -> !path.getFileName().toString().endsWith(".part"))
                    .findFirst().orElse(null);
        }
    }
    private static AudioType detect(Path file) throws Exception {
        byte[] head = new byte[12];
        int length;
        try (var input = Files.newInputStream(file)) { length = input.read(head); }
        if (length >= 4 && head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return AudioType.FLAC;
        if (length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return AudioType.WAV;
        if (length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return AudioType.MP3;
        if (length >= 2 && (head[0] & 0xff) == 0xff && (head[1] & 0xe0) == 0xe0) return AudioType.MP3;
        if (length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return AudioType.MP4;
        if (length >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return AudioType.OGG;
        return AudioType.UNKNOWN;
    }
    private static boolean validWav(Path file) { try { return Files.isRegularFile(file) && Files.size(file) > 44L && detect(file) == AudioType.WAV; } catch (Exception ignored) { return false; } }
    private static void checkCancelled(BooleanSupplier cancelled) { if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Cancelled(); }
    private static TransferStatus value(Music music, Quality quality, TransferStage stage, long done, long total, long speed, String error) {
        return new TransferStatus(music.id(), stage, done, total, speed, quality, error);
    }
    private static void touch(Path path) { try { Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())); } catch (Exception ignored) {} }
    private static void move(Path source, Path target) throws Exception {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private synchronized void updateIndex(Music music, Path songDirectory) throws Exception {
        EnumSet<Quality> qualities = EnumSet.noneOf(Quality.class);
        for (Quality quality : Quality.values()) {
            if (validWav(songDirectory.resolve(quality.apiValue() + ".wav"))) qualities.add(quality);
        }
        if (qualities.isEmpty()) {
            removeIndex(music.id());
            return;
        }
        cachedSongs.put(music.id(), new CachedSong(music, qualities, size(songDirectory), System.currentTimeMillis()));
        saveIndex();
    }

    private synchronized void removeIndex(long musicId) {
        if (cachedSongs.remove(musicId) != null) saveIndex();
    }

    private synchronized void loadIndex() {
        cachedSongs.clear();
        if (!Files.isRegularFile(indexFile)) return;
        try (var reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray values = root.has("songs") && root.get("songs").isJsonArray()
                    ? root.getAsJsonArray("songs") : new JsonArray();
            for (JsonElement value : values) {
                if (!value.isJsonObject()) continue;
                JsonObject entry = value.getAsJsonObject();
                Music music = GSON.fromJson(entry.get("music"), Music.class);
                if (music == null || music.id() <= 0L) continue;
                EnumSet<Quality> qualities = EnumSet.noneOf(Quality.class);
                if (entry.has("qualities") && entry.get("qualities").isJsonArray()) {
                    for (JsonElement quality : entry.getAsJsonArray("qualities")) {
                        Quality parsed = Quality.parse(quality.getAsString());
                        if (parsed != null) qualities.add(parsed);
                    }
                }
                long bytes = entry.has("sizeBytes") ? entry.get("sizeBytes").getAsLong() : 0L;
                long used = entry.has("lastUsedAt") ? entry.get("lastUsedAt").getAsLong() : 0L;
                if (!qualities.isEmpty()) cachedSongs.put(music.id(), new CachedSong(music, qualities, bytes, used));
            }
        } catch (Exception error) {
            cachedSongs.clear();
            System.err.println("[Nimbus] Ignoring damaged music cache index: " + message(error));
        }
    }

    private synchronized void reconcileIndex() {
        boolean changed = cachedSongs.entrySet().removeIf(entry -> {
            Path songDirectory = directory.resolve(Long.toString(entry.getKey()));
            return entry.getValue().qualities().stream()
                    .noneMatch(quality -> validWav(songDirectory.resolve(quality.apiValue() + ".wav")));
        });
        if (changed) saveIndex();
    }

    private synchronized void saveIndex() {
        Path temporary = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonArray values = new JsonArray();
            for (CachedSong cached : cachedSongs.values()) {
                JsonObject entry = new JsonObject();
                entry.add("music", GSON.toJsonTree(cached.music()));
                JsonArray qualities = new JsonArray();
                cached.qualities().forEach(quality -> qualities.add(quality.name()));
                entry.add("qualities", qualities);
                entry.addProperty("sizeBytes", cached.sizeBytes());
                entry.addProperty("lastUsedAt", cached.lastUsedAt());
                values.add(entry);
            }
            root.add("songs", values);
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, indexFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            System.err.println("[Nimbus] Failed to save music cache index: " + message(error));
        } finally {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }
    private List<Path> songDirectories() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) { return files.filter(Files::isDirectory).toList(); }
        catch (Exception ignored) { return List.of(); }
    }
    private static long size(Path path) throws Exception {
        if (!Files.exists(path)) return 0L;
        if (Files.isRegularFile(path)) return Files.size(path);
        try (var files = Files.list(path)) { return files.mapToLong(child -> { try { return size(child); } catch (Exception ignored) { return 0L; } }).sum(); }
    }
    private static void delete(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                for (Path child : files.toList()) delete(child);
            }
        }
        Files.deleteIfExists(path);
    }
    private static long id(Path path) { try { return Long.parseLong(path.getFileName().toString()); } catch (Exception ignored) { return -1L; } }
    private static long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (Exception ignored) { return 0L; } }
    private static String message(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }

    @Override public void close() {
        saveIndex();
        http.close();
    }

    private enum AudioType {
        FLAC("flac"), MP3("mp3"), WAV("wav"), MP4(null), OGG(null), UNKNOWN(null);
        private final String extension;
        AudioType(String extension) { this.extension = extension; }
    }
    private static final class Cancelled extends RuntimeException {}
    private static final class PcmWriteFailure extends RuntimeException { private PcmWriteFailure(Throwable cause) { super(cause); } }
}
