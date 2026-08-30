package cc.nimbus.features.gateway.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Verified and safely extracted white-client resources, not yet assembled into a role runtime. */
public record WhiteClientInstallation(
        Path root,
        Path basePackage,
        Path versionPackage,
        Path coreLibraries,
        List<Path> coreComponents,
        List<Path> serverComponents
) {
    public WhiteClientInstallation {
        root = Objects.requireNonNull(root, "root");
        basePackage = Objects.requireNonNull(basePackage, "basePackage");
        versionPackage = Objects.requireNonNull(versionPackage, "versionPackage");
        coreComponents = List.copyOf(coreComponents);
        serverComponents = List.copyOf(serverComponents);
    }
}
