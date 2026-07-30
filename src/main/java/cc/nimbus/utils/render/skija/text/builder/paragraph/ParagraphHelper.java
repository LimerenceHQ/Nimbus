package cc.nimbus.utils.render.skija.text.builder.paragraph;

import cc.nimbus.utils.render.skija.text.FontFamily;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.paragraph.*;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings("unused")
public class ParagraphHelper {
    private static final Paint defaultPaint = new Paint().setColor(0xFFFFFFFF);

    public static Paragraph buildParagraph(ParagraphText[] texts, Alignment alignment) {
        try (ParagraphBuilder builder = createBuilder(alignment)) {
            try (TextStyle def = new TextStyle()) {
                String defFamily = FontFamily.getFirstFamily();
                float defSize = 18;
                Paint defPaint = defaultPaint;
                for (ParagraphText text : texts) {
                    if (text != null) {
                        if (text.hasFamily()) {
                            defFamily = text.getFamily();
                            break;
                        }
                    }
                }
                for (ParagraphText text : texts) {
                    if (text != null) {
                        if (text.hasSize()) {
                            defSize = text.getSize();
                            break;
                        }
                    }
                }
                for (ParagraphText text : texts) {
                    if (text != null) {
                        if (text.getPaint() != null && !text.getPaint().isClosed()) {
                            defPaint = text.getPaint();
                            break;
                        }
                    }
                }
                def.setFontFamily(defFamily);
                def.setFontSize(defSize);
                def.setForeground(defPaint);
                builder.pushStyle(def);
            }
            for (ParagraphText text : texts) {
                try (TextStyle ts = new TextStyle()) {
                    ts.setFontFamily(text.getFamily());
                    ts.setFontSize(text.getSize());
                    ts.setForeground(text.getPaint());
                    addText(builder, ts, text.getText());
                }
                text.close();
            }
            return builder.build();
        }
    }

    public static Paragraph buildParagraph(String family, float size, String[] texts, Alignment alignment) {
        try (ParagraphBuilder builder = createBuilder(alignment)) {
            try (TextStyle ts = new TextStyle()) {
                ts.setFontFamily(family);
                ts.setFontSize(size);
                ts.setForeground(defaultPaint);
                for (String text : texts) {
                    addText(builder, ts, text);
                }
            }
            return builder.build();
        }
    }

    public static Paragraph buildParagraph(String family, float size, String text, Alignment alignment) {
        try (ParagraphBuilder builder = createBuilder(alignment)) {
            try (TextStyle ts = new TextStyle()) {
                ts.setFontFamily(family);
                ts.setFontSize(size);
                ts.setForeground(defaultPaint);
                addText(builder, ts, text);
            }
            return builder.build();
        }
    }

    @ApiStatus.Internal
    private static ParagraphBuilder createBuilder(Alignment alignment) {
        try (ParagraphStyle ps = new ParagraphStyle().setAlignment(alignment)) {
            return new ParagraphBuilder(ps, FontFamily.FONT_COLLECTION);
        }
    }

    @ApiStatus.Internal
    private static void addText(ParagraphBuilder builder, TextStyle ts, String text) {
        builder.pushStyle(ts);
        builder.addText(text);
        builder.popStyle();
    }
}
