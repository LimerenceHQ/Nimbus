package cc.nimbus.features.music.model;

import cc.nimbus.features.music.Quality;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record CachedSong(Music music, Set<Quality> qualities, long sizeBytes, long lastUsedAt) {
    public CachedSong {
        music = Objects.requireNonNull(music, "music");
        qualities = qualities == null || qualities.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(qualities));
        sizeBytes = Math.max(0L, sizeBytes);
        lastUsedAt = Math.max(0L, lastUsedAt);
    }
}
