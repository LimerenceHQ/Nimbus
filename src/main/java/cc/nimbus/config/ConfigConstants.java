package cc.nimbus.config;

import java.nio.file.Path;

public final class ConfigConstants {
    public static volatile Path CONFIG_DIR = Path.of("config", "nimbus", "configs");
    public static final String JSON_EXT = ".json";
    public static final String TEMP_EXT = ".json.tmp";

    private ConfigConstants() {}
    static void configure(Path directory) { CONFIG_DIR = directory; }
}
