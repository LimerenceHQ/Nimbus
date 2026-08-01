package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Music {
    private static final long INSTRUMENTAL = 131072L;
    private static final long DOLBY_ATMOS = 262144L;
    private static final long DIRTY = 1048576L;
    private static final long HI_RES = 17179869184L;

    @SerializedName("name") private String name;
    @SerializedName("mainTitle") private String mainTitle;
    @SerializedName("additionalTitle") private String additionalTitle;
    @SerializedName("id") private long id;
    @SerializedName("ar") private List<Artist> artists;
    @SerializedName("alia") private List<String> aliases;
    @SerializedName("al") private Album album;
    @SerializedName("dt") private long durationMs;
    @SerializedName("mark") private long features;
    @SerializedName("publishTime") private long publishedAt;
    @SerializedName("tns") private List<String> translatedNames;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String mainTitle() { return mainTitle == null ? "" : mainTitle; }
    public String additionalTitle() { return additionalTitle == null ? "" : additionalTitle; }
    public List<Artist> artists() { return artists == null ? List.of() : List.copyOf(artists); }
    public List<String> aliases() { return aliases == null ? List.of() : List.copyOf(aliases); }
    public Album album() { return album; }
    public long durationMs() { return Math.max(0L, durationMs); }
    public long publishedAt() { return publishedAt; }
    public List<String> translatedNames() { return translatedNames == null ? List.of() : List.copyOf(translatedNames); }
    public String artistsText() {
        if (artists == null || artists.isEmpty()) return "Unknown";
        return artists.stream().map(Artist::name).collect(Collectors.joining(", "));
    }
    public String coverUrl(int size) {
        if (album == null || album.pictureUrl().isBlank()) return "";
        int bounded = Math.max(32, Math.min(2048, size));
        return album.pictureUrl() + "?param=" + bounded + "y" + bounded;
    }
    public boolean instrumental() { return (features & INSTRUMENTAL) != 0; }
    public boolean dolbyAtmos() { return (features & DOLBY_ATMOS) != 0; }
    public boolean explicit() { return (features & DIRTY) != 0; }
    public boolean hiRes() { return (features & HI_RES) != 0; }

    @Override public boolean equals(Object value) { return value instanceof Music music && id == music.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return name() + " - " + artistsText() + " (" + id + ")"; }
}
