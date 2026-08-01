package cc.nimbus.features.music.lyrics;

import java.util.List;

public record WordLine(int startMs, int durationMs, String text, List<TimedWord> words) {
    public WordLine { words = List.copyOf(words); }
}
