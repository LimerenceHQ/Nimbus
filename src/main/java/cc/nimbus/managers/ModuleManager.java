package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.input.EventKeyPress;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;

import cc.nimbus.features.module.api.setting.impl.submodule.Mode;
import cc.nimbus.features.module.modules.combat.*;
import cc.nimbus.features.module.modules.exploit.*;
import cc.nimbus.features.module.modules.hud.*;
import cc.nimbus.features.module.modules.movement.*;
import cc.nimbus.features.module.modules.player.*;
import cc.nimbus.features.module.modules.render.*;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModuleManager implements InstanceManager {
    private final List<BasicModule> modules = new ArrayList<>();
    private final Map<Class<? extends BasicModule>, BasicModule> byClass = new HashMap<>();
    private final Map<ModuleCategory, List<BasicModule>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<String, BasicModule> byName = new HashMap<>();
    private final Map<Integer, List<BasicModule>> byKey = new HashMap<>();
    private Runnable changeListener = () -> {};

    @Override
    public synchronized void init() {
        clear();
        try {
            registerModules(
                    new KillAura(),
                    new AntiCrystal(),
                    new AntiFireball(),
                    new AutoTrap(),
                    new ProjectileAura(),
                    new BowAssistant(),
                    new Velocity(),
                    new Disabler(),
                    new ClickGui(),
                    new ActiveList(),
                    new MusicLyrics(),
                    new MusicSpectrum(),
                    new MusicToast(),
                    new TargetHUD(),
                    new PotionHUD(),
                    new Watermark(),
                    new Sprint(),
                    new Scaffold(),
                    new Speed(),
                    new FastWeb(),
                    new NoFall(),
                    new NoSlow(),
                    new Target(),
                    new Teams(),
                    new Timer(),
                    new AntiBot(),
                    new Stuck(),
                    new MidPearl(),
                    new FireGuard(),
                    new GhostHand(),
                    new ContainerStealer(),
                    new InventoryManager(),
                    new AutoTool(),
                    new FastPlace(),
                    new ESP(),
                    new Nametag(),
                    new FullBright(),
                    new NoFov(),
                    new NoHurtCamera(),
                    new WorldTweaks(),
                    new BetterThirdPerson(),
                    new ItemPhysics(),
                    new Projectile(),
                    new OldHitting());
            modules.stream()
                    .filter(BasicModule::shouldAutoEnable)
                    .forEach(module -> module.setEnabled(true));
            EventBus.register(this);
        } catch (RuntimeException error) {
            clear();
            throw error;
        }
    }

    @Override
    public synchronized void shutdown() {
        clear();
        changeListener = () -> {};
    }

    public synchronized void clear() {
        EventBus.unregister(this);
        modules.forEach(module -> module.setEnabled(false));
        modules.clear();
        byClass.clear();
        byCategory.clear();
        byName.clear();
        byKey.clear();
    }

    public synchronized void registerModules(BasicModule... modules) {
        for (BasicModule module : modules) registerModule(module);
    }

    public synchronized void registerModule(BasicModule module) {
        Objects.requireNonNull(module, "module");
        if (byClass.containsKey(module.getClass())) {
            throw new IllegalArgumentException("Duplicate module class: " + module.getClass().getName());
        }

        Set<String> names = new LinkedHashSet<>();
        names.add(module.getName());
        names.addAll(Arrays.asList(module.getAliases()));
        for (String name : names) validateName(name);

        module.bindStateListeners(changeListener, this::rebuildKeyMap);
        modules.add(module);
        byClass.put(module.getClass(), module);
        byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        names.forEach(name -> byName.put(normalize(name), module));
        modules.sort(Comparator.comparing(BasicModule::getName, String.CASE_INSENSITIVE_ORDER));
        rebuildKeyMap();
    }

    public synchronized void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
        modules.forEach(module -> module.bindStateListeners(changeListener, this::rebuildKeyMap));
    }

    public synchronized void rebuildKeyMap() {
        byKey.clear();
        for (BasicModule module : modules) {
            if (module.getKey() != 0) {
                byKey.computeIfAbsent(module.getKey(), ignored -> new ArrayList<>()).add(module);
            }
        }
    }

    public synchronized List<BasicModule> getModules() {
        return List.copyOf(modules);
    }

    public synchronized List<BasicModule> getModulesByCategory(ModuleCategory category) {
        return List.copyOf(byCategory.getOrDefault(category, List.of()));
    }

    public synchronized List<BasicModule> getModulesByKey(int key) {
        return List.copyOf(byKey.getOrDefault(key, List.of()));
    }

    public synchronized <T extends BasicModule> T getModule(Class<T> type) {
        return type.cast(byClass.get(type));
    }

    public synchronized BasicModule getModule(String name) {
        return byName.get(normalize(name));
    }

    @EventTarget
    public void onKey(EventKeyPress event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !acceptsModuleKeybinds()) return;
        getModulesByKey(event.getKey()).forEach(BasicModule::toggle);
    }

    private static boolean acceptsModuleKeybinds() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null && minecraft.screen == null;
    }

    private void validateName(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Empty module name or alias");
        if (byName.containsKey(normalized)) {
            throw new IllegalArgumentException("Duplicate module name or alias: " + name);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
