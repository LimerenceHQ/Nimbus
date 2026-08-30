package cc.nimbus.features.gateway.model;

import java.net.URI;
import java.util.Objects;

public record WhiteClientArtifact(
        URI archiveUrl,
        String archiveName,
        String archiveMd5,
        String jarMd5,
        long archiveSize,
        String minecraftVersion,
        int javaVersion
) {
    public WhiteClientArtifact {
        archiveUrl = Objects.requireNonNull(archiveUrl, "archiveUrl");
        archiveName = Objects.requireNonNull(archiveName, "archiveName");
        archiveMd5 = Objects.requireNonNullElse(archiveMd5, "");
        jarMd5 = Objects.requireNonNullElse(jarMd5, "");
        minecraftVersion = Objects.requireNonNullElse(minecraftVersion, "");
    }
}
