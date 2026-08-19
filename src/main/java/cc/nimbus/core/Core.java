package cc.nimbus.core;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.features.client.ClientLanguage;
import cc.nimbus.managers.*;
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
    private PacketManager packetManager;
    private I18nManager i18nManager;
    private RotationManager rotationManager;
    private TargetManager targetManager;
    private GameStateManager gameStateManager;
    private boolean initialized;

    public synchronized void init(Path fabricConfigDirectory) {
        if (initialized) return;

        Path nimbusDirectory = Objects.requireNonNull(fabricConfigDirectory, "fabricConfigDirectory").resolve("nimbus");
        RenderManager renders = new RenderManager();
        RotationManager rotations = new RotationManager(Minecraft.getInstance());
        PacketManager packets = new PacketManager(Minecraft.getInstance());
        ModuleManager modules = new ModuleManager();
        TargetManager targets = new TargetManager(Minecraft.getInstance(), modules);
        ConfigManager configs = new ConfigManager(nimbusDirectory, modules);
        GameStateManager state = new GameStateManager(Minecraft.getInstance());
        I18nManager i18n = new I18nManager(() -> ClientLanguage.fromValue(configs.clientSettings().language.getValue()));
        HudManager hud = new HudManager(modules, renders, configs.clientSettings());
        AccountManager accounts = new AccountManager(nimbusDirectory.resolve("accounts"));
        MusicManager music = new MusicManager(nimbusDirectory.resolve("music"));
        CommandManager commands = new CommandManager();
        List<InstanceManager> started = new ArrayList<>(13);

        EventBus.clear();
        try {
            start(renders, started);
            start(rotations, started);
            start(packets, started);
            start(modules, started);
            start(targets, started);
            start(hud, started);
            start(configs, started);
            start(state, started);
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
        packetManager = packets;
        targetManager = targets;
        renderManager = renders;
        hudManager = hud;
        gameStateManager = state;
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
                packetManager,
                moduleManager,
                targetManager,
                hudManager,
                configManager,
                gameStateManager,
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
            packetManager = null;
            targetManager = null;
            renderManager = null;
            hudManager = null;
            configManager = null;
            gameStateManager = null;
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

    public PacketManager getPacketManager() {
        return requireInitialized(packetManager, "PacketManager");
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

    public GameStateManager getGameStateManager() {
        return requireInitialized(gameStateManager, "GameStateManager");
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
