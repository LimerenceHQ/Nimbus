package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.player.NameProtect;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nimbus$suppressAutomaticProbe(Component message, CallbackInfo ci) {
        if (NameProtect.observeIncomingChat(message)) ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nimbus$suppressSignedAutomaticProbe(Component message, MessageSignature signature,
                                                       GuiMessageTag tag, CallbackInfo ci) {
        if (NameProtect.observeIncomingChat(message)) ci.cancel();
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component nimbus$protectMessage(Component message) {
        Component replacement = NameProtect.replace(message);
        return replacement == null ? message : replacement;
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component nimbus$protectSignedMessage(Component message) {
        Component replacement = NameProtect.replace(message);
        return replacement == null ? message : replacement;
    }
}
