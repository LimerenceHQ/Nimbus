package cc.nimbus.features.gateway.model;

import java.util.Objects;

/** MPay device identity bound to the local Windows user. */
public record GatewayDevice(String uniqueId, String deviceId, String deviceKey) {
    public GatewayDevice {
        uniqueId = requireValue(uniqueId, "uniqueId");
        deviceId = requireValue(deviceId, "deviceId");
        deviceKey = requireValue(deviceKey, "deviceKey");
    }

    private static String requireValue(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }
}
