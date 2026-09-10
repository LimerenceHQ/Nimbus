package cc.nimbus.features.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ProxyPayload {
    private static final String RESOURCE = "/assets/nimbus/native/windows-x86_64/nimbus_proxy.dll";
    private ProxyPayload() {}
    private static volatile Path logDirectory;

    public static Path logDirectory() { return logDirectory; }

    public static Path extractBundled(Path directory) throws IOException {
        try (InputStream input = ProxyPayload.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Nimbus 未打包 A 端 DLL；请使用 -PbundleNativeProxy=true 构建");
            return extract(directory, input);
        }
    }

    public static Path extract(Path directory, InputStream input) throws IOException {
        byte[] bytes = input.readAllBytes();
        if (bytes.length == 0) throw new IOException("A 端 DLL 资源为空");
        byte[] digest = sha256(bytes);
        logDirectory = directory.toAbsolutePath();
        Path parent = directory.toAbsolutePath().resolve("native");
        Files.createDirectories(parent);
        // Keep the content-addressed cache in the original directory without
        // embedding the product name in the runtime filename.
        Path target = parent.resolve(HexFormat.of().formatHex(digest) + ".dll");
        if (Files.isRegularFile(target) && MessageDigest.isEqual(digest, sha256(Files.readAllBytes(target)))) return target;
        Path temp = Files.createTempFile(parent, "payload-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally { Files.deleteIfExists(temp); }
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
