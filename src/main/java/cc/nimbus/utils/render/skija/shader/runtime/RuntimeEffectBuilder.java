package cc.nimbus.utils.render.skija.shader.runtime;

import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.Shader;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;

public class RuntimeEffectBuilder {
    private final ArrayList<Object> uniforms = new ArrayList<>();
    private final RuntimeEffect effect;

    public RuntimeEffectBuilder(String shader) {
        this.effect = RuntimeEffect.makeForShader(shader);
    }

    public RuntimeEffectBuilder setUniform(Float value) {
        if (checkEffect() && checkUniform(value)) {
            uniforms.add(value);
        }
        return this;
    }

    public RuntimeEffectBuilder setUniform(Integer value) {
        if (checkEffect() && checkUniform(value)) {
            uniforms.add(value);
        }
        return this;
    }

    public RuntimeEffectBuilder setUniform(Boolean value) {
        if (checkEffect() && checkUniform(value)) {
            uniforms.add(value);
        }
        return this;
    }

    public Shader makeShader(Shader... childs) {
        if (!checkEffect()) return null;

        UniformTransformer transformer = new UniformTransformer(uniforms);
        uniforms.clear();
        return effect.makeShader(
                transformer.getData(),
                childs,
                Matrix33.IDENTITY
        );
    }

    public RuntimeEffectBuilder clearUniforms() {
        uniforms.clear();
        return this;
    }

    @ApiStatus.Internal
    private boolean checkEffect() {
        if (effect != null) {
            return true;
        }
        System.err.println("[RuntimeEffectBuilder] Effect is null");
        return false;
    }

    @ApiStatus.Internal
    private boolean checkUniform(Object value) {
        if (UniformTransformer.isSupported(value)) {
            return true;
        }
        System.err.println("[RuntimeEffectBuilder] Unsupported transformer type: " + value);
        return false;
    }
}