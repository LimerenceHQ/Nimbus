package cc.nimbus.features.music;

import java.util.Locale;

public enum Quality {
    STANDARD("standard"),
    HIGHER("higher"),
    EXHIGH("exhigh"),
    LOSSLESS("lossless"),
    HIRES("hires"),
    JYEFFECT("jyeffect"),
    SKY("sky"),
    DOLBY("dolby"),
    JYMASTER("jymaster");

    private final String apiValue;

    Quality(String apiValue) { this.apiValue = apiValue; }
    public String apiValue() { return apiValue; }
    public static Quality parse(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Quality quality : values()) {
            if (quality.name().equalsIgnoreCase(normalized) || quality.apiValue.equals(normalized)) return quality;
        }
        return null;
    }
}
