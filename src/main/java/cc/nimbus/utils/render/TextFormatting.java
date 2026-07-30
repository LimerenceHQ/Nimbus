package cc.nimbus.utils.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum TextFormatting
{
    BLACK('0', 0),
    DARK_BLUE('1', 1),
    DARK_GREEN('2', 2),
    DARK_AQUA('3', 3),
    DARK_RED('4', 4),
    DARK_PURPLE('5', 5),
    GOLD('6', 6),
    GRAY('7', 7),
    DARK_GRAY('8', 8),
    BLUE('9', 9),
    GREEN('a', 10),
    AQUA('b', 11),
    RED('c', 12),
    LIGHT_PURPLE('d', 13),
    YELLOW('e', 14),
    WHITE('f', 15),
    RESET('r', -1);

    public static final Map<Character, TextFormatting> CODE_MAP = new HashMap<>();
    public static final int[] COLOR_CODE;
    public static final String STAERT_CODE = "\u200E";
    public static final char STAERT_CODEC = '\u200E';

    private final String controlString;
    private final int index;

    TextFormatting(char formattingCodeIn, int index)
    {
        this.controlString = TextFormatting.STAERT_CODE + formattingCodeIn;
        this.index = index;
    }

    public String getControlString() {
        return controlString;
    }

    public int getIndex() {
        return index;
    }

    public int getColorCode() {
        return COLOR_CODE[index];
    }

    public boolean isColor() {
        return index >= 0 && index <= 15;
    }

    public String toString()
    {
        return this.controlString;
    }

    private static final String LEGACY_CHARS = "0123456789abcdef";

    public static char rgbToLegacyChar(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            int c = COLOR_CODE[i];
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            int diff = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return LEGACY_CHARS.charAt(best);
    }

    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '搂' || c == '&') {
                if (i + 1 < text.length()) {
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (c == STAERT_CODEC && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (CODE_MAP.containsKey(next)) {
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public record ColoredSegment(String text, int rgb) {
        public boolean isEmpty() { return text == null || text.isEmpty(); }
    }
    public static List<ColoredSegment> parseColorCodeSegments(String text, int defaultRgb) {
        List<ColoredSegment> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder current = new StringBuilder();
        int currentRgb = defaultRgb;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '搂' || c == '&') {
                if (current.length() > 0) {
                    out.add(new ColoredSegment(current.toString(), currentRgb));
                    current.setLength(0);
                }
                if (i + 1 < text.length()) {
                    char code = Character.toLowerCase(text.charAt(i + 1));
                    TextFormatting tf = CODE_MAP.get(code);
                    if (tf != null) {
                        if (tf.isColor()) currentRgb = tf.getColorCode();
                        else if (tf == RESET) currentRgb = defaultRgb;
                    }
                    i += 2;
                } else
                    i++;
                continue;
            }
            if (c == STAERT_CODEC && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                TextFormatting tf = CODE_MAP.get(code);
                if (tf != null) {
                    if (current.length() > 0) {
                        out.add(new ColoredSegment(current.toString(), currentRgb));
                        current.setLength(0);
                    }
                    if (tf.isColor()) currentRgb = tf.getColorCode();
                    else if (tf == RESET) currentRgb = defaultRgb;
                    i += 2;
                } else {
                    current.append(c);
                    i++;
                }
                continue;
            }
            current.append(c);
            i++;
        }
        if (current.length() > 0)
            out.add(new ColoredSegment(current.toString(), currentRgb));
        return out;
    }

    static {
        for (TextFormatting tf : values()) {
            CODE_MAP.put(tf.controlString.charAt(1), tf);
        }
        int[] colorCode = new int[32];
        for (int index = 0; index < 32; index++) {
            int noClue = (index >> 3 & 0x1) * 85;
            int red = (index >> 2 & 0x1) * 170 + noClue;
            int green = (index >> 1 & 0x1) * 170 + noClue;
            int blue = (index & 0x1) * 170 + noClue;

            if (index == 6) {
                red += 85;
            }

            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }

            colorCode[index] = ((red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF);
        }
        COLOR_CODE = colorCode;
    }
}

