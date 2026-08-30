package cc.nimbus.features.gateway.launch;

import java.nio.file.Path;
import java.util.Objects;

/** Role-specific directories produced from the verified white-client packages. */
public record WhiteClientRuntimeLayout(
        Path gameBaseMinecraft,
        Path runtimeMinecraft,
        Path assetsDirectory,
        Path nativesDirectory
) {
    public WhiteClientRuntimeLayout {
        gameBaseMinecraft = require(gameBaseMinecraft, "gameBaseMinecraft");
        runtimeMinecraft = require(runtimeMinecraft, "runtimeMinecraft");
        assetsDirectory = require(assetsDirectory, "assetsDirectory");
        nativesDirectory = require(nativesDirectory, "nativesDirectory");
    }

    private static Path require(Path value, String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    }
}
