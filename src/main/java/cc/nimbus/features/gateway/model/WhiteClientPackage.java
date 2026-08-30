package cc.nimbus.features.gateway.model;

import java.net.URI;
import java.util.Objects;

public record WhiteClientPackage(
        String name,
        String version,
        int minecraftVersionId,
        URI archiveUrl,
        String archiveMd5,
        long archiveSize,
        URI coreLibrariesUrl,
        String coreLibrariesMd5,
        String coreLibrariesName,
        long coreLibrariesSize
) {
    public WhiteClientPackage {
        name = Objects.requireNonNullElse(name, "");
        version = Objects.requireNonNullElse(version, "");
        archiveUrl = Objects.requireNonNull(archiveUrl, "archiveUrl");
        archiveMd5 = Objects.requireNonNull(archiveMd5, "archiveMd5");
        coreLibrariesMd5 = Objects.requireNonNullElse(coreLibrariesMd5, "");
        coreLibrariesName = Objects.requireNonNullElse(coreLibrariesName, "");
    }
}
