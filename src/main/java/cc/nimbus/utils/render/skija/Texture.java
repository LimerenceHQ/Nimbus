package cc.nimbus.utils.render.skija;

import com.mojang.blaze3d.textures.*;
import net.minecraft.client.renderer.texture.AbstractTexture;

final class Texture extends AbstractTexture {
    Texture(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
        this.texture = texture; this.textureView = view; this.sampler = sampler;
    }
    @Override public void close() { sampler.close(); textureView.close(); texture.close(); }
}
