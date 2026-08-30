package cc.nimbus.mixin.client;

import cc.nimbus.core.Core;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.managers.CommandManager;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Publishes outgoing chat before vanilla sends it, allowing local dot commands to cancel transmission. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void nimbus$handleLocalCommand(
            String message,
            boolean addToRecentChat,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!Core.INSTANCE.isInitialized() || message == null
                || !message.startsWith(CommandManager.PREFIX)) return;
        EventChatSend event = EventBus.call(new EventChatSend(message));
        if (event.isCancel()) callback.setReturnValue(true);
    }
}
