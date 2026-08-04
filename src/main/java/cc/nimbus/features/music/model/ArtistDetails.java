package cc.nimbus.features.music.model;

import java.util.List;

public record ArtistDetails(Artist artist, List<Music> popularSongs, List<Album> albums) {
    public ArtistDetails {
        popularSongs = popularSongs == null ? List.of() : List.copyOf(popularSongs);
        albums = albums == null ? List.of() : List.copyOf(albums);
    }
}
