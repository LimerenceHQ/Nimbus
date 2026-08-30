package cc.nimbus.features.gateway.install;

import cc.nimbus.features.gateway.net.GatewayProtocolException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Downloads the managed Java runtime using the same resource contract as NEL/Fantnel. */
public final class GatewayJavaRuntimeInstaller {
    private static final URI DEFAULT_RESOURCE_API = URI.create("http://110.42.70.32:13423/api/fantnel/resource/md5");
    private final Path javaRoot;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GatewayJavaRuntimeInstaller(Path gatewayRoot) {
        javaRoot = gatewayRoot.resolve("Java").resolve("jdk17").toAbsolutePath().normalize();
    }

    public Path prepare() throws GatewayProtocolException {
        try {
            Path executable = findExecutable(javaRoot);
            if (executable != null && isJava17(executable)) return executable;
            URI api = URI.create(System.getProperty("nimbus.gateway.javaResourceApi", DEFAULT_RESOURCE_API.toString()));
            String query = api + "?id=win.x64.jdk17.java";
            String body = http.send(HttpRequest.newBuilder(URI.create(query)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
            String url = string(data, "url");
            String md5 = string(data, "md5");
            if (url.isBlank() || md5.isBlank()) throw new IOException("Java 资源响应缺少 url/md5");
            Files.createDirectories(javaRoot);
            Path archive = javaRoot.resolveSibling("jdk17.zip");
            HttpResponse<Path> download = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(archive));
            if (download.statusCode() < 200 || download.statusCode() >= 300 || !WhiteClientResourceInstaller.matches(archive, 0, md5)) {
                Files.deleteIfExists(archive);
                throw new IOException("Java 17 下载或 MD5 校验失败");
            }
            Path staging = javaRoot.resolveSibling("jdk17.staging");
            deleteTree(staging);
            Files.createDirectories(staging);
            WhiteClientResourceInstaller.extractArchive(archive, staging);
            deleteTree(javaRoot);
            Files.move(staging, javaRoot, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(javaRoot.resolveSibling("jdk17.md5"), md5);
            Files.deleteIfExists(archive);
            executable = findExecutable(javaRoot);
            if (executable == null || !isJava17(executable)) throw new IOException("Java 17 解压后不可执行");
            return executable;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GatewayProtocolException("Java 17 下载已取消", interrupted);
        } catch (IOException | RuntimeException error) {
            throw new GatewayProtocolException("无法准备官方 Java 17 运行时", error);
        }
    }

    private static Path findExecutable(Path root) {
        for (String name : new String[]{"javaw.exe", "java.exe"}) {
            Path path = root.resolve("bin").resolve(name);
            if (Files.isRegularFile(path)) return path;
        }
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equalsIgnoreCase("java.exe"))
                    .findFirst().orElse(null);
        } catch (IOException ignored) { return null; }
    }

    private static boolean isJava17(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); return false; }
            return process.exitValue() == 0 && (output.contains("version \"17") || output.contains("openjdk 17"));
        } catch (Exception ignored) { return false; }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(file);
        }
    }
}
