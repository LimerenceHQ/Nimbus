package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.features.music.MusicConfig;
import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.lyrics.TimedLine;
import cc.nimbus.features.music.lyrics.TimedWord;
import cc.nimbus.features.music.lyrics.WordLine;
import cc.nimbus.features.music.model.CachedSong;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.model.User;
import cc.nimbus.features.music.playback.PlayMode;
import cc.nimbus.features.music.playback.PlaybackQueueState;
import cc.nimbus.features.music.playback.PlaybackState;
import cc.nimbus.features.music.resource.Artwork;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.ui.clickgui.ClickGuiIcons;
import cc.nimbus.ui.clickgui.ClickGuiSession;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.input.FocusManager;
import cc.nimbus.ui.clickgui.input.TextInput;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import cc.nimbus.ui.clickgui.tool.music.MusicDataState;
import cc.nimbus.utils.render.font.NimbusFonts;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MusicTool implements ToolPage {
    private static final float SIDEBAR_WIDTH = 194f;
    private static final float PLAYER_HEIGHT = 76f;
    private static final float HEADER_HEIGHT = 58f;
    private static final float LYRIC_ROW_HEIGHT = 68f;
    private static final int IMAGE_PLACEHOLDER = 0x351F2329;

    private final ClickGuiSession session;
    private final FocusManager focus;
    private final MusicManager manager = Core.INSTANCE.getMusicManager();
    private final MusicDataState data = new MusicDataState(manager);
    private final TextInput search = new TextInput(Minecraft.getInstance().font, 80);
    private final TextInput country = new TextInput(Minecraft.getInstance().font, 4);
    private final TextInput phone = new TextInput(Minecraft.getInstance().font, 15);
    private final TextInput captcha = new TextInput(Minecraft.getInstance().font, 8);
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hovers = boundedAnimations();
    private final Map<String, Float> imageFades = boundedAnimations();
    private final Map<Artwork, byte[]> encoded = new IdentityHashMap<>();
    private UiRect sidebarBounds;
    private UiRect sidebarClipBounds;
    private UiRect bodyBounds;
    private UiRect searchBounds;
    private UiRect seekBounds;
    private UiRect volumeBounds;
    private UiRect rateBounds;
    private UiRect countryBounds;
    private UiRect phoneBounds;
    private UiRect captchaBounds;
    private UiRect queueBounds;
    private float contentScroll;
    private float targetContentScroll;
    private float maximumContentScroll;
    private float sidebarScroll;
    private float targetSidebarScroll;
    private float maximumSidebarScroll;
    private float queueScroll;
    private float targetQueueScroll;
    private float maximumQueueScroll;
    private float routeProgress;
    private float coverBlend;
    private float queueProgress;
    private float previewProgress;
    private float translationProgress = 1f;
    private boolean queueOpen;
    private boolean coverPreview;
    private boolean translationsEnabled = true;
    private boolean draggingSeek;
    private boolean draggingVolume;
    private boolean draggingRate;
    private boolean disposed = true;
    private LoginTab loginTab = LoginTab.QR;
    private MusicConfig.Hotkey bindingHotkey;
    private Artwork qrArtwork;
    private boolean qrRequesting;
    private boolean qrChecking;
    private long nextQrCheck;
    private long generation;
    private String message = "";
    private long messageUntil;
    private ClickGuiSession.MusicRoute previousRoute = ClickGuiSession.MusicRoute.RECOMMENDED;
    private long marqueeSongId = -1L;
    private long marqueeStartedAt;

    public MusicTool(ClickGuiSession session, FocusManager focus) {
        this.session = session;
        this.focus = focus;
        country.value("86");
    }

    @Override
    public void opened() {
        disposed = false;
        generation++;
        routeProgress = 0f;
        data.opened(session.musicRoute());
    }

    @Override
    public void render(UiContext context, UiRect bounds) {
        PlaybackState playback = manager.state();
        long currentSongId = playback.music() == null ? -1L : playback.music().id();
        if (currentSongId != marqueeSongId) {
            marqueeSongId = currentSongId;
            marqueeStartedAt = context.nowMillis();
        }
        data.observePlayback(playback);
        hits.clear();
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float scrollSpeed = 1f - (float) Math.exp(-context.deltaSeconds() * 18f);
        contentScroll = animate(contentScroll, targetContentScroll, scrollSpeed);
        sidebarScroll = animate(sidebarScroll, targetSidebarScroll, scrollSpeed);
        queueScroll = animate(queueScroll, targetQueueScroll, scrollSpeed);
        routeProgress = animate(routeProgress, 1f, speed);
        coverBlend = data.currentArtwork() == null ? 0f : animate(coverBlend, 1f, speed * .72f);
        queueProgress = animate(queueProgress, queueOpen ? 1f : 0f, speed);
        previewProgress = animate(previewProgress, coverPreview ? 1f : 0f, speed);
        translationProgress = animate(translationProgress, translationsEnabled ? 1f : 0f, speed);

        boolean nowPlaying = session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING;
        sidebarBounds = nowPlaying ? null
                : new UiRect(bounds.x(), bounds.y(), SIDEBAR_WIDTH, bounds.height() - PLAYER_HEIGHT);
        bodyBounds = nowPlaying
                ? bounds
                : new UiRect(bounds.x() + SIDEBAR_WIDTH, bounds.y() + HEADER_HEIGHT,
                bounds.width() - SIDEBAR_WIDTH, bounds.height() - HEADER_HEIGHT - PLAYER_HEIGHT);
        if (!nowPlaying) {
            context.frame().draw().rect(bounds.x() + SIDEBAR_WIDTH, bounds.y(), 1f,
                    bounds.height() - PLAYER_HEIGHT, ClickGuiTheme.STROKE);
            renderSidebar(context, bounds);
            renderHeader(context, bounds);
        }
        renderRoute(context, playback);
        context.frame().draw().rect(bounds.x(), bounds.y() + bounds.height() - PLAYER_HEIGHT,
                bounds.width(), 1f, ClickGuiTheme.STROKE);
        renderPlayer(context, bounds, playback);
        if (queueProgress > .005f) renderQueue(context, bounds, playback);
        else queueBounds = null;
        if (previewProgress > .005f) renderCoverPreview(context, bounds, playback);
        renderMessage(context, bounds);
        pollQr(context.nowMillis());
    }

    private void renderSidebar(UiContext context, UiRect bounds) {
        context.center(NimbusFonts.DISPLAY, 18.5f, "Nimbus Music",
                bounds.x() + SIDEBAR_WIDTH * .5f, bounds.y() + 12f, ClickGuiTheme.TEXT);

        User profile = manager.profile();
        UiRect account = new UiRect(bounds.x() + 8f, bounds.y() + 53f, SIDEBAR_WIDTH - 16f, 38f);
        boolean accountActive = session.musicRoute() == ClickGuiSession.MusicRoute.LOGIN;
        if (accountActive || account.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(account.x(), account.y(), account.width(), account.height(), 4f,
                    accountActive ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        }
        UiRect avatar = new UiRect(account.x() + 5f, account.y() + 5f, 28f, 28f);
        if (profile == null) {
            context.frame().draw().roundedRect(avatar.x(), avatar.y(), avatar.width(), avatar.height(), 14f,
                    IMAGE_PLACEHOLDER);
            icon(context, ClickGuiIcons.USER, avatar.x() + 6f, avatar.y() + 6f, 16f, ClickGuiTheme.MUTED);
        } else {
            drawArtwork(context, data.artwork(profile, 96), "profile:" + profile.id(), avatar, 14f, 255);
        }
        context.medium(profile == null ? context.translate("nimbus.music.sign_in", "Sign In")
                        : ellipsize(context, profile.name(), account.width() - 48f),
                account.x() + 42f, account.y() + 12f, 10.5f,
                accountActive ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(account, Action.NAVIGATE, ClickGuiSession.MusicRoute.LOGIN));

        sidebarClipBounds = new UiRect(bounds.x() + 8f, bounds.y() + 99f,
                SIDEBAR_WIDTH - 16f, bounds.height() - PLAYER_HEIGHT - 107f);
        int clip = context.clip(sidebarClipBounds, 3f);
        try {
            float y = sidebarClipBounds.y() + sidebarScroll;
            float contentStart = y;
            y = group(context, context.translate("nimbus.music.group.discover", "DISCOVER:"), y);
            y = nav(context, context.translate("nimbus.music.recommended", "Recommended"),
                    ClickGuiIcons.HOME, ClickGuiSession.MusicRoute.RECOMMENDED, y);
            y = nav(context, context.translate("nimbus.music.featured", "Featured"),
                    ClickGuiIcons.SPARKLES, ClickGuiSession.MusicRoute.FEATURED, y);
            y = nav(context, context.translate("nimbus.music.search", "Search"),
                    ClickGuiIcons.SEARCH, ClickGuiSession.MusicRoute.SEARCH, y);
            y += 8f;
            y = group(context, context.translate("nimbus.music.group.my_music", "MY MUSIC:"), y);
            y = nav(context, context.translate("nimbus.music.liked", "Liked Music"),
                    ClickGuiIcons.HEART, ClickGuiSession.MusicRoute.LIKED, y);
            y = nav(context, context.translate("nimbus.music.cached", "Cached Songs"),
                    ClickGuiIcons.CACHE, ClickGuiSession.MusicRoute.CACHED, y);
            y = nav(context, context.translate("nimbus.music.now_playing", "Now Playing"),
                    ClickGuiIcons.MUSIC, ClickGuiSession.MusicRoute.NOW_PLAYING, y);
            y += 8f;

            if (profile != null) {
                List<Playlist> created = manager.accountPlaylists().stream()
                        .filter(playlist -> playlist.creator() != null && playlist.creator().id() == profile.id())
                        .toList();
                List<Playlist> subscribed = manager.accountPlaylists().stream()
                        .filter(Playlist::subscribed).toList();
                if (!created.isEmpty()) {
                    y = group(context, context.translate("nimbus.music.group.playlists", "PLAYLISTS:"), y);
                    for (Playlist playlist : created) y = playlistNav(context, playlist, y);
                    y += 7f;
                }
                if (!subscribed.isEmpty()) {
                    y = group(context, context.translate("nimbus.music.group.subscribed", "SUBSCRIBED:"), y);
                    for (Playlist playlist : subscribed) y = playlistNav(context, playlist, y);
                    y += 7f;
                }
            }
            maximumSidebarScroll = Math.max(0f, y - contentStart - sidebarClipBounds.height());
            targetSidebarScroll = Math.clamp(targetSidebarScroll, -maximumSidebarScroll, 0f);
            sidebarScroll = Math.clamp(sidebarScroll, -maximumSidebarScroll, 0f);
        } finally {
            context.restore(clip);
        }
    }

    private float group(UiContext context, String label, float y) {
        context.medium(label, sidebarBounds.x() + 10f, y + 3f, 9.2f, 0xFFF2F3F5);
        return y + 22f;
    }

    private float nav(UiContext context, String label, String icon, ClickGuiSession.MusicRoute route, float y) {
        UiRect row = new UiRect(sidebarClipBounds.x(), y, sidebarClipBounds.width(), 28f);
        if (!row.intersects(sidebarClipBounds)) return y + 31f;
        boolean active = session.musicRoute() == route;
        boolean hover = row.contains(context.mouseX(), context.mouseY());
        if (active || hover) context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                active ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        icon(context, icon, row.x() + 7f, row.y() + 7f, 14f, active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        context.textIn(label, new UiRect(row.x() + 28f, row.y(), row.width() - 33f, row.height()),
                10f, active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(row, Action.NAVIGATE, route, sidebarClipBounds));
        return y + 31f;
    }

    private float playlistNav(UiContext context, Playlist playlist, float y) {
        UiRect row = new UiRect(sidebarClipBounds.x(), y, sidebarClipBounds.width(), 38f);
        if (!row.intersects(sidebarClipBounds)) return y + 41f;
        boolean active = data.selectedPlaylist() != null && data.selectedPlaylist().id() == playlist.id()
                && session.musicRoute() == ClickGuiSession.MusicRoute.PLAYLIST;
        if (active || row.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                    active ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        }
        UiRect cover = new UiRect(row.x() + 5f, row.y() + 5f, 28f, 28f);
        drawArtwork(context, data.artwork(playlist, 96), "sidebar-playlist:" + playlist.id(), cover, 3f, 255);
        context.textIn(ellipsize(context, playlist.name(), row.width() - 48f),
                new UiRect(row.x() + 41f, row.y(), row.width() - 46f, row.height()), 9.8f,
                active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(row, Action.OPEN_PLAYLIST, playlist, sidebarClipBounds));
        return y + 41f;
    }

    private void renderHeader(UiContext context, UiRect bounds) {
        searchBounds = new UiRect(bounds.x() + SIDEBAR_WIDTH + 18f, bounds.y() + 14f,
                Math.min(292f, bounds.width() - SIDEBAR_WIDTH - 78f), 29f);
        search.render(context, searchBounds,
                context.translate("nimbus.music.search_placeholder", "Search songs and playlists"),
                10.5f, false);
        icon(context, ClickGuiIcons.SEARCH, searchBounds.x() + searchBounds.width() - 22f,
                searchBounds.y() + 7f, 14f, ClickGuiTheme.MUTED);
    }

    private void renderRoute(UiContext context, PlaybackState playback) {
        float offset = (1f - routeProgress) * 16f;
        UiRect shifted = new UiRect(bodyBounds.x() + offset, bodyBounds.y(), bodyBounds.width(), bodyBounds.height());
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bodyBounds.x(), bodyBounds.y(), bodyBounds.width(), bodyBounds.height()),
                Math.round(routeProgress * 255f));
        int clip = context.clip(bodyBounds, 0f);
        try {
            float y = shifted.y() + 12f + contentScroll;
            float end = switch (session.musicRoute()) {
                case RECOMMENDED -> renderRecommended(context, shifted, y);
                case FEATURED -> renderFeatured(context, shifted, y);
                case SEARCH -> renderSearch(context, shifted, y);
                case LIKED -> renderSongsPage(context, shifted, y,
                        context.translate("nimbus.music.liked", "Liked Music"), data.songs());
                case CACHED -> renderCached(context, shifted, y);
                case PLAYLIST -> renderPlaylist(context, shifted, y);
                case NOW_PLAYING -> renderNowPlaying(context, shifted, playback);
                case LOGIN -> renderLogin(context, shifted, y);
            };
            maximumContentScroll = session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING
                    ? 0f : Math.max(0f, end - shifted.y() - shifted.height() - contentScroll + 14f);
            targetContentScroll = Math.clamp(targetContentScroll, -maximumContentScroll, 0f);
            contentScroll = Math.clamp(contentScroll, -maximumContentScroll, 0f);
        } finally {
            context.restore(clip);
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private float renderRecommended(UiContext context, UiRect bounds, float y) {
        y = heading(context, context.translate("nimbus.music.recommended", "Recommended"),
                context.translate("nimbus.music.recommended.subtitle", "Made for this session"), bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        y = section(context, context.translate("nimbus.music.daily_playlists",
                "Daily and Personalized Playlists"), bounds, y);
        y = playlistGrid(context, bounds, y, data.playlists(), 8);
        if (!data.songs().isEmpty()) {
            y += 10f;
            float sectionY = y;
            y = section(context, context.translate("nimbus.music.songs_for_you", "Songs for You"), bounds, y);
            control(context, new UiRect(bounds.x() + bounds.width() - 46f, sectionY - 6f, 28f, 28f),
                    ClickGuiIcons.PLAY, Action.PLAY_ALL, bodyBounds);
            y = songList(context, bounds, y, data.songs());
        }
        return y;
    }

    private float renderFeatured(UiContext context, UiRect bounds, float y) {
        y = heading(context, context.translate("nimbus.music.featured", "Featured"),
                context.translate("nimbus.music.featured.subtitle", "High quality playlists"), bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        return playlistGrid(context, bounds, y, data.playlists(), data.playlists().size());
    }

    private float renderSearch(UiContext context, UiRect bounds, float y) {
        y = heading(context, context.translate("nimbus.music.search", "Search"),
                search.value().isBlank()
                        ? context.translate("nimbus.music.search.subtitle", "Songs and playlists")
                        : search.value(), bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        if (data.songs().isEmpty() && data.playlists().isEmpty()) {
            context.text(context.translate("nimbus.music.no_results", "No results"),
                    bounds.x() + 18f, y + 10f, 11f, ClickGuiTheme.MUTED);
            return y + 44f;
        }
        if (!data.playlists().isEmpty()) {
            y = section(context, context.translate("nimbus.music.playlists", "Playlists"), bounds, y);
            y = playlistGrid(context, bounds, y, data.playlists(), Math.min(8, data.playlists().size()));
        }
        if (!data.songs().isEmpty()) {
            y += 8f;
            y = section(context, context.translate("nimbus.music.songs", "Songs"), bounds, y);
            y = songList(context, bounds, y, data.songs());
        }
        return y;
    }

    private float renderSongsPage(UiContext context, UiRect bounds, float y, String title, List<Music> songs) {
        float headingY = y;
        y = heading(context, title,
                context.translate("nimbus.music.song_count", "%s songs", songs.size()), bounds, y);
        if (manager.profile() == null && session.musicRoute() == ClickGuiSession.MusicRoute.LIKED) {
            context.text(context.translate("nimbus.music.sign_in_liked", "Sign in to view liked music"),
                    bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
            return y + 42f;
        }
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        if (!songs.isEmpty()) {
            control(context, new UiRect(bounds.x() + bounds.width() - 46f, headingY - 2f, 28f, 28f),
                    ClickGuiIcons.PLAY, Action.PLAY_ALL, bodyBounds);
        }
        return songList(context, bounds, y, songs);
    }

    private float renderCached(UiContext context, UiRect bounds, float y) {
        List<CachedSong> cached = data.cachedSongs();
        y = heading(context, context.translate("nimbus.music.cached", "Cached Songs"),
                formatBytes(manager.cacheSizeBytes()), bounds, y);
        if (cached.isEmpty()) {
            context.text(context.translate("nimbus.music.no_cached", "No cached songs"),
                    bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
            return y + 42f;
        }
        List<Long> ids = cached.stream().map(value -> value.music().id()).toList();
        for (int index = 0; index < cached.size(); index++) {
            CachedSong value = cached.get(index);
            UiRect row = new UiRect(bounds.x() + 14f, y, bounds.width() - 28f, 48f);
            if (row.intersects(bodyBounds)) {
                hoverRow(context, row, "cached:" + value.music().id());
                UiRect cover = new UiRect(row.x() + 7f, row.y() + 6f, 36f, 36f);
                drawArtwork(context, data.thumbnail(value.music(), 128), "cached-cover:" + value.music().id(),
                        cover, 4f, 255);
                context.draw(NimbusFonts.CONTENT_MEDIUM, 10.8f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.8f,
                                value.music().name(), row.width() * .38f),
                        row.x() + 53f, row.y() + 8f, ClickGuiTheme.TEXT);
                context.draw(NimbusFonts.CONTENT_MEDIUM, 9.2f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.2f,
                                value.music().artistsText(), row.width() * .38f),
                        row.x() + 53f, row.y() + 27f, ClickGuiTheme.MUTED);
                String detail = value.qualities().stream().map(Quality::apiValue).reduce((a, b) -> a + ", " + b).orElse("")
                        + "  " + formatBytes(value.sizeBytes());
                context.medium(detail, row.x() + row.width() * .62f, row.y() + 17f, 9.5f,
                        ClickGuiTheme.MUTED);
                UiRect delete = new UiRect(row.x() + row.width() - 29f, row.y() + 12f, 24f, 24f);
                icon(context, ClickGuiIcons.TRASH, delete.x() + 5f, delete.y() + 5f, 14f, ClickGuiTheme.MUTED);
                hits.add(new Hit(delete, Action.DELETE_CACHED, value.music().id(), bodyBounds));
                hits.add(new Hit(new UiRect(row.x(), row.y(), row.width() - 31f, row.height()),
                        Action.PLAY_CACHED, new CachedSelection(ids, index), bodyBounds));
            }
            y += 52f;
        }
        return y;
    }

    private float renderPlaylist(UiContext context, UiRect bounds, float y) {
        Playlist playlist = data.selectedPlaylist();
        if (playlist == null) return error(context, bounds, y);
        Artwork artwork = data.artwork(playlist, 512);
        UiRect cover = new UiRect(bounds.x() + 18f, y, 156f, 156f);
        drawArtwork(context, artwork, "playlist:" + playlist.id(), cover, 4f, 255);
        float infoX = cover.x() + cover.width() + 18f;
        float infoWidth = bounds.x() + bounds.width() - infoX - 22f;
        context.draw(NimbusFonts.CONTENT_MEDIUM, 22f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 22f, playlist.name(), infoWidth),
                infoX, cover.y(), ClickGuiTheme.TEXT);
        User creator = playlist.creator();
        UiRect avatar = new UiRect(infoX, cover.y() + 38f, 34f, 34f);
        if (creator == null) {
            context.frame().draw().roundedRect(avatar.x(), avatar.y(), avatar.width(), avatar.height(), 16f,
                    IMAGE_PLACEHOLDER);
            icon(context, ClickGuiIcons.USER, avatar.x() + 7f, avatar.y() + 7f, 18f, ClickGuiTheme.MUTED);
        } else {
            drawArtwork(context, data.artwork(creator, 128), "playlist-creator:" + creator.id(),
                    avatar, 16f, 255);
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f, creator.name(), avatar.x() + 44f,
                    avatar.y() + 1f, ClickGuiTheme.TEXT);
            context.medium("ID: " + creator.id(), avatar.x() + 44f, avatar.y() + 19f, 9.2f,
                    ClickGuiTheme.MUTED);
        }
        context.medium(playlist.trackCount() + " songs  /  " + formatCount(playlist.playCount()) + " plays",
                infoX, cover.y() + 82f, 10f, ClickGuiTheme.MUTED);
        context.draw(NimbusFonts.CONTENT_MEDIUM, 9.8f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.8f,
                        playlist.description().isBlank()
                                ? context.translate("nimbus.music.no_description", "No description")
                                : playlist.description(), infoWidth),
                infoX, cover.y() + 103f, ClickGuiTheme.MUTED);
        if (!data.loading() && !data.songs().isEmpty()) {
            UiRect playAll = new UiRect(infoX, cover.y() + 125f, 112f, 31f);
            button(context, playAll, context.translate("nimbus.music.play_all", "Play All"),
                    Action.PLAY_ALL, null);
        }
        y = cover.y() + cover.height() + 18f;
        if (data.loading()) return loading(context, bounds, y);
        return songList(context, bounds, y, data.songs());
    }

    private float renderNowPlaying(UiContext context, UiRect bounds, PlaybackState playback) {
        renderDynamicBackground(context, bounds, data.currentArtwork());
        UiRect content = new UiRect(bounds.x(), bounds.y(), bounds.width(), bounds.height() - PLAYER_HEIGHT);
        UiRect back = new UiRect(content.x() + 18f, content.y() + 16f, 86f, 31f);
        if (back.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(back.x(), back.y(), back.width(), back.height(), 4f,
                    0x20FFFFFF);
        }
        icon(context, ClickGuiIcons.BACK, back.x() + 7f, back.y() + 7f, 17f, ClickGuiTheme.TEXT);
        context.medium(context.translate("nimbus.music.back", "Back"),
                back.x() + 31f, back.y() + 9f, 10.5f, ClickGuiTheme.TEXT);
        hits.add(new Hit(back, Action.BACK_FROM_NOW_PLAYING, null));

        Music music = playback.music();
        if (music == null) {
            context.centered(context.translate("nimbus.music.nothing_playing", "Nothing is playing"),
                    content, 12f, ClickGuiTheme.MUTED);
            return bounds.y() + bounds.height();
        }
        float leftWidth = Math.min(content.width() * .38f, 390f);
        float coverSize = Math.min(leftWidth - 74f, content.height() - 154f);
        UiRect cover = new UiRect(content.x() + 46f,
                content.y() + (content.height() - coverSize) * .5f - 16f,
                coverSize, coverSize);
        drawCurrentArtwork(context, music, cover);
        hits.add(new Hit(cover, Action.PREVIEW_COVER, null));
        float titleY = cover.y() + cover.height() + 15f;
        UiRect titleBounds = new UiRect(cover.x(), titleY, Math.max(40f, cover.width() - 68f),
                context.height(NimbusFonts.CONTENT_MEDIUM, 20f) + 2f);
        drawMarquee(context, NimbusFonts.CONTENT_MEDIUM, 20f, music.name(), titleBounds, ClickGuiTheme.TEXT);
        context.draw(NimbusFonts.CONTENT_MEDIUM, 11f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 11f, music.artistsText(), cover.width() - 92f),
                cover.x(), cover.y() + cover.height() + 43f, 0xD0E0E3E8);
        float iconY = cover.y() + cover.height() + 10f;
        UiRect like = new UiRect(cover.x() + cover.width() - 54f, iconY, 24f, 24f);
        UiRect cache = new UiRect(cover.x() + cover.width() - 24f, iconY, 24f, 24f);
        icon(context, ClickGuiIcons.HEART, like.x() + 4f, like.y() + 4f, 16f,
                manager.likedIds().contains(music.id()) ? 0xFFFF5264 : 0xFFFFFFFF);
        icon(context, ClickGuiIcons.DOWNLOAD, cache.x() + 4f, cache.y() + 4f, 16f,
                manager.isCached(music.id()) ? 0xFF777D86 : 0xFFFFFFFF);
        hits.add(new Hit(like, Action.LIKE_CURRENT, null));
        hits.add(new Hit(cache, Action.CACHE_CURRENT, null));
        UiRect quality = new UiRect(cover.x() + cover.width() - 84f, cover.y() + cover.height() + 66f, 84f, 22f);
        context.frame().draw().roundedRect(quality.x(), quality.y(), quality.width(), quality.height(), 3f,
                quality.contains(context.mouseX(), context.mouseY()) ? 0x20FFFFFF : 0x12FFFFFF);
        context.centered(manager.config().quality().apiValue(), quality, 9f, ClickGuiTheme.TEXT);
        hits.add(new Hit(quality, Action.QUALITY, null));

        UiRect lyrics = new UiRect(content.x() + leftWidth + 28f, content.y() + 20f,
                content.width() - leftWidth - 54f, content.height() - 40f);
        renderLyrics(context, lyrics, playback);
        return bounds.y() + bounds.height();
    }

    private void renderDynamicBackground(UiContext context, UiRect bounds, Artwork artwork) {
        context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xFF101318);
        if (artwork == null || !manager.config().dynamicBackground()
                || !Core.INSTANCE.getConfigManager().clientSettings().postProcessing.getValue()) return;
        int clip = context.clip(bounds, 0f);
        try (Paint firstPaint = new Paint().setAntiAlias(true);
             Paint secondPaint = new Paint().setAntiAlias(true)) {
            double time = System.currentTimeMillis() / 7000d;
            float firstDrift = (float) ((Math.sin(time) + 1d) * .5d);
            float secondDrift = (float) ((Math.cos(time * .73d) + 1d) * .5d);
            firstPaint.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x() + bounds.width() * firstDrift, bounds.y()),
                    new Point(bounds.x() + bounds.width() * (1f - firstDrift), bounds.y() + bounds.height()),
                    new int[]{alpha(artwork.color(0), 158), alpha(artwork.color(1), 54),
                            alpha(artwork.color(2), 132)}));
            secondPaint.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x(), bounds.y() + bounds.height() * secondDrift),
                    new Point(bounds.x() + bounds.width(), bounds.y() + bounds.height() * (1f - secondDrift)),
                    new int[]{alpha(artwork.color(2), 92), 0x00101318, alpha(artwork.color(0), 74)}));
            context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), firstPaint);
            context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), secondPaint);
            context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0x42080A0D);
        } finally {
            context.restore(clip);
        }
    }

    private void renderLyrics(UiContext context, UiRect bounds, PlaybackState playback) {
        MusicDataState.LyricsView lyrics = data.lyrics();
        List<TimedLine> lines = lyrics.lines();
        if (lines.isEmpty()) {
            context.centered(context.translate("nimbus.music.lyrics_unavailable", "Lyrics unavailable"),
                    bounds, 11f, ClickGuiTheme.MUTED);
            return;
        }
        int now = Math.round(playback.progressSeconds() * 1000f);
        int active = activeLine(lines, now);
        float lyricPosition = lyricTarget(lines, active, now);

        context.draw(NimbusFonts.CONTENT_MEDIUM, 15f,
                context.translate("nimbus.music.lyrics", "Lyrics"), bounds.x(), bounds.y() + 4f,
                ClickGuiTheme.TEXT);
        float footerInset = lyrics.hasTranslations() ? 34f : 0f;
        if (lyrics.hasTranslations()) {
            String label = context.translate("nimbus.music.translation", "Translation");
            float labelWidth = context.width(NimbusFonts.BODY_MEDIUM, 9.5f, label);
            float width = labelWidth + 43f;
            UiRect toggle = new UiRect(bounds.x() + bounds.width() - width, bounds.y() + bounds.height() - 26f,
                    width, 24f);
            if (toggle.contains(context.mouseX(), context.mouseY())) {
                context.frame().draw().roundedRect(toggle.x(), toggle.y(), toggle.width(), toggle.height(), 4f,
                        0x14FFFFFF);
            }
            drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 9.5f, label, toggle.x() + 4f,
                    toggle.y(), toggle.height(), translationsEnabled ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            UiRect track = new UiRect(toggle.x() + width - 30f, toggle.y() + 6f, 26f, 12f);
            context.frame().draw().roundedRect(track.x(), track.y(), track.width(), track.height(), 6f,
                    alpha(0xFFFFFFFF, Math.round(40f + 72f * translationProgress)));
            float knobX = track.x() + 3f + 12f * translationProgress;
            context.frame().draw().roundedRect(knobX, track.y() + 2f, 8f, 8f, 4f,
                    alpha(0xFFFFFFFF, Math.round(168f + 87f * translationProgress)));
            hits.add(new Hit(toggle, Action.TOGGLE_TRANSLATION, null));
        }

        UiRect viewport = new UiRect(bounds.x(), bounds.y() + 40f, bounds.width(),
                bounds.height() - 40f - footerInset);
        int clip = context.clip(viewport, 0f);
        float centerY = viewport.y() + viewport.height() * .48f;
        float rowHeight = translationsEnabled && lyrics.hasTranslations() ? LYRIC_ROW_HEIGHT : 52f;
        int first = Math.max(0, (int) Math.floor(lyricPosition) - 6);
        int last = Math.min(lines.size() - 1, (int) Math.ceil(lyricPosition) + 6);
        WordLine wordLine = activeWordLine(lyrics.words(), now);
        try {
            for (int index = first; index <= last; index++) {
                float distance = index - lyricPosition;
                float rowY = centerY + distance * rowHeight - rowHeight * .5f;
                UiRect row = new UiRect(viewport.x(), rowY, viewport.width(), rowHeight);
                if (!row.intersects(viewport)) continue;
                TimedLine line = lines.get(index);
                boolean selected = index == active;
                boolean hover = viewport.contains(context.mouseX(), context.mouseY())
                        && row.contains(context.mouseX(), context.mouseY());
                if (hover) context.frame().draw().roundedRect(row.x(), row.y() + 3f,
                        row.width(), row.height() - 6f, 5f, 0x14FFFFFF);
                int lineAlpha = selected ? 255 : Math.max(58, Math.round(170f - Math.abs(distance) * 23f));
                float textX = row.x() + 10f;
                float textWidth = row.width() - 20f;
                String original = selected && wordLine != null && !wordLine.text().isBlank()
                        ? wordLine.text() : line.text();
                float size = fitTextSize(context, NimbusFonts.CONTENT_MEDIUM,
                        selected ? 18f : 13.5f, selected ? 11.5f : 10.5f, original, textWidth);
                float lineY = row.y() + (row.height() - context.height(NimbusFonts.CONTENT_MEDIUM, size)) * .5f
                        - (translationsEnabled && !lyrics.translation(index).isBlank() ? 9f : 0f);
                if (selected && wordLine != null) {
                    drawWordProgress(context, wordLine, line.text(), textX, lineY, textWidth, size, now);
                } else {
                    context.draw(NimbusFonts.CONTENT_MEDIUM, size, line.text(), textX, lineY,
                            alpha(ClickGuiTheme.TEXT, lineAlpha));
                }
                String translated = translationsEnabled ? lyrics.translation(index) : "";
                if (!translated.isBlank()) {
                    float translatedSize = fitTextSize(context, NimbusFonts.CONTENT_MEDIUM,
                            selected ? 11f : 10f, 8.5f, translated, textWidth);
                    context.draw(NimbusFonts.CONTENT_MEDIUM, translatedSize, translated,
                            textX, lineY + (selected ? 28f : 23f),
                            alpha(0xFFE1E4E9, selected ? 214 : Math.max(48, lineAlpha - 58)));
                }
                hits.add(new Hit(row, Action.SEEK_LYRIC, line.timeMs(), viewport));
            }
        } finally {
            context.restore(clip);
        }
    }

    private void drawWordProgress(UiContext context, WordLine wordLine, String fallback, float x, float y,
                                  float width, float size, int now) {
        String text = wordLine.text().isBlank() ? fallback : wordLine.text();
        context.draw(NimbusFonts.CONTENT_MEDIUM, size, text, x, y, 0x78FFFFFF);
        float highlighted = 0f;
        int cursor = 0;
        for (TimedWord word : wordLine.words()) {
            int start = text.indexOf(word.text(), cursor);
            if (start < 0) start = Math.min(cursor, text.length());
            int end = Math.min(text.length(), start + word.text().length());
            float startWidth = context.width(NimbusFonts.CONTENT_MEDIUM, size, text.substring(0, start));
            float endWidth = context.width(NimbusFonts.CONTENT_MEDIUM, size, text.substring(0, end));
            if (now >= word.endMs()) highlighted = endWidth;
            else if (now > word.startMs()) {
                float progress = (now - word.startMs()) / (float) Math.max(1, word.endMs() - word.startMs());
                highlighted = startWidth + (endWidth - startWidth) * Math.clamp(progress, 0f, 1f);
                break;
            } else break;
            cursor = end;
        }
        int clip = context.clip(new UiRect(x, y - 2f, Math.min(width, highlighted),
                context.height(NimbusFonts.CONTENT_MEDIUM, size) + 4f), 0f);
        try {
            context.draw(NimbusFonts.CONTENT_MEDIUM, size, text, x, y, ClickGuiTheme.TEXT);
        } finally {
            context.restore(clip);
        }
    }

    private float renderPreferences(UiContext context, UiRect bounds, float y) {
        MusicConfig config = manager.config();
        String on = context.translate("nimbus.ui.on", "On");
        String off = context.translate("nimbus.ui.off", "Off");
        y = settingRow(context, bounds, y, context.translate("nimbus.music.prefetch", "Prefetch"),
                config.prefetch() ? on : off, Action.PREFETCH);
        y = settingRow(context, bounds, y,
                context.translate("nimbus.music.dynamic_background", "Dynamic Background"),
                config.dynamicBackground() ? on : off, Action.DYNAMIC_BACKGROUND);
        y = settingRow(context, bounds, y, context.translate("nimbus.music.cache_limit", "Cache Limit"),
                formatBytes(config.cacheLimitBytes()), Action.CACHE_LIMIT);
        y += 8f;
        y = section(context, context.translate("nimbus.music.hotkeys", "Hotkeys"), bounds, y);
        y = settingRow(context, bounds, y, context.translate("nimbus.music.previous", "Previous"),
                hotkey(config.hotkey(MusicConfig.Hotkey.PREVIOUS)), Action.HOTKEY_PREVIOUS);
        y = settingRow(context, bounds, y, context.translate("nimbus.music.next", "Next"),
                hotkey(config.hotkey(MusicConfig.Hotkey.NEXT)), Action.HOTKEY_NEXT);
        y = settingRow(context, bounds, y, context.translate("nimbus.music.play_pause", "Play / Pause"),
                hotkey(config.hotkey(MusicConfig.Hotkey.PAUSE)), Action.HOTKEY_PAUSE);
        y += 8f;
        float width = (bounds.width() - 44f) * .5f;
        UiRect trim = new UiRect(bounds.x() + 18f, y, width, 30f);
        UiRect clear = new UiRect(trim.x() + width + 8f, y, width, 30f);
        button(context, trim, context.translate("nimbus.music.trim_cache", "Trim Cache"),
                Action.TRIM_CACHE, null);
        button(context, clear, context.translate("nimbus.music.clear_cache", "Clear Cache"),
                Action.CLEAR_CACHE, null);
        return y + 46f;
    }

    private float settingRow(UiContext context, UiRect bounds, float y, String name, String value, Action action) {
        UiRect row = new UiRect(bounds.x() + 18f, y, bounds.width() - 36f, 34f);
        hoverRow(context, row, "setting:" + action);
        context.textIn(name, new UiRect(row.x() + 9f, row.y(), row.width() * .55f, row.height()),
                10.5f, ClickGuiTheme.TEXT);
        context.textIn(bindingHotkey != null && action.hotkey() == bindingHotkey
                        ? context.translate("nimbus.music.listening", "Listening...") : value,
                new UiRect(row.x() + row.width() * .62f, row.y(), row.width() * .34f, row.height()),
                10f, ClickGuiTheme.MUTED);
        hits.add(new Hit(row, action, null));
        return y + 38f;
    }

    private float renderLogin(UiContext context, UiRect bounds, float y) {
        User profile = manager.profile();
        y = heading(context, profile == null ? context.translate("nimbus.music.sign_in", "Sign In") : profile.name(),
                profile == null ? "Netease Cloud Music" : profile.signature(), bounds, y);
        if (profile != null) {
            UiRect avatar = new UiRect(bounds.x() + 18f, y + 4f, 54f, 54f);
            drawArtwork(context, data.artwork(profile, 160), "account-profile:" + profile.id(), avatar, 27f, 255);
            String id = "ID: " + profile.id();
            String vip = context.translate("nimbus.music.vip_level", "VIP Level: %s", profile.vipType());
            float nameHeight = context.height(NimbusFonts.CONTENT_MEDIUM, 11f, profile.name());
            float idHeight = context.height(UiContext.FONT_MEDIUM, 9.5f, id);
            float vipHeight = context.height(UiContext.FONT_MEDIUM, 9.2f, vip);
            float gap = 2.5f;
            float textY = avatar.y() + (avatar.height() - nameHeight - idHeight - vipHeight - gap * 2f) * .5f;
            float textX = avatar.x() + 68f;
            context.draw(NimbusFonts.CONTENT_MEDIUM, 11f, profile.name(), textX, textY,
                    ClickGuiTheme.TEXT);
            textY += nameHeight + gap;
            context.medium(id, textX, textY, 9.5f, ClickGuiTheme.MUTED);
            textY += idHeight + gap;
            context.medium(vip, textX, textY, 9.2f,
                    profile.vipType() > 0 ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
            UiRect logout = new UiRect(bounds.x() + bounds.width() - 134f, y + 15f, 112f, 31f);
            button(context, logout, context.translate("nimbus.music.sign_out", "Sign Out"),
                    Action.LOGOUT, null);
            y += 78f;
            y = section(context, context.translate("nimbus.music.settings", "Music Settings"), bounds, y);
            return renderPreferences(context, bounds, y);
        }
        UiRect qrTab = new UiRect(bounds.x() + 18f, y, 92f, 28f);
        UiRect smsTab = new UiRect(qrTab.x() + 98f, y, 92f, 28f);
        tab(context, qrTab, context.translate("nimbus.music.qr_code", "QR Code"),
                loginTab == LoginTab.QR, Action.LOGIN_QR);
        tab(context, smsTab, context.translate("nimbus.music.sms", "SMS"),
                loginTab == LoginTab.SMS, Action.LOGIN_SMS);
        y += 44f;
        y = loginTab == LoginTab.QR ? renderQrLogin(context, bounds, y) : renderSmsLogin(context, bounds, y);
        y += 10f;
        y = section(context, context.translate("nimbus.music.settings", "Music Settings"), bounds, y);
        return renderPreferences(context, bounds, y);
    }

    private float renderQrLogin(UiContext context, UiRect bounds, float y) {
        UiRect image = new UiRect(bounds.x() + 18f, y, 190f, 190f);
        if (qrArtwork == null) {
            context.frame().draw().roundedRect(image.x(), image.y(), image.width(), image.height(), 4f,
                    IMAGE_PLACEHOLDER);
            context.centered(qrRequesting ? context.translate("nimbus.ui.loading", "Loading...")
                    : context.translate("nimbus.music.qr_code", "QR Code"), image, 11f, ClickGuiTheme.MUTED);
        } else {
            drawArtwork(context, qrArtwork, "login-qr", image, 4f, 255);
        }
        UiRect request = new UiRect(image.x() + image.width() + 24f, y + 24f, 150f, 31f);
        button(context, request, qrArtwork == null
                ? context.translate("nimbus.music.generate_qr", "Generate QR")
                : context.translate("nimbus.music.refresh_qr", "Refresh QR"), Action.REQUEST_QR, null);
        context.text(context.translate("nimbus.music.scan_qr", "Scan with Netease Cloud Music"),
                request.x(), request.y() + 48f, 10f, ClickGuiTheme.MUTED);
        return y + 212f;
    }

    private float renderSmsLogin(UiContext context, UiRect bounds, float y) {
        countryBounds = new UiRect(bounds.x() + 18f, y, 64f, 29f);
        phoneBounds = new UiRect(countryBounds.x() + 72f, y, Math.min(240f, bounds.width() - 126f), 29f);
        country.render(context, countryBounds, "86", 10.5f, false);
        phone.render(context, phoneBounds, context.translate("nimbus.music.phone", "Phone number"), 10.5f, false);
        UiRect send = new UiRect(phoneBounds.x() + phoneBounds.width() + 8f, y,
                Math.max(88f, bounds.x() + bounds.width() - phoneBounds.x() - phoneBounds.width() - 26f), 29f);
        button(context, send, context.translate("nimbus.music.send_code", "Send Code"), Action.SEND_SMS, null);
        y += 41f;
        captchaBounds = new UiRect(bounds.x() + 18f, y, Math.min(240f, bounds.width() - 130f), 29f);
        captcha.render(context, captchaBounds,
                context.translate("nimbus.music.verification_code", "Verification code"), 10.5f, false);
        UiRect login = new UiRect(captchaBounds.x() + captchaBounds.width() + 8f, y, 112f, 29f);
        button(context, login, context.translate("nimbus.music.sign_in", "Sign In"), Action.COMPLETE_SMS, null);
        return y + 52f;
    }

    private void renderPlayer(UiContext context, UiRect bounds, PlaybackState playback) {
        float y = bounds.y() + bounds.height() - PLAYER_HEIGHT;
        Music music = playback.music();
        UiRect cover = new UiRect(bounds.x() + 13f, y + 10f, 50f, 50f);
        if (music != null) drawCurrentArtwork(context, music, cover);
        else context.frame().draw().roundedRect(cover.x(), cover.y(), cover.width(), cover.height(), 4f,
                IMAGE_PLACEHOLDER);
        if (music != null) {
            hits.add(new Hit(cover, Action.NOW_PLAYING, null));
            drawMarquee(context, NimbusFonts.CONTENT_MEDIUM, 11f, music.name(),
                    new UiRect(cover.x() + 61f, y + 16f, 180f,
                            context.height(NimbusFonts.CONTENT_MEDIUM, 11f) + 2f), ClickGuiTheme.TEXT);
            context.draw(NimbusFonts.CONTENT, 9f, ellipsizeFont(context,
                    NimbusFonts.CONTENT, 9f, music.artistsText(), 180f), cover.x() + 61f, y + 37f,
                    ClickGuiTheme.MUTED);
        } else {
            context.text(context.translate("nimbus.music.nothing_playing_short", "Nothing playing"),
                    cover.x() + 61f, y + 25f, 10.5f, ClickGuiTheme.MUTED);
        }

        float center = bounds.x() + bounds.width() * .5f;
        control(context, new UiRect(center - 63f, y + 12f, 28f, 28f), ClickGuiIcons.PREVIOUS, Action.PREVIOUS);
        control(context, new UiRect(center - 17f, y + 8f, 36f, 36f),
                playback.phase() == cc.nimbus.features.music.playback.PlaybackPhase.PLAYING
                        ? ClickGuiIcons.PAUSE : ClickGuiIcons.PLAY, Action.TOGGLE_PLAYBACK);
        control(context, new UiRect(center + 37f, y + 12f, 28f, 28f), ClickGuiIcons.NEXT, Action.NEXT);

        seekBounds = new UiRect(center - 155f, y + 53f, 310f, 3f);
        float progress = playback.durationSeconds() <= 0f ? 0f
                : Math.clamp(playback.progressSeconds() / playback.durationSeconds(), 0f, 1f);
        context.frame().draw().rect(seekBounds.x(), seekBounds.y(), seekBounds.width(), seekBounds.height(), 0x25FFFFFF);
        context.frame().draw().rect(seekBounds.x(), seekBounds.y(), seekBounds.width() * progress,
                seekBounds.height(), 0xB8FFFFFF);
        hits.add(new Hit(new UiRect(seekBounds.x(), seekBounds.y() - 5f, seekBounds.width(), 13f), Action.SEEK, null));
        context.text(time(playback.progressSeconds()), seekBounds.x() - 39f, y + 47f, 8.5f, ClickGuiTheme.MUTED);
        context.text(time(playback.durationSeconds()), seekBounds.x() + seekBounds.width() + 8f,
                y + 47f, 8.5f, ClickGuiTheme.MUTED);

        float right = bounds.x() + bounds.width() - 14f;
        UiRect queue = new UiRect(right - 27f, y + 22f, 25f, 25f);
        UiRect mode = new UiRect(right - 62f, y + 22f, 25f, 25f);
        icon(context, ClickGuiIcons.QUEUE, queue.x() + 5f, queue.y() + 5f, 15f,
                queueOpen ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        String modeIcon = switch (manager.config().playMode()) {
            case SINGLE -> ClickGuiIcons.REPEAT_ONE;
            case LOOP_LIST -> ClickGuiIcons.REPEAT;
            case SHUFFLE -> ClickGuiIcons.SHUFFLE;
            case INTELLIGENCE -> ClickGuiIcons.SPARKLES;
        };
        icon(context, modeIcon, mode.x() + 5f, mode.y() + 5f, 15f, ClickGuiTheme.MUTED);
        hits.add(new Hit(queue, Action.TOGGLE_QUEUE, null));
        hits.add(new Hit(mode, Action.MODE, null));
        icon(context, ClickGuiIcons.SPEED, right - 178f, y + 13f, 14f, ClickGuiTheme.MUTED);
        rateBounds = new UiRect(right - 153f, y + 21f, 78f, 3f);
        float rateProgress = (playback.rate() - .5f) / 1.5f;
        drawSlider(context, rateBounds, rateProgress);
        if (draggingRate) drawSliderValue(context, rateBounds, rateProgress, formatRate(playback.rate()));
        hits.add(new Hit(new UiRect(rateBounds.x(), rateBounds.y() - 6f, rateBounds.width(), 15f),
                Action.RATE, null));

        volumeBounds = new UiRect(right - 153f, y + 51f, 78f, 3f);
        icon(context, ClickGuiIcons.VOLUME, volumeBounds.x() - 25f, y + 43f, 15f, ClickGuiTheme.MUTED);
        drawSlider(context, volumeBounds, playback.volume());
        if (draggingVolume) drawSliderValue(context, volumeBounds, playback.volume(),
                String.format(java.util.Locale.ROOT, "%.1f", playback.volume()));
        hits.add(new Hit(new UiRect(volumeBounds.x(), volumeBounds.y() - 5f, volumeBounds.width(), 13f),
                Action.VOLUME, null));
    }

    private void renderQueue(UiContext context, UiRect bounds, PlaybackState playback) {
        float width = Math.min(300f, bounds.width() * .4f);
        float x = bounds.x() + bounds.width() - width + (1f - queueProgress) * width;
        float topInset = session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING ? 0f : HEADER_HEIGHT;
        queueBounds = new UiRect(x, bounds.y() + topInset,
                width, bounds.height() - topInset - PLAYER_HEIGHT);
        Rect panelBounds = Rect.makeXYWH(queueBounds.x(), queueBounds.y(),
                queueBounds.width(), queueBounds.height());
        int layer = context.frame().canvas().saveLayerAlpha(
                panelBounds, Math.round(queueProgress * 255f));
        try {
            context.frame().draw().rect(queueBounds.x(), queueBounds.y(), queueBounds.width(),
                    queueBounds.height(), 0xA00E1013);
            context.frame().draw().rect(queueBounds.x(), queueBounds.y(), 1f, queueBounds.height(),
                    ClickGuiTheme.STROKE);
            context.medium(context.translate("nimbus.music.queue", "Queue"),
                    queueBounds.x() + 16f, queueBounds.y() + 15f, 14f,
                    ClickGuiTheme.TEXT);
            PlaybackQueueState queue = manager.queueState();
            UiRect viewport = new UiRect(queueBounds.x(), queueBounds.y() + 47f, queueBounds.width(),
                    queueBounds.height() - 47f);
            float y = viewport.y() + queueScroll;
            int clip = context.clip(viewport, 0f);
            try {
                for (int index = 0; index < queue.songs().size(); index++) {
                    Music music = queue.songs().get(index);
                    UiRect row = new UiRect(queueBounds.x() + 8f, y, queueBounds.width() - 16f, 42f);
                    if (!row.intersects(viewport)) {
                        y += 45f;
                        continue;
                    }
                    if (index == queue.index() || row.contains(context.mouseX(), context.mouseY())) {
                        context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                                index == queue.index() ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
                    }
                    UiRect cover = new UiRect(row.x() + 6f, row.y() + 5f, 32f, 32f);
                    drawArtwork(context, data.thumbnail(music, 96), "queue-cover:" + music.id(),
                            cover, 4f, 255);
                    drawCenteredY(context, NimbusFonts.CONTENT_MEDIUM, 9.8f,
                            ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.8f,
                                    music.name(), row.width() - 56f),
                            row.x() + 48f, row.y(), row.height(),
                            index == queue.index() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
                    if (queueProgress > .85f) hits.add(new Hit(row, Action.PLAY_AT, index, viewport));
                    y += 45f;
                }
                maximumQueueScroll = Math.max(0f, queue.songs().size() * 45f - viewport.height());
                targetQueueScroll = Math.clamp(targetQueueScroll, -maximumQueueScroll, 0f);
                queueScroll = Math.clamp(queueScroll, -maximumQueueScroll, 0f);
            } finally {
                context.restore(clip);
            }
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private void renderCoverPreview(UiContext context, UiRect bounds, PlaybackState playback) {
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height()),
                Math.round(previewProgress * 255f));
        try {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 6f,
                    0xF20A0B0D);
            Music music = playback.music();
            if (music == null) return;
            float maximum = Math.min(bounds.width() * .52f, bounds.height() - 118f);
            float size = maximum * (.94f + .06f * previewProgress);
            UiRect image = new UiRect(bounds.x() + (bounds.width() - size) * .5f,
                    bounds.y() + 30f + (1f - previewProgress) * 12f, size, size);
            drawCurrentArtwork(context, music, image);
            context.center(NimbusFonts.CONTENT_MEDIUM, 17f, music.name(),
                    bounds.x() + bounds.width() * .5f, image.y() + image.height() + 16f, ClickGuiTheme.TEXT);
            String detail = music.artistsText() + (music.album() == null ? "" : "  /  " + music.album().name());
            context.center(NimbusFonts.CONTENT, 10.5f, detail,
                    bounds.x() + bounds.width() * .5f, image.y() + image.height() + 43f, ClickGuiTheme.MUTED);
            if (previewProgress > .85f) hits.add(new Hit(bounds, Action.CLOSE_PREVIEW, null));
        } finally {
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private float heading(UiContext context, String title, String subtitle, UiRect bounds, float y) {
        context.draw(NimbusFonts.DISPLAY, 20f, title, bounds.x() + 18f, y,
                ClickGuiTheme.TEXT);
        context.text(subtitle, bounds.x() + 18f, y + 29f, 10f, ClickGuiTheme.MUTED);
        return y + 58f;
    }

    private float section(UiContext context, String title, UiRect bounds, float y) {
        context.draw(NimbusFonts.BODY_MEDIUM, 11f, title, bounds.x() + 18f, y,
                ClickGuiTheme.TEXT);
        return y + 25f;
    }

    private float playlistGrid(UiContext context, UiRect bounds, float y, List<Playlist> playlists, int limit) {
        int count = Math.min(limit, playlists.size());
        int columns = Math.clamp((int) (bounds.width() / 172f), 3, 5);
        float gap = 14f;
        float width = (bounds.width() - 36f - gap * (columns - 1)) / columns;
        float imageSize = width;
        float rowHeight = imageSize + 50f;
        for (int index = 0; index < count; index++) {
            int column = index % columns;
            int row = index / columns;
            float x = bounds.x() + 18f + column * (width + gap);
            float top = y + row * rowHeight;
            Playlist playlist = playlists.get(index);
            UiRect hit = new UiRect(x, top, width, rowHeight - 6f);
            if (!hit.intersects(bodyBounds)) continue;
            Artwork artwork = data.artwork(playlist, 512);
            UiRect cover = new UiRect(x, top, imageSize, imageSize);
            drawArtwork(context, artwork, "playlist:" + playlist.id(), cover, 4f, 255);
            float hover = hover(context, "playlist:" + playlist.id(), hit.contains(context.mouseX(), context.mouseY()));
            if (hover > .01f) context.frame().draw().outline(cover.x(), cover.y(), cover.width(), cover.height(),
                    4f, 1f, alpha(0xFFFFFFFF, Math.round(36f * hover)));
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10.8f,
                    ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.8f, playlist.name(), width),
                    x, top + imageSize + 8f, ClickGuiTheme.TEXT);
            context.medium(formatCount(playlist.playCount()) + " plays", x, top + imageSize + 28f,
                    9f, ClickGuiTheme.MUTED);
            hits.add(new Hit(hit, Action.OPEN_PLAYLIST, playlist, bodyBounds));
        }
        int rows = (count + columns - 1) / columns;
        return y + rows * rowHeight;
    }

    private float songList(UiContext context, UiRect bounds, float y, List<Music> songs) {
        for (int index = 0; index < songs.size(); index++) {
            Music music = songs.get(index);
            UiRect row = new UiRect(bounds.x() + 14f, y, bounds.width() - 28f, 48f);
            if (row.intersects(bodyBounds)) {
                hoverRow(context, row, "song:" + music.id());
                drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 9.2f, Integer.toString(index + 1),
                        row.x() + 4f, row.y(), row.height(), ClickGuiTheme.MUTED);
                UiRect cover = new UiRect(row.x() + 32f, row.y() + 6f, 36f, 36f);
                drawArtwork(context, data.thumbnail(music, 128), "song-cover:" + music.id(), cover, 4f, 255);
                float titleX = row.x() + 80f;
                float artistX = row.x() + row.width() * .64f;
                float timeX = row.x() + row.width() - 48f;
                drawCenteredY(context, NimbusFonts.CONTENT_MEDIUM, 10.8f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.8f,
                                music.name(), artistX - titleX - 14f),
                        titleX, row.y(), row.height(), ClickGuiTheme.TEXT);
                context.draw(NimbusFonts.CONTENT_MEDIUM, 9.5f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.5f,
                                music.artistsText(), timeX - artistX - 12f),
                        artistX, row.y() + (row.height() - context.height(
                                NimbusFonts.CONTENT_MEDIUM, 9.5f, music.artistsText())) * .5f,
                        ClickGuiTheme.MUTED);
                drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 9.2f,
                        time(music.durationMs() / 1000f), timeX, row.y(), row.height(), ClickGuiTheme.MUTED);
                hits.add(new Hit(row, Action.PLAY_SONG, new SongSelection(songs, index), bodyBounds));
            }
            y += 52f;
        }
        return y;
    }

    private void hoverRow(UiContext context, UiRect row, String key) {
        float hover = hover(context, key, row.contains(context.mouseX(), context.mouseY()));
        if (hover > .01f) context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                alpha(0xFFFFFFFF, Math.round(20f * hover)));
    }

    private float hover(UiContext context, String key, boolean target) {
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 18f);
        float value = animate(hovers.getOrDefault(key, 0f), target ? 1f : 0f, speed);
        hovers.put(key, value);
        return value;
    }

    private void tab(UiContext context, UiRect bounds, String label, boolean active, Action action) {
        if (active || bounds.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                    active ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        }
        context.centered(label, bounds, 10f, active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(bounds, action, null));
    }

    private void button(UiContext context, UiRect bounds, String label, Action action, Object value) {
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                bounds.contains(context.mouseX(), context.mouseY()) ? ClickGuiTheme.HOVER : ClickGuiTheme.CONTROL);
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        context.centered(label, bounds, 10f, ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, action, value));
    }

    private void control(UiContext context, UiRect bounds, String svg, Action action) {
        control(context, bounds, svg, action, null);
    }

    private void control(UiContext context, UiRect bounds, String svg, Action action, UiRect clip) {
        if (bounds.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    bounds.width() * .5f, ClickGuiTheme.HOVER);
        }
        float size = Math.min(18f, bounds.width() - 10f);
        icon(context, svg, bounds.x() + (bounds.width() - size) * .5f,
                bounds.y() + (bounds.height() - size) * .5f, size, ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, action, null, clip));
    }

    private void drawSlider(UiContext context, UiRect bounds, float progress) {
        float value = Math.clamp(progress, 0f, 1f);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1.5f,
                0x28FFFFFF);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width() * value, bounds.height(), 1.5f,
                0xC8FFFFFF);
        float knobX = bounds.x() + bounds.width() * value - 2.5f;
        context.frame().draw().roundedRect(knobX, bounds.y() - 1f, 5f, 5f, 2.5f, 0xFFFFFFFF);
    }

    private void drawSliderValue(UiContext context, UiRect bounds, float progress, String value) {
        float width = context.width(NimbusFonts.BODY_MEDIUM, 8.5f, value) + 10f;
        float x = Math.clamp(bounds.x() + bounds.width() * progress - width * .5f,
                bounds.x() - 4f, bounds.x() + bounds.width() - width + 4f);
        UiRect tooltip = new UiRect(x, bounds.y() - 20f, width, 15f);
        context.frame().draw().roundedRect(tooltip.x(), tooltip.y(), tooltip.width(), tooltip.height(), 3f,
                0xD81A1C20);
        context.centered(value, tooltip, 8.5f, ClickGuiTheme.TEXT);
    }

    private float loading(UiContext context, UiRect bounds, float y) {
        context.text(context.translate("nimbus.ui.loading", "Loading..."),
                bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
        return y + 42f;
    }

    private float error(UiContext context, UiRect bounds, float y) {
        context.text(data.error().isBlank()
                        ? context.translate("nimbus.music.content_unavailable", "Content unavailable")
                        : data.error(),
                bounds.x() + 18f, y + 8f, 10.5f, ClickGuiTheme.DANGER);
        return y + 42f;
    }

    private void drawCurrentArtwork(UiContext context, Music music, UiRect bounds) {
        Artwork previous = data.previousArtwork();
        Artwork current = data.currentArtwork();
        if (previous != null && coverBlend < .999f) {
            drawArtwork(context, previous, "current-previous", bounds, 4f, Math.round(255f * (1f - coverBlend)));
        }
        if (current != null) {
            drawArtwork(context, current, "current:" + music.id(), bounds, 4f, Math.round(255f * coverBlend));
        } else if (previous == null) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4f,
                    IMAGE_PLACEHOLDER);
        }
    }

    private void drawArtwork(UiContext context, Artwork artwork, String key, UiRect bounds, float radius, int alpha) {
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius,
                IMAGE_PLACEHOLDER);
        Image image = image(context, artwork, key);
        if (image == null || alpha <= 0) return;
        String fadeKey = key + ':' + System.identityHashCode(artwork);
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 12f);
        float fade = animate(imageFades.getOrDefault(fadeKey, 0f), 1f, speed);
        imageFades.put(fadeKey, fade);
        int clip = context.clip(bounds, radius);
        try (Paint paint = new Paint().setAntiAlias(true)
                .setAlpha(Math.clamp(Math.round(alpha * fade), 0, 255))) {
            context.frame().draw().image(image, bounds.x(), bounds.y(), bounds.width(), bounds.height(), paint);
        } finally {
            context.restore(clip);
        }
    }

    private Image image(UiContext context, Artwork artwork, String key) {
        if (artwork == null) return null;
        byte[] bytes = encoded.get(artwork);
        if (bytes == null) {
            if (encoded.size() >= 128) encoded.remove(encoded.keySet().iterator().next());
            bytes = artwork.encoded();
            encoded.put(artwork, bytes);
        }
        if (bytes.length == 0) return null;
        return context.frame().images().encoded("music-ui:" + key, bytes);
    }

    @Override
    public boolean mouseClicked(float x, float y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (coverPreview) {
            coverPreview = false;
            return true;
        }
        if (searchBounds != null && searchBounds.contains(x, y)) {
            focus.focus(search);
            return true;
        }
        if (countryBounds != null && countryBounds.contains(x, y)) { focus.focus(country); return true; }
        if (phoneBounds != null && phoneBounds.contains(x, y)) { focus.focus(phone); return true; }
        if (captchaBounds != null && captchaBounds.contains(x, y)) { focus.focus(captcha); return true; }
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (!hit.bounds().contains(x, y) || hit.clip() != null && !hit.clip().contains(x, y)) continue;
            act(hit, x);
            return true;
        }
        focus.clear();
        return true;
    }

    private void act(Hit hit, float mouseX) {
        switch (hit.action()) {
            case NAVIGATE -> navigate((ClickGuiSession.MusicRoute) hit.value());
            case OPEN_PLAYLIST -> openPlaylist((Playlist) hit.value());
            case PLAY_SONG -> {
                SongSelection selection = (SongSelection) hit.value();
                manager.play(selection.songs(), selection.index());
            }
            case PLAY_ALL -> {
                if (!data.songs().isEmpty()) manager.play(data.songs(), 0);
            }
            case PLAY_CACHED -> {
                CachedSelection selection = (CachedSelection) hit.value();
                manager.playCached(selection.ids(), selection.index());
            }
            case DELETE_CACHED -> run(manager.removeCached((long) hit.value()), removed -> {
                data.refreshCached();
                show(removed ? tr("nimbus.music.message.cache_removed", "Cached song removed")
                        : tr("nimbus.music.message.cache_remove_failed", "Unable to remove cached song"));
            });
            case PREVIOUS -> manager.previous();
            case NEXT -> manager.next();
            case TOGGLE_PLAYBACK -> manager.togglePause();
            case MODE -> cycleMode();
            case TOGGLE_QUEUE -> {
                queueOpen = !queueOpen;
                if (!queueOpen) {
                    queueScroll = 0f;
                    targetQueueScroll = 0f;
                }
            }
            case PLAY_AT -> manager.playAt((int) hit.value());
            case SEEK -> { draggingSeek = true; updateSeek(mouseX); }
            case VOLUME -> { draggingVolume = true; updateVolume(mouseX); }
            case NOW_PLAYING -> navigate(ClickGuiSession.MusicRoute.NOW_PLAYING);
            case BACK_FROM_NOW_PLAYING -> navigate(previousRoute);
            case PREVIEW_COVER -> coverPreview = true;
            case CLOSE_PREVIEW -> coverPreview = false;
            case LIKE_CURRENT -> likeCurrent();
            case CACHE_CURRENT -> cacheCurrent();
            case SEEK_LYRIC -> manager.seek((int) hit.value() / 1000d);
            case TOGGLE_TRANSLATION -> translationsEnabled = !translationsEnabled;
            case QUALITY -> cycleQuality();
            case PREFETCH -> manager.prefetch(!manager.config().prefetch());
            case DYNAMIC_BACKGROUND -> manager.dynamicBackground(!manager.config().dynamicBackground());
            case RATE -> { draggingRate = true; updateRate(mouseX); }
            case CACHE_LIMIT -> cycleCacheLimit();
            case HOTKEY_PREVIOUS -> bindingHotkey = MusicConfig.Hotkey.PREVIOUS;
            case HOTKEY_NEXT -> bindingHotkey = MusicConfig.Hotkey.NEXT;
            case HOTKEY_PAUSE -> bindingHotkey = MusicConfig.Hotkey.PAUSE;
            case TRIM_CACHE -> run(manager.trimCache(), bytes -> show(
                    tr("nimbus.music.message.freed", "Freed %s", formatBytes(bytes))));
            case CLEAR_CACHE -> run(manager.clearCache(), bytes -> {
                data.refreshCached();
                show(tr("nimbus.music.message.freed", "Freed %s", formatBytes(bytes)));
            });
            case LOGIN_QR -> loginTab = LoginTab.QR;
            case LOGIN_SMS -> {
                loginTab = LoginTab.SMS;
                manager.cancelLogin();
                qrArtwork = null;
                qrRequesting = false;
                qrChecking = false;
            }
            case REQUEST_QR -> requestQr();
            case SEND_SMS -> sendSms();
            case COMPLETE_SMS -> completeSms();
            case LOGOUT -> {
                manager.logout();
                navigate(ClickGuiSession.MusicRoute.LOGIN);
            }
        }
    }

    @Override
    public void mouseMoved(float x, float y, boolean leftDown) {
        if (!leftDown) {
            draggingSeek = false;
            draggingVolume = false;
            draggingRate = false;
            return;
        }
        if (draggingSeek) updateSeek(x);
        if (draggingVolume) updateVolume(x);
        if (draggingRate) updateRate(x);
    }

    @Override
    public void mouseReleased() {
        draggingSeek = false;
        draggingVolume = false;
        draggingRate = false;
    }

    @Override
    public boolean mouseScrolled(float x, float y, double delta) {
        if (queueOpen && queueBounds != null && queueBounds.contains(x, y)) {
            targetQueueScroll = Math.clamp(targetQueueScroll + (float) delta * 34f,
                    -maximumQueueScroll, 0f);
            return true;
        }
        if (sidebarBounds != null && sidebarBounds.contains(x, y)) {
            targetSidebarScroll = Math.clamp(targetSidebarScroll + (float) delta * 34f,
                    -maximumSidebarScroll, 0f);
            return true;
        }
        if (bodyBounds != null && bodyBounds.contains(x, y)) {
            targetContentScroll = Math.clamp(targetContentScroll + (float) delta * 40f,
                    -maximumContentScroll, 0f);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingHotkey != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) bindingHotkey = null;
            else {
                int key = event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE ? 0 : event.key();
                manager.hotkey(bindingHotkey, key);
                bindingHotkey = null;
            }
            return true;
        }
        if (search.focused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                startSearch();
                return true;
            }
            return search.keyPressed(event);
        }
        if (country.focused()) return country.keyPressed(event);
        if (phone.focused()) return phone.keyPressed(event);
        if (captcha.focused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                completeSms();
                return true;
            }
            return captcha.keyPressed(event);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return search.charTyped(event) || country.charTyped(event) || phone.charTyped(event) || captcha.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        return search.preeditUpdated(event) || country.preeditUpdated(event)
                || phone.preeditUpdated(event) || captcha.preeditUpdated(event);
    }

    @Override
    public boolean cancelTransient() {
        if (coverPreview) { coverPreview = false; return true; }
        if (queueOpen) { queueOpen = false; return true; }
        if (bindingHotkey != null) { bindingHotkey = null; return true; }
        if (focus.hasFocus()) { focus.clear(); return true; }
        if (session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING) {
            navigate(previousRoute);
            return true;
        }
        return false;
    }

    @Override
    public void closed() {
        disposed = true;
        generation++;
        data.closed();
        manager.cancelLogin();
        focus.clear();
        search.value("");
        phone.value("");
        captcha.value("");
        country.value("86");
        qrArtwork = null;
        qrRequesting = false;
        qrChecking = false;
        queueOpen = false;
        queueScroll = 0f;
        targetQueueScroll = 0f;
        coverPreview = false;
        bindingHotkey = null;
        contentScroll = 0f;
        targetContentScroll = 0f;
        sidebarScroll = 0f;
        targetSidebarScroll = 0f;
        encoded.clear();
        hovers.clear();
        imageFades.clear();
        message = "";
        translationsEnabled = true;
        previousRoute = ClickGuiSession.MusicRoute.RECOMMENDED;
    }

    private void navigate(ClickGuiSession.MusicRoute route) {
        ClickGuiSession.MusicRoute currentRoute = session.musicRoute();
        if (session.musicRoute() == ClickGuiSession.MusicRoute.LOGIN && route != ClickGuiSession.MusicRoute.LOGIN) {
            manager.cancelLogin();
            qrArtwork = null;
            qrRequesting = false;
            qrChecking = false;
        }
        if (route == ClickGuiSession.MusicRoute.NOW_PLAYING
                && currentRoute != ClickGuiSession.MusicRoute.NOW_PLAYING) {
            previousRoute = currentRoute;
        }
        session.musicRoute(route);
        contentScroll = 0f;
        targetContentScroll = 0f;
        routeProgress = 0f;
        boolean preservesRouteData = route == ClickGuiSession.MusicRoute.NOW_PLAYING
                || (currentRoute == ClickGuiSession.MusicRoute.NOW_PLAYING && route == previousRoute);
        if (!preservesRouteData) data.load(route);
    }

    private void openPlaylist(Playlist playlist) {
        session.musicRoute(ClickGuiSession.MusicRoute.PLAYLIST);
        contentScroll = 0f;
        targetContentScroll = 0f;
        routeProgress = 0f;
        data.playlist(playlist);
    }

    private void startSearch() {
        String query = search.value().strip();
        if (query.isEmpty()) return;
        session.musicRoute(ClickGuiSession.MusicRoute.SEARCH);
        contentScroll = 0f;
        targetContentScroll = 0f;
        routeProgress = 0f;
        data.search(query);
    }

    private void cycleMode() {
        PlayMode[] values = PlayMode.values();
        PlayMode next = values[(manager.config().playMode().ordinal() + 1) % values.length];
        if (next == PlayMode.INTELLIGENCE) startIntelligence();
        else manager.mode(next);
    }

    private void cycleQuality() {
        Quality[] values = Quality.values();
        manager.quality(values[(manager.config().quality().ordinal() + 1) % values.length]);
    }

    private void cycleCacheLimit() {
        long gib = 1024L * 1024 * 1024;
        long current = manager.config().cacheLimitBytes();
        long next = current < gib ? gib : current < 2 * gib ? 2 * gib : current < 5 * gib ? 5 * gib : 512L * 1024 * 1024;
        manager.cacheLimitBytes(next);
    }

    private void updateSeek(float mouseX) {
        PlaybackState state = manager.state();
        if (seekBounds == null || state.durationSeconds() <= 0f) return;
        float progress = Math.clamp((mouseX - seekBounds.x()) / seekBounds.width(), 0f, 1f);
        manager.seek(state.durationSeconds() * progress);
    }

    private void updateVolume(float mouseX) {
        if (volumeBounds == null) return;
        float progress = Math.clamp((mouseX - volumeBounds.x()) / volumeBounds.width(), 0f, 1f);
        manager.volume(Math.round(progress * 10f) / 10f);
    }

    private void updateRate(float mouseX) {
        if (rateBounds == null) return;
        float progress = Math.clamp((mouseX - rateBounds.x()) / rateBounds.width(), 0f, 1f);
        manager.rate(Math.round((.5f + progress * 1.5f) * 10f) / 10f);
    }

    private void likeCurrent() {
        Music music = manager.state().music();
        if (music == null) return;
        boolean liked = !manager.likedIds().contains(music.id());
        run(manager.like(music.id(), liked), success -> show(success
                ? liked ? tr("nimbus.music.message.liked", "Added to liked music")
                : tr("nimbus.music.message.unliked", "Removed from liked music")
                : tr("nimbus.music.message.like_failed", "Unable to update liked music")));
    }

    private void startIntelligence() {
        Playlist playlist = data.selectedPlaylist();
        if (playlist == null || data.songs().isEmpty()) {
            show(tr("nimbus.music.message.open_playlist", "Open a playlist before enabling Intelligence"));
            return;
        }
        Music current = manager.state().music();
        Music seed = current != null && data.songs().stream().anyMatch(song -> song.id() == current.id())
                ? current : data.songs().getFirst();
        PlayMode previous = manager.config().playMode();
        manager.mode(PlayMode.INTELLIGENCE);
        show(tr("nimbus.music.message.building_intelligence", "Building intelligence queue..."));
        long token = generation;
        manager.playIntelligent(seed.id(), playlist.id(), 50).whenComplete((songs, failure) -> main(() -> {
            if (failure != null && manager.config().playMode() == PlayMode.INTELLIGENCE) manager.mode(previous);
            if (disposed || token != generation) return;
            if (failure != null) show(message(failure));
            else show(songs.isEmpty()
                    ? tr("nimbus.music.message.no_intelligence", "No intelligence recommendations")
                    : tr("nimbus.music.message.intelligence_started", "Intelligence playback started"));
        }));
    }

    private void cacheCurrent() {
        Music music = manager.state().music();
        if (music == null) return;
        run(manager.cache(music), cached -> {
            data.refreshCached();
            show(tr("nimbus.music.message.cached", "Cached %s", cached.music().name()));
        });
    }

    private void requestQr() {
        if (qrRequesting) return;
        qrRequesting = true;
        long token = generation;
        manager.requestQrLogin().thenCompose(login -> manager.qrImageBytes()).whenComplete((bytes, failure) -> main(() -> {
            if (disposed || token != generation) return;
            qrRequesting = false;
            if (failure != null) { show(message(failure)); return; }
            qrArtwork = bytes == null || bytes.length == 0 ? null : new Artwork(bytes, null);
            nextQrCheck = System.currentTimeMillis() + 2000L;
        }));
    }

    private void pollQr(long now) {
        if (disposed || session.musicRoute() != ClickGuiSession.MusicRoute.LOGIN || loginTab != LoginTab.QR
                || manager.activeQr() == null || qrChecking || now < nextQrCheck) return;
        qrChecking = true;
        nextQrCheck = now + 2000L;
        long token = generation;
        manager.checkQrLogin().whenComplete((status, failure) -> main(() -> {
            if (disposed || token != generation) return;
            qrChecking = false;
            if (failure != null) { show(message(failure)); return; }
            if (status.code() == 800) {
                qrArtwork = null;
                show(tr("nimbus.music.message.qr_expired", "QR code expired"));
            } else if (status.code() == 802) {
                show(tr("nimbus.music.message.confirm_phone", "Confirm sign in on your phone"));
            }
            else if (status.code() == 803) {
                show(tr("nimbus.music.message.signed_in", "Signed in"));
                navigate(ClickGuiSession.MusicRoute.RECOMMENDED);
            }
        }));
    }

    private void sendSms() {
        run(manager.requestSmsLogin(phone.value(), country.value()), status ->
                show(status.successful()
                        ? tr("nimbus.music.message.code_sent", "Verification code sent") : status.message()));
    }

    private void completeSms() {
        run(manager.completeSmsLogin(captcha.value()), status -> {
            if (status.successful()) {
                show(tr("nimbus.music.message.signed_in", "Signed in"));
                navigate(ClickGuiSession.MusicRoute.RECOMMENDED);
            } else show(status.message());
        });
    }

    private <T> void run(CompletableFuture<T> future, Consumer<T> success) {
        long token = generation;
        future.whenComplete((value, failure) -> main(() -> {
            if (disposed || token != generation) return;
            if (failure != null) show(message(failure));
            else success.accept(value);
        }));
    }

    private void renderMessage(UiContext context, UiRect bounds) {
        if (message.isBlank() || context.nowMillis() >= messageUntil) return;
        float width = Math.min(320f, context.width(UiContext.FONT, 10f, message) + 24f);
        UiRect toast = new UiRect(bounds.x() + (bounds.width() - width) * .5f,
                bounds.y() + bounds.height() - PLAYER_HEIGHT - 42f, width, 29f);
        context.frame().draw().roundedRect(toast.x(), toast.y(), toast.width(), toast.height(), 4f, 0xED111318);
        context.centered(message, toast, 10f, ClickGuiTheme.TEXT);
    }

    private void show(String value) {
        message = value == null ? "" : value;
        messageUntil = System.currentTimeMillis() + 2800L;
    }

    private void drawMarquee(UiContext context, String font, float size, String text, UiRect bounds, int color) {
        float textWidth = context.width(font, size, text);
        if (textWidth <= bounds.width()) {
            context.draw(font, size, text, bounds.x(), bounds.y(), color);
            return;
        }
        float overflow = textWidth - bounds.width();
        long hold = 1100L;
        long travel = Math.max(1100L, Math.round(overflow / 28f * 1000f));
        long cycle = hold * 2L + travel * 2L;
        long elapsed = Math.floorMod(context.nowMillis() - marqueeStartedAt, cycle);
        float offset;
        if (elapsed < hold) offset = 0f;
        else if (elapsed < hold + travel) {
            offset = overflow * smoothStep((elapsed - hold) / (float) travel);
        } else if (elapsed < hold * 2L + travel) offset = overflow;
        else offset = overflow * (1f - smoothStep((elapsed - hold * 2L - travel) / (float) travel));
        int clip = context.clip(bounds, 0f);
        try {
            context.draw(font, size, text, bounds.x() - offset, bounds.y(), color);
        } finally {
            context.restore(clip);
        }
    }

    private static float lyricTarget(List<TimedLine> lines, int active, int now) {
        if (active >= lines.size() - 1) return active;
        int nextTime = lines.get(active + 1).timeMs();
        int transition = Math.min(300, Math.max(140, nextTime - lines.get(active).timeMs()));
        float progress = Math.clamp((now - (nextTime - transition)) / (float) transition, 0f, 1f);
        return active + smoothStep(progress);
    }

    private static float smoothStep(float value) {
        float bounded = Math.clamp(value, 0f, 1f);
        return bounded * bounded * (3f - 2f * bounded);
    }

    private static int activeLine(List<TimedLine> lines, int now) {
        int low = 0;
        int high = lines.size() - 1;
        int active = 0;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (lines.get(middle).timeMs() <= now) {
                active = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return active;
    }

    private static WordLine activeWordLine(List<WordLine> lines, int now) {
        int low = 0;
        int high = lines.size() - 1;
        WordLine active = null;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            WordLine value = lines.get(middle);
            if (value.startMs() <= now) {
                active = value;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return active != null && now <= active.startMs() + active.durationMs() ? active : null;
    }

    private static int alpha(int color, int alpha) { return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24; }
    private static float animate(float value, float target, float speed) {
        float result = value + (target - value) * Math.clamp(speed, 0f, 1f);
        return Math.abs(result - target) < .002f ? target : result;
    }
    private static String time(float seconds) {
        int value = Math.max(0, Math.round(seconds));
        return String.format(java.util.Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }
    private static String formatRate(float rate) { return String.format(java.util.Locale.ROOT, "%.1fx", rate); }
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do { value /= 1024d; unit++; } while (value >= 1024d && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, value >= 10d ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
    private static String formatCount(long value) {
        if (value >= 100_000_000L) return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000d);
        if (value >= 100_000L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000d);
        if (value >= 1_000L) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000d);
        return Long.toString(value);
    }
    private static String hotkey(int[] keys) {
        if (keys.length == 0) return tr("nimbus.music.unbound", "Unbound");
        List<String> values = new ArrayList<>(keys.length);
        for (int key : keys) {
            String name = GLFW.glfwGetKeyName(key, 0);
            values.add(name == null ? Integer.toString(key) : name.toUpperCase(java.util.Locale.ROOT));
        }
        return String.join(" + ", values);
    }
    private static String ellipsize(UiContext context, String value, float width) {
        return ellipsizeFont(context, UiContext.FONT, 10f, value, width);
    }
    private static float fitTextSize(UiContext context, String font, float preferred, float minimum,
                                     String value, float width) {
        if (value == null || value.isEmpty() || context.width(font, preferred, value) <= width) return preferred;
        float minimumWidth = context.width(font, minimum, value);
        if (minimumWidth > width) return Math.max(7f, minimum * width / minimumWidth);
        float low = minimum;
        float high = preferred;
        for (int iteration = 0; iteration < 7; iteration++) {
            float middle = (low + high) * .5f;
            if (context.width(font, middle, value) <= width) low = middle;
            else high = middle;
        }
        return low;
    }
    private static String ellipsizeFont(UiContext context, String font, float size, String value, float width) {
        if (value == null) return "";
        if (context.width(font, size, value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && context.width(font, size, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
    private static void drawCenteredY(UiContext context, String font, float size, String value,
                                      float x, float y, float height, int color) {
        context.draw(font, size, value, x, y + (height - context.height(font, size, value)) * .5f, color);
    }
    private static void icon(UiContext context, String svg, float x, float y, float size, int color) {
        try (Paint paint = new Paint().setAntiAlias(true).setColor(color)) {
            context.frame().draw().svg(svg, x, y, size, paint);
        }
    }
    private static void main(Runnable action) { Minecraft.getInstance().execute(action); }
    private static String tr(String key, String fallback, Object... arguments) {
        return Core.INSTANCE.isInitialized()
                ? Core.INSTANCE.getI18nManager().get(key, fallback, arguments) : fallback;
    }
    private static String message(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }

    private static Map<String, Float> boundedAnimations() {
        return new LinkedHashMap<>(64, .75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                return size() > 512;
            }
        };
    }

    private enum LoginTab { QR, SMS }
    private enum Action {
        NAVIGATE, OPEN_PLAYLIST, PLAY_SONG, PLAY_ALL, PLAY_CACHED, DELETE_CACHED,
        PREVIOUS, NEXT, TOGGLE_PLAYBACK, MODE, TOGGLE_QUEUE, PLAY_AT, SEEK, VOLUME,
        NOW_PLAYING, BACK_FROM_NOW_PLAYING, PREVIEW_COVER, CLOSE_PREVIEW,
        LIKE_CURRENT, CACHE_CURRENT, SEEK_LYRIC, TOGGLE_TRANSLATION,
        QUALITY, PREFETCH, DYNAMIC_BACKGROUND, RATE, CACHE_LIMIT,
        HOTKEY_PREVIOUS, HOTKEY_NEXT, HOTKEY_PAUSE, TRIM_CACHE, CLEAR_CACHE,
        LOGIN_QR, LOGIN_SMS, REQUEST_QR, SEND_SMS, COMPLETE_SMS, LOGOUT;

        private MusicConfig.Hotkey hotkey() {
            return switch (this) {
                case HOTKEY_PREVIOUS -> MusicConfig.Hotkey.PREVIOUS;
                case HOTKEY_NEXT -> MusicConfig.Hotkey.NEXT;
                case HOTKEY_PAUSE -> MusicConfig.Hotkey.PAUSE;
                default -> null;
            };
        }
    }
    private record Hit(UiRect bounds, Action action, Object value, UiRect clip) {
        private Hit(UiRect bounds, Action action, Object value) {
            this(bounds, action, value, null);
        }
    }
    private record SongSelection(List<Music> songs, int index) {
        private SongSelection { songs = List.copyOf(songs); }
    }
    private record CachedSelection(List<Long> ids, int index) {
        private CachedSelection { ids = List.copyOf(ids); }
    }
}
