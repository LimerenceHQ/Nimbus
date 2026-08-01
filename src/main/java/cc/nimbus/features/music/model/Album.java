package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class Album {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("picUrl") private String pictureUrl;
    @SerializedName("tns") private List<String> translatedNames;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String pictureUrl() { return pictureUrl == null ? "" : pictureUrl; }
    public List<String> translatedNames() { return translatedNames == null ? List.of() : List.copyOf(translatedNames); }
}
