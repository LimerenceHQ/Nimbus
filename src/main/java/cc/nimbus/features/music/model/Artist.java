package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class Artist {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("picUrl") private String pictureUrl;
    @SerializedName("img1v1Url") private String imageUrl;
    @SerializedName("cover") private String coverUrl;
    @SerializedName("avatar") private String avatarUrl;
    @SerializedName("briefDesc") private String description;
    @SerializedName("musicSize") private int musicCount;
    @SerializedName("albumSize") private int albumCount;
    @SerializedName("tns") private List<String> translatedNames;
    @SerializedName("alias") private List<String> aliases;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String pictureUrl() {
        if (coverUrl != null && !coverUrl.isBlank()) return coverUrl;
        if (avatarUrl != null && !avatarUrl.isBlank()) return avatarUrl;
        if (pictureUrl != null && !pictureUrl.isBlank()) return pictureUrl;
        if (imageUrl != null && !imageUrl.isBlank()) return imageUrl;
        return "";
    }
    public String description() { return description == null ? "" : description; }
    public int musicCount() { return Math.max(0, musicCount); }
    public int albumCount() { return Math.max(0, albumCount); }
    public List<String> translatedNames() { return translatedNames == null ? List.of() : List.copyOf(translatedNames); }
    public List<String> aliases() { return aliases == null ? List.of() : List.copyOf(aliases); }
}
