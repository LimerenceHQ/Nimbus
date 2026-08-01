package cc.nimbus.features.music.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsParser {
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)");

    private LyricsParser() {}

    public static List<TimedLine> lines(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<TimedLine> result = new ArrayList<>();
        for (String raw : source.split("\\R")) {
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
        for (String raw : source.split("\\R")) {
            Matcher line = YRC_LINE.matcher(raw.trim());
            if (!line.matches()) continue;
            int start = integer(line.group(1));
            int duration = integer(line.group(2));
            List<TimedWord> timedWords = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Matcher word = YRC_WORD.matcher(line.group(3));
            while (word.find()) {
                int wordStart = integer(word.group(1));
                int wordDuration = integer(word.group(2));
                String value = word.group(3);
                timedWords.add(new TimedWord(wordStart, wordStart + wordDuration, value));
                text.append(value);
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
}
