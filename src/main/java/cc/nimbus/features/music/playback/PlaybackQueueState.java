package cc.nimbus.features.music.playback;

import cc.nimbus.features.music.model.Music;

import java.util.List;

public record PlaybackQueueState(List<Music> songs, int index) {
    public PlaybackQueueState {
        songs = List.copyOf(songs);
        if (songs.isEmpty()) index = -1;
        else if (index < 0 || index >= songs.size()) {
            throw new IllegalArgumentException("Queue index is outside the snapshot: " + index);
        }
    }

    public static PlaybackQueueState empty() {
        return new PlaybackQueueState(List.of(), -1);
    }
}
