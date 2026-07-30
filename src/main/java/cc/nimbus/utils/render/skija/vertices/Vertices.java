package cc.nimbus.utils.render.skija.vertices;

import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.impl.Native;

@SuppressWarnings("unused")
public class Vertices implements AutoCloseable {
    private VerticesMode mode = null;
    private float[] positions = null;
    private int[] colors = null;
    private float[] texCoords = null;
    private short[] indices = null;

    public VerticesMode getMode() {
        return mode;
    }

    public Vertices setMode(VerticesMode mode) {
        this.mode = mode;
        return this;
    }

    public float[] getPositions() {
        return positions;
    }

    public Vertices setPositions(float[] positions) {
        this.positions = positions;
        return this;
    }

    public int[] getColors() {
        return colors;
    }

    public Vertices setColors(int[] colors) {
        this.colors = colors;
        return this;
    }

    public float[] getTexCoords() {
        return texCoords;
    }

    public Vertices setTexCoords(float[] texCoords) {
        this.texCoords = texCoords;
        return this;
    }

    public short[] getIndices() {
        return indices;
    }

    public Vertices setIndices(short[] indices) {
        this.indices = indices;
        return this;
    }

    // Is Canvas.drawTriangles
    public Vertices drawVertices(Canvas canvas, BlendMode blendMode, Paint paint) {
        assert canvas._ptr != 0 : "Canvas is closed";
        assert mode != null : "Mode is null";
        assert positions != null : "Positions is null";

        if (this.positions.length % 2 != 0) {
            throw new IllegalArgumentException("positions length must be even");
        }
        if (this.indices != null && this.indices.length % 3 != 0 && this.mode == VerticesMode.TRIANGLES) {
            throw new IllegalArgumentException("indices length must be multiple of 3 for TRIANGLES");
        }
        Canvas._nDrawVertices(canvas._ptr, this.mode.ordinal(), this.positions, this.colors, this.texCoords, this.indices, blendMode.ordinal(), Native.getPtr(paint));
        return this;
    }

    @Override
    public void close() {
        mode = null;
        positions = null;
        colors = null;
        texCoords = null;
        indices = null;
    }
}