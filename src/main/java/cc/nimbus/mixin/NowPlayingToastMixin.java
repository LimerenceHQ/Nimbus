package cc.nimbus.mixin;

import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;
import cc.nimbus.features.music.integration.MusicToastState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NowPlayingToast.class)
public final class NowPlayingToastMixin {
    @Inject(method = "getCurrentSongName", at = @At("HEAD"), cancellable = true)
    private static void nimbus$currentSong(CallbackInfoReturnable<String> callback) {
        if (MusicToastState.active()) callback.setReturnValue(MusicToastState.text());
    }

    @Inject(method = "getNowPlayingString", at = @At("HEAD"), cancellable = true)
    private static void nimbus$nowPlayingString(String currentSongKey,
                                                 CallbackInfoReturnable<Component> callback) {
        if (MusicToastState.active()) callback.setReturnValue(Component.literal(MusicToastState.text()));
    }
}
