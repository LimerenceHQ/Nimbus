package cc.nimbus.features.music.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsParser {
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD_MARKER = Pattern.compile(
            "[({<](\\d+),(\\d+)(?:,\\d+)?[)}>]"
    );

    private LyricsParser() {}

    public static List<TimedLine> lines(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<TimedLine> result = new ArrayList<>();
        for (String raw : splitLines(source)) {
            Matcher matcher = LRC_TIME.matcher(raw);
            List<Integer> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                times.add((Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2))) * 1000
                        + fraction(matcher.group(3)));
                textStart = matcher.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(Math.min(textStart, raw.length())).trim();
            times.forEach(time -> result.add(new TimedLine(time, text)));
        }
        result.sort(Comparator.comparingInt(TimedLine::timeMs));
        return List.copyOf(result);
    }

    public static List<WordLine> words(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<WordLine> result = new ArrayList<>();
        for (String raw : splitLines(source)) {
            Matcher line = YRC_LINE.matcher(raw.trim());
            if (!line.matches()) continue;
            int start = integer(line.group(1));
            int duration = integer(line.group(2));
            List<TimedWord> timedWords = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            String encoded = line.group(3);
            Matcher marker = YRC_WORD_MARKER.matcher(encoded);
            if (marker.find()) {
                int wordStart = absoluteWordStart(start, duration, integer(marker.group(1)));
                int wordDuration = integer(marker.group(2));
                int textStart = marker.end();
                while (marker.find()) {
                    appendWord(timedWords, text, wordStart, wordDuration,
                            encoded.substring(textStart, marker.start()));
                    wordStart = absoluteWordStart(start, duration, integer(marker.group(1)));
                    wordDuration = integer(marker.group(2));
                    textStart = marker.end();
                }
                appendWord(timedWords, text, wordStart, wordDuration, encoded.substring(textStart));
            }
            result.add(new WordLine(start, duration, text.toString(), timedWords));
        }
        result.sort(Comparator.comparingInt(WordLine::startMs));
        return List.copyOf(result);
    }

    private static int fraction(String value) {
        if (value == null || value.isEmpty()) return 0;
        int parsed = integer(value);
        return switch (value.length()) { case 1 -> parsed * 100; case 2 -> parsed * 10; default -> parsed; };
    }
    private static int integer(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String[] splitLines(String source) {
        String normalized = source.replace("\\r\\n", "\n").replace("\\n", "\n");
        return normalized.split("\\R");
    }

    private static int absoluteWordStart(int lineStart, int lineDuration, int wordStart) {
        return wordStart < lineStart && wordStart <= lineDuration ? lineStart + wordStart : wordStart;
    }

    private static void appendWord(List<TimedWord> words, StringBuilder text,
                                   int start, int duration, String value) {
        if (value.isEmpty()) return;
        if (duration <= 0 && !words.isEmpty()) {
            TimedWord previous = words.removeLast();
            words.add(new TimedWord(previous.startMs(), previous.endMs(), previous.text() + value));
        } else {
            words.add(new TimedWord(start, start + Math.max(1, duration), value));
        }
        text.append(value);
    }
}
