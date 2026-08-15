package cc.nimbus.features.music.lyrics;

import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LyricVisuals {
    private static final int GRADIENT_SEGMENTS = 16;
    private static final int MAX_WORD_LAYOUTS = 512;
    private static final float WORD_GEOMETRY_SIZE = 100f;
    private static final Map<WordLayoutKey, WordGeometry> WORD_LAYOUTS = new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WordLayoutKey, WordGeometry> eldest) {
            return size() > MAX_WORD_LAYOUTS;
        }
    };

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

        drawGradient(frame, theme, family, size, text, x, y, textWidth, completion,
                baseColor, opacity, highlight, colorTime, style);
    }

    public static void drawFlowing(
            SkijaFrame frame, ClientSettings theme, String family, float size, String text,
            float x, float y, int baseColor, float opacity, float highlight, long colorTime
    ) {
        if (text == null || text.isEmpty() || opacity <= .001f) return;
        float textWidth = frame.text().width(family, size, text);
        if (textWidth <= .01f) return;
        drawGradient(frame, theme, family, size, text, x, y, textWidth, 1f,
                baseColor, opacity, highlight, colorTime, ProgressStyle.FILL);
    }

    private static void drawGradient(
            SkijaFrame frame, ClientSettings theme, String family, float size, String text,
            float x, float y, float textWidth, float completion, int baseColor,
            float opacity, float highlight, long colorTime, ProgressStyle style
    ) {

        float[] rawStops = new float[GRADIENT_SEGMENTS + 4];
        int rawCount = 0;
        for (int index = 0; index <= GRADIENT_SEGMENTS; index++) {
            rawStops[rawCount++] = index / (float) GRADIENT_SEGMENTS;
        }
        float featherScale = style == ProgressStyle.WAVE ? .42f : .14f;
        float feather = Math.min(style == ProgressStyle.WAVE ? .075f : .035f,
                Math.max(.008f, size * featherScale / textWidth));
        rawStops[rawCount++] = Math.clamp(completion - feather, 0f, 1f);
        rawStops[rawCount++] = completion;
        rawStops[rawCount] = Math.clamp(completion + feather, 0f, 1f);
        Arrays.sort(rawStops);
        int uniqueCount = 1;
        for (int index = 1; index < rawStops.length; index++) {
            if (Float.compare(rawStops[index], rawStops[uniqueCount - 1]) != 0) {
                rawStops[uniqueCount++] = rawStops[index];
            }
        }
        int[] colors = new int[uniqueCount];
        float[] positions = Arrays.copyOf(rawStops, uniqueCount);
        for (int index = 0; index < positions.length; index++) {
            float position = positions[index];
            positions[index] = position;
            int resting = multiplyAlpha(baseColor, opacity);
            int flowing = mix(multiplyAlpha(theme.accentColorAt(position, colorTime), opacity),
                    resting, 1f - Math.clamp(highlight, 0f, 1f));
            float mix = completion <= 0f ? 1f : completion >= 1f ? 0f
                    : smoothStep((position - completion + feather) / Math.max(.0001f, feather * 2f));
            colors[index] = mix(flowing, resting, mix);
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
        WordGeometry geometry = wordGeometry(frame, family, text, line);
        float geometryScale = size / WORD_GEOMETRY_SIZE;
        float completed = 0f;
        for (int index = 0; index < line.words().size(); index++) {
            TimedWord word = line.words().get(index);
            float startWidth = geometry.starts()[index] * geometryScale;
            float endWidth = geometry.ends()[index] * geometryScale;
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
        }
        return completed;
    }

    private static synchronized WordGeometry wordGeometry(
            SkijaFrame frame, String family, String text, WordLine line
    ) {
        WordLayoutKey key = new WordLayoutKey(family, text, line);
        WordGeometry cached = WORD_LAYOUTS.get(key);
        if (cached != null) return cached;

        float[] starts = new float[line.words().size()];
        float[] ends = new float[line.words().size()];
        int cursor = 0;
        for (int index = 0; index < line.words().size(); index++) {
            TimedWord word = line.words().get(index);
            int start = text.indexOf(word.text(), cursor);
            if (start < 0) start = Math.min(cursor, text.length());
            int end = Math.min(text.length(), start + word.text().length());
            starts[index] = frame.text().width(family, WORD_GEOMETRY_SIZE, text.substring(0, start));
            ends[index] = frame.text().width(family, WORD_GEOMETRY_SIZE, text.substring(0, end));
            cursor = end;
        }
        WordGeometry geometry = new WordGeometry(starts, ends);
        WORD_LAYOUTS.put(key, geometry);
        return geometry;
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

    private static final class WordLayoutKey {
        private final String family;
        private final String text;
        private final WordLine line;

        private WordLayoutKey(String family, String text, WordLine line) {
            this.family = family;
            this.text = text;
            this.line = line;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof WordLayoutKey other)) return false;
            return line == other.line && family.equals(other.family) && text.equals(other.text);
        }

        @Override
        public int hashCode() {
            int result = 31 * family.hashCode() + text.hashCode();
            return 31 * result + System.identityHashCode(line);
        }
    }

    private record WordGeometry(float[] starts, float[] ends) {
    }
}
