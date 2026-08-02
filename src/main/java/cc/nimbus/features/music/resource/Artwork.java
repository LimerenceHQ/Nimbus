package cc.nimbus.features.music.resource;

import java.util.Arrays;

public record Artwork(byte[] encoded, int[] colors) {
    public Artwork {
        encoded = encoded == null ? new byte[0] : encoded.clone();
        colors = colors == null || colors.length == 0
                ? new int[]{0xFF30343A, 0xFF1F2328, 0xFF454B54}
                : colors.clone();
    }

    @Override public byte[] encoded() { return encoded.clone(); }
    @Override public int[] colors() { return colors.clone(); }

    public int color(int index) {
        return colors[Math.clamp(index, 0, colors.length - 1)];
    }

    @Override public boolean equals(Object value) {
        return value instanceof Artwork artwork
                && Arrays.equals(encoded, artwork.encoded)
                && Arrays.equals(colors, artwork.colors);
    }

    @Override public int hashCode() { return 31 * Arrays.hashCode(encoded) + Arrays.hashCode(colors); }
}
