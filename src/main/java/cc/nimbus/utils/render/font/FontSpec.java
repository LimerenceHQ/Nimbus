package cc.nimbus.utils.render.font;

import java.util.Objects;

public record FontSpec(FontFace family, float weight) {
    public FontSpec(FontFace family) {
        this(family, 400f);
    }

    public FontSpec {
        Objects.requireNonNull(family, "family");
        if (!Float.isFinite(weight) || weight < 100f || weight > 900f) {
            throw new IllegalArgumentException("Font weight must be between 100 and 900: " + weight);
        }
    }

    public FontSpec weight(float weight) {
        return this.weight == weight ? this : new FontSpec(family, weight);
    }
}
