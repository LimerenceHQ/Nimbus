package cc.nimbus.features.music.lyrics;

public record Lyrics(String line, String words, String karaoke, String translated, String romanized) {
    public Lyrics {
        line = line == null ? "" : line;
        words = words == null ? "" : words;
        karaoke = karaoke == null ? "" : karaoke;
        translated = translated == null ? "" : translated;
        romanized = romanized == null ? "" : romanized;
    }
}
