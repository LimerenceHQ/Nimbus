package cc.nimbus.features.music.model;

import java.util.List;

public record AlbumDetails(Album album, List<Music> songs) {
    public AlbumDetails {
        songs = songs == null ? List.of() : List.copyOf(songs);
    }
}
