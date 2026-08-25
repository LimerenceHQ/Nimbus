package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.player.NameProtect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsTextMixin {
    @ModifyVariable(
            method = {
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"
            },
            at = @At("HEAD"), argsOnly = true)
    private String nimbus$protectString(String text) {
        return NameProtect.replace(text);
    }

    @ModifyVariable(
            method = {
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
            },
            at = @At("HEAD"), argsOnly = true)
    private Component nimbus$protectComponent(Component text) {
        Component replacement = NameProtect.replace(text);
        return replacement == null ? text : replacement;
    }

    @ModifyVariable(
            method = {
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"
            },
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence nimbus$protectSequence(FormattedCharSequence text) {
        return NameProtect.replace(text);
    }
}
