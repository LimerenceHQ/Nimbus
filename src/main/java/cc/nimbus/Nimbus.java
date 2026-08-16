package cc.nimbus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.ui.screen.UnsupportedPlatformScreen;

public final class Nimbus implements ClientModInitializer {
    private static volatile boolean initialized;

    public Nimbus() {}

    @Override
    public void onInitializeClient() {
        if (initialized) return;

        Core.INSTANCE.init(FabricLoader.getInstance().getConfigDir());

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !EventBus.call(new EventChatSend(message)).isCancel());

        initialized = true;

        Minecraft.getInstance().schedule(() -> {
            var support = Core.INSTANCE.getRenderManager().skija();
            support.validateSupport();
            if (!support.isSupported() && Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new UnsupportedPlatformScreen(support.unsupportedReason()));
            }
        });
    }

    public static void shutdown() {
        if (!initialized) return;
        Core.INSTANCE.shutdown();
        initialized = false;
    }
}
