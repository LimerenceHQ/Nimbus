package cc.nimbus.features.client;

import java.util.Arrays;

public enum ClientLanguage {
    FOLLOW_GAME("Follow Game", null),
    ENGLISH("English", "en_us"),
    SIMPLIFIED_CHINESE("Simplified Chinese", "zh_cn");

    private final String value;
    private final String languageCode;

    ClientLanguage(String value, String languageCode) {
        this.value = value;
        this.languageCode = languageCode;
    }

    public String value() {
        return value;
    }

    public String languageCode() {
        return languageCode;
    }

    public static ClientLanguage fromValue(String value) {
        return Arrays.stream(values())
                .filter(language -> language.value.equals(value))
                .findFirst()
                .orElse(FOLLOW_GAME);
    }

    public static String[] valuesForSetting() {
        return Arrays.stream(values()).map(ClientLanguage::value).toArray(String[]::new);
    }
}
