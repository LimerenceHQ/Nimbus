package cc.nimbus.features.module.api;

import cc.nimbus.features.module.api.setting.Setting;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicModuleTest {
    @Test
    void discoversSettingsAndPreservesDynamicRegistration() {
        TestModule module = new TestModule();

        assertEquals(
                List.of("Inherited", "Number", "Runtime"),
                module.getSettings().stream().map(Setting::getName).toList()
        );
        assertThrows(UnsupportedOperationException.class, () -> module.getSettings().clear());

        AtomicInteger changes = new AtomicInteger();
        module.bindStateListeners(changes::incrementAndGet, () -> {});
        module.number.setValue(7);
        assertEquals(1, changes.get());
    }

    private abstract static class ParentModule extends BasicModule {
        private static final BooleanSetting STATIC = new BooleanSetting("Static", true);

        protected final BooleanSetting inherited = new BooleanSetting("Inherited", true);

        @Exclude
        protected final BooleanSetting excluded = new BooleanSetting("Excluded", true);
    }

    @ModuleInfo(name = "Test", category = ModuleCategory.HUD)
    private static final class TestModule extends ParentModule {
        private final NumberSetting number = new NumberSetting("Number", 5, 0, 10, 1);

        private TestModule() {
            addSetting(new BooleanSetting("Runtime", true));
        }
    }
}
