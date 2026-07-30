package cc.nimbus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import cc.nimbus.command.CommandManager;
import cc.nimbus.config.ConfigManager;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.ui.clickgui.neutron.NeutronScreen;
import cc.nimbus.ui.screen.UnsupportedPlatformScreen;
import cc.nimbus.utils.helper.Schedulers;
import cc.nimbus.utils.render.NativeSupport;

public final class Nimbus implements ClientModInitializer {
    public static final Nimbus INSTANCE = new Nimbus();

    public static final String NAME = "Nimbus";
    public static final String VERSION = "0.0";
    private static volatile boolean initialized;

    public Nimbus() {}

    public ModuleManager getModuleManager() { return ModuleManager.getInstance(); }

    @Override
    public void onInitializeClient() {
        if (initialized) return;

        NativeSupport.initialize();
        EventManager.flush();
        ModuleManager.getInstance().init();
        ConfigManager.init(FabricLoader.getInstance().getConfigDir());
        ModuleManager.getInstance().postInit();
        CommandManager.getInstance().init();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !EventManager.call(new EventChatSend(message)).isCancel());

        initialized = true;

        Minecraft.getInstance().schedule(() -> {
            NativeSupport.validateBackend();
            if (!NativeSupport.isSupported() && Minecraft.getInstance().gui.screen() == null) {
                Minecraft.getInstance().gui.setScreen(new UnsupportedPlatformScreen(NativeSupport.getUnsupportedReason()));
            }
        });
    }

    public static void shutdown() {
        if (!initialized) return;
        ConfigManager.shutdown();
        CommandManager.getInstance().shutdown();
        NeutronScreen.closeRenderer();
        NativeSupport.close();
        ModuleManager.getInstance().clear();
        EventManager.flush();
        Schedulers.shutdown();
        initialized = false;
    }
}
