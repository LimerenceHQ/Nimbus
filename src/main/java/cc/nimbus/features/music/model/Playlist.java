package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class Playlist {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName(value = "coverImgUrl", alternate = "picUrl") private String coverUrl;
    @SerializedName("trackCount") private int trackCount;
    @SerializedName(value = "playCount", alternate = "playcount") private long playCount;
    @SerializedName("creator") private User creator;
    @SerializedName("description") private String description;
    @SerializedName("subscribed") private boolean subscribed;
    @SerializedName("createTime") private long createdAt;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String coverUrl() { return coverUrl == null ? "" : coverUrl; }
    public int trackCount() { return Math.max(0, trackCount); }
    public long playCount() { return Math.max(0L, playCount); }
    public User creator() { return creator; }
    public String description() { return description == null ? "" : description; }
    public boolean subscribed() { return subscribed; }
    public long createdAt() { return createdAt; }

    @Override public boolean equals(Object value) { return value instanceof Playlist playlist && id == playlist.id; }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return name() + " (" + id + ", " + trackCount() + " tracks)"; }
}
