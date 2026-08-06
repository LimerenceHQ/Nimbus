package cc.nimbus.features.music.lyrics;

import java.util.ArrayList;
import java.util.List;

public record LyricsTimeline(List<LyricLine> lines, boolean hasTranslations) {
    public static final LyricsTimeline EMPTY = new LyricsTimeline(List.of(), false);

    public LyricsTimeline {
        lines = List.copyOf(lines == null ? List.of() : lines);
    }

    public static LyricsTimeline parse(Lyrics value) {
        if (value == null) return EMPTY;
        List<TimedLine> lines = LyricsParser.lines(value.line());
        List<TimedLine> translated = LyricsParser.lines(value.translated());
        List<WordLine> wordLines = usableWordLines(value.words());
        if (wordLines.isEmpty()) wordLines = usableWordLines(value.karaoke());
        List<LyricLine> result = wordLines.isEmpty()
                ? visibleRows(lines, translated)
                : visibleWordRows(wordLines, translated);
        return result.isEmpty() ? EMPTY
                : new LyricsTimeline(result, result.stream().anyMatch(line -> !line.translation().isBlank()));
    }

    private static List<WordLine> usableWordLines(String source) {
        return LyricsParser.words(source).stream()
                .filter(line -> !line.words().isEmpty() && hasVisibleText(line.text()))
                .toList();
    }

    public int activeLine(int timeMs) {
        if (lines.isEmpty()) return -1;
        int low = 0;
        int high = lines.size() - 1;
        int active = 0;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (lines.get(middle).timeMs() <= timeMs) {
                active = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return active;
    }

    private static List<LyricLine> visibleRows(List<TimedLine> lines, List<TimedLine> translated) {
        if (lines.isEmpty()) return List.of();
        List<String> translations = alignTranslations(lines, translated);
        List<LyricLine> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            TimedLine line = lines.get(index);
            if (!hasVisibleText(line.text())) continue;
            String translation = index < translations.size() ? translations.get(index) : "";
            if (equivalentText(line.text(), translation)) translation = "";
            result.add(new LyricLine(line.timeMs(), line.text(), translation, null));
        }
        return List.copyOf(result);
    }

    private static List<LyricLine> visibleWordRows(List<WordLine> lines, List<TimedLine> translated) {
        List<TimedLine> timedLines = lines.stream()
                .map(line -> new TimedLine(line.startMs(), line.text()))
                .toList();
        List<String> translations = alignTranslations(timedLines, translated);
        List<LyricLine> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            WordLine line = lines.get(index);
            String translation = index < translations.size() ? translations.get(index) : "";
            if (equivalentText(line.text(), translation)) translation = "";
            result.add(new LyricLine(line.startMs(), line.text(), translation, line));
        }
        return List.copyOf(result);
    }

    private static List<String> alignTranslations(List<TimedLine> lines, List<TimedLine> translated) {
        if (lines.isEmpty() || translated.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(lines.size());
        int translatedIndex = 0;
        for (TimedLine line : lines) {
            while (translatedIndex + 1 < translated.size()
                    && Math.abs(translated.get(translatedIndex + 1).timeMs() - line.timeMs())
                    <= Math.abs(translated.get(translatedIndex).timeMs() - line.timeMs())) {
                translatedIndex++;
            }
            TimedLine nearest = translated.get(translatedIndex);
            result.add(Math.abs(nearest.timeMs() - line.timeMs()) <= 1200 ? nearest.text() : "");
        }
        return List.copyOf(result);
    }

    private static boolean hasVisibleText(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> !Character.isWhitespace(codePoint)
                && Character.getType(codePoint) != Character.FORMAT);
    }

    private static boolean equivalentText(String first, String second) {
        return hasVisibleText(second) && visibleText(first).equals(visibleText(second));
    }

    private static String visibleText(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
