package cc.nimbus.utils.render.skija.internal.effect.shader.runtime;

import io.github.humbleui.skija.Data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class UniformTransformer {
    private UniformTransformer() {
    }

    public static Data toData(List<?> uniforms) {
        ByteBuffer buffer = ByteBuffer
                .allocate(uniforms.size() * 4)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (Object value : uniforms) {
            write(buffer, value);
        }
        return Data.makeFromBytes(buffer.array());
    }

    private static void write(ByteBuffer buffer, Object value) {
        switch (value) {
            case Float number -> buffer.putFloat(number);
            case Integer number -> buffer.putInt(number);
            case Boolean flag -> buffer.putInt(flag ? 1 : 0);
            default -> throw new IllegalArgumentException("Unsupported shader uniform: " + value);
        }
    }

    public static boolean isSupported(Object value) {
        return value instanceof Float
                || value instanceof Integer
                || value instanceof Boolean;
    }
}
