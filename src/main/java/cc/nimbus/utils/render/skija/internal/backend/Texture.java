package cc.nimbus.utils.render.skija.internal.backend;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

final class Texture extends AbstractTexture {
    Texture(int textureId) {
        this.id = textureId;
    }
    @Override public void close() { releaseId(); }
    @Override public void load(ResourceManager resourceManager) { }
}
