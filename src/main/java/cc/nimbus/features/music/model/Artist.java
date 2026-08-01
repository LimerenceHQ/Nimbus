package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class Artist {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("tns") private List<String> translatedNames;
    @SerializedName("alias") private List<String> aliases;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public List<String> translatedNames() { return translatedNames == null ? List.of() : List.copyOf(translatedNames); }
    public List<String> aliases() { return aliases == null ? List.of() : List.copyOf(aliases); }
}
