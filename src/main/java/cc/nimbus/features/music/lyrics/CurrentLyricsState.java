package cc.nimbus.features.music.lyrics;

public record CurrentLyricsState(long musicId, LyricsTimeline timeline, boolean loading, String error) {
    public static final CurrentLyricsState EMPTY = new CurrentLyricsState(-1L, LyricsTimeline.EMPTY, false, "");

    public CurrentLyricsState {
        timeline = timeline == null ? LyricsTimeline.EMPTY : timeline;
        error = error == null ? "" : error;
    }

    public static CurrentLyricsState loading(long musicId) {
        return new CurrentLyricsState(musicId, LyricsTimeline.EMPTY, true, "");
    }
}
