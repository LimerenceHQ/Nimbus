package cc.nimbus.managers;

import cc.nimbus.features.module.modules.hud.PostProcess;
import cc.nimbus.features.module.modules.render.ESP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultConfigRestoresModuleAndSettingState() {
        Path configs = temporaryDirectory.resolve("configs");

        ModuleManager firstModules = new ModuleManager();
        ConfigManager firstConfig = new ConfigManager(configs, firstModules);
        firstModules.init();
        firstConfig.init();
        try {
            PostProcess postProcess = firstModules.getModule(PostProcess.class);
            ESP esp = firstModules.getModule(ESP.class);
            assertNotNull(postProcess);
            assertNotNull(esp);

            postProcess.setEnabled(false);
            postProcess.blur_mode.setValue("Kawase");
            esp.outlineColor.setRainbow(true);
            assertTrue(firstConfig.isDirty());
            assertTrue(firstConfig.saveConfig(ConfigManager.DEFAULT_CONFIG));
        } finally {
            firstConfig.shutdown();
            firstModules.shutdown();
        }

        ModuleManager restoredModules = new ModuleManager();
        ConfigManager restoredConfig = new ConfigManager(configs, restoredModules);
        restoredModules.init();
        restoredConfig.init();
        try {
            PostProcess postProcess = restoredModules.getModule(PostProcess.class);
            ESP esp = restoredModules.getModule(ESP.class);
            assertFalse(postProcess.isEnabled(), "persisted state must override enableOnStartUp");
            assertEquals("Kawase", postProcess.blur_mode.getValue());
            assertTrue(esp.outlineColor.isRainbow());
            assertFalse(restoredConfig.isDirty());
        } finally {
            restoredConfig.shutdown();
            restoredModules.shutdown();
        }
    }

    @Test
    void configListHasNoAccountSpecificRules() throws Exception {
        ModuleManager modules = new ModuleManager();
        ConfigManager config = new ConfigManager(temporaryDirectory, modules);
        modules.init();
        config.init();
        try {
            Files.writeString(temporaryDirectory.resolve("alts.json"), "[]");
            Files.writeString(temporaryDirectory.resolve("lastalt.json"), "{}");
            assertEquals(
                    java.util.List.of("alts", ConfigManager.DEFAULT_CONFIG, "lastalt"),
                    config.getConfigs()
            );
        } finally {
            config.shutdown();
            modules.shutdown();
        }
    }
}
