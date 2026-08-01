package cc.nimbus.features.music.resource;

import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.internal.codec.jflac.FLACDecoder;
import cc.nimbus.features.music.internal.codec.jflac.PCMProcessor;
import cc.nimbus.features.music.internal.codec.jflac.metadata.StreamInfo;
import cc.nimbus.features.music.internal.codec.jflac.util.ByteData;
import cc.nimbus.features.music.internal.codec.jflac.util.WavWriter;
import cc.nimbus.features.music.internal.codec.jl.converter.Converter;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.provider.NeteaseApi;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class AudioCache implements AutoCloseable {
    private final Path directory;
    private final NeteaseApi api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<Long, ReentrantLock> songLocks = new ConcurrentHashMap<>();

    public AudioCache(Path directory, NeteaseApi api) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.api = Objects.requireNonNull(api, "api");
    }

    public void init() {
        try { Files.createDirectories(directory); }
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
            try { delete(child); } catch (Exception ignored) {} finally { lock.unlock(); }
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

    @Override public void close() { http.close(); }

    private enum AudioType {
        FLAC("flac"), MP3("mp3"), WAV("wav"), MP4(null), OGG(null), UNKNOWN(null);
        private final String extension;
        AudioType(String extension) { this.extension = extension; }
    }
    private static final class Cancelled extends RuntimeException {}
    private static final class PcmWriteFailure extends RuntimeException { private PcmWriteFailure(Throwable cause) { super(cause); } }
}
