package cc.nimbus.features.gateway.install;

import cc.nimbus.features.gateway.model.WhiteClientArtifact;
import cc.nimbus.features.gateway.model.WhiteClientComponent;
import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import cc.nimbus.features.gateway.model.WhiteClientManifest;
import cc.nimbus.features.gateway.model.WhiteClientPackage;
import cc.nimbus.features.gateway.launch.WhiteClientRuntimeLayout;
import cc.nimbus.features.gateway.launch.GatewayLaunchTarget;
import cc.nimbus.features.gateway.net.GatewayProtocolException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads, verifies and extracts NetEase white-client archives without executing their contents. */
public final class WhiteClientResourceInstaller {
    private static final long MAX_EXPANDED_BYTES = 32L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 200_000;
    private static final byte[] SEVEN_Z_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    private final Path root;
    private final HttpClient http;
    private static final String NETEASE_RUNTIME_DLL = "nimbus/gateway/native/api-ms-win-crt-utility-l1-1-1.dll";

    public WhiteClientResourceInstaller(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public WhiteClientInstallation prepare(WhiteClientManifest manifest) throws GatewayProtocolException {
        Objects.requireNonNull(manifest, "manifest");
        try {
            Files.createDirectories(root);
            Path packages = root.resolve("packages");
            Path base = preparePackage(manifest.basePackage(), packages.resolve("base"), "base-client");
            Path version = preparePackage(manifest.versionPackage(), packages.resolve("version"), "version-client");
            Path coreLibraries = prepareCoreLibraries(manifest.versionPackage(), packages.resolve("core-libraries"));
            List<Path> core = prepareComponent(manifest.coreComponents(), packages.resolve("core-components"));
            List<Path> server = prepareComponent(List.of(manifest.serverComponent()), packages.resolve("server-components"));
            return new WhiteClientInstallation(root, base, version, coreLibraries, core, server);
        } catch (IOException error) {
            throw new GatewayProtocolException("无法准备白端资源", error);
        }
    }

    /** Mirrors the official role runtime preparation after package verification. */
    public WhiteClientRuntimeLayout assembleRuntime(
            WhiteClientInstallation installation,
            GatewayLaunchTarget target
    ) throws GatewayProtocolException {
        Objects.requireNonNull(installation, "installation");
        Objects.requireNonNull(target, "target");
        try {
            Path baseSource = findMinecraftRoot(installation.basePackage());
            Path gameBase = installation.root().resolve("assembled-base").resolve(".minecraft");
            copyTree(baseSource, gameBase);
            copyTree(findMinecraftRoot(installation.versionPackage()), gameBase);
            if (installation.coreLibraries() != null) copyTree(installation.coreLibraries(), gameBase);

            String role = target.role().name().replaceAll("[^A-Za-z0-9._-]", "_");
            if (role.isBlank()) role = "role";
            Path runtimeMinecraft = installation.root().resolve("runtime")
                    .resolve(target.server().gameId() + "-" + role).resolve(".minecraft");
            Files.createDirectories(runtimeMinecraft);
            Path assets = gameBase.resolve("assets");
            Path natives = gameBase.resolve("versions").resolve(target.server().gameVersion()).resolve("natives");
            Files.createDirectories(assets);
            Files.createDirectories(natives);
            installNeteaseRuntime(natives);
            copyTree(gameBase, runtimeMinecraft);
            List<Path> components = new ArrayList<>();
            components.addAll(installation.coreComponents());
            components.addAll(installation.serverComponents());
            for (Path component : components) copyComponent(component, runtimeMinecraft);

            return new WhiteClientRuntimeLayout(gameBase, runtimeMinecraft, assets, natives);
        } catch (IOException | RuntimeException error) {
            throw new GatewayProtocolException("无法组装白端角色运行目录", error);
        }
    }

    /**
     * The official NEL launcher places the JNI bridge runtime under
     * versions/<version>/natives/runtime.  It is not part of the ordinary
     * LWJGL natives archive and must be copied explicitly before Java starts.
     */
    private static void installNeteaseRuntime(Path natives) throws IOException, GatewayProtocolException {
        Path runtime = natives.resolve("runtime");
        Files.createDirectories(runtime);
        Path target = runtime.resolve("api-ms-win-crt-utility-l1-1-1.dll");
        try (InputStream input = WhiteClientResourceInstaller.class.getClassLoader()
                .getResourceAsStream(NETEASE_RUNTIME_DLL)) {
            if (input == null) throw new GatewayProtocolException("Nimbus Gateway native runtime resource is missing");
            Path temporary = target.resolveSibling(target.getFileName() + ".part");
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomic(temporary, target);
        }
    }

    private static Path findMinecraftRoot(Path source) {
        Path nested = source.resolve(".minecraft");
        return Files.isDirectory(nested) ? nested : source;
    }

    private static void copyComponent(Path source, Path runtimeMinecraft) throws IOException {
        Path nested = source.resolve(".minecraft");
        if (Files.isDirectory(nested)) {
            copyTree(nested, runtimeMinecraft);
            return;
        }
        Path mods = source.resolve("mods");
        if (Files.isDirectory(mods)) {
            copyTree(source, runtimeMinecraft);
            return;
        }
        Path targetMods = runtimeMinecraft.resolve("mods");
        Files.createDirectories(targetMods);
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    Files.copy(file, targetMods.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path relative = source.relativize(file);
                Path output = target.resolve(relative.toString());
                if (Files.isDirectory(file)) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path preparePackage(WhiteClientPackage value, Path directory, String archiveLabel)
            throws IOException, GatewayProtocolException {
        Path archive = downloadVerified(
                value.archiveUrl(),
                root.resolve("downloads").resolve(archiveLabel + archiveSuffix(value.archiveUrl())),
                value.archiveSize(),
                value.archiveMd5()
        );
        extractVerified(archive, directory, value.archiveMd5());
        return directory;
    }

    private Path prepareCoreLibraries(WhiteClientPackage value, Path directory)
            throws IOException, GatewayProtocolException {
        if (value.coreLibrariesUrl() == null) return null;
        String fileName = safeFileName(value.coreLibrariesName(), "core-libraries" + archiveSuffix(value.coreLibrariesUrl()));
        Path archive = downloadVerified(
                value.coreLibrariesUrl(),
                root.resolve("downloads").resolve(fileName),
                value.coreLibrariesSize(),
                value.coreLibrariesMd5()
        );
        extractVerified(archive, directory, value.coreLibrariesMd5());
        return directory;
    }

    private List<Path> prepareComponent(List<WhiteClientComponent> components, Path directory)
            throws IOException, GatewayProtocolException {
        List<Path> prepared = new ArrayList<>();
        for (WhiteClientComponent component : components) {
            String componentId = safeSegment(component.itemId()) + "-" + component.moduleTypeId();
            int index = 0;
            for (WhiteClientArtifact artifact : component.artifacts()) {
                String archiveName = safeFileName(
                        artifact.archiveName(),
                        componentId + "-" + index + archiveSuffix(artifact.archiveUrl())
                );
                Path archive = downloadVerified(
                        artifact.archiveUrl(),
                        root.resolve("downloads").resolve("components").resolve(componentId).resolve(archiveName),
                        artifact.archiveSize(),
                        artifact.archiveMd5()
                );
                Path target = directory.resolve(componentId).resolve(Integer.toString(index));
                extractVerified(archive, target, artifact.archiveMd5());
                prepared.add(target);
                index++;
            }
        }
        return List.copyOf(prepared);
    }

    private Path downloadVerified(URI uri, Path target, long expectedSize, String expectedMd5)
            throws IOException, GatewayProtocolException {
        requireHttpUri(uri);
        Files.createDirectories(target.getParent());
        if (Files.isRegularFile(target) && matches(target, expectedSize, expectedMd5)) return target;

        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(temporary);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(15)).GET().build();
        try {
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GatewayProtocolException("白端资源下载失败 (HTTP " + response.statusCode() + ")");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(temporary);
            throw new GatewayProtocolException("白端资源下载已取消", interrupted);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
        if (!matches(temporary, expectedSize, expectedMd5)) {
            Files.deleteIfExists(temporary);
            throw new GatewayProtocolException("白端资源大小或 MD5 校验失败: " + target.getFileName());
        }
        moveAtomic(temporary, target);
        return target;
    }

    private static void extractVerified(Path archive, Path target, String archiveMd5)
            throws IOException, GatewayProtocolException {
        Path marker = target.resolveSibling(target.getFileName() + ".md5");
        String expectedMarker = Objects.requireNonNullElse(archiveMd5, "").trim().toLowerCase(Locale.ROOT);
        if (Files.isDirectory(target) && Files.isRegularFile(marker)
                && Files.readString(marker).trim().equalsIgnoreCase(expectedMarker)) {
            return;
        }

        Path staging = target.resolveSibling(target.getFileName() + ".staging");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            extractArchive(archive, staging);
            deleteTree(target);
            moveAtomic(staging, target);
            Files.writeString(marker, expectedMarker);
        } catch (IOException | GatewayProtocolException | RuntimeException error) {
            deleteTree(staging);
            throw error;
        }
    }

    static void extractArchive(Path archive, Path destination) throws IOException, GatewayProtocolException {
        byte[] header = new byte[SEVEN_Z_MAGIC.length];
        try (InputStream input = Files.newInputStream(archive)) {
            int read = input.read(header);
            if (read >= 4 && header[0] == 'P' && header[1] == 'K') {
                extractZip(archive, destination);
                return;
            }
            if (read == SEVEN_Z_MAGIC.length && MessageDigest.isEqual(header, SEVEN_Z_MAGIC)) {
                extractSevenZ(archive, destination);
                return;
            }
        }
        throw new GatewayProtocolException("不支持的白端压缩格式: " + archive.getFileName());
    }

    private static void extractZip(Path archive, Path destination) throws IOException, GatewayProtocolException {
        ExtractionBudget budget = new ExtractionBudget();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = safeEntry(destination, entry.getName());
                budget.entry();
                if (entry.isDirectory()) Files.createDirectories(output);
                else writeEntry(zip, output, budget);
                zip.closeEntry();
            }
        }
    }

