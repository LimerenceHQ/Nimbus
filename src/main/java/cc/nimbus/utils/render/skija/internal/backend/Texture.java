package cc.nimbus.utils.render.skija.internal.backend;

import net.minecraft.client.renderer.texture.AbstractTexture;

final class Texture extends AbstractTexture {
    Texture(int textureId) {
        this.id = textureId;
    }
    @Override public void close() { releaseId(); }
}
