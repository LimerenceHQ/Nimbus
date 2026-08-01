package cc.nimbus.features.music.resource;

import cc.nimbus.features.music.Quality;

public record TransferStatus(long musicId, TransferStage stage, long downloadedBytes, long totalBytes,
                             long bytesPerSecond, Quality quality, String error) {
    public static TransferStatus idle() { return new TransferStatus(-1L, TransferStage.IDLE, 0L, -1L, 0L, null, ""); }
    public TransferStatus {
        error = error == null ? "" : error;
        downloadedBytes = Math.max(0L, downloadedBytes);
        bytesPerSecond = Math.max(0L, bytesPerSecond);
    }
}
