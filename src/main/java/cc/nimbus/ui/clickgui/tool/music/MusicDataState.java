package cc.nimbus.ui.clickgui.tool.music;

import cc.nimbus.features.music.lyrics.Lyrics;
import cc.nimbus.features.music.lyrics.LyricsParser;
import cc.nimbus.features.music.lyrics.TimedLine;
import cc.nimbus.features.music.lyrics.WordLine;
import cc.nimbus.features.music.model.CachedSong;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.playback.PlaybackState;
import cc.nimbus.features.music.resource.Artwork;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.ui.clickgui.ClickGuiSession;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MusicDataState {
    private final MusicManager manager;
    private final Map<String, Artwork> artwork = new LinkedHashMap<>(32, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Artwork> eldest) {
            return size() > 128;
        }
    };
    private final Set<String> pendingArtwork = new HashSet<>();
    private long generation;
    private long routeGeneration;
    private long playbackGeneration;
    private long currentSongId = -1L;
    private List<Music> songs = List.of();
    private List<Playlist> playlists = List.of();
    private List<CachedSong> cachedSongs = List.of();
    private Playlist selectedPlaylist;
    private Artwork currentArtwork;
    private Artwork previousArtwork;
    private LyricsView lyrics = LyricsView.EMPTY;
    private boolean loading;
    private String error = "";

    public MusicDataState(MusicManager manager) {
        this.manager = manager;
    }

    public void opened(ClickGuiSession.MusicRoute route) {
        generation++;
        load(route);
    }

    public void closed() {
        generation++;
        routeGeneration++;
        playbackGeneration++;
        pendingArtwork.clear();
        artwork.clear();
        songs = List.of();
        playlists = List.of();
        cachedSongs = List.of();
        selectedPlaylist = null;
        currentSongId = -1L;
        currentArtwork = null;
        previousArtwork = null;
        lyrics = LyricsView.EMPTY;
        loading = false;
        error = "";
    }

    public void load(ClickGuiSession.MusicRoute route) {
        long token = ++routeGeneration;
        loading = true;
        error = "";
        songs = List.of();
        playlists = List.of();
        selectedPlaylist = null;
        switch (route) {
            case RECOMMENDED -> loadRecommended(token);
            case FEATURED -> runRoute(manager.featuredPlaylists(40), token, values -> playlists = values);
            case LIKED -> {
                if (manager.profile() == null) finishRoute(token);
                else runRoute(manager.likedSongs(200), token, values -> songs = values);
            }
            case CACHED -> {
                cachedSongs = manager.cachedSongs();
                finishRoute(token);
            }
            default -> finishRoute(token);
        }
    }

    public void search(String query) {
        long token = ++routeGeneration;
        loading = true;
        error = "";
        CompletableFuture<List<Music>> songSearch = manager.searchSongs(query, 50);
        CompletableFuture<List<Playlist>> playlistSearch = manager.searchPlaylists(query, 30);
        runRoute(songSearch.thenCombine(playlistSearch, SearchResult::new), token, result -> {
            songs = result.songs();
            playlists = result.playlists();
        });
    }

    public void playlist(Playlist playlist) {
        selectedPlaylist = playlist;
        long token = ++routeGeneration;
        loading = true;
        error = "";
        runRoute(manager.playlistSongs(playlist.id()), token, values -> songs = values);
    }

    public void refreshCached() {
        cachedSongs = manager.cachedSongs();
    }

    public void observePlayback(PlaybackState playback) {
        Music music = playback.music();
        long id = music == null ? -1L : music.id();
        if (id == currentSongId) return;
        currentSongId = id;
        previousArtwork = currentArtwork;
        currentArtwork = null;
        lyrics = LyricsView.EMPTY;
        long token = ++playbackGeneration;
        if (music == null) return;
        runPlayback(manager.artwork(music, 1024), token, value -> currentArtwork = value);
        runPlayback(manager.lyrics(music.id()), token, value -> lyrics = parse(value));
    }

    public Artwork artwork(Music music, int size) {
        if (music == null) return null;
        String key = "music:" + music.id() + ':' + size;
        requestArtwork(key, manager.artwork(music, size));
        return artwork.get(key);
    }

    public Artwork artwork(Playlist playlist, int size) {
        if (playlist == null) return null;
        String key = "playlist:" + playlist.id() + ':' + size;
        if (!artwork.containsKey(key) && pendingArtwork.add(key)) {
            long token = generation;
            manager.cover(playlist, size).whenComplete((bytes, failure) -> main(() -> {
                pendingArtwork.remove(key);
                if (token != generation || failure != null || bytes == null) return;
                artwork.put(key, new Artwork(bytes, null));
            }));
        }
        return artwork.get(key);
    }

    private void requestArtwork(String key, CompletableFuture<Artwork> future) {
        if (artwork.containsKey(key) || !pendingArtwork.add(key)) return;
        long token = generation;
        future.whenComplete((value, failure) -> main(() -> {
            pendingArtwork.remove(key);
            if (token != generation || failure != null || value == null) return;
            artwork.put(key, value);
        }));
    }

    private void loadRecommended(long token) {
        CompletableFuture<List<Playlist>> publicPlaylists = manager.personalizedPlaylists(12);
        CompletableFuture<List<Playlist>> dailyPlaylists = manager.profile() == null
                ? CompletableFuture.completedFuture(List.of()) : manager.dailyRecommendedPlaylists();
        CompletableFuture<List<Music>> dailySongs = manager.profile() == null
                ? CompletableFuture.completedFuture(List.of()) : manager.dailyRecommendedSongs();
        CompletableFuture<RecommendedResult> result = publicPlaylists.thenCombine(dailyPlaylists, (personal, daily) -> {
            List<Playlist> combined = new ArrayList<>(daily);
            for (Playlist playlist : personal) if (!combined.contains(playlist)) combined.add(playlist);
            return combined;
        }).thenCombine(dailySongs, RecommendedResult::new);
        runRoute(result, token, value -> {
            playlists = value.playlists();
            songs = value.songs();
        });
    }

    private <T> void runRoute(CompletableFuture<T> future, long token, Consumer<T> success) {
        long owner = generation;
        future.whenComplete((value, failure) -> main(() -> {
            if (owner != generation || token != routeGeneration) return;
            loading = false;
            if (failure != null) {
                error = message(failure);
                return;
            }
            success.accept(value);
        }));
    }

    private <T> void runPlayback(CompletableFuture<T> future, long token, Consumer<T> success) {
        long owner = generation;
        future.whenComplete((value, failure) -> main(() -> {
            if (owner != generation || token != playbackGeneration || failure != null) return;
            success.accept(value);
        }));
    }

    private void finishRoute(long token) {
        if (token == routeGeneration) loading = false;
    }

    private static LyricsView parse(Lyrics value) {
        if (value == null) return LyricsView.EMPTY;
        return new LyricsView(
                LyricsParser.lines(value.line()),
                LyricsParser.lines(value.translated()),
                LyricsParser.words(value.words())
        );
    }

    private static void main(Runnable action) {
        Minecraft.getInstance().execute(action);
    }

    private static String message(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }

    public List<Music> songs() { return songs; }
    public List<Playlist> playlists() { return playlists; }
    public List<CachedSong> cachedSongs() { return cachedSongs; }
    public Playlist selectedPlaylist() { return selectedPlaylist; }
    public Artwork currentArtwork() { return currentArtwork; }
    public Artwork previousArtwork() { return previousArtwork; }
    public LyricsView lyrics() { return lyrics; }
    public boolean loading() { return loading; }
    public String error() { return error; }

    public record LyricsView(List<TimedLine> lines, List<TimedLine> translated, List<WordLine> words) {
        private static final LyricsView EMPTY = new LyricsView(List.of(), List.of(), List.of());
        public LyricsView {
            lines = List.copyOf(lines);
            translated = List.copyOf(translated);
            words = List.copyOf(words);
        }
    }
    private record SearchResult(List<Music> songs, List<Playlist> playlists) { }
    private record RecommendedResult(List<Playlist> playlists, List<Music> songs) { }
}
