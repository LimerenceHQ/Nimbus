package cc.nimbus.utils.render.skija.text.builder.paragraph;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.paragraph.Alignment;
import io.github.humbleui.skija.paragraph.Paragraph;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class MinecraftParagraph {
    private static final int[] MC_COLORS = new int[] {
            0xFF000000, // 0 black
            0xFF0000AA, // 1 dark_blue
            0xFF00AA00, // 2 dark_green
            0xFF00AAAA, // 3 dark_aqua
            0xFFAA0000, // 4 dark_red
            0xFFAA00AA, // 5 dark_purple
            0xFFFFAA00, // 6 gold
            0xFFAAAAAA, // 7 gray
            0xFF555555, // 8 dark_gray
            0xFF5555FF, // 9 blue
            0xFF55FF55, // a green
            0xFF55FFFF, // b aqua
            0xFFFF5555, // c red
            0xFFFF55FF, // d light_purple
            0xFFFFFF55, // e yellow
            0xFFFFFFFF  // f white
    };

    public static Paragraph buildMcParagraph(String family, float size, String text) {
        Paragraph paragraph;
        if (text.contains("§")) {
            ParagraphText[] paragraphTexts = parseParagraph(family, size, text);
            paragraph = ParagraphHelper.buildParagraph(paragraphTexts, Alignment.LEFT);
        } else {
            paragraph = ParagraphHelper.buildParagraph(family, size, text, Alignment.LEFT);
        }
        return paragraph;
    }

    public static ParagraphText[] parseParagraph(String family, float size, String text) {
        if (text == null || text.isEmpty()) {
            return new ParagraphText[0];
        }

        List<ParagraphText> result = new ArrayList<>();
        int currentColor = MC_COLORS[15];
        String[] parts = text.split("§");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            if (i > 0) {
                char code = part.charAt(0);
                int colorIndex = getColorIndex(code);
                if (colorIndex != -1) {
                    currentColor = MC_COLORS[colorIndex];
                }
                part = part.substring(1);
            }
            if (!part.isEmpty()) {
                ParagraphText paragraphText = new ParagraphText();
                paragraphText.setFamily(family);
                paragraphText.setSize(size);
                paragraphText.setText(part);
                paragraphText.setPaint(new Paint().setColor(currentColor));
                result.add(paragraphText);
            }
        }

        return result.toArray(new ParagraphText[0]);
    }

    public static String[] parseString(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        List<String> result = new ArrayList<>();
        String[] parts = text.split("§");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            if (i > 0) {
                part = part.substring(1);
            }
            if (!part.isEmpty()) {
                result.add(part);
            }
        }

        return result.toArray(new String[0]);
    }

    private static int getColorIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }
}
