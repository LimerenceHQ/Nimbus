package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import io.github.humbleui.skija.Image;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class SkijaImages {
    SkijaImages() {
    }

    public Image resource(Identifier id) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getResourceImage(id);
    }

    public Image minecraftTexture(Identifier id) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getGlImage(id);
    }

    public Identifier entityTexture(Entity entity) {
        return cc.nimbus.utils.render.skija.internal.image.ImageStore.getEntityTexture(entity);
    }

    public Image scene() {
        return MinecraftSnapshot.getScreenshot();
    }
}
