package cc.nimbus.features.gateway.install;

import cc.nimbus.features.gateway.net.GatewayProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WhiteClientResourceInstallerTest {
    @Test
    void extractsZipAndRejectsTraversal() throws Exception {
        Path temp = Files.createTempDirectory("nimbus-gateway-test");
        try {
            Path archive = temp.resolve("ok.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("nested/file.txt"));
                zip.write("Nimbus".getBytes());
                zip.closeEntry();
            }
            Path destination = temp.resolve("out");
            WhiteClientResourceInstaller.extractArchive(archive, destination);
            assertEquals("Nimbus", Files.readString(destination.resolve("nested/file.txt")));

            Path malicious = temp.resolve("bad.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(malicious))) {
                zip.putNextEntry(new ZipEntry("../escape.txt"));
                zip.write("bad".getBytes());
                zip.closeEntry();
            }
            assertThrows(
                    GatewayProtocolException.class,
                    () -> WhiteClientResourceInstaller.extractArchive(malicious, temp.resolve("bad-out"))
            );
        } finally {
            try (var entries = Files.walk(temp)) {
                entries.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    @Test
    void verifiesExpectedSizeAndMd5() throws Exception {
        Path file = Files.createTempFile("nimbus-gateway-md5", ".bin");
        try {
            Files.writeString(file, "Nimbus");
            String md5 = WhiteClientResourceInstaller.md5(file);
            assertEquals(6, Files.size(file));
            assertEquals(true, WhiteClientResourceInstaller.matches(file, 6, md5));
            assertEquals(false, WhiteClientResourceInstaller.matches(file, 7, md5));
            assertEquals(false, WhiteClientResourceInstaller.matches(file, 6, "00000000000000000000000000000000"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
