package cc.nimbus.utils.render.skija.internal.effect.shader.runtime;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.Shader;

import java.util.ArrayList;

public final class RuntimeEffectBuilder implements AutoCloseable {
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

    public Shader makeShader(Shader... children) {
        ensureOpen();
        try (Data data = UniformTransformer.toData(uniforms)) {
            return effect.makeShader(data, children, Matrix33.IDENTITY);
        } finally {
            uniforms.clear();
        }
    }

    public RuntimeEffectBuilder clearUniforms() {
        uniforms.clear();
        return this;
    }

    private boolean checkEffect() {
        ensureOpen();
        return true;
    }

    private boolean checkUniform(Object value) {
        if (UniformTransformer.isSupported(value)) return true;
        throw new IllegalArgumentException("Unsupported shader uniform: " + value);
    }

    private void ensureOpen() {
        if (effect == null || effect.isClosed()) {
            throw new IllegalStateException("Runtime effect is closed");
        }
    }

    @Override
    public void close() {
        uniforms.clear();
        if (effect != null && !effect.isClosed()) effect.close();
    }
}
