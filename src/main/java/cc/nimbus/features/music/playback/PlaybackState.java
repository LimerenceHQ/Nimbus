package cc.nimbus.features.music.playback;

import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.resource.TransferStatus;

public record PlaybackState(PlaybackPhase phase, Music music, float progressSeconds, float durationSeconds,
                            float volume, float rate, int queueIndex, int queueSize, String error,
                            TransferStatus transfer, TransferStatus prefetch) {
    public PlaybackState { error = error == null ? "" : error; }
}
