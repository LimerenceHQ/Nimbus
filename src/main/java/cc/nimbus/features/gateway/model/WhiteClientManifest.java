package cc.nimbus.features.gateway.model;

import java.util.List;
import java.util.Objects;

/** Complete NetEase resource manifest required before starting the white client. */
public record WhiteClientManifest(
        WhiteClientPackage basePackage,
        WhiteClientPackage versionPackage,
        List<WhiteClientComponent> coreComponents,
        WhiteClientComponent serverComponent
) {
    public WhiteClientManifest {
        basePackage = Objects.requireNonNull(basePackage, "basePackage");
        versionPackage = Objects.requireNonNull(versionPackage, "versionPackage");
        coreComponents = List.copyOf(coreComponents);
        serverComponent = Objects.requireNonNull(serverComponent, "serverComponent");
    }
}
