package cc.nimbus.features.gateway.model;

import java.util.List;
import java.util.Objects;

public record WhiteClientComponent(
        String itemId,
        String entityId,
        int moduleTypeId,
        List<WhiteClientArtifact> artifacts
) {
    public WhiteClientComponent {
        itemId = Objects.requireNonNull(itemId, "itemId");
        entityId = Objects.requireNonNullElse(entityId, "");
        artifacts = List.copyOf(artifacts);
    }
}
