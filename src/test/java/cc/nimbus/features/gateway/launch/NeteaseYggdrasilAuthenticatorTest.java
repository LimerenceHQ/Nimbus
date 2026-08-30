package cc.nimbus.features.gateway.launch;

import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NeteaseYggdrasilAuthenticatorTest {
    @Test
    void writesLauncherVersionBeforeMinecraftVersionInJoinMessage() throws Exception {
        byte[] message = NeteaseYggdrasilAuthenticator.buildJoinMessage(
                "4661334467366178884", "server-id", "1.15.28.11160", "1.20", 123,
                "salt", "{\"mods\":[]}", "bootstrap", "dat", 42, new byte[16]
        );
        ByteBuffer input = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(4661334467366178884L, input.getLong());
        assertEquals("server-id", readByteString(input));
        assertEquals("1.15.28.11160", readByteString(input));
        assertEquals("1.20", readByteString(input));
        assertEquals(123, input.getInt());
    }

    @Test
    void serializesCoreAndServerModsExactlyLikeNel() throws Exception {
        Path root = Files.createTempDirectory("nimbus-yggdrasil-mods");
        try {
            Path core = root.resolve("core-components/4681704866889354274-3/0");
            Path server = root.resolve("server-components/4661334467366178884-2/0");
            Files.createDirectories(core);
            Files.createDirectories(server);
            Files.write(core.resolve("NeteaseNetOffcialMod.jar"), new byte[]{1});
            Files.write(server.resolve("4690063640154067974@2@8.jar"), new byte[]{2});
            WhiteClientInstallation installation = new WhiteClientInstallation(
                    root, root, root, null, List.of(core), List.of(server)
            );

            assertEquals(
                    "{\"mods\":["
                            + "{\"modPath\":\"4681704866889354274@3@0.jar\",\"name\":\"\","
                            + "\"id\":\"4681704866889354274@3@0.jar\",\"iid\":\"4681704866889354274\","
                            + "\"md5\":\"55A54008AD1BA589AA210D2629C1DF41\",\"version\":\"\"},"
                            + "{\"modPath\":\"4690063640154067974@2@8.jar\",\"name\":\"\","
                            + "\"id\":\"4690063640154067974@2@8.jar\",\"iid\":\"4690063640154067974\","
                            + "\"md5\":\"9E688C58A5487B8EAF69C9E1005AD0BF\",\"version\":\"\"}]}",
                    NeteaseYggdrasilAuthenticator.modInfo(installation)
            );
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    private static String readByteString(ByteBuffer input) {
        byte[] value = new byte[input.get() & 0xFF];
        input.get(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
