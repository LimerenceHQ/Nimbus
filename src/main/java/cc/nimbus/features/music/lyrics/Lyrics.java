package cc.nimbus.features.music.lyrics;

public record Lyrics(String line, String words, String translated, String romanized) {
    public Lyrics {
        line = line == null ? "" : line;
        words = words == null ? "" : words;
        translated = translated == null ? "" : translated;
        romanized = romanized == null ? "" : romanized;
    }
}
