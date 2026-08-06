package cc.nimbus.features.music.lyrics;

import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;

import java.util.NavigableSet;
import java.util.TreeSet;

/** Shared single-pass rendering for synchronized lyric text. */
public final class LyricVisuals {
    private static final int GRADIENT_SEGMENTS = 16;

    public enum ProgressStyle {
        FILL,
        WORD,
        WAVE
    }

    private LyricVisuals() {}

    public static void draw(
            SkijaFrame frame, ClientSettings theme, String family, float size, String text,
            float x, float y, WordLine words, int timeMs, int baseColor,
            float opacity, float highlight, long colorTime, ProgressStyle style
    ) {
        if (text == null || text.isEmpty() || opacity <= .001f) return;
        float textWidth = frame.text().width(family, size, text);
        if (textWidth <= .01f) return;
        float completion = words == null ? 0f
                : Math.clamp(progressWidth(frame, family, size, text, words, timeMs, style) / textWidth, 0f, 1f);

        NavigableSet<Float> stops = new TreeSet<>();
        for (int index = 0; index <= GRADIENT_SEGMENTS; index++) {
            stops.add(index / (float) GRADIENT_SEGMENTS);
        }
        float featherScale = style == ProgressStyle.WAVE ? .42f : .14f;
        float feather = Math.min(style == ProgressStyle.WAVE ? .075f : .035f,
                Math.max(.008f, size * featherScale / textWidth));
        stops.add(Math.clamp(completion - feather, 0f, 1f));
        stops.add(completion);
        stops.add(Math.clamp(completion + feather, 0f, 1f));
        int[] colors = new int[stops.size()];
        float[] positions = new float[stops.size()];
        int index = 0;
        for (float position : stops) {
            positions[index] = position;
            int resting = multiplyAlpha(baseColor, opacity);
            int flowing = mix(multiplyAlpha(theme.accentColorAt(position, colorTime), opacity),
                    resting, 1f - Math.clamp(highlight, 0f, 1f));
            float mix = completion <= 0f ? 1f : completion >= 1f ? 0f
                    : smoothStep((position - completion + feather) / Math.max(.0001f, feather * 2f));
            colors[index] = mix(flowing, resting, mix);
            index++;
        }

        try (Paint paint = new Paint().setAntiAlias(true).setShader(frame.effects().linearGradient(
                new Point(x, y), new Point(x + textWidth, y), colors, positions,
                io.github.humbleui.skija.GradientStyle.DEFAULT
        ))) {
            frame.text().draw(family, size, text, x, y, paint);
        }
    }

    private static float progressWidth(
            SkijaFrame frame, String family, float size, String text, WordLine line,
            int timeMs, ProgressStyle style
    ) {
        float completed = 0f;
        int cursor = 0;
        for (TimedWord word : line.words()) {
            int start = text.indexOf(word.text(), cursor);
            if (start < 0) start = Math.min(cursor, text.length());
            int end = Math.min(text.length(), start + word.text().length());
            float startWidth = frame.text().width(family, size, text.substring(0, start));
            float endWidth = frame.text().width(family, size, text.substring(0, end));
            if (timeMs >= word.endMs()) {
                completed = endWidth;
            } else if (timeMs >= word.startMs()) {
                float progress = (timeMs - word.startMs())
                        / (float) Math.max(1, word.endMs() - word.startMs());
                progress = switch (style) {
                    case WORD -> smoothStep(Math.clamp(
                            (timeMs - word.startMs()) / (float) Math.min(180,
                                    Math.max(1, word.endMs() - word.startMs())), 0f, 1f));
                    case WAVE -> .5f - .5f * (float) Math.cos(Math.PI * Math.clamp(progress, 0f, 1f));
                    default -> Math.clamp(progress, 0f, 1f);
                };
                return startWidth + (endWidth - startWidth) * Math.clamp(progress, 0f, 1f);
            } else {
                return completed;
            }
            cursor = end;
        }
        return completed;
    }

    private static float smoothStep(float value) {
        float bounded = Math.clamp(value, 0f, 1f);
        return bounded * bounded * (3f - 2f * bounded);
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private static int mix(int first, int second, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        int a = Math.round((first >>> 24) + ((second >>> 24) - (first >>> 24)) * value);
        int r = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * value);
        int g = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * value);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * value);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
