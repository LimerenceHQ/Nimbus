package cc.nimbus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.ui.clickgui.neutron.NeutronScreen;
import cc.nimbus.ui.screen.UnsupportedPlatformScreen;
import cc.nimbus.utils.helper.Schedulers;
import cc.nimbus.utils.render.NativeSupport;

public final class Nimbus implements ClientModInitializer {
    public static final String NAME = "Nimbus";
    public static final String VERSION = "0.1";
    private static volatile boolean initialized;

    public Nimbus() {}

    @Override
    public void onInitializeClient() {
        if (initialized) return;

        NativeSupport.initialize();
        Core.INSTANCE.init(FabricLoader.getInstance().getConfigDir());

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !EventBus.call(new EventChatSend(message)).isCancel());

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
        Core.INSTANCE.shutdown();
        NeutronScreen.closeRenderer();
        NativeSupport.close();
        Schedulers.shutdown();
        initialized = false;
    }
}
