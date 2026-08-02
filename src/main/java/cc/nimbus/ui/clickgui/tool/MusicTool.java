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
import io.github.humbleui.skija.FilterTileMode;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MusicTool implements ToolPage {
    private static final float SIDEBAR_WIDTH = 178f;
    private static final float PLAYER_HEIGHT = 72f;
    private static final float HEADER_HEIGHT = 56f;
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
    private UiRect countryBounds;
    private UiRect phoneBounds;
    private UiRect captchaBounds;
    private UiRect queueBounds;
    private float contentScroll;
    private float maximumContentScroll;
    private float sidebarScroll;
    private float maximumSidebarScroll;
    private float queueScroll;
    private float maximumQueueScroll;
    private float routeProgress;
    private float coverBlend;
    private float queueProgress;
    private float previewProgress;
    private boolean queueOpen;
    private boolean coverPreview;
    private boolean draggingSeek;
    private boolean draggingVolume;
    private boolean disposed = true;
    private LoginTab loginTab = LoginTab.QR;
    private MusicConfig.Hotkey bindingHotkey;
    private byte[] qrImage = new byte[0];
    private boolean qrRequesting;
    private boolean qrChecking;
    private long nextQrCheck;
    private long generation;
    private String message = "";
    private long messageUntil;

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
        data.observePlayback(playback);
        hits.clear();
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        routeProgress = animate(routeProgress, 1f, speed);
        coverBlend = data.currentArtwork() == null ? 0f : animate(coverBlend, 1f, speed * .72f);
        queueProgress = animate(queueProgress, queueOpen ? 1f : 0f, speed);
        previewProgress = animate(previewProgress, coverPreview ? 1f : 0f, speed);

        sidebarBounds = new UiRect(bounds.x(), bounds.y(), SIDEBAR_WIDTH, bounds.height() - PLAYER_HEIGHT);
        bodyBounds = new UiRect(bounds.x() + SIDEBAR_WIDTH, bounds.y() + HEADER_HEIGHT,
                bounds.width() - SIDEBAR_WIDTH, bounds.height() - HEADER_HEIGHT - PLAYER_HEIGHT);
        context.frame().draw().rect(bounds.x() + SIDEBAR_WIDTH, bounds.y(), 1f,
                bounds.height() - PLAYER_HEIGHT, ClickGuiTheme.STROKE);
        context.frame().draw().rect(bounds.x(), bounds.y() + bounds.height() - PLAYER_HEIGHT,
                bounds.width(), 1f, ClickGuiTheme.STROKE);

        renderSidebar(context, bounds);
        renderHeader(context, bounds);
        renderRoute(context, playback);
        renderPlayer(context, bounds, playback);
        if (queueProgress > .005f) renderQueue(context, bounds, playback);
        else queueBounds = null;
        if (previewProgress > .005f) renderCoverPreview(context, bounds, playback);
        renderMessage(context, bounds);
        pollQr(context.nowMillis());
    }

    private void renderSidebar(UiContext context, UiRect bounds) {
        try (Paint paint = new Paint().setAntiAlias(true).setColor(ClickGuiTheme.TEXT)) {
            context.frame().draw().svgResource("assets/nimbus/image/logo.svg",
                    bounds.x() + 16f, bounds.y() + 15f, 27f, paint);
        }
        context.draw(NimbusFonts.DISPLAY, 15f, "Nimbus Music",
                bounds.x() + 50f, bounds.y() + 20f, ClickGuiTheme.TEXT);

        sidebarClipBounds = new UiRect(bounds.x() + 8f, bounds.y() + 55f,
                SIDEBAR_WIDTH - 16f, bounds.height() - PLAYER_HEIGHT - 63f);
        int clip = context.clip(sidebarClipBounds, 3f);
        try {
            float y = sidebarClipBounds.y() + sidebarScroll;
            y = group(context, "DISCOVER", y);
            y = nav(context, "Recommended", ClickGuiIcons.HOME, ClickGuiSession.MusicRoute.RECOMMENDED, y);
            y = nav(context, "Featured", ClickGuiIcons.SPARKLES, ClickGuiSession.MusicRoute.FEATURED, y);
            y = nav(context, "Search", ClickGuiIcons.SEARCH, ClickGuiSession.MusicRoute.SEARCH, y);
            y += 8f;
            y = group(context, "MY MUSIC", y);
            y = nav(context, "Liked Music", ClickGuiIcons.HEART, ClickGuiSession.MusicRoute.LIKED, y);
            y = nav(context, "Cached Songs", ClickGuiIcons.CACHE, ClickGuiSession.MusicRoute.CACHED, y);
            y = nav(context, "Now Playing", ClickGuiIcons.MUSIC, ClickGuiSession.MusicRoute.NOW_PLAYING, y);
            y += 8f;

            User profile = manager.profile();
            if (profile != null) {
                List<Playlist> created = manager.accountPlaylists().stream()
                        .filter(playlist -> playlist.creator() != null && playlist.creator().id() == profile.id())
                        .toList();
                List<Playlist> subscribed = manager.accountPlaylists().stream()
                        .filter(Playlist::subscribed).toList();
                if (!created.isEmpty()) {
                    y = group(context, "PLAYLISTS", y);
                    for (Playlist playlist : created) y = playlistNav(context, playlist, y);
                    y += 7f;
                }
                if (!subscribed.isEmpty()) {
                    y = group(context, "SUBSCRIBED", y);
                    for (Playlist playlist : subscribed) y = playlistNav(context, playlist, y);
                    y += 7f;
                }
            }
            y = group(context, "ACCOUNT", y);
            y = nav(context, profile == null ? "Sign In" : profile.name(), ClickGuiIcons.USER,
                    ClickGuiSession.MusicRoute.LOGIN, y);
            y = nav(context, "Music Settings", ClickGuiIcons.SETTINGS,
                    ClickGuiSession.MusicRoute.SETTINGS, y);
            maximumSidebarScroll = Math.max(0f, y - sidebarClipBounds.y() - sidebarClipBounds.height());
            sidebarScroll = Math.clamp(sidebarScroll, -maximumSidebarScroll, 0f);
        } finally {
            context.restore(clip);
        }
    }

    private float group(UiContext context, String label, float y) {
        context.text(label, sidebarBounds.x() + 10f, y + 3f, 8.5f, 0xFFA4A8B0);
        return y + 21f;
    }

    private float nav(UiContext context, String label, String icon, ClickGuiSession.MusicRoute route, float y) {
        UiRect row = new UiRect(sidebarBounds.x() + 4f, y, SIDEBAR_WIDTH - 18f, 28f);
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
        UiRect row = new UiRect(sidebarBounds.x() + 4f, y, SIDEBAR_WIDTH - 18f, 27f);
        if (!row.intersects(sidebarClipBounds)) return y + 30f;
        boolean active = data.selectedPlaylist() != null && data.selectedPlaylist().id() == playlist.id()
                && session.musicRoute() == ClickGuiSession.MusicRoute.PLAYLIST;
        if (active || row.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                    active ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        }
        context.textIn(ellipsize(context, playlist.name(), row.width() - 17f),
                new UiRect(row.x() + 8f, row.y(), row.width() - 12f, row.height()), 9.8f,
                active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(row, Action.OPEN_PLAYLIST, playlist, sidebarClipBounds));
        return y + 30f;
    }

    private void renderHeader(UiContext context, UiRect bounds) {
        searchBounds = new UiRect(bounds.x() + SIDEBAR_WIDTH + 18f, bounds.y() + 14f,
                Math.min(292f, bounds.width() - SIDEBAR_WIDTH - 78f), 29f);
        search.render(context, searchBounds, "Search songs and playlists", 10.5f, false);
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
                case LIKED -> renderSongsPage(context, shifted, y, "Liked Music", data.songs());
                case CACHED -> renderCached(context, shifted, y);
                case PLAYLIST -> renderPlaylist(context, shifted, y);
                case NOW_PLAYING -> renderNowPlaying(context, shifted, playback);
                case SETTINGS -> renderSettings(context, shifted, y);
                case LOGIN -> renderLogin(context, shifted, y);
            };
            maximumContentScroll = session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING
                    ? 0f : Math.max(0f, end - shifted.y() - shifted.height() - contentScroll + 14f);
            contentScroll = Math.clamp(contentScroll, -maximumContentScroll, 0f);
        } finally {
            context.restore(clip);
            context.frame().canvas().restoreToCount(layer);
        }
    }

    private float renderRecommended(UiContext context, UiRect bounds, float y) {
        y = heading(context, "Recommended", "Made for this session", bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        y = section(context, "Daily and Personalized Playlists", bounds, y);
        y = playlistGrid(context, bounds, y, data.playlists(), 8);
        if (!data.songs().isEmpty()) {
            y += 10f;
            y = section(context, "Songs for You", bounds, y);
            y = songList(context, bounds, y, data.songs());
        }
        return y;
    }

    private float renderFeatured(UiContext context, UiRect bounds, float y) {
        y = heading(context, "Featured", "High quality playlists", bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        return playlistGrid(context, bounds, y, data.playlists(), data.playlists().size());
    }

    private float renderSearch(UiContext context, UiRect bounds, float y) {
        y = heading(context, "Search", search.value().isBlank() ? "Songs and playlists" : search.value(), bounds, y);
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        if (data.songs().isEmpty() && data.playlists().isEmpty()) {
            context.text("No results", bounds.x() + 18f, y + 10f, 11f, ClickGuiTheme.MUTED);
            return y + 44f;
        }
        if (!data.playlists().isEmpty()) {
            y = section(context, "Playlists", bounds, y);
            y = playlistGrid(context, bounds, y, data.playlists(), Math.min(8, data.playlists().size()));
        }
        if (!data.songs().isEmpty()) {
            y += 8f;
            y = section(context, "Songs", bounds, y);
            y = songList(context, bounds, y, data.songs());
        }
        return y;
    }

    private float renderSongsPage(UiContext context, UiRect bounds, float y, String title, List<Music> songs) {
        y = heading(context, title, songs.size() + " songs", bounds, y);
        if (manager.profile() == null && session.musicRoute() == ClickGuiSession.MusicRoute.LIKED) {
            context.text("Sign in to view liked music", bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
            return y + 42f;
        }
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        return songList(context, bounds, y, songs);
    }

    private float renderCached(UiContext context, UiRect bounds, float y) {
        List<CachedSong> cached = data.cachedSongs();
        y = heading(context, "Cached Songs", formatBytes(manager.cacheSizeBytes()), bounds, y);
        if (cached.isEmpty()) {
            context.text("No cached songs", bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
            return y + 42f;
        }
        List<Long> ids = cached.stream().map(value -> value.music().id()).toList();
        for (int index = 0; index < cached.size(); index++) {
            CachedSong value = cached.get(index);
            UiRect row = new UiRect(bounds.x() + 14f, y, bounds.width() - 28f, 38f);
            if (row.intersects(bodyBounds)) {
                hoverRow(context, row, "cached:" + value.music().id());
                context.textIn(value.music().name(), new UiRect(row.x() + 9f, row.y(), row.width() * .46f, 22f),
                        10.5f, ClickGuiTheme.TEXT);
                context.text(value.music().artistsText(), row.x() + 9f, row.y() + 22f, 9f, ClickGuiTheme.MUTED);
                String detail = value.qualities().stream().map(Quality::apiValue).reduce((a, b) -> a + ", " + b).orElse("")
                        + "  " + formatBytes(value.sizeBytes());
                context.text(detail, row.x() + row.width() * .55f, row.y() + 13f, 9.5f, ClickGuiTheme.MUTED);
                UiRect delete = new UiRect(row.x() + row.width() - 27f, row.y() + 7f, 24f, 24f);
                icon(context, ClickGuiIcons.TRASH, delete.x() + 5f, delete.y() + 5f, 14f, ClickGuiTheme.MUTED);
                hits.add(new Hit(delete, Action.DELETE_CACHED, value.music().id(), bodyBounds));
                hits.add(new Hit(new UiRect(row.x(), row.y(), row.width() - 31f, row.height()),
                        Action.PLAY_CACHED, new CachedSelection(ids, index), bodyBounds));
            }
            y += 41f;
        }
        return y;
    }

    private float renderPlaylist(UiContext context, UiRect bounds, float y) {
        Playlist playlist = data.selectedPlaylist();
        if (playlist == null) return error(context, bounds, y);
        Artwork artwork = data.artwork(playlist, 320);
        UiRect cover = new UiRect(bounds.x() + 18f, y, 94f, 94f);
        drawArtwork(context, artwork, "playlist:" + playlist.id(), cover, 4f, 255);
        context.draw(NimbusFonts.CONTENT_MEDIUM, 19f, playlist.name(),
                cover.x() + cover.width() + 16f, y + 10f, ClickGuiTheme.TEXT);
        context.text(playlist.trackCount() + " tracks", cover.x() + cover.width() + 16f,
                y + 42f, 10f, ClickGuiTheme.MUTED);
        context.text(ellipsize(context, playlist.description(), bounds.width() - 160f),
                cover.x() + cover.width() + 16f, y + 64f, 9.5f, ClickGuiTheme.MUTED);
        if (!data.loading() && !data.songs().isEmpty()) {
            UiRect smart = new UiRect(bounds.x() + bounds.width() - 120f, y + 11f, 96f, 29f);
            Music current = manager.state().music();
            Music seed = data.songs().stream()
                    .filter(song -> current != null && song.id() == current.id())
                    .findFirst().orElse(data.songs().getFirst());
            button(context, smart, "Smart Play", Action.INTELLIGENT,
                    new SmartSelection(seed.id(), playlist.id()));
        }
        y += 112f;
        if (data.loading()) return loading(context, bounds, y);
        return songList(context, bounds, y, data.songs());
    }

    private float renderNowPlaying(UiContext context, UiRect bounds, PlaybackState playback) {
        Music music = playback.music();
        if (music == null) {
            context.centered("Nothing is playing", bounds, 12f, ClickGuiTheme.MUTED);
            return bounds.y() + bounds.height();
        }
        renderDynamicBackground(context, bounds, data.currentArtwork());
        float coverSize = Math.min(248f, bounds.height() - 86f);
        UiRect cover = new UiRect(bounds.x() + 28f, bounds.y() + (bounds.height() - coverSize) * .5f - 8f,
                coverSize, coverSize);
        drawCurrentArtwork(context, music, cover);
        hits.add(new Hit(cover, Action.PREVIEW_COVER, null));
        context.draw(NimbusFonts.CONTENT_MEDIUM, 18f, music.name(), cover.x(),
                cover.y() + cover.height() + 13f, ClickGuiTheme.TEXT);
        context.text(music.artistsText(), cover.x(), cover.y() + cover.height() + 38f, 10.5f, ClickGuiTheme.MUTED);
        float iconY = cover.y() + cover.height() + 7f;
        UiRect like = new UiRect(cover.x() + cover.width() - 54f, iconY, 24f, 24f);
        UiRect cache = new UiRect(cover.x() + cover.width() - 24f, iconY, 24f, 24f);
        icon(context, ClickGuiIcons.HEART, like.x() + 4f, like.y() + 4f, 16f,
                manager.likedIds().contains(music.id()) ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        icon(context, ClickGuiIcons.DOWNLOAD, cache.x() + 4f, cache.y() + 4f, 16f, ClickGuiTheme.MUTED);
        hits.add(new Hit(like, Action.LIKE_CURRENT, null));
        hits.add(new Hit(cache, Action.CACHE_CURRENT, null));

        UiRect lyrics = new UiRect(cover.x() + cover.width() + 36f, bounds.y() + 18f,
                bounds.x() + bounds.width() - cover.x() - cover.width() - 58f, bounds.height() - 36f);
        renderLyrics(context, lyrics, playback);
        return bounds.y() + bounds.height();
    }

    private void renderDynamicBackground(UiContext context, UiRect bounds, Artwork artwork) {
        if (artwork == null || !manager.config().dynamicBackground()
                || !Core.INSTANCE.getConfigManager().clientSettings().postProcessing.getValue()) return;
        Image image = image(context, artwork, "now-bg");
        if (image == null) return;
        int clip = context.clip(bounds, 0f);
        try (Paint imagePaint = new Paint().setAntiAlias(true).setAlpha(62);
             Paint colorPaint = new Paint().setAntiAlias(true)) {
            imagePaint.setImageFilter(context.frame().effects().imageBlur(28f, 28f, FilterTileMode.CLAMP));
            float zoom = 28f;
            context.frame().draw().image(image, bounds.x() - zoom, bounds.y() - zoom,
                    bounds.width() + zoom * 2f, bounds.height() + zoom * 2f, imagePaint);
            float drift = (float) ((Math.sin(System.currentTimeMillis() / 5500d) + 1d) * .5d);
            int first = alpha(artwork.color(0), 42);
            int second = alpha(artwork.color(1), 18);
            int third = alpha(artwork.color(2), 34);
            colorPaint.setShader(context.frame().effects().linearGradient(
                    new Point(bounds.x() + bounds.width() * drift, bounds.y()),
                    new Point(bounds.x() + bounds.width() * (1f - drift), bounds.y() + bounds.height()),
                    new int[]{first, second, third}));
            context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), colorPaint);
            context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0x300A0B0D);
        } finally {
            context.restore(clip);
        }
    }

    private void renderLyrics(UiContext context, UiRect bounds, PlaybackState playback) {
        List<TimedLine> lines = data.lyrics().lines();
        if (lines.isEmpty()) {
            context.centered("Lyrics unavailable", bounds, 11f, ClickGuiTheme.MUTED);
            return;
        }
        int now = Math.round(playback.progressSeconds() * 1000f);
        int active = activeLine(lines, now);
        float centerY = bounds.y() + bounds.height() * .5f;
        for (int index = Math.max(0, active - 4); index <= Math.min(lines.size() - 1, active + 4); index++) {
            float distance = index - active;
            float y = centerY + distance * 46f - 12f;
            TimedLine line = lines.get(index);
            int color = index == active ? ClickGuiTheme.TEXT : 0x8FA8ABB2;
            float size = index == active ? 13f : 10.5f;
            if (index == active && !data.lyrics().words().isEmpty()) {
                drawWordProgress(context, line.text(), bounds.x(), y, bounds.width(), size, now);
            } else {
                context.draw(NimbusFonts.CONTENT, size,
                        ellipsizeFont(context, NimbusFonts.CONTENT, size, line.text(), bounds.width()),
                        bounds.x(), y, color);
            }
            String translated = translated(index, line.timeMs());
            if (!translated.isBlank()) {
                context.draw(NimbusFonts.CONTENT, 9.5f,
                        ellipsizeFont(context, NimbusFonts.CONTENT, 9.5f, translated, bounds.width()),
                        bounds.x(), y + 22f, index == active ? 0xD8D5D7DC : 0x708F939B);
            }
        }
    }

    private void drawWordProgress(UiContext context, String fallback, float x, float y, float width,
                                  float size, int now) {
        WordLine wordLine = data.lyrics().words().stream()
                .filter(line -> now >= line.startMs() && now <= line.startMs() + line.durationMs())
                .findFirst().orElse(null);
        String text = wordLine == null || wordLine.text().isBlank() ? fallback : wordLine.text();
        text = ellipsizeFont(context, NimbusFonts.CONTENT, size, text, width);
        context.draw(NimbusFonts.CONTENT, size, text, x, y, 0x78FFFFFF);
        float highlighted = 0f;
        if (wordLine != null) {
            for (TimedWord word : wordLine.words()) {
                float wordWidth = context.width(NimbusFonts.CONTENT, size, word.text());
                if (now >= word.endMs()) highlighted += wordWidth;
                else if (now > word.startMs()) {
                    float progress = (now - word.startMs()) / (float) Math.max(1, word.endMs() - word.startMs());
                    highlighted += wordWidth * Math.clamp(progress, 0f, 1f);
                    break;
                } else break;
            }
        }
        int clip = context.clip(new UiRect(x, y - 2f, Math.min(width, highlighted), size + 8f), 0f);
        try {
            context.draw(NimbusFonts.CONTENT, size, text, x, y, ClickGuiTheme.TEXT);
        } finally {
            context.restore(clip);
        }
    }

    private String translated(int index, int time) {
        List<TimedLine> translated = data.lyrics().translated();
        if (translated.isEmpty()) return "";
        if (index < translated.size() && Math.abs(translated.get(index).timeMs() - time) < 1200) {
            return translated.get(index).text();
        }
        return translated.stream().filter(line -> Math.abs(line.timeMs() - time) < 600)
                .findFirst().map(TimedLine::text).orElse("");
    }

    private float renderSettings(UiContext context, UiRect bounds, float y) {
        y = heading(context, "Music Settings", "Playback, cache and shortcuts", bounds, y);
        MusicConfig config = manager.config();
        y = settingRow(context, bounds, y, "Quality", config.quality().apiValue(), Action.QUALITY);
        y = settingRow(context, bounds, y, "Prefetch", config.prefetch() ? "On" : "Off", Action.PREFETCH);
        y = settingRow(context, bounds, y, "Dynamic Background",
                config.dynamicBackground() ? "On" : "Off", Action.DYNAMIC_BACKGROUND);
        y = settingRow(context, bounds, y, "Playback Rate", formatRate(config.rate()), Action.RATE);
        y = settingRow(context, bounds, y, "Cache Limit", formatBytes(config.cacheLimitBytes()), Action.CACHE_LIMIT);
        y += 8f;
        y = section(context, "Hotkeys", bounds, y);
        y = settingRow(context, bounds, y, "Previous", hotkey(config.hotkey(MusicConfig.Hotkey.PREVIOUS)), Action.HOTKEY_PREVIOUS);
        y = settingRow(context, bounds, y, "Next", hotkey(config.hotkey(MusicConfig.Hotkey.NEXT)), Action.HOTKEY_NEXT);
        y = settingRow(context, bounds, y, "Play / Pause", hotkey(config.hotkey(MusicConfig.Hotkey.PAUSE)), Action.HOTKEY_PAUSE);
        y += 8f;
        float width = (bounds.width() - 44f) * .5f;
        UiRect trim = new UiRect(bounds.x() + 18f, y, width, 30f);
        UiRect clear = new UiRect(trim.x() + width + 8f, y, width, 30f);
        button(context, trim, "Trim Cache", Action.TRIM_CACHE, null);
        button(context, clear, "Clear Cache", Action.CLEAR_CACHE, null);
        return y + 46f;
    }

    private float settingRow(UiContext context, UiRect bounds, float y, String name, String value, Action action) {
        UiRect row = new UiRect(bounds.x() + 18f, y, bounds.width() - 36f, 34f);
        hoverRow(context, row, "setting:" + action);
        context.textIn(name, new UiRect(row.x() + 9f, row.y(), row.width() * .55f, row.height()),
                10.5f, ClickGuiTheme.TEXT);
        context.textIn(bindingHotkey != null && action.hotkey() == bindingHotkey ? "Listening..." : value,
                new UiRect(row.x() + row.width() * .62f, row.y(), row.width() * .34f, row.height()),
                10f, ClickGuiTheme.MUTED);
        hits.add(new Hit(row, action, null));
        return y + 38f;
    }

    private float renderLogin(UiContext context, UiRect bounds, float y) {
        User profile = manager.profile();
        y = heading(context, profile == null ? "Sign In" : profile.name(),
                profile == null ? "Netease Cloud Music" : profile.signature(), bounds, y);
        if (profile != null) {
            UiRect logout = new UiRect(bounds.x() + 18f, y + 12f, 150f, 31f);
            button(context, logout, "Sign Out", Action.LOGOUT, null);
            return y + 60f;
        }
        UiRect qrTab = new UiRect(bounds.x() + 18f, y, 92f, 28f);
        UiRect smsTab = new UiRect(qrTab.x() + 98f, y, 92f, 28f);
        tab(context, qrTab, "QR Code", loginTab == LoginTab.QR, Action.LOGIN_QR);
        tab(context, smsTab, "SMS", loginTab == LoginTab.SMS, Action.LOGIN_SMS);
        y += 44f;
        if (loginTab == LoginTab.QR) return renderQrLogin(context, bounds, y);
        return renderSmsLogin(context, bounds, y);
    }

    private float renderQrLogin(UiContext context, UiRect bounds, float y) {
        UiRect image = new UiRect(bounds.x() + 18f, y, 190f, 190f);
        if (qrImage.length == 0) {
            context.frame().draw().roundedRect(image.x(), image.y(), image.width(), image.height(), 4f,
                    IMAGE_PLACEHOLDER);
            context.centered(qrRequesting ? "Loading..." : "QR Code", image, 11f, ClickGuiTheme.MUTED);
        } else {
            Artwork qr = new Artwork(qrImage, null);
            drawArtwork(context, qr, "login-qr", image, 4f, 255);
        }
        UiRect request = new UiRect(image.x() + image.width() + 24f, y + 24f, 150f, 31f);
        button(context, request, qrImage.length == 0 ? "Generate QR" : "Refresh QR", Action.REQUEST_QR, null);
        context.text("Scan with Netease Cloud Music", request.x(), request.y() + 48f, 10f, ClickGuiTheme.MUTED);
        return y + 212f;
    }

    private float renderSmsLogin(UiContext context, UiRect bounds, float y) {
        countryBounds = new UiRect(bounds.x() + 18f, y, 64f, 29f);
        phoneBounds = new UiRect(countryBounds.x() + 72f, y, Math.min(240f, bounds.width() - 126f), 29f);
        country.render(context, countryBounds, "86", 10.5f, false);
        phone.render(context, phoneBounds, "Phone number", 10.5f, false);
        UiRect send = new UiRect(phoneBounds.x() + phoneBounds.width() + 8f, y,
                Math.max(88f, bounds.x() + bounds.width() - phoneBounds.x() - phoneBounds.width() - 26f), 29f);
        button(context, send, "Send Code", Action.SEND_SMS, null);
        y += 41f;
        captchaBounds = new UiRect(bounds.x() + 18f, y, Math.min(240f, bounds.width() - 130f), 29f);
        captcha.render(context, captchaBounds, "Verification code", 10.5f, false);
        UiRect login = new UiRect(captchaBounds.x() + captchaBounds.width() + 8f, y, 112f, 29f);
        button(context, login, "Sign In", Action.COMPLETE_SMS, null);
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
            context.draw(NimbusFonts.CONTENT_MEDIUM, 11f, ellipsizeFont(context,
                    NimbusFonts.CONTENT_MEDIUM, 11f, music.name(), 180f), cover.x() + 61f, y + 16f,
                    ClickGuiTheme.TEXT);
            context.draw(NimbusFonts.CONTENT, 9f, ellipsizeFont(context,
                    NimbusFonts.CONTENT, 9f, music.artistsText(), 180f), cover.x() + 61f, y + 37f,
                    ClickGuiTheme.MUTED);
        } else {
            context.text("Nothing playing", cover.x() + 61f, y + 25f, 10.5f, ClickGuiTheme.MUTED);
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
        icon(context, manager.config().playMode() == PlayMode.SHUFFLE ? ClickGuiIcons.SHUFFLE : ClickGuiIcons.REPEAT,
                mode.x() + 5f, mode.y() + 5f, 15f, ClickGuiTheme.MUTED);
        hits.add(new Hit(queue, Action.TOGGLE_QUEUE, null));
        hits.add(new Hit(mode, Action.MODE, null));
        volumeBounds = new UiRect(right - 145f, y + 34f, 70f, 3f);
        icon(context, ClickGuiIcons.VOLUME, volumeBounds.x() - 24f, y + 27f, 15f, ClickGuiTheme.MUTED);
        context.frame().draw().rect(volumeBounds.x(), volumeBounds.y(), volumeBounds.width(), 3f, 0x25FFFFFF);
        context.frame().draw().rect(volumeBounds.x(), volumeBounds.y(), volumeBounds.width() * playback.volume(),
                3f, 0xB8FFFFFF);
        hits.add(new Hit(new UiRect(volumeBounds.x(), volumeBounds.y() - 5f, volumeBounds.width(), 13f),
                Action.VOLUME, null));
    }

    private void renderQueue(UiContext context, UiRect bounds, PlaybackState playback) {
        float width = Math.min(300f, bounds.width() * .4f);
        float x = bounds.x() + bounds.width() - width + (1f - queueProgress) * width;
        queueBounds = new UiRect(x, bounds.y() + HEADER_HEIGHT,
                width, bounds.height() - HEADER_HEIGHT - PLAYER_HEIGHT);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(queueBounds.x(), queueBounds.y(), queueBounds.width(), queueBounds.height()),
                Math.round(queueProgress * 255f));
        try {
            context.frame().draw().rect(queueBounds.x(), queueBounds.y(), queueBounds.width(), queueBounds.height(),
                    0xA00E1013);
            context.frame().draw().rect(queueBounds.x(), queueBounds.y(), 1f, queueBounds.height(), ClickGuiTheme.STROKE);
            context.medium("Queue", queueBounds.x() + 16f, queueBounds.y() + 15f, 14f, ClickGuiTheme.TEXT);
            PlaybackQueueState queue = manager.queueState();
            UiRect viewport = new UiRect(queueBounds.x(), queueBounds.y() + 47f, queueBounds.width(),
                    queueBounds.height() - 47f);
            float y = viewport.y() + queueScroll;
            int clip = context.clip(viewport, 0f);
            try {
                for (int index = 0; index < queue.songs().size(); index++) {
                    Music music = queue.songs().get(index);
                    UiRect row = new UiRect(queueBounds.x() + 8f, y, queueBounds.width() - 16f, 34f);
                    if (!row.intersects(viewport)) {
                        y += 37f;
                        continue;
                    }
                    if (index == queue.index() || row.contains(context.mouseX(), context.mouseY())) {
                        context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                                index == queue.index() ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
                    }
                    context.textIn(ellipsize(context, music.name(), row.width() - 24f),
                            new UiRect(row.x() + 8f, row.y(), row.width() - 16f, row.height()), 9.8f,
                            index == queue.index() ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
                    if (queueProgress > .85f) hits.add(new Hit(row, Action.PLAY_AT, index, viewport));
                    y += 37f;
                }
                maximumQueueScroll = Math.max(0f, queue.songs().size() * 37f - viewport.height());
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
        int columns = Math.clamp((int) (bounds.width() / 145f), 3, 5);
        float gap = 12f;
        float width = (bounds.width() - 36f - gap * (columns - 1)) / columns;
        float imageSize = Math.min(116f, width);
        float rowHeight = imageSize + 44f;
        for (int index = 0; index < count; index++) {
            int column = index % columns;
            int row = index / columns;
            float x = bounds.x() + 18f + column * (width + gap);
            float top = y + row * rowHeight;
            Playlist playlist = playlists.get(index);
            UiRect hit = new UiRect(x, top, width, rowHeight - 6f);
            if (!hit.intersects(bodyBounds)) continue;
            Artwork artwork = data.artwork(playlist, 256);
            UiRect cover = new UiRect(x, top, imageSize, imageSize);
            drawArtwork(context, artwork, "playlist:" + playlist.id(), cover, 4f, 255);
            float hover = hover(context, "playlist:" + playlist.id(), hit.contains(context.mouseX(), context.mouseY()));
            if (hover > .01f) context.frame().draw().outline(cover.x(), cover.y(), cover.width(), cover.height(),
                    4f, 1f, alpha(0xFFFFFFFF, Math.round(36f * hover)));
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10f,
                    ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10f, playlist.name(), width),
                    x, top + imageSize + 8f, ClickGuiTheme.TEXT);
            context.text(formatCount(playlist.playCount()) + " plays", x, top + imageSize + 25f,
                    8.7f, ClickGuiTheme.MUTED);
            hits.add(new Hit(hit, Action.OPEN_PLAYLIST, playlist, bodyBounds));
        }
        int rows = (count + columns - 1) / columns;
        return y + rows * rowHeight;
    }

    private float songList(UiContext context, UiRect bounds, float y, List<Music> songs) {
        for (int index = 0; index < songs.size(); index++) {
            Music music = songs.get(index);
            UiRect row = new UiRect(bounds.x() + 14f, y, bounds.width() - 28f, 37f);
            if (row.intersects(bodyBounds)) {
                hoverRow(context, row, "song:" + music.id());
                context.textIn(Integer.toString(index + 1), new UiRect(row.x() + 6f, row.y(), 26f, row.height()),
                        9f, ClickGuiTheme.MUTED);
                context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.5f, music.name(), row.width() * .43f),
                        row.x() + 34f, row.y() + 6f, ClickGuiTheme.TEXT);
                context.draw(NimbusFonts.CONTENT, 9f,
                        ellipsizeFont(context, NimbusFonts.CONTENT, 9f, music.artistsText(), row.width() * .3f),
                        row.x() + row.width() * .55f, row.y() + 9f, ClickGuiTheme.MUTED);
                context.text(time(music.durationMs() / 1000f), row.x() + row.width() - 45f,
                        row.y() + 9f, 9f, ClickGuiTheme.MUTED);
                hits.add(new Hit(row, Action.PLAY_SONG, new SongSelection(songs, index), bodyBounds));
            }
            y += 40f;
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
        if (bounds.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    bounds.width() * .5f, ClickGuiTheme.HOVER);
        }
        float size = Math.min(18f, bounds.width() - 10f);
        icon(context, svg, bounds.x() + (bounds.width() - size) * .5f,
                bounds.y() + (bounds.height() - size) * .5f, size, ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, action, null));
    }

    private float loading(UiContext context, UiRect bounds, float y) {
        context.text("Loading...", bounds.x() + 18f, y + 8f, 11f, ClickGuiTheme.MUTED);
        return y + 42f;
    }

    private float error(UiContext context, UiRect bounds, float y) {
        context.text(data.error().isBlank() ? "Content unavailable" : data.error(),
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
            case PLAY_CACHED -> {
                CachedSelection selection = (CachedSelection) hit.value();
                manager.playCached(selection.ids(), selection.index());
            }
            case DELETE_CACHED -> run(manager.removeCached((long) hit.value()), removed -> {
                data.refreshCached();
                show(removed ? "Cached song removed" : "Unable to remove cached song");
            });
            case PREVIOUS -> manager.previous();
            case NEXT -> manager.next();
            case TOGGLE_PLAYBACK -> manager.togglePause();
            case MODE -> cycleMode();
            case TOGGLE_QUEUE -> {
                queueOpen = !queueOpen;
                if (!queueOpen) queueScroll = 0f;
            }
            case PLAY_AT -> manager.playAt((int) hit.value());
            case SEEK -> { draggingSeek = true; updateSeek(mouseX); }
            case VOLUME -> { draggingVolume = true; updateVolume(mouseX); }
            case NOW_PLAYING -> navigate(ClickGuiSession.MusicRoute.NOW_PLAYING);
            case PREVIEW_COVER -> coverPreview = true;
            case CLOSE_PREVIEW -> coverPreview = false;
            case LIKE_CURRENT -> likeCurrent();
            case CACHE_CURRENT -> cacheCurrent();
            case INTELLIGENT -> {
                SmartSelection selection = (SmartSelection) hit.value();
                run(manager.playIntelligent(selection.songId(), selection.playlistId(), 50),
                        songs -> show("Smart queue ready: " + songs.size() + " songs"));
            }
            case QUALITY -> cycleQuality();
            case PREFETCH -> manager.prefetch(!manager.config().prefetch());
            case DYNAMIC_BACKGROUND -> manager.dynamicBackground(!manager.config().dynamicBackground());
            case RATE -> cycleRate();
            case CACHE_LIMIT -> cycleCacheLimit();
            case HOTKEY_PREVIOUS -> bindingHotkey = MusicConfig.Hotkey.PREVIOUS;
            case HOTKEY_NEXT -> bindingHotkey = MusicConfig.Hotkey.NEXT;
            case HOTKEY_PAUSE -> bindingHotkey = MusicConfig.Hotkey.PAUSE;
            case TRIM_CACHE -> run(manager.trimCache(), bytes -> show("Freed " + formatBytes(bytes)));
            case CLEAR_CACHE -> run(manager.clearCache(), bytes -> {
                data.refreshCached();
                show("Freed " + formatBytes(bytes));
            });
            case LOGIN_QR -> loginTab = LoginTab.QR;
            case LOGIN_SMS -> {
                loginTab = LoginTab.SMS;
                manager.cancelLogin();
                qrImage = new byte[0];
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
            return;
        }
        if (draggingSeek) updateSeek(x);
        if (draggingVolume) updateVolume(x);
    }

    @Override
    public void mouseReleased() {
        draggingSeek = false;
        draggingVolume = false;
    }

    @Override
    public boolean mouseScrolled(float x, float y, double delta) {
        if (queueOpen && queueBounds != null && queueBounds.contains(x, y)) {
            queueScroll = Math.clamp(queueScroll + (float) delta * 26f, -maximumQueueScroll, 0f);
            return true;
        }
        if (sidebarBounds != null && sidebarBounds.contains(x, y)) {
            sidebarScroll = Math.clamp(sidebarScroll + (float) delta * 24f, -maximumSidebarScroll, 0f);
            return true;
        }
        if (bodyBounds != null && bodyBounds.contains(x, y)) {
            contentScroll = Math.clamp(contentScroll + (float) delta * 28f, -maximumContentScroll, 0f);
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
        qrImage = new byte[0];
        qrRequesting = false;
        qrChecking = false;
        queueOpen = false;
        queueScroll = 0f;
        coverPreview = false;
        bindingHotkey = null;
        contentScroll = 0f;
        sidebarScroll = 0f;
        encoded.clear();
        hovers.clear();
        imageFades.clear();
        message = "";
    }

    private void navigate(ClickGuiSession.MusicRoute route) {
        if (session.musicRoute() == ClickGuiSession.MusicRoute.LOGIN && route != ClickGuiSession.MusicRoute.LOGIN) {
            manager.cancelLogin();
            qrImage = new byte[0];
            qrRequesting = false;
            qrChecking = false;
        }
        session.musicRoute(route);
        contentScroll = 0f;
        routeProgress = 0f;
        data.load(route);
    }

    private void openPlaylist(Playlist playlist) {
        session.musicRoute(ClickGuiSession.MusicRoute.PLAYLIST);
        contentScroll = 0f;
        routeProgress = 0f;
        data.playlist(playlist);
    }

    private void startSearch() {
        String query = search.value().strip();
        if (query.isEmpty()) return;
        session.musicRoute(ClickGuiSession.MusicRoute.SEARCH);
        contentScroll = 0f;
        routeProgress = 0f;
        data.search(query);
    }

    private void cycleMode() {
        PlayMode[] values = PlayMode.values();
        manager.mode(values[(manager.config().playMode().ordinal() + 1) % values.length]);
    }

    private void cycleQuality() {
        Quality[] values = Quality.values();
        manager.quality(values[(manager.config().quality().ordinal() + 1) % values.length]);
    }

    private void cycleRate() {
        float value = manager.config().rate();
        manager.rate(value < .99f ? 1f : value < 1.24f ? 1.25f : value < 1.49f ? 1.5f : .75f);
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
        manager.volume(Math.clamp((mouseX - volumeBounds.x()) / volumeBounds.width(), 0f, 1f));
    }

    private void likeCurrent() {
        Music music = manager.state().music();
        if (music == null) return;
        boolean liked = !manager.likedIds().contains(music.id());
        run(manager.like(music.id(), liked), success -> show(success ? (liked ? "Added to liked music" : "Removed from liked music")
                : "Unable to update liked music"));
    }

    private void cacheCurrent() {
        Music music = manager.state().music();
        if (music == null) return;
        run(manager.cache(music), cached -> {
            data.refreshCached();
            show("Cached " + cached.music().name());
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
            qrImage = bytes == null ? new byte[0] : bytes;
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
                qrImage = new byte[0];
                show("QR code expired");
            } else if (status.code() == 802) show("Confirm sign in on your phone");
            else if (status.code() == 803) {
                show("Signed in");
                navigate(ClickGuiSession.MusicRoute.RECOMMENDED);
            }
        }));
    }

    private void sendSms() {
        run(manager.requestSmsLogin(phone.value(), country.value()), status ->
                show(status.successful() ? "Verification code sent" : status.message()));
    }

    private void completeSms() {
        run(manager.completeSmsLogin(captcha.value()), status -> {
            if (status.successful()) {
                show("Signed in");
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

    private static int activeLine(List<TimedLine> lines, int now) {
        int active = 0;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).timeMs() > now) break;
            active = index;
        }
        return active;
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
    private static String formatRate(float rate) { return String.format(java.util.Locale.ROOT, "%.2fx", rate); }
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
        if (keys.length == 0) return "Unbound";
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
    private static String ellipsizeFont(UiContext context, String font, float size, String value, float width) {
        if (value == null) return "";
        if (context.width(font, size, value) <= width) return value;
        String result = value;
        while (!result.isEmpty() && context.width(font, size, result + "...") > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }
    private static void icon(UiContext context, String svg, float x, float y, float size, int color) {
        try (Paint paint = new Paint().setAntiAlias(true).setColor(color)) {
            context.frame().draw().svg(svg, x, y, size, paint);
        }
    }
    private static void main(Runnable action) { Minecraft.getInstance().execute(action); }
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
        NAVIGATE, OPEN_PLAYLIST, PLAY_SONG, PLAY_CACHED, DELETE_CACHED,
        PREVIOUS, NEXT, TOGGLE_PLAYBACK, MODE, TOGGLE_QUEUE, PLAY_AT, SEEK, VOLUME,
        NOW_PLAYING, PREVIEW_COVER, CLOSE_PREVIEW, LIKE_CURRENT, CACHE_CURRENT, INTELLIGENT,
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
    private record SmartSelection(long songId, long playlistId) { }
}
