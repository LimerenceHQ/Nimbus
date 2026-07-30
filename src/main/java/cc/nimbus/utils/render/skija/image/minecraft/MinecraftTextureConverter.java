package cc.nimbus.utils.render.skija.image.minecraft;

import io.github.humbleui.skija.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import cc.nimbus.utils.render.Render2DUtils;

public final class MinecraftTextureConverter {
    private MinecraftTextureConverter() {
    }

    public static Image item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return null;
            }

            ItemStackRenderState state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForLiving(state, stack, ItemDisplayContext.GUI, minecraft.player);
            var material = state.pickParticleMaterial(RandomSource.create());
            if (material == null) {
                return null;
            }

            TextureAtlasSprite sprite = material.sprite();
            Identifier spriteName = sprite.contents().name();
            Identifier texture = Identifier.fromNamespaceAndPath(
                    spriteName.getNamespace(),
                    "textures/" + spriteName.getPath() + ".png"
            );
            return Render2DUtils.getCachedImage(texture);
        } catch (RuntimeException exception) {
            System.err.println("[Nimbus/Skija] Unable to resolve item texture: " + exception.getMessage());
            return null;
        }
    }
}
