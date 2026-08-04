package cc.nimbus.ui.clickgui.tool.music;

import cc.nimbus.features.music.lyrics.Lyrics;
import cc.nimbus.features.music.lyrics.LyricsParser;
import cc.nimbus.features.music.lyrics.TimedLine;
import cc.nimbus.features.music.lyrics.WordLine;
import cc.nimbus.features.music.model.Album;
import cc.nimbus.features.music.model.Artist;
import cc.nimbus.features.music.model.ArtistDetails;
import cc.nimbus.features.music.model.CachedSong;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.model.User;
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
    private List<Album> albums = List.of();
    private List<CachedSong> cachedSongs = List.of();
    private Playlist selectedPlaylist;
    private Album selectedAlbum;
    private ArtistDetails selectedArtist;
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
        cachedSongs = manager.cachedSongs();
        switch (route) {
            case PLAYLIST -> {
                if (selectedPlaylist == null) load(route);
                else playlist(selectedPlaylist);
            }
            case ALBUM -> {
                if (selectedAlbum == null) load(route);
                else album(selectedAlbum);
            }
            case ARTIST -> {
                if (selectedArtist == null || selectedArtist.artist() == null) load(route);
                else artist(selectedArtist.artist());
            }
            default -> load(route);
        }
    }

    public void closed() {
        generation++;
        routeGeneration++;
        playbackGeneration++;
        pendingArtwork.clear();
        artwork.clear();
        songs = List.of();
        playlists = List.of();
        albums = List.of();
        cachedSongs = List.of();
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
        albums = List.of();
        selectedPlaylist = null;
        selectedAlbum = null;
        selectedArtist = null;
        switch (route) {
            case RECOMMENDED -> loadRecommended(token);
            case FEATURED -> runRoute(manager.featuredPlaylists(40), token, values -> playlists = values);
            case COVER_FLOW -> loadCoverFlow(token);
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
        selectedAlbum = null;
        long token = ++routeGeneration;
        loading = true;
        error = "";
        CompletableFuture<Playlist> detail = manager.playlist(playlist.id());
        CompletableFuture<List<Music>> tracks = manager.playlistSongs(playlist.id());
        runRoute(detail.thenCombine(tracks, PlaylistResult::new), token, value -> {
            selectedPlaylist = value.playlist() == null ? playlist : value.playlist();
            songs = value.songs();
        });
    }

    public void album(Album album) {
        selectedPlaylist = null;
        selectedAlbum = album;
        long token = ++routeGeneration;
        loading = true;
        error = "";
        runRoute(manager.album(album.id()), token, value -> {
            selectedAlbum = value.album() == null ? album : value.album();
            songs = value.songs();
        });
    }

    public void artist(Artist artist) {
        selectedPlaylist = null;
        selectedAlbum = null;
        selectedArtist = new ArtistDetails(artist, List.of(), List.of());
        long token = ++routeGeneration;
        loading = true;
        error = "";
        runRoute(manager.artist(artist.id()), token, value -> {
            selectedArtist = value;
            songs = value.popularSongs();
            albums = value.albums();
        });
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

    public Artwork thumbnail(Music music, int size) {
        if (music == null) return null;
        String key = "music-thumbnail:" + music.id() + ':' + size;
        requestEncoded(key, manager.cover(music, size));
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

    public Artwork artwork(Album album, int size) {
        if (album == null) return null;
        String key = "album:" + album.id() + ':' + size;
        requestEncoded(key, manager.cover(album, size));
        return artwork.get(key);
    }

    public Artwork artwork(Artist artist, int size) {
        if (artist == null) return null;
        String key = "artist:" + artist.id() + ':' + size;
        requestEncoded(key, manager.portrait(artist, size));
        return artwork.get(key);
    }

    public Artwork artwork(User user, int size) {
        if (user == null) return null;
        String key = "user:" + user.id() + ':' + size;
        requestEncoded(key, manager.avatar(user, size));
        return artwork.get(key);
    }

    private void requestEncoded(String key, CompletableFuture<byte[]> future) {
        if (artwork.containsKey(key) || !pendingArtwork.add(key)) return;
        long token = generation;
        future.whenComplete((bytes, failure) -> main(() -> {
            pendingArtwork.remove(key);
            if (token != generation || failure != null || bytes == null) return;
            artwork.put(key, new Artwork(bytes, null));
        }));
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

    private void loadCoverFlow(long token) {
        CompletableFuture<List<Playlist>> personal = manager.personalizedPlaylists(20);
        CompletableFuture<List<Playlist>> featured = manager.featuredPlaylists(20);
        CompletableFuture<List<Album>> recentAlbums = manager.newAlbums(40);
        CompletableFuture<List<Album>> subscribedAlbums = manager.profile() == null
                ? CompletableFuture.completedFuture(List.of()) : manager.subscribedAlbums(30);
        CompletableFuture<CoverFlowResult> result = personal.thenCombine(featured,
                        (recommended, curated) -> mergePlaylists(manager.accountPlaylists(), recommended, curated))
                .thenCombine(recentAlbums.thenCombine(subscribedAlbums, MusicDataState::mergeAlbums),
                        CoverFlowResult::new);
        runRoute(result, token, value -> {
            playlists = value.playlists();
            albums = value.albums();
        });
    }

    @SafeVarargs
    private static List<Playlist> mergePlaylists(List<Playlist>... sources) {
        Map<Long, Playlist> merged = new LinkedHashMap<>();
        for (List<Playlist> source : sources) {
            for (Playlist playlist : source) merged.putIfAbsent(playlist.id(), playlist);
        }
        return List.copyOf(merged.values());
    }

    private static List<Album> mergeAlbums(List<Album> first, List<Album> second) {
        Map<Long, Album> merged = new LinkedHashMap<>();
        for (Album album : second) merged.putIfAbsent(album.id(), album);
        for (Album album : first) merged.putIfAbsent(album.id(), album);
        return List.copyOf(merged.values());
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
        List<TimedLine> lines = LyricsParser.lines(value.line());
        List<TimedLine> translated = LyricsParser.lines(value.translated());
        return new LyricsView(
                lines,
                alignTranslations(lines, translated),
                LyricsParser.words(value.words())
        );
    }

    private static List<String> alignTranslations(List<TimedLine> lines, List<TimedLine> translated) {
        if (lines.isEmpty() || translated.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(lines.size());
        int translatedIndex = 0;
        for (TimedLine line : lines) {
            while (translatedIndex + 1 < translated.size()
                    && Math.abs(translated.get(translatedIndex + 1).timeMs() - line.timeMs())
                    <= Math.abs(translated.get(translatedIndex).timeMs() - line.timeMs())) {
                translatedIndex++;
            }
            TimedLine nearest = translated.get(translatedIndex);
            result.add(Math.abs(nearest.timeMs() - line.timeMs()) <= 1200 ? nearest.text() : "");
        }
        return List.copyOf(result);
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
    public List<Album> albums() { return albums; }
    public List<CachedSong> cachedSongs() { return cachedSongs; }
    public Playlist selectedPlaylist() { return selectedPlaylist; }
    public Album selectedAlbum() { return selectedAlbum; }
    public ArtistDetails selectedArtist() { return selectedArtist; }
    public Artwork currentArtwork() { return currentArtwork; }
    public Artwork previousArtwork() { return previousArtwork; }
    public LyricsView lyrics() { return lyrics; }
    public boolean loading() { return loading; }
    public String error() { return error; }

    public record LyricsView(List<TimedLine> lines, List<String> translations, List<WordLine> words) {
        private static final LyricsView EMPTY = new LyricsView(List.of(), List.of(), List.of());
        public LyricsView {
            lines = List.copyOf(lines);
            translations = List.copyOf(translations);
            words = List.copyOf(words);
        }

        public boolean hasTranslations() { return !translations.isEmpty(); }
        public String translation(int index) {
            return index >= 0 && index < translations.size() ? translations.get(index) : "";
        }
    }
    private record SearchResult(List<Music> songs, List<Playlist> playlists) { }
    private record RecommendedResult(List<Playlist> playlists, List<Music> songs) { }
    private record PlaylistResult(Playlist playlist, List<Music> songs) { }
    private record CoverFlowResult(List<Playlist> playlists, List<Album> albums) { }
}
