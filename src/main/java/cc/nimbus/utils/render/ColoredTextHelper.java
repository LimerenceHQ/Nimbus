package cc.nimbus.utils.render;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class ColoredTextHelper {

    private static final int DEFAULT_RGB = 0xFFFFFF;

    public static List<TextFormatting.ColoredSegment> extractSegments(FormattedCharSequence sequence, int defaultRgb) {
        if (sequence == null) return List.of();
        StringBuilder raw = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            raw.appendCodePoint(codePoint);
            return true;
        });
        String rawStr = raw.toString();
        if (hasColorCodes(rawStr)) {
            List<TextFormatting.ColoredSegment> parsed = TextFormatting.parseColorCodeSegments(rawStr, defaultRgb);
            return parsed.isEmpty() ? List.of(new TextFormatting.ColoredSegment(rawStr, defaultRgb)) : parsed;
        }
        StringBuilder withCodes = new StringBuilder();
        int[] currentRgb = new int[]{defaultRgb};
        sequence.accept((index, style, codePoint) -> {
            int rgb = getRgbFromStyle(style);
            if (rgb != currentRgb[0]) {
                currentRgb[0] = rgb;
                withCodes.append('搂').append(TextFormatting.rgbToLegacyChar(rgb));
            }
            withCodes.appendCodePoint(codePoint);
            return true;
        });
        String withCodesStr = withCodes.toString();
        List<TextFormatting.ColoredSegment> parsed = TextFormatting.parseColorCodeSegments(withCodesStr, defaultRgb);
        return parsed.isEmpty() ? List.of(new TextFormatting.ColoredSegment(withCodesStr, defaultRgb)) : parsed;
    }

    public static boolean hasColorCodes(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '搂' || c == '&') return true;
            if (c == TextFormatting.STAERT_CODEC && i + 1 < text.length()
                && TextFormatting.CODE_MAP.containsKey(text.charAt(i + 1))) return true;
        }
        return false;
    }

    public static int getRgbFromStyle(Style style) {
        if (style == null) return DEFAULT_RGB;
        try {
            TextColor color = style.getColor();
            if (color == null) return DEFAULT_RGB;
            Integer value = color.getValue();
            if (value == null) return DEFAULT_RGB;
            if (value >= 0 && value <= 15) return TextFormatting.COLOR_CODE[value];
            return value;
        } catch (Exception ignored) {
            return DEFAULT_RGB;
        }
    }

    private ColoredTextHelper() {}
}

