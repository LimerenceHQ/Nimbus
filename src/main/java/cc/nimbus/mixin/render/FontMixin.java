package cc.nimbus.mixin.render;

import cc.nimbus.features.module.modules.player.NameProtect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class FontMixin {
    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        String replacement = NameProtect.replace(text);
        if (replacement.equals(text)) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).width(replacement));
        } finally {
            NameProtect.endReplacing();
        }
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectFormattedWidth(net.minecraft.network.chat.FormattedText text,
                                               CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing() || !(text instanceof Component component)) return;
        Component replacement = (Component) NameProtect.replace(component);
        if (replacement == component) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).width((net.minecraft.network.chat.FormattedText) replacement));
        } finally {
            NameProtect.endReplacing();
        }
    }

    @Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectSequenceWidth(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        FormattedCharSequence replacement = NameProtect.replace(text);
        if (replacement == text) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).width(replacement));
        } finally {
            NameProtect.endReplacing();
        }
    }

    @Inject(method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectStringDraw(String text, float x, float y, int color, boolean dropShadow,
                                           Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode,
                                           int backgroundColor, int packedLight, CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        String replacement = NameProtect.replace(text);
        if (replacement.equals(text)) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).drawInBatch(replacement, x, y, color, dropShadow,
                    pose, buffer, mode, backgroundColor, packedLight));
        } finally {
            NameProtect.endReplacing();
        }
    }

    /* 1.21.4 removed the String overload with the bidirectional flag. */
    @Inject(method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectStringDrawSeeThrough(String text, float x, float y, int color, boolean dropShadow,
                                                    Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode,
                                                    int backgroundColor, int packedLight,
                                                    CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        String replacement = NameProtect.replace(text);
        if (replacement.equals(text)) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).drawInBatch(replacement, x, y, color, dropShadow,
                    pose, buffer, mode, backgroundColor, packedLight));
        } finally {
            NameProtect.endReplacing();
        }
    }

    @Inject(method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectComponentDraw(Component text, float x, float y, int color, boolean dropShadow,
                                              Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode,
                                              int backgroundColor, int packedLight, CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        Component replacement = (Component) NameProtect.replace(text);
        if (replacement == text) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).drawInBatch(replacement, x, y, color, dropShadow,
                    pose, buffer, mode, backgroundColor, packedLight));
        } finally {
            NameProtect.endReplacing();
        }
    }

    @Inject(method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"), cancellable = true)
    private void nimbus$protectSequenceDraw(FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
                                             Matrix4f pose, MultiBufferSource buffer, Font.DisplayMode mode,
                                             int backgroundColor, int packedLight, CallbackInfoReturnable<Integer> cir) {
        if (NameProtect.isReplacing()) return;
        FormattedCharSequence replacement = NameProtect.replace(text);
        if (replacement == text) return;
        NameProtect.beginReplacing();
        try {
            cir.setReturnValue(((Font) (Object) this).drawInBatch(replacement, x, y, color, dropShadow,
                    pose, buffer, mode, backgroundColor, packedLight));
        } finally {
            NameProtect.endReplacing();
        }
    }
}