    private static void extractSevenZ(Path archive, Path destination) throws IOException, GatewayProtocolException {
        ExtractionBudget budget = new ExtractionBudget();
        try (SevenZFile sevenZ = SevenZFile.builder().setPath(archive).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                Path output = safeEntry(destination, entry.getName());
                budget.entry();
                if (entry.isDirectory()) Files.createDirectories(output);
                else writeSevenZEntry(sevenZ, output, budget);
            }
        }
    }

    private static void writeEntry(InputStream input, Path output, ExtractionBudget budget)
            throws IOException, GatewayProtocolException {
        Files.createDirectories(output.getParent());
        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(output))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                budget.bytes(read);
                stream.write(buffer, 0, read);
            }
        }
    }

    private static void writeSevenZEntry(SevenZFile input, Path output, ExtractionBudget budget)
            throws IOException, GatewayProtocolException {
        Files.createDirectories(output.getParent());
        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(output))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                budget.bytes(read);
                stream.write(buffer, 0, read);
            }
        }
    }

    private static Path safeEntry(Path destination, String entryName) throws GatewayProtocolException {
        if (entryName == null || entryName.isBlank()) throw new GatewayProtocolException("白端压缩包包含空路径");
        Path root = destination.toAbsolutePath().normalize();
        Path output = root.resolve(entryName.replace('\\', '/')).normalize();
        if (!output.startsWith(root) || output.equals(root)) {
            throw new GatewayProtocolException("白端压缩包包含不安全路径: " + entryName);
        }
        return output;
    }

    static boolean matches(Path file, long expectedSize, String expectedMd5) throws IOException {
        if (expectedSize > 0 && Files.size(file) != expectedSize) return false;
        String md5 = Objects.requireNonNullElse(expectedMd5, "").trim();
        return md5.isEmpty() || md5(file).equalsIgnoreCase(md5);
    }

    static String md5(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("MD5 is unavailable", impossible);
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void requireHttpUri(URI uri) throws GatewayProtocolException {
        if (uri == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new GatewayProtocolException("白端资源地址不是 HTTP(S)");
        }
    }

    private static String safeFileName(String proposed, String fallback) {
        String value = proposed == null ? "" : proposed.trim();
        if (value.isEmpty()) return fallback;
        try {
            Path path = Path.of(value);
            if (path.getNameCount() != 1 || !path.getFileName().toString().equals(value)) return fallback;
            return safeSegment(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String safeSegment(String value) {
        String result = Objects.requireNonNullElse(value, "").replaceAll("[^A-Za-z0-9._-]", "_");
        return result.isBlank() || result.equals(".") || result.equals("..") ? "resource" : result;
    }

    private static String archiveSuffix(URI uri) {
        String path = uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".7z")) return ".7z";
        if (path.endsWith(".zip")) return ".zip";
        return ".archive";
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var entries = Files.walk(directory)) {
            for (Path path : entries.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class ExtractionBudget {
        private long bytes;
        private int entries;

        private void entry() throws GatewayProtocolException {
            if (++entries > MAX_ENTRIES) throw new GatewayProtocolException("白端压缩包文件数量超过安全限制");
        }

        private void bytes(int count) throws GatewayProtocolException {
            bytes += count;
            if (bytes > MAX_EXPANDED_BYTES) throw new GatewayProtocolException("白端压缩包解压大小超过安全限制");
        }
    }
}
