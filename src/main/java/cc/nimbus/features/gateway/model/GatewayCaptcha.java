package cc.nimbus.features.gateway.model;

import java.util.Arrays;
import java.util.Objects;

/** One-use 4399 captcha challenge. Image data is intentionally never persisted. */
public record GatewayCaptcha(String sessionId, byte[] image) {
    public GatewayCaptcha {
        sessionId = Objects.requireNonNull(sessionId, "sessionId").trim();
        if (sessionId.isEmpty()) throw new IllegalArgumentException("sessionId must not be blank");
        image = Arrays.copyOf(Objects.requireNonNull(image, "image"), image.length);
        if (image.length == 0) throw new IllegalArgumentException("captcha image must not be empty");
    }

    @Override
    public byte[] image() {
        return Arrays.copyOf(image, image.length);
    }
}
