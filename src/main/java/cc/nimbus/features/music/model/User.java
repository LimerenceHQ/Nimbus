package cc.nimbus.features.music.model;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class User {
    @SerializedName("userId") private long id;
    @SerializedName("nickname") private String name;
    @SerializedName("signature") private String signature;
    @SerializedName("vipType") private int vipType;
    @SerializedName("avatarUrl") private String avatarUrl;

    public long id() { return id; }
    public String name() { return name == null ? "Unknown" : name; }
    public String signature() { return signature == null ? "" : signature; }
    public int vipType() { return vipType; }
    public String avatarUrl() { return avatarUrl == null ? "" : avatarUrl; }

    @Override public boolean equals(Object value) { return value instanceof User user && id == user.id; }
    @Override public int hashCode() { return Objects.hash(id); }
}
