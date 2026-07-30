package cc.nimbus.utils.render.skija.shader.runtime;

import io.github.humbleui.skija.Data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@SuppressWarnings("all")
public class UniformTransformer {
    private final ByteBuffer uniformBuffer;

    public UniformTransformer(ArrayList<Object> uniforms) {
        uniformBuffer = ByteBuffer
                .allocate(uniforms.size() * 4)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (Object value : uniforms) {
            if (!this.transform(value)) {
                System.err.println("Untransformed: " + value);
            }
        }
    }

    private boolean transform(Object value) {
        if (!isSupported(value) || value == null) {
            return false;
        }

        if (value instanceof Float) {
            uniformBuffer.putFloat((Float) value);
        } else if (value instanceof Integer) {
            uniformBuffer.putInt((Integer) value);
        } else if (value instanceof Boolean) {
            uniformBuffer.putInt((Boolean) value ? 1 : 0);
        } else {
            return false;
        }

        return true;
    }

    public Data getData() {
        return Data.makeFromBytes(uniformBuffer.array());
    }

    public static boolean isSupported(Object value) {
        return value instanceof Float
                || value instanceof Integer
                || value instanceof Boolean;
    }
}