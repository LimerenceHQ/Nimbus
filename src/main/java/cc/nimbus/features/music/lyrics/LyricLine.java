package cc.nimbus.features.music.lyrics;

public record LyricLine(int timeMs, String text, String translation, WordLine wordLine) {
    public LyricLine {
        timeMs = Math.max(0, timeMs);
        text = text == null ? "" : text;
        translation = translation == null ? "" : translation;
    }
}
