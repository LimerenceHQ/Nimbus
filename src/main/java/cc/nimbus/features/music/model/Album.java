package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Album {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("picUrl") private String pictureUrl;
    @SerializedName("tns") private List<String> translatedNames;
    @SerializedName("artist") private Artist artist;
    @SerializedName("artists") private List<Artist> artists;
    @SerializedName("size") private int trackCount;
    @SerializedName("publishTime") private long publishedAt;
    @SerializedName("description") private String description;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String pictureUrl() { return pictureUrl == null ? "" : pictureUrl; }
    public List<String> translatedNames() { return translatedNames == null ? List.of() : List.copyOf(translatedNames); }
    public Artist artist() { return artist; }
    public List<Artist> artists() { return artists == null ? List.of() : List.copyOf(artists); }
    public int trackCount() { return Math.max(0, trackCount); }
    public long publishedAt() { return Math.max(0L, publishedAt); }
    public String description() { return description == null ? "" : description; }
    public String artistsText() {
        if (artists != null && !artists.isEmpty()) {
            return artists.stream().map(Artist::name).collect(Collectors.joining(", "));
        }
        return artist == null ? "Unknown" : artist.name();
    }

    @Override public boolean equals(Object value) { return value instanceof Album album && id == album.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return name() + " - " + artistsText() + " (" + id + ')'; }
}
