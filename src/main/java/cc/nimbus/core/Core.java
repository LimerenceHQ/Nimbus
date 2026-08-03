package cc.nimbus.core;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.managers.AccountManager;
import cc.nimbus.managers.CommandManager;
import cc.nimbus.managers.ConfigManager;
import cc.nimbus.managers.HudManager;
import cc.nimbus.managers.ModuleManager;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.managers.RenderManager;

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
    private boolean initialized;

    public synchronized void init(Path fabricConfigDirectory) {
        if (initialized) return;

        Path nimbusDirectory = Objects.requireNonNull(fabricConfigDirectory, "fabricConfigDirectory")
                .resolve("nimbus");
        RenderManager renders = new RenderManager();
        ModuleManager modules = new ModuleManager();
        ConfigManager configs = new ConfigManager(nimbusDirectory, modules);
        HudManager hud = new HudManager(modules, renders, configs.clientSettings());
        AccountManager accounts = new AccountManager(nimbusDirectory.resolve("accounts"));
        MusicManager music = new MusicManager(nimbusDirectory.resolve("music"));
        CommandManager commands = new CommandManager();
        List<InstanceManager> started = new ArrayList<>(7);

        EventBus.clear();
        try {
            start(renders, started);
            start(modules, started);
            start(hud, started);
            start(configs, started);
            start(accounts, started);
            start(music, started);
            start(commands, started);
        } catch (RuntimeException error) {
            shutdownReverse(started, error);
            EventBus.clear();
            throw error;
        }

        moduleManager = modules;
        renderManager = renders;
        hudManager = hud;
        configManager = configs;
        accountManager = accounts;
        musicManager = music;
        commandManager = commands;
        initialized = true;
    }

    public synchronized void shutdown() {
        if (!initialized) return;

        RuntimeException failure = shutdownReverse(List.of(
                renderManager,
                moduleManager,
                hudManager,
                configManager,
                accountManager,
                musicManager,
                commandManager
        ), null);
        try {
            if (failure != null) throw failure;
        } finally {
            EventBus.clear();
            moduleManager = null;
            renderManager = null;
            hudManager = null;
            configManager = null;
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

    public boolean isInitialized() {
        return initialized;
    }

    private static <T> T requireInitialized(T value, String name) {
        if (value == null) throw new IllegalStateException(name + " is not initialized");
        return value;
    }
}
