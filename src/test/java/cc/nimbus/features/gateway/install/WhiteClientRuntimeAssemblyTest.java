package cc.nimbus.features.gateway.install;

import cc.nimbus.features.gateway.launch.GatewayLaunchTarget;
import cc.nimbus.features.gateway.model.BujiIslandSnapshot;
import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayRole;
import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WhiteClientRuntimeAssemblyTest {
    @Test
    void assemblesMinecraftRootAndServerJarIntoRoleRuntime() throws Exception {
        Path root = Files.createTempDirectory("nimbus-runtime-test");
        try {
            Path base = root.resolve("base");
            Files.createDirectories(base.resolve(".minecraft/versions/1.20"));
            Files.writeString(base.resolve(".minecraft/versions/1.20/1.20.json"), "{}");
            Files.createDirectories(base.resolve(".minecraft/assets"));
            Path component = root.resolve("component");
            Files.createDirectories(component.resolve("nested"));
            Files.write(component.resolve("nested/protocol.jar"), new byte[] {1, 2, 3});

            WhiteClientInstallation installation = new WhiteClientInstallation(
                    root, base, base, null, List.of(), List.of(component)
            );
            GatewayLaunchTarget target = new GatewayLaunchTarget(
                    new GatewayAccount("42", "token", "sdk", "session", "Nimbus", "netease"),
                    new BujiIslandSnapshot("4661334467366178884", "布吉岛", "1.20", "127.0.0.1", 25565, List.of(
                            new GatewayRole("4661334467366178884", "42", "角色", 0, 0)
                    )),
                    new GatewayRole("4661334467366178884", "42", "角色", 0, 0)
            );

            var layout = new WhiteClientResourceInstaller(root.resolve("cache")).assembleRuntime(installation, target);
            assertTrue(Files.isRegularFile(layout.gameBaseMinecraft().resolve("versions/1.20/1.20.json")));
            assertTrue(Files.isRegularFile(layout.runtimeMinecraft().resolve("mods/protocol.jar")));
            assertEquals(layout.gameBaseMinecraft().resolve("assets"), layout.assetsDirectory());
            assertTrue(Files.isDirectory(layout.nativesDirectory()));
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
