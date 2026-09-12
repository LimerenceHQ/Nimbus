package cc.nimbus.features.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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
        Path parent = directory.toAbsolutePath().resolve("native");
        Files.createDirectories(parent);
        logDirectory = parent.resolve("log");
        Files.createDirectories(logDirectory);
        Path target = parent.resolve(UUID.randomUUID().toString().replace("-", "") + ".dll");
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

}
