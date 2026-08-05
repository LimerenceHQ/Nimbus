package cc.nimbus.features.music.integration;

public record SystemMediaState(
        Connection connection,
        String sourceAppId,
        String title,
        String subtitle,
        String artist,
        String albumArtist,
        String albumTitle,
        int trackNumber,
        int albumTrackCount,
        PlaybackStatus playbackStatus,
        double positionSeconds,
        double durationSeconds,
        Controls controls,
        long observedAtMillis,
        String error
) {
    public SystemMediaState {
        connection = connection == null ? Connection.ERROR : connection;
        sourceAppId = text(sourceAppId);
        title = text(title);
        subtitle = text(subtitle);
        artist = text(artist);
        albumArtist = text(albumArtist);
        albumTitle = text(albumTitle);
        playbackStatus = playbackStatus == null ? PlaybackStatus.CLOSED : playbackStatus;
        positionSeconds = finitePositive(positionSeconds);
        durationSeconds = finitePositive(durationSeconds);
        controls = controls == null ? Controls.NONE : controls;
        error = text(error);
    }

    public static SystemMediaState unsupported() {
        return empty(Connection.UNSUPPORTED, "");
    }

    public static SystemMediaState connecting() {
        return empty(Connection.CONNECTING, "");
    }

    public static SystemMediaState noSession() {
        return empty(Connection.NO_SESSION, "");
    }

    public static SystemMediaState error(String message) {
        return empty(Connection.ERROR, message);
    }

    public boolean hasSession() {
        return connection == Connection.ACTIVE;
    }

    private static SystemMediaState empty(Connection connection, String error) {
        return new SystemMediaState(connection, "", "", "", "", "", "", 0, 0,
                PlaybackStatus.CLOSED, 0d, 0d, Controls.NONE, System.currentTimeMillis(), error);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static double finitePositive(double value) {
        return Double.isFinite(value) ? Math.max(0d, value) : 0d;
    }

    public enum Connection {
        UNSUPPORTED,
        CONNECTING,
        NO_SESSION,
        ACTIVE,
        ERROR
    }

    public enum PlaybackStatus {
        CLOSED,
        OPENED,
        CHANGING,
        STOPPED,
        PLAYING,
        PAUSED;

        public static PlaybackStatus fromNative(int value) {
            return value >= 0 && value < values().length ? values()[value] : CLOSED;
        }
    }

    public record Controls(
            boolean play,
            boolean pause,
            boolean stop,
            boolean next,
            boolean previous,
            boolean toggle,
            boolean shuffle,
            boolean repeat,
            boolean playbackRate,
            boolean playbackPosition
    ) {
        public static final Controls NONE = new Controls(
                false, false, false, false, false, false, false, false, false, false);
    }
}
