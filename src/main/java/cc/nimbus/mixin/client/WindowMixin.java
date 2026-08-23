package cc.nimbus.mixin.client;

import cc.nimbus.core.Core;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;
import java.util.List;

@Mixin(com.mojang.blaze3d.platform.Window.class)
public final class WindowMixin {
    @Redirect(
            method = "setIcon",
            at = @At(value = "INVOKE", target =
                    "Lcom/mojang/blaze3d/platform/IconSet;getStandardIcons(Lnet/minecraft/server/packs/PackResources;)Ljava/util/List;")
    )
    private List<IoSupplier<InputStream>> nimbus$selectIcons(IconSet iconSet, PackResources resources) {
        if (!Core.INSTANCE.isInitialized()
                || !Core.INSTANCE.getConfigManager().clientSettings().arentIOnePointEight.getValue()) {
            try {
                return iconSet.getStandardIcons(resources);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Unable to load Minecraft window icons", error);
            }
        }

        return List.of(
                () -> WindowMixin.class.getResourceAsStream("/assets/nimbus/icons/icon_16x16.png"),
                () -> WindowMixin.class.getResourceAsStream("/assets/nimbus/icons/icon_32x32.png")
        );
    }
}
