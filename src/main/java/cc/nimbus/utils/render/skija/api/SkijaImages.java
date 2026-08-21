package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import io.github.humbleui.skija.Image;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class SkijaImages {
    SkijaImages() {
    }

    public Image resource(ResourceLocation id) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getResourceImage(id);
    }

    public Image encoded(String key, byte[] bytes) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getEncodedImage(key, bytes);
    }

    public Image minecraftTexture(ResourceLocation id) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getGlImage(id);
    }

    public ResourceLocation entityTexture(Entity entity) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getEntityTexture(entity);
    }

    public Image scene() {
        return MinecraftSnapshot.getScreenshot();
    }
}
