package cc.nimbus.features.music.resource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ArtworkCache implements AutoCloseable {
    private static final int LIMIT = 128;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, CompletableFuture<byte[]>> entries = new LinkedHashMap<>(32, 0.75f, true);

    public CompletableFuture<byte[]> load(String key, String url, Executor executor) {
        Objects.requireNonNull(key, "key");
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(new byte[0]);
        synchronized (entries) {
            CompletableFuture<byte[]> existing = entries.get(key);
            if (existing != null) return existing;
            CompletableFuture<byte[]> created = CompletableFuture.supplyAsync(() -> download(url), executor);
            created.whenComplete((ignored, error) -> { if (error != null) remove(key); });
            entries.put(key, created);
            trim();
            return created;
        }
    }

    public void remove(String key) { synchronized (entries) { entries.remove(key); } }
    public void clear() { synchronized (entries) { entries.clear(); } }

    private byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Nimbus-Music").GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Artwork HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Artwork request interrupted", interrupted);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load artwork", error);
        }
    }

    private void trim() {
        while (entries.size() > LIMIT) entries.remove(entries.keySet().iterator().next());
    }

    @Override public void close() { clear(); http.close(); }
}
