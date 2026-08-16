package cc.nimbus.core;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.features.client.ClientLanguage;
import cc.nimbus.managers.AccountManager;
import cc.nimbus.managers.CommandManager;
import cc.nimbus.managers.ConfigManager;
import cc.nimbus.managers.HudManager;
import cc.nimbus.managers.I18nManager;
import cc.nimbus.managers.ModuleManager;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.managers.RenderManager;
import cc.nimbus.managers.RotationManager;
import cc.nimbus.managers.TargetManager;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public enum Core {
    INSTANCE;

    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private AccountManager accountManager;
    private RenderManager renderManager;
    private HudManager hudManager;
    private MusicManager musicManager;
    private I18nManager i18nManager;
    private RotationManager rotationManager;
    private TargetManager targetManager;
    private boolean initialized;

    public synchronized void init(Path fabricConfigDirectory) {
        if (initialized) return;

        Path nimbusDirectory = Objects.requireNonNull(fabricConfigDirectory, "fabricConfigDirectory").resolve("nimbus");
        RenderManager renders = new RenderManager();
        RotationManager rotations = new RotationManager(Minecraft.getInstance());
        ModuleManager modules = new ModuleManager();
        TargetManager targets = new TargetManager(Minecraft.getInstance(), modules);
        ConfigManager configs = new ConfigManager(nimbusDirectory, modules);
        I18nManager i18n = new I18nManager(() -> ClientLanguage.fromValue(configs.clientSettings().language.getValue()));
        HudManager hud = new HudManager(modules, renders, configs.clientSettings());
        AccountManager accounts = new AccountManager(nimbusDirectory.resolve("accounts"));
        MusicManager music = new MusicManager(nimbusDirectory.resolve("music"));
        CommandManager commands = new CommandManager();
        List<InstanceManager> started = new ArrayList<>(10);

        EventBus.clear();
        try {
            start(renders, started);
            start(rotations, started);
            start(modules, started);
            start(targets, started);
            start(hud, started);
            start(configs, started);
            start(i18n, started);
            start(accounts, started);
            start(music, started);
            start(commands, started);
        } catch (RuntimeException error) {
            shutdownReverse(started, error);
            EventBus.clear();
            throw error;
        }

        moduleManager = modules;
        rotationManager = rotations;
        targetManager = targets;
        renderManager = renders;
        hudManager = hud;
        configManager = configs;
        i18nManager = i18n;
        accountManager = accounts;
        musicManager = music;
        commandManager = commands;
        initialized = true;
    }

    public synchronized void shutdown() {
        if (!initialized) return;

        RuntimeException failure = shutdownReverse(List.of(
                renderManager,
                rotationManager,
                moduleManager,
                targetManager,
                hudManager,
                configManager,
                i18nManager,
                accountManager,
                musicManager,
                commandManager
        ), null);
        try {
            if (failure != null) throw failure;
        } finally {
            EventBus.clear();
            moduleManager = null;
            rotationManager = null;
            targetManager = null;
            renderManager = null;
            hudManager = null;
            configManager = null;
            i18nManager = null;
            accountManager = null;
            musicManager = null;
            commandManager = null;
            initialized = false;
        }
    }

    private static void start(InstanceManager manager, List<InstanceManager> started) {
        manager.init();
        started.add(manager);
    }

    private static RuntimeException shutdownReverse(
            List<? extends InstanceManager> managers,
            RuntimeException failure
    ) {
        for (int index = managers.size() - 1; index >= 0; index--) {
            try {
                managers.get(index).shutdown();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        return failure;
    }

    public ModuleManager getModuleManager() {
        return requireInitialized(moduleManager, "ModuleManager");
    }

    public RotationManager getRotationManager() {
        return requireInitialized(rotationManager, "RotationManager");
    }

    public TargetManager getTargetManager() {
        return requireInitialized(targetManager, "TargetManager");
    }

    public CommandManager getCommandManager() {
        return requireInitialized(commandManager, "CommandManager");
    }

    public ConfigManager getConfigManager() {
        return requireInitialized(configManager, "ConfigManager");
    }

    public AccountManager getAccountManager() {
        return requireInitialized(accountManager, "AccountManager");
    }

    public RenderManager getRenderManager() {
        return requireInitialized(renderManager, "RenderManager");
    }

    public MusicManager getMusicManager() {
        return requireInitialized(musicManager, "MusicManager");
    }

    public HudManager getHudManager() {
        return requireInitialized(hudManager, "HudManager");
    }

    public I18nManager getI18nManager() {
        return requireInitialized(i18nManager, "I18nManager");
    }

    public boolean isInitialized() {
        return initialized;
    }

    private static <T> T requireInitialized(T value, String name) {
        if (value == null) throw new IllegalStateException(name + " is not initialized");
        return value;
    }
}
