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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public final class ArtworkCache implements AutoCloseable {
    private static final int LIMIT = 128;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, CompletableFuture<byte[]>> entries = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<String, CompletableFuture<Artwork>> artworkEntries = new LinkedHashMap<>(32, 0.75f, true);

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

    public CompletableFuture<Artwork> loadArtwork(String key, String url, Executor executor) {
        Objects.requireNonNull(key, "key");
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(new Artwork(null, null));
        synchronized (artworkEntries) {
            CompletableFuture<Artwork> existing = artworkEntries.get(key);
            if (existing != null) return existing;
            CompletableFuture<Artwork> created = CompletableFuture
                    .supplyAsync(() -> download(url), executor)
                    .thenApply(bytes -> new Artwork(bytes, palette(bytes)));
            created.whenComplete((ignored, error) -> { if (error != null) remove(key); });
            artworkEntries.put(key, created);
            trimArtwork();
            return created;
        }
    }

    public void remove(String key) {
        synchronized (entries) { entries.remove(key); }
        synchronized (artworkEntries) { artworkEntries.remove(key); }
    }
    public void clear() {
        synchronized (entries) { entries.clear(); }
        synchronized (artworkEntries) { artworkEntries.clear(); }
    }

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

    private void trimArtwork() {
        while (artworkEntries.size() > LIMIT) artworkEntries.remove(artworkEntries.keySet().iterator().next());
    }

    private static int[] palette(byte[] encoded) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
            if (image == null) return null;
            int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 48);
            Map<Integer, Bucket> buckets = new HashMap<>();
            for (int y = 0; y < image.getHeight(); y += step) {
                for (int x = 0; x < image.getWidth(); x += step) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) < 160) continue;
                    int red = argb >> 16 & 0xFF;
                    int green = argb >> 8 & 0xFF;
                    int blue = argb & 0xFF;
                    int maximum = Math.max(red, Math.max(green, blue));
                    int minimum = Math.min(red, Math.min(green, blue));
                    if (maximum < 24 || minimum > 238) continue;
                    int key = (red >> 4) << 8 | (green >> 4) << 4 | blue >> 4;
                    buckets.computeIfAbsent(key, ignored -> new Bucket()).add(red, green, blue);
                }
            }
            List<Bucket> sorted = new ArrayList<>(buckets.values());
            sorted.sort(Comparator.comparingDouble(Bucket::score).reversed());
            List<Integer> colors = new ArrayList<>(3);
            for (Bucket bucket : sorted) {
                int color = bucket.color();
                if (colors.stream().allMatch(existing -> distance(existing, color) > 52 * 52)) colors.add(color);
                if (colors.size() == 3) break;
            }
            while (colors.size() < 3) colors.add(colors.isEmpty() ? 0xFF30343A : shade(colors.getFirst(), colors.size()));
            return colors.stream().mapToInt(Integer::intValue).toArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int distance(int first, int second) {
        int red = (first >> 16 & 0xFF) - (second >> 16 & 0xFF);
        int green = (first >> 8 & 0xFF) - (second >> 8 & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return red * red + green * green + blue * blue;
    }

    private static int shade(int color, int index) {
        float factor = index == 1 ? .68f : 1.18f;
        int red = Math.clamp(Math.round((color >> 16 & 0xFF) * factor), 0, 255);
        int green = Math.clamp(Math.round((color >> 8 & 0xFF) * factor), 0, 255);
        int blue = Math.clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static final class Bucket {
        private long red;
        private long green;
        private long blue;
        private int count;

        private void add(int red, int green, int blue) {
            this.red += red;
            this.green += green;
            this.blue += blue;
            count++;
        }

        private int color() {
            if (count == 0) return 0xFF30343A;
            return 0xFF000000 | (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
        }

        private double score() {
            int color = color();
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            int saturation = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
            return count * (1d + saturation / 255d);
        }
    }

    @Override public void close() { clear(); http.close(); }
}
