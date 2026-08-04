package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.features.music.MusicConfig;
import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.lyrics.TimedLine;
import cc.nimbus.features.music.lyrics.TimedWord;
import cc.nimbus.features.music.lyrics.WordLine;
import cc.nimbus.features.music.model.Album;
import cc.nimbus.features.music.model.Artist;
import cc.nimbus.features.music.model.ArtistDetails;
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
import cc.nimbus.ui.clickgui.tool.music.coverflow.CoverFlowLayout;
import cc.nimbus.ui.clickgui.tool.music.coverflow.CoverFlowState;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MusicTool implements ToolPage {
    private static final float SIDEBAR_WIDTH = 194f;
    private static final float PLAYER_HEIGHT = 64f;
    private static final float HEADER_HEIGHT = 58f;
    private static final float LYRIC_ROW_HEIGHT = 88f;
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
    private final Map<String, List<Music>> coverFlowTracks = new LinkedHashMap<>(8, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Music>> eldest) {
            return size() > 24;
        }
    };
    private final Set<String> pendingCoverFlowTracks = new HashSet<>();
    private final CoverFlowState coverFlow = new CoverFlowState();
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
    private UiRect lyricsViewport;
    private UiRect lyricScrollTrack;
    private UiRect lyricScrollThumb;
    private UiRect coverFlowBounds;
    private float contentScroll;
    private float targetContentScroll;
    private float contentScrollVelocity;
    private float maximumContentScroll;
    private float sidebarScroll;
    private float targetSidebarScroll;
    private float sidebarScrollVelocity;
    private float maximumSidebarScroll;
    private float queueScroll;
    private float targetQueueScroll;
    private float queueScrollVelocity;
    private float maximumQueueScroll;
    private float routeProgress;
    private float routeDirection = 1f;
    private float coverBlend;
    private int artworkDirection = 1;
    private int flippedCoverFlowIndex = -1;
    private float coverFlowFlipProgress;
    private boolean coverFlowFlipped;
    private float queueProgress;
    private float previewProgress;
    private float translationProgress = 1f;
    private float artistSongsProgress;
    private float lyricPosition = Float.NaN;
    private float lyricVelocity;
    private float lyricScroll;
    private float lyricScrollVelocity;
    private float targetLyricScroll;
    private int lyricLineCount;
    private boolean queueOpen;
    private boolean coverPreview;
    private boolean translationsEnabled = true;
    private boolean artistSongsExpanded;
    private boolean draggingSeek;
    private boolean draggingVolume;
    private boolean draggingLyrics;
    private CoverFlowCard pressedCoverFlowCard;
    private CoverFlowKind coverFlowKind = CoverFlowKind.PLAYLISTS;
    private float coverFlowItemSpacing = 1f;
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
    private final Deque<RouteEntry> routeHistory = new ArrayDeque<>();
    private long marqueeSongId = -1L;
    private long marqueeStartedAt;
    private long lyricFollowAt;

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
            lyricPosition = Float.NaN;
            lyricVelocity = 0f;
            lyricScroll = 0f;
            lyricScrollVelocity = 0f;
            targetLyricScroll = 0f;
            lyricFollowAt = 0L;
        }
        data.observePlayback(playback);
        hits.clear();
        float speed = 1f - (float) Math.exp(-context.deltaSeconds() * 14f);
        float scrollDt = Math.min(context.deltaSeconds(), 1f / 30f);
        contentScrollVelocity += ((targetContentScroll - contentScroll) * 125f
                - contentScrollVelocity * 22f) * scrollDt;
        contentScroll += contentScrollVelocity * scrollDt;
        sidebarScrollVelocity += ((targetSidebarScroll - sidebarScroll) * 125f
                - sidebarScrollVelocity * 22f) * scrollDt;
        sidebarScroll += sidebarScrollVelocity * scrollDt;
        queueScrollVelocity += ((targetQueueScroll - queueScroll) * 135f
                - queueScrollVelocity * 23f) * scrollDt;
        queueScroll += queueScrollVelocity * scrollDt;
        routeProgress = animate(routeProgress, 1f, speed);
        coverBlend = data.currentArtwork() == null ? 0f : animate(coverBlend, 1f, speed * .72f);
        int selectedCover = coverFlowItemCount() == 0 ? -1 : coverFlow.selectedIndex(coverFlowItemCount());
        float flipTarget = coverFlowFlipped && flippedCoverFlowIndex == selectedCover && selectedCover >= 0 ? 1f : 0f;
        coverFlowFlipProgress = animate(coverFlowFlipProgress, flipTarget, speed * .9f);
        if (!coverFlowFlipped && coverFlowFlipProgress <= .001f) flippedCoverFlowIndex = -1;
        queueProgress = animate(queueProgress, queueOpen ? 1f : 0f, speed);
        previewProgress = animate(previewProgress, coverPreview ? 1f : 0f, speed);
        translationProgress = animate(translationProgress, translationsEnabled ? 1f : 0f, speed);
        artistSongsProgress = animate(artistSongsProgress, artistSongsExpanded ? 1f : 0f, speed * .82f);

        boolean immersive = immersiveRoute(session.musicRoute());
        sidebarBounds = immersive ? null
                : new UiRect(bounds.x(), bounds.y(), SIDEBAR_WIDTH, bounds.height() - PLAYER_HEIGHT);
        bodyBounds = immersive
                ? bounds
                : new UiRect(bounds.x() + SIDEBAR_WIDTH, bounds.y() + HEADER_HEIGHT,
                bounds.width() - SIDEBAR_WIDTH, bounds.height() - HEADER_HEIGHT - PLAYER_HEIGHT);
        lyricsViewport = null;
        lyricScrollTrack = null;
        lyricScrollThumb = null;
        lyricLineCount = 0;
        coverFlowBounds = null;
        seekBounds = null;
        volumeBounds = null;
        queueBounds = queueProgress > .005f ? queueRect(bounds) : null;
        boolean pointerInPlayer = !immersive && context.mouseY() >= bounds.y() + bounds.height() - PLAYER_HEIGHT;
        UiContext contentContext = pointerInPlayer || queueBounds != null
                && queueBounds.contains(context.mouseX(), context.mouseY()) ? context.withoutPointer() : context;
        if (!immersive) {
            contentContext.frame().draw().rect(bounds.x() + SIDEBAR_WIDTH, bounds.y(), 1f,
                    bounds.height() - PLAYER_HEIGHT, ClickGuiTheme.STROKE);
            renderSidebar(contentContext, bounds);
            renderHeader(contentContext, bounds);
        }
        renderRoute(contentContext, playback);
        if (!immersive) {
            contentContext.frame().draw().rect(bounds.x(), bounds.y() + bounds.height() - PLAYER_HEIGHT,
                    bounds.width(), 1f, ClickGuiTheme.STROKE);
            renderPlayer(context, bounds, playback);
        }
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
            icon(context, ClickGuiIcons.MUSIC_USER, avatar.x() + 6f, avatar.y() + 6f, 16f, ClickGuiTheme.MUTED);
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
                    ClickGuiIcons.MUSIC_RECOMMENDED, ClickGuiSession.MusicRoute.RECOMMENDED, y);
            y = nav(context, context.translate("nimbus.music.featured", "Featured"),
                    ClickGuiIcons.MUSIC_FEATURED, ClickGuiSession.MusicRoute.FEATURED, y);
            y = nav(context, context.translate("nimbus.music.search", "Search"),
                    ClickGuiIcons.MUSIC_SEARCH, ClickGuiSession.MusicRoute.SEARCH, y);
            y += 8f;
            y = group(context, context.translate("nimbus.music.group.my_music", "MY MUSIC:"), y);
            y = nav(context, context.translate("nimbus.music.liked", "Liked Music"),
                    ClickGuiIcons.MUSIC_LIKED, ClickGuiSession.MusicRoute.LIKED, y);
            y = nav(context, context.translate("nimbus.music.cached", "Cached Songs"),
                    ClickGuiIcons.MUSIC_CACHED, ClickGuiSession.MusicRoute.CACHED, y);
            y += 8f;

            y = group(context, context.translate("nimbus.music.group.playlists", "PLAYLISTS:"), y);
            y = nav(context, context.translate("nimbus.music.cover_flow", "Cover Flow"),
                    ClickGuiIcons.MUSIC_COVER_FLOW, ClickGuiSession.MusicRoute.COVER_FLOW, y);

            if (profile != null) {
                List<Playlist> created = manager.accountPlaylists().stream()
                        .filter(playlist -> playlist.creator() != null && playlist.creator().id() == profile.id())
                        .toList();
                List<Playlist> subscribed = manager.accountPlaylists().stream()
                        .filter(Playlist::subscribed).toList();
                if (!created.isEmpty()) {
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
        boolean active = session.musicRoute() == route
                || route == ClickGuiSession.MusicRoute.COVER_FLOW
                && session.musicRoute() == ClickGuiSession.MusicRoute.ALBUM;
        boolean hover = row.contains(context.mouseX(), context.mouseY());
        if (active || hover) context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                active ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
        icon(context, icon, row.x() + 6f, row.y() + 6f, 16f, active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
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
        icon(context, ClickGuiIcons.MUSIC_SEARCH, searchBounds.x() + searchBounds.width() - 22f,
                searchBounds.y() + 7f, 14f, ClickGuiTheme.MUTED);
    }

    private void renderRoute(UiContext context, PlaybackState playback) {
        float eased = smoothStep(routeProgress);
        float offset = (1f - eased) * (routeDirection >= 0f ? 16f : -12f);
        UiRect shifted = new UiRect(bodyBounds.x(), bodyBounds.y() + offset,
                bodyBounds.width(), bodyBounds.height());
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(bodyBounds.x(), bodyBounds.y(), bodyBounds.width(), bodyBounds.height()),
                Math.round(eased * 255f));
        int clip = context.clip(bodyBounds, 0f);
        try {
            float y = shifted.y() + 12f + contentScroll;
            float end = switch (session.musicRoute()) {
                case RECOMMENDED -> renderRecommended(context, shifted, y);
                case FEATURED -> renderFeatured(context, shifted, y);
                case COVER_FLOW -> renderCoverFlow(context, shifted, y);
                case SEARCH -> renderSearch(context, shifted, y);
                case LIKED -> renderSongsPage(context, shifted, y,
                        context.translate("nimbus.music.liked", "Liked Music"), data.songs());
                case CACHED -> renderCached(context, shifted, y);
                case PLAYLIST -> renderPlaylist(context, shifted, y);
                case ALBUM -> renderAlbum(context, shifted, y);
                case ARTIST -> renderArtist(context, shifted, y);
                case NOW_PLAYING -> renderNowPlaying(context, shifted, playback);
                case LOGIN -> renderLogin(context, shifted, y);
            };
            maximumContentScroll = session.musicRoute() == ClickGuiSession.MusicRoute.NOW_PLAYING
                    || session.musicRoute() == ClickGuiSession.MusicRoute.COVER_FLOW
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
                    ClickGuiIcons.PLAYER_PLAY, Action.PLAY_ALL, bodyBounds);
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

    private float renderCoverFlow(UiContext context, UiRect bounds, float y) {
        context.frame().draw().rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xE20B0C0F);
        float headingY = y;
        y = heading(context, context.translate("nimbus.music.cover_flow", "Cover Flow"),
                context.translate("nimbus.music.cover_flow.subtitle", "Browse collections visually"), bounds, y);
        float tabsWidth = 176f;
        UiRect tabs = new UiRect(bounds.x() + bounds.width() - tabsWidth - 18f,
                headingY + 3f, tabsWidth, 30f);
        context.frame().draw().roundedRect(tabs.x(), tabs.y(), tabs.width(), tabs.height(), 5f,
                0x16000000);
        UiRect playlistsTab = new UiRect(tabs.x() + 3f, tabs.y() + 3f, 84f, 24f);
        UiRect albumsTab = new UiRect(tabs.x() + 89f, tabs.y() + 3f, 84f, 24f);
        coverFlowTab(context, playlistsTab,
                context.translate("nimbus.music.playlists", "Playlists"),
                coverFlowKind == CoverFlowKind.PLAYLISTS, Action.COVER_PLAYLISTS);
        coverFlowTab(context, albumsTab,
                context.translate("nimbus.music.albums", "Albums"),
                coverFlowKind == CoverFlowKind.ALBUMS, Action.COVER_ALBUMS);

        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        List<?> items = coverFlowKind == CoverFlowKind.PLAYLISTS ? data.playlists() : data.albums();
        if (items.isEmpty()) {
            context.centered(context.translate("nimbus.music.no_collections", "No collections available"),
                    new UiRect(bounds.x(), y, bounds.width(), 80f), 11f, ClickGuiTheme.MUTED);
            return y + 80f;
        }

        coverFlowBounds = new UiRect(bounds.x() + 18f, y - 8f,
                bounds.width() - 36f, Math.max(1f, bounds.y() + bounds.height() - y - 8f));
        coverFlow.update(context.deltaSeconds(), items.size());
        CoverFlowLayout.Result layout = CoverFlowLayout.calculate(
                coverFlowBounds, items.size(), coverFlow.position());
        coverFlowItemSpacing = layout.itemSpacing();
        int selected = coverFlow.selectedIndex(items.size());
        for (CoverFlowLayout.Slot slot : layout.slots()) {
            Object value = items.get(slot.index());
            UiRect card = slot.bounds();
            CoverFlowCard hit = new CoverFlowCard(slot.index(), value);
            // Keep the card hit below the back-side controls in z-order.
            hits.add(new Hit(card, Action.COVER_ITEM, hit, bodyBounds));
            context.frame().draw().roundedRect(card.x() + 2f, card.y() + 5f,
                    card.width(), card.height(), 7f, alpha(0xFF000000, Math.round(slot.prominence() * 72f)));
            Artwork artwork;
            String key;
            if (value instanceof Playlist playlist) {
                artwork = data.artwork(playlist, 768);
                key = "cover-flow-playlist:" + playlist.id();
            } else {
                Album album = (Album) value;
                artwork = data.artwork(album, 768);
                key = "cover-flow-album:" + album.id();
            }
            float flip = slot.index() == flippedCoverFlowIndex ? coverFlowFlipProgress : 0f;
            if (flip >= .5f) {
                drawCoverFlowBack(context, card, value, flip);
            } else {
                UiRect front = flip <= .001f ? card
                        : scaleAroundCenter(card, Math.max(.02f, 1f - flip * 2f), 1f);
                drawArtworkReflection(context, artwork, key, front, slot.prominence());
                drawArtwork(context, artwork, key, front, 6f, slot.alpha());
            }
            boolean hover = card.contains(context.mouseX(), context.mouseY());
            if (slot.index() == selected || hover) {
                int outlineAlpha = slot.index() == selected ? 82 : 42;
                context.frame().draw().outline(card.x(), card.y(), card.width(), card.height(),
                        6f, 1f, alpha(0xFFFFFFFF, outlineAlpha));
            }
        }

        Object current = items.get(selected);
        String title;
        String subtitle;
        if (current instanceof Playlist playlist) {
            title = playlist.name();
            subtitle = playlist.trackCount() + " "
                    + context.translate("nimbus.music.songs", "Songs") + "  /  "
                    + formatCount(playlist.playCount()) + " "
                    + context.translate("nimbus.music.plays", "plays");
        } else {
            Album album = (Album) current;
            title = album.name();
            subtitle = album.artistsText() + "  /  " + album.trackCount() + " "
                    + context.translate("nimbus.music.songs", "Songs");
        }
        float metadataY = coverFlowBounds.y() + coverFlowBounds.height() - 64f;
        String visibleTitle = ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 17f,
                title, coverFlowBounds.width() * .62f);
        context.center(NimbusFonts.CONTENT_MEDIUM, 17f, visibleTitle,
                coverFlowBounds.x() + coverFlowBounds.width() * .5f, metadataY, ClickGuiTheme.TEXT);
        context.center(NimbusFonts.BODY_MEDIUM, 9.8f, subtitle,
                coverFlowBounds.x() + coverFlowBounds.width() * .5f, metadataY + 28f, ClickGuiTheme.MUTED);

        if (items.size() > 1) {
            float controlY = coverFlowBounds.y() + coverFlowBounds.height() * .43f - 16f;
            UiRect previous = new UiRect(coverFlowBounds.x() + 8f, controlY, 32f, 32f);
            UiRect next = new UiRect(coverFlowBounds.x() + coverFlowBounds.width() - 40f,
                    controlY, 32f, 32f);
            coverFlowControl(context, previous, ClickGuiIcons.CHEVRON_LEFT, Action.COVER_PREVIOUS);
            coverFlowControl(context, next, ClickGuiIcons.CHEVRON_RIGHT, Action.COVER_NEXT);
        }
        return bounds.y() + bounds.height();
    }

    private void drawArtworkReflection(UiContext context, Artwork artwork, String key,
                                       UiRect cover, float prominence) {
        if (artwork == null || prominence <= .05f) return;
        Image image = image(context, artwork, key);
        if (image == null) return;
        float gap = 4f;
        float height = cover.height() * .28f;
        UiRect reflection = new UiRect(cover.x(), cover.y() + cover.height() + gap, cover.width(), height);
        int outerClip = context.clip(reflection, 0f);
        int transform = context.frame().canvas().save();
        try (Paint reflectionPaint = new Paint().setAntiAlias(true)
                .setAlpha(Math.round(52f * prominence))) {
            float axis = cover.y() + cover.height() + gap * .5f;
            context.frame().canvas().translate(0f, axis * 2f);
            context.frame().canvas().scale(1f, -1f);
            context.frame().draw().image(image, cover.x(), cover.y(),
                    cover.width(), cover.height(), reflectionPaint);
        } finally {
            context.frame().canvas().restoreToCount(transform);
            context.restore(outerClip);
        }
        int fadeClip = context.clip(reflection, 0f);
        try (Paint fade = new Paint().setAntiAlias(true)) {
            fade.setShader(context.frame().effects().linearGradient(
                    new Point(reflection.x(), reflection.y()),
                    new Point(reflection.x(), reflection.y() + reflection.height()),
                    new int[]{0x160B0C0F, 0xF20B0C0F}));
            context.frame().draw().rect(reflection.x(), reflection.y(), reflection.width(), reflection.height(), fade);
        } finally {
            context.restore(fadeClip);
        }
    }

    private void drawCoverFlowBack(UiContext context, UiRect card, Object value, float progress) {
        float scale = Math.max(.02f, progress * 2f - 1f);
        UiRect back = scaleAroundCenter(card, scale, 1f);
        int backClip = context.clip(back, 7f);
        try (Paint background = new Paint().setAntiAlias(true)) {
            background.setShader(context.frame().effects().linearGradient(
                    new Point(card.x(), card.y()), new Point(card.x(), card.y() + card.height()),
                    new int[]{0xF522252B, 0xFA0D0F12}));
            context.frame().draw().rect(back.x(), back.y(), back.width(), back.height(), background);
        } finally {
            context.restore(backClip);
        }
        context.frame().draw().outline(back.x(), back.y(), back.width(), back.height(), 7f, 1f, 0x24FFFFFF);

        float reveal = smoothStep(Math.clamp((scale - .28f) / .72f, 0f, 1f));
        if (reveal <= .001f) return;
        int clip = context.clip(back, 7f);
        int layer = context.frame().canvas().saveLayerAlpha(
                Rect.makeXYWH(card.x(), card.y(), card.width(), card.height()), Math.round(reveal * 255f));
        try {
            float padding = 16f;
            String title;
            String detail;
            String kind;
            if (value instanceof Playlist playlist) {
                title = playlist.name();
                detail = playlist.trackCount() + " " + tr("nimbus.music.songs", "Songs");
                kind = tr("nimbus.music.playlist", "PLAYLIST");
            } else {
                Album album = (Album) value;
                title = album.name();
                detail = album.artistsText() + "  /  " + album.trackCount() + " "
                        + tr("nimbus.music.songs", "Songs");
                kind = tr("nimbus.music.album", "ALBUM");
            }
            float textWidth = card.width() - padding * 2f;
            context.draw(NimbusFonts.BODY_MEDIUM, 8.2f, kind,
                    card.x() + padding, card.y() + 13f, 0x78FFFFFF);
            context.draw(NimbusFonts.CONTENT_MEDIUM, 13.5f,
                    ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 13.5f, title, textWidth),
                    card.x() + padding, card.y() + 31f, ClickGuiTheme.TEXT);
            context.draw(NimbusFonts.BODY_MEDIUM, 8.8f,
                    ellipsizeFont(context, NimbusFonts.BODY_MEDIUM, 8.8f, detail, textWidth),
                    card.x() + padding, card.y() + 54f, 0x94FFFFFF);

            float dividerY = card.y() + 73f;
            context.frame().draw().rect(card.x() + padding, dividerY,
                    card.width() - padding * 2f, 1f, 0x20FFFFFF);
            drawCoverFlowTracks(context, card, value, dividerY + 6f, padding, reveal);

            float footerY = card.y() + card.height() - 47f;
            context.frame().draw().rect(card.x() + padding, footerY,
                    card.width() - padding * 2f, 1f, 0x20FFFFFF);
            UiRect play = new UiRect(card.x() + padding, footerY + 8f, 82f, 30f);
            UiRect open = new UiRect(card.x() + card.width() - padding - 78f, footerY + 8f, 78f, 30f);
            coverFlowBackButton(context, play, ClickGuiIcons.PLAYER_PLAY,
                    tr("nimbus.music.play", "Play"), true, Action.COVER_PLAY, value, reveal);
            coverFlowBackButton(context, open, ClickGuiIcons.OPEN_IN_NEW,
                    tr("nimbus.music.open", "Open"), false, Action.COVER_OPEN, value, reveal);
        } finally {
            context.frame().canvas().restoreToCount(layer);
            context.restore(clip);
        }
    }

    private void drawCoverFlowTracks(UiContext context, UiRect card, Object value,
                                     float y, float padding, float reveal) {
        String key = coverFlowCollectionKey(value);
        List<Music> tracks = coverFlowTracks.get(key);
        float bottom = card.y() + card.height() - 48f;
        if (tracks == null) {
            String label = pendingCoverFlowTracks.contains(key)
                    ? tr("nimbus.ui.loading", "Loading...")
                    : tr("nimbus.music.no_songs", "No songs");
            context.center(NimbusFonts.BODY_MEDIUM, 9f, label,
                    card.x() + card.width() * .5f, y + 14f, ClickGuiTheme.MUTED);
            return;
        }
        int visible = Math.min(tracks.size(), Math.max(1, (int) ((bottom - y) / 24f)));
        float width = card.width() - padding * 2f;
        for (int index = 0; index < visible; index++) {
            Music music = tracks.get(index);
            UiRect row = new UiRect(card.x() + padding, y + index * 24f, width, 22f);
            boolean hover = reveal > .94f && row.contains(context.mouseX(), context.mouseY());
            if (hover) context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                    0x16FFFFFF);
            String number = Integer.toString(index + 1);
            drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 8.5f, number,
                    row.x() + 9f - context.width(NimbusFonts.BODY_MEDIUM, 8.5f, number) * .5f,
                    row.y(), row.height(), ClickGuiTheme.MUTED);
            drawCenteredY(context, NimbusFonts.CONTENT_MEDIUM, 9.3f,
                    ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.3f,
                            music.name(), row.width() - 70f),
                    row.x() + 23f, row.y(), row.height(), hover ? ClickGuiTheme.TEXT : 0xD8FFFFFF);
            String duration = time(music.durationMs() / 1000f);
            drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 8.2f, duration,
                    row.x() + row.width() - context.width(NimbusFonts.BODY_MEDIUM, 8.2f, duration),
                    row.y(), row.height(), 0x78FFFFFF);
            if (reveal > .94f) hits.add(new Hit(row, Action.PLAY_SONG,
                    new SongSelection(tracks, index), bodyBounds));
        }
    }

    private void coverFlowBackButton(UiContext context, UiRect bounds, String svg, String label,
                                     boolean primary, Action action, Object value, float reveal) {
        boolean hover = reveal > .94f && bounds.contains(context.mouseX(), context.mouseY());
        float hoverProgress = hover(context, "cover-back:" + action + ':' + coverFlowCollectionKey(value), hover);
        int background = primary ? alpha(0xFFFFFFFF, Math.round(224f + hoverProgress * 31f))
                : alpha(0xFFFFFFFF, Math.round(hoverProgress * 16f));
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                primary ? 15f : 5f, background);
        float iconSize = 16f;
        float gap = 5f;
        float textWidth = context.width(NimbusFonts.BODY_MEDIUM, 9.2f, label);
        float x = Math.round(bounds.x() + (bounds.width() - iconSize - gap - textWidth) * .5f);
        int color = primary ? 0xFF111318 : ClickGuiTheme.TEXT;
        icon(context, svg, x, Math.round(bounds.y() + (bounds.height() - iconSize) * .5f),
                iconSize, color);
        drawCenteredY(context, NimbusFonts.BODY_MEDIUM, 9.2f, label,
                x + iconSize + gap, bounds.y(), bounds.height(), color);
        if (reveal > .94f) hits.add(new Hit(bounds, action, value, bodyBounds));
    }

    private static UiRect scaleAroundCenter(UiRect bounds, float xScale, float yScale) {
        float width = bounds.width() * xScale;
        float height = bounds.height() * yScale;
        return new UiRect(bounds.x() + (bounds.width() - width) * .5f,
                bounds.y() + (bounds.height() - height) * .5f, width, height);
    }

    private void coverFlowTab(UiContext context, UiRect bounds, String label,
                              boolean active, Action action) {
        if (active || bounds.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4f,
                    active ? context.theme().accent(0x2E) : ClickGuiTheme.HOVER);
        }
        context.visualCentered(label, bounds, 9.5f,
                active ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        hits.add(new Hit(bounds, action, null, bodyBounds));
    }

    private void coverFlowControl(UiContext context, UiRect bounds, String image, Action action) {
        if (bounds.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 16f,
                    0x1EFFFFFF);
        }
        icon(context, image, bounds.x() + 7f, bounds.y() + 7f, 18f, ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, action, null, bodyBounds));
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
                    ClickGuiIcons.PLAYER_PLAY, Action.PLAY_ALL, bodyBounds);
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
            icon(context, ClickGuiIcons.MUSIC_USER, avatar.x() + 7f, avatar.y() + 7f, 18f, ClickGuiTheme.MUTED);
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
            playAllButton(context, playAll);
        }
        y = cover.y() + cover.height() + 18f;
        if (data.loading()) return loading(context, bounds, y);
        return songList(context, bounds, y, data.songs());
    }

    private float renderAlbum(UiContext context, UiRect bounds, float y) {
        Album album = data.selectedAlbum();
        if (album == null) return error(context, bounds, y);
        Artwork artwork = data.artwork(album, 512);
        UiRect cover = new UiRect(bounds.x() + 18f, y, 156f, 156f);
        drawArtwork(context, artwork, "album:" + album.id(), cover, 4f, 255);
        float infoX = cover.x() + cover.width() + 18f;
        float infoWidth = bounds.x() + bounds.width() - infoX - 22f;
        context.draw(NimbusFonts.CONTENT_MEDIUM, 22f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 22f, album.name(), infoWidth),
                infoX, cover.y(), ClickGuiTheme.TEXT);
        context.draw(NimbusFonts.CONTENT_MEDIUM, 11f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 11f, album.artistsText(), infoWidth),
                infoX, cover.y() + 42f, ClickGuiTheme.TEXT);
        int trackCount = album.trackCount() > 0 ? album.trackCount() : data.songs().size();
        context.medium(trackCount + " " + context.translate("nimbus.music.songs", "Songs"),
                infoX, cover.y() + 68f, 10f, ClickGuiTheme.MUTED);
        context.draw(NimbusFonts.CONTENT_MEDIUM, 9.8f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 9.8f,
                        album.description().isBlank()
                                ? context.translate("nimbus.music.no_description", "No description")
                                : album.description(), infoWidth),
                infoX, cover.y() + 92f, ClickGuiTheme.MUTED);
        if (!data.loading() && !data.songs().isEmpty()) {
            UiRect playAll = new UiRect(infoX, cover.y() + 125f, 112f, 31f);
            playAllButton(context, playAll);
        }
        y = cover.y() + cover.height() + 18f;
        if (data.loading()) return loading(context, bounds, y);
        return songList(context, bounds, y, data.songs());
    }

    private float renderArtist(UiContext context, UiRect bounds, float y) {
        ArtistDetails details = data.selectedArtist();
        Artist artist = details == null ? null : details.artist();
        if (artist == null) return data.loading() ? loading(context, bounds, y) : error(context, bounds, y);
        UiRect portrait = new UiRect(bounds.x() + 18f, y, 156f, 156f);
        drawArtwork(context, data.artwork(artist, 512), "artist:" + artist.id(), portrait, 78f, 255);
        float infoX = portrait.x() + portrait.width() + 22f;
        float infoWidth = bounds.x() + bounds.width() - infoX - 22f;
        context.draw(NimbusFonts.CONTENT_MEDIUM, 24f,
                ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 24f, artist.name(), infoWidth),
                infoX, portrait.y() + 5f, ClickGuiTheme.TEXT);
        String counts = artist.musicCount() + " " + context.translate("nimbus.music.songs", "Songs")
                + "  /  " + artist.albumCount() + " " + context.translate("nimbus.music.albums", "Albums");
        context.medium(counts, infoX, portrait.y() + 47f, 10f, ClickGuiTheme.MUTED);
        String description = artist.description().isBlank()
                ? context.translate("nimbus.music.no_description", "No description") : artist.description();
        List<String> lines = wrap(context, NimbusFonts.CONTENT_MEDIUM, 9.8f, description, infoWidth, 3);
        float textY = portrait.y() + 72f;
        for (String line : lines) {
            context.draw(NimbusFonts.CONTENT_MEDIUM, 9.8f, line, infoX, textY, ClickGuiTheme.MUTED);
            textY += 17f;
        }
        if (!data.loading() && !details.popularSongs().isEmpty()) {
            playAllButton(context, new UiRect(infoX, portrait.y() + 125f, 116f, 31f));
        }
        y = portrait.y() + portrait.height() + 22f;
        if (data.loading()) return loading(context, bounds, y);
        if (!data.error().isBlank()) return error(context, bounds, y);
        if (!details.popularSongs().isEmpty()) {
            float songsHeader = y;
            y = section(context, context.translate("nimbus.music.popular_songs", "Popular Songs"), bounds, y);
            int compactCount = Math.min(8, details.popularSongs().size());
            if (details.popularSongs().size() > compactCount) {
                UiRect toggle = new UiRect(bounds.x() + bounds.width() - 48f, songsHeader - 5f, 30f, 30f);
                control(context, toggle, artistSongsExpanded ? ClickGuiIcons.EXPAND_LESS : ClickGuiIcons.EXPAND_MORE,
                        Action.TOGGLE_ARTIST_SONGS, bodyBounds);
            }
            y = songList(context, bounds, y, details.popularSongs().subList(0, compactCount));
            float extraHeight = Math.max(0f, (details.popularSongs().size() - compactCount) * 52f);
            float visibleExtra = extraHeight * artistSongsProgress;
            if (visibleExtra > .01f) {
                UiRect reveal = new UiRect(bounds.x(), y, bounds.width(), visibleExtra);
                int revealClip = context.clip(reveal, 0f);
                try {
                    songList(context, bounds, y, details.popularSongs().subList(
                            compactCount, details.popularSongs().size()), reveal);
                } finally {
                    context.restore(revealClip);
                }
            }
            y += visibleExtra + 12f;
        }
        if (!details.albums().isEmpty()) {
            y = section(context, context.translate("nimbus.music.albums", "Albums"), bounds, y);
            y = albumGrid(context, bounds, y, details.albums());
        }
        return y;
    }

    private float renderNowPlaying(UiContext context, UiRect bounds, PlaybackState playback) {
        renderDynamicBackground(context, bounds, data.currentArtwork());
        UiRect content = bounds;
        UiRect back = new UiRect(content.x() + 18f, content.y() + 16f, 32f, 32f);
        if (back.contains(context.mouseX(), context.mouseY())) {
            context.frame().draw().roundedRect(back.x(), back.y(), back.width(), back.height(), 4f,
                    0x20FFFFFF);
        }
        icon(context, ClickGuiIcons.PLAYER_BACK, back.x() + 6f, back.y() + 6f, 20f, ClickGuiTheme.TEXT);
        hits.add(new Hit(back, Action.BACK_FROM_NOW_PLAYING, null));

        Music music = playback.music();
        if (music == null) {
            context.centered(context.translate("nimbus.music.nothing_playing", "Nothing is playing"),
                    content, 12f, ClickGuiTheme.MUTED);
            return bounds.y() + bounds.height();
        }
        float leftWidth = content.width() * .43f;
        float availableLeft = leftWidth - 58f;
        float coverSize = Math.min(availableLeft, content.height() * .46f);
        float leftBlockHeight = coverSize + 153f;
        float coverY = content.y() + Math.max(54f, (content.height() - leftBlockHeight) * .5f);
        UiRect cover = new UiRect(content.x() + (leftWidth - coverSize) * .5f,
                coverY, coverSize, coverSize);
        drawCurrentArtwork(context, music, cover);
        hits.add(new Hit(cover, Action.PREVIEW_COVER, null));
        float titleY = cover.y() + cover.height() + 13f;
        UiRect titleBounds = new UiRect(cover.x(), titleY, cover.width() - 64f,
                context.height(NimbusFonts.CONTENT_MEDIUM, 18f) + 3f);
        drawMarquee(context, NimbusFonts.CONTENT_MEDIUM, 18f, music.name(), titleBounds, ClickGuiTheme.TEXT);
        float iconY = titleBounds.y() + (titleBounds.height() - 24f) * .5f + 2f;
        UiRect like = new UiRect(cover.x() + cover.width() - 55f, iconY, 24f, 24f);
        UiRect cache = new UiRect(cover.x() + cover.width() - 25f, iconY, 24f, 24f);
        icon(context, ClickGuiIcons.PLAYER_HEART, like.x() + 3f, like.y() + 3f, 18f,
                manager.likedIds().contains(music.id()) ? 0xFFFF5264 : 0xFFFFFFFF);
        icon(context, ClickGuiIcons.PLAYER_DOWNLOAD, cache.x() + 3f, cache.y() + 3f, 18f,
                manager.isCached(music.id()) ? 0xFF777D86 : 0xFFFFFFFF);
        hits.add(new Hit(like, Action.LIKE_CURRENT, null));
        hits.add(new Hit(cache, Action.CACHE_CURRENT, null));
        float artistsY = titleY + 30f;
        renderArtistLinks(context, music.artists(), cover.x(), artistsY, cover.width());

        float progressY = artistsY + 29f;
        seekBounds = new UiRect(cover.x(), progressY, cover.width(), 4f);
        float seekProgress = playback.durationSeconds() <= 0f ? 0f
                : playback.progressSeconds() / playback.durationSeconds();
        drawSlider(context, seekBounds, seekProgress);
        context.draw(NimbusFonts.BODY_MEDIUM, 8.5f, time(playback.progressSeconds()),
                seekBounds.x(), seekBounds.y() + 10f, 0xA8FFFFFF);
        String duration = time(playback.durationSeconds());
        context.draw(NimbusFonts.BODY_MEDIUM, 8.5f, duration,
                seekBounds.x() + seekBounds.width() - context.width(NimbusFonts.BODY_MEDIUM, 8.5f, duration),
                seekBounds.y() + 10f, 0xA8FFFFFF);
        hits.add(new Hit(new UiRect(seekBounds.x(), seekBounds.y() - 6f, seekBounds.width(), 16f),
                Action.SEEK, null));

        float controlsY = seekBounds.y() + 28f;
        float centerX = cover.x() + cover.width() * .5f;
        control(context, new UiRect(centerX - 70f, controlsY + 4f, 30f, 30f),
                ClickGuiIcons.PLAYER_PREVIOUS, Action.PREVIOUS);
        control(context, new UiRect(centerX - 20f, controlsY, 40f, 40f),
                playback.phase() == cc.nimbus.features.music.playback.PlaybackPhase.PLAYING
                        ? ClickGuiIcons.PLAYER_PAUSE : ClickGuiIcons.PLAYER_PLAY, Action.TOGGLE_PLAYBACK);
        control(context, new UiRect(centerX + 40f, controlsY + 4f, 30f, 30f),
                ClickGuiIcons.PLAYER_NEXT, Action.NEXT);

        float volumeY = Math.round(controlsY + 50f);
        icon(context, ClickGuiIcons.PLAYER_VOLUME, Math.round(cover.x()), volumeY - 9f, 20f, 0xC8FFFFFF);
        volumeBounds = new UiRect(Math.round(cover.x() + 25f), volumeY, Math.round(cover.width() - 25f), 2f);
        drawSlider(context, volumeBounds, playback.volume());
        hits.add(new Hit(new UiRect(volumeBounds.x(), volumeBounds.y() - 7f,
                volumeBounds.width(), 17f), Action.VOLUME, null));

        float topRight = content.x() + content.width() - 18f;
        UiRect queue = new UiRect(topRight - 30f, content.y() + 17f, 28f, 28f);
        UiRect mode = new UiRect(topRight - 68f, content.y() + 17f, 28f, 28f);
        control(context, mode, modeIcon(manager.config().playMode()), Action.MODE);
        control(context, queue, ClickGuiIcons.PLAYER_QUEUE, Action.TOGGLE_QUEUE);

        float dividerX = content.x() + leftWidth + 7f;
        context.frame().draw().rect(dividerX, content.y() + 58f, 1f,
                content.height() - 96f, 0x24FFFFFF);
        UiRect lyrics = new UiRect(content.x() + leftWidth + 24f, content.y() + 38f,
                content.width() - leftWidth - 54f, content.height() - 70f);
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
        if (!Float.isFinite(lyricPosition) || Math.abs(active - lyricPosition) > 3f) {
            lyricPosition = active;
            lyricVelocity = 0f;
        } else {
            float dt = Math.min(context.deltaSeconds(), 1f / 30f);
            lyricVelocity += ((active - lyricPosition) * 125f - lyricVelocity * 21f) * dt;
            lyricPosition += lyricVelocity * dt;
            if (Math.abs(active - lyricPosition) < .001f && Math.abs(lyricVelocity) < .01f) {
                lyricPosition = active;
                lyricVelocity = 0f;
            }
        }

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

        UiRect viewport = new UiRect(bounds.x(), bounds.y() + 6f, bounds.width(),
                bounds.height() - 6f - footerInset);
        lyricsViewport = viewport;
        lyricLineCount = lines.size();
        if (!draggingLyrics && context.nowMillis() >= lyricFollowAt) targetLyricScroll = 0f;
        clampLyricScroll();
        float scrollDt = Math.min(context.deltaSeconds(), 1f / 30f);
        lyricScrollVelocity += ((targetLyricScroll - lyricScroll) * 145f
                - lyricScrollVelocity * 23f) * scrollDt;
        lyricScroll += lyricScrollVelocity * scrollDt;
        clampLyricScroll();

        int clip = context.clip(viewport, 0f);
        float centerY = viewport.y() + viewport.height() * .48f;
        float translationAmount = lyrics.hasTranslations() ? translationProgress : 0f;
        float rowHeight = lerp(70f, LYRIC_ROW_HEIGHT, translationAmount);
        float viewPosition = lyricPosition - lyricScroll;
        int visibleRadius = Math.max(5, (int) Math.ceil(viewport.height() / rowHeight * .5f) + 2);
        int first = Math.max(0, (int) Math.floor(viewPosition) - visibleRadius);
        int last = Math.min(lines.size() - 1, (int) Math.ceil(viewPosition) + visibleRadius);
        int nextLineTime = active + 1 < lines.size() ? lines.get(active + 1).timeMs() : Integer.MAX_VALUE;
        WordLine wordLine = wordLineForLine(lyrics.words(), lines.get(active).timeMs(), nextLineTime);
        boolean hoveringLyrics = viewport.contains(context.mouseX(), context.mouseY());
        try {
            for (int index = first; index <= last; index++) {
                float distance = index - viewPosition;
                float emphasis = smoothStep(1f - Math.clamp(Math.abs(index - lyricPosition), 0f, 1f));
                float rowY = centerY + distance * rowHeight - rowHeight * .5f;
                UiRect row = new UiRect(viewport.x(), rowY, viewport.width() - 12f, rowHeight);
                if (!row.intersects(viewport)) continue;
                TimedLine line = lines.get(index);
                boolean hover = viewport.contains(context.mouseX(), context.mouseY())
                        && row.contains(context.mouseX(), context.mouseY());
                if (hover) context.frame().draw().roundedRect(row.x(), row.y() + 3f,
                        row.width(), row.height() - 6f, 5f, 0x14FFFFFF);
                int restingAlpha = Math.max(58, Math.round(170f - Math.abs(distance) * 23f));
                int lineAlpha = Math.round(lerp(restingAlpha, 255f, emphasis));
                float textX = row.x() + 10f;
                float textWidth = row.width() - 20f;
                boolean wordProgress = index == active && wordLine != null;
                String original = wordProgress && !wordLine.text().isBlank()
                        ? wordLine.text() : line.text();
                float size = fitTextSize(context, NimbusFonts.CONTENT_MEDIUM,
                        lerp(16f, 23f, emphasis), lerp(11.5f, 13f, emphasis), original, textWidth);
                boolean translated = !lyrics.translation(index).isBlank();
                float lineY = row.y() + (row.height() - context.height(NimbusFonts.CONTENT_MEDIUM, size)) * .5f
                        - (translated ? 9f * translationAmount : 0f);
                float wordOpacity = wordProgress ? emphasis : 0f;
                int normalAlpha = Math.round(lineAlpha * (1f - wordOpacity));
                String translation = lyrics.translation(index);
                float blurTarget = hoveringLyrics ? 0f
                        : Math.clamp(Math.abs(index - lyricPosition) * .72f - .18f, 0f, 1f);
                String blurKey = "lyric-blur:" + marqueeSongId + ':' + index;
                float blurMix = animate(hovers.getOrDefault(blurKey, 0f), blurTarget,
                        1f - (float) Math.exp(-context.deltaSeconds() * 7f));
                hovers.put(blurKey, blurMix);
                float sigma = blurMix * 2.6f;
                Paint blurPaint = null;
                int textLayer = -1;
                if (sigma > .04f) {
                    float margin = sigma * 3f;
                    blurPaint = new Paint().setAntiAlias(true).setImageFilter(
                            context.frame().effects().imageBlur(sigma, sigma, FilterTileMode.CLAMP));
                    textLayer = context.frame().canvas().saveLayer(Rect.makeXYWH(
                            row.x() - margin, row.y() - margin,
                            row.width() + margin * 2f, row.height() + margin * 2f), blurPaint);
                }
                try {
                    if (normalAlpha > 0) {
                        context.draw(NimbusFonts.CONTENT_MEDIUM, size, line.text(), textX, lineY,
                                alpha(ClickGuiTheme.TEXT, normalAlpha));
                    }
                    if (wordProgress && wordOpacity > .001f) {
                        drawWordProgress(context, wordLine, line.text(), textX, lineY,
                                textWidth, size, now, wordOpacity);
                    }
                    if (translated && translationAmount > .001f) {
                        float translatedSize = fitTextSize(context, NimbusFonts.CONTENT_MEDIUM,
                                lerp(10.5f, 12.5f, emphasis), 9f, translation, textWidth);
                        int translatedAlpha = Math.round(lerp(Math.max(48, lineAlpha - 58), 214f, emphasis)
                                * translationAmount);
                        context.draw(NimbusFonts.CONTENT_MEDIUM, translatedSize, translation,
                                textX, lineY + lerp(28f, 35f, emphasis),
                                alpha(0xFFE1E4E9, translatedAlpha));
                    }
                } finally {
                    if (textLayer >= 0) context.frame().canvas().restoreToCount(textLayer);
                    if (blurPaint != null) blurPaint.close();
                }
                hits.add(new Hit(row, Action.SEEK_LYRIC, line.timeMs(), viewport));
            }
        } finally {
            context.restore(clip);
        }

        if (lines.size() > 1) {
            lyricScrollTrack = new UiRect(viewport.x() + viewport.width() - 3f,
                    viewport.y() + 6f, 2f, viewport.height() - 12f);
            float visibleLines = Math.max(1f, viewport.height() / rowHeight);
            float thumbHeight = Math.max(22f,
                    lyricScrollTrack.height() * Math.min(1f, visibleLines / lines.size()));
            float progress = Math.clamp(viewPosition / (lines.size() - 1f), 0f, 1f);
            float thumbY = lyricScrollTrack.y() + (lyricScrollTrack.height() - thumbHeight) * progress;
            lyricScrollThumb = new UiRect(lyricScrollTrack.x() - 2f, thumbY, 4f, thumbHeight);
            int trackAlpha = lyricsViewport.contains(context.mouseX(), context.mouseY()) || draggingLyrics
                    ? 34 : 18;
            context.frame().draw().roundedRect(lyricScrollTrack.x(), lyricScrollTrack.y(),
                    lyricScrollTrack.width(), lyricScrollTrack.height(), 1f, alpha(0xFFFFFFFF, trackAlpha));
            context.frame().draw().roundedRect(lyricScrollThumb.x(), lyricScrollThumb.y(),
                    lyricScrollThumb.width(), lyricScrollThumb.height(), 2f,
                    alpha(0xFFFFFFFF, draggingLyrics ? 156 : 92));
            hits.add(new Hit(new UiRect(lyricScrollTrack.x() - 7f, lyricScrollTrack.y(),
                    10f, lyricScrollTrack.height()), Action.LYRIC_SCROLL, null, viewport));
        }
    }

    private void drawWordProgress(UiContext context, WordLine wordLine, String fallback, float x, float y,
                                  float width, float size, int now, float opacity) {
        String text = wordLine.text().isBlank() ? fallback : wordLine.text();
        context.draw(NimbusFonts.CONTENT_MEDIUM, size, text, x, y,
                alpha(0xFFFFFFFF, Math.round(120f * opacity)));
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
            context.draw(NimbusFonts.CONTENT_MEDIUM, size, text, x, y,
                    alpha(ClickGuiTheme.TEXT, Math.round(255f * opacity)));
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
        y = settingRow(context, bounds, y, context.translate("nimbus.music.quality", "Audio Quality"),
                config.quality().apiValue(), Action.QUALITY);
        y = settingRow(context, bounds, y, context.translate("nimbus.music.playback_rate", "Playback Rate"),
                formatRate(manager.state().rate()), Action.CYCLE_RATE);
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
        UiRect cover = new UiRect(bounds.x() + 13f, y + 11f, 42f, 42f);
        if (music != null) drawCurrentArtwork(context, music, cover);
        else context.frame().draw().roundedRect(cover.x(), cover.y(), cover.width(), cover.height(), 4f,
                IMAGE_PLACEHOLDER);
        if (music != null) {
            hits.add(new Hit(new UiRect(bounds.x() + 8f, y + 6f, 250f, 52f), Action.NOW_PLAYING, null));
            drawMarquee(context, NimbusFonts.CONTENT_MEDIUM, 11f, music.name(),
                    new UiRect(cover.x() + 52f, y + 13f, 180f,
                            context.height(NimbusFonts.CONTENT_MEDIUM, 11f) + 2f), ClickGuiTheme.TEXT);
            context.draw(NimbusFonts.CONTENT, 9f, ellipsizeFont(context,
                    NimbusFonts.CONTENT, 9f, music.artistsText(), 180f), cover.x() + 52f, y + 34f,
                    ClickGuiTheme.MUTED);
        } else {
            context.text(context.translate("nimbus.music.nothing_playing_short", "Nothing playing"),
                    cover.x() + 52f, y + 22f, 10.5f, ClickGuiTheme.MUTED);
        }

        float center = bounds.x() + bounds.width() * .5f;
        control(context, new UiRect(center - 63f, y + 18f, 28f, 28f), ClickGuiIcons.PLAYER_PREVIOUS, Action.PREVIOUS);
        control(context, new UiRect(center - 17f, y + 14f, 36f, 36f),
                playback.phase() == cc.nimbus.features.music.playback.PlaybackPhase.PLAYING
                        ? ClickGuiIcons.PLAYER_PAUSE : ClickGuiIcons.PLAYER_PLAY, Action.TOGGLE_PLAYBACK);
        control(context, new UiRect(center + 37f, y + 18f, 28f, 28f), ClickGuiIcons.PLAYER_NEXT, Action.NEXT);

        float right = bounds.x() + bounds.width() - 14f;
        UiRect queue = new UiRect(right - 27f, y + 19f, 25f, 25f);
        UiRect mode = new UiRect(right - 62f, y + 19f, 25f, 25f);
        icon(context, ClickGuiIcons.PLAYER_QUEUE, queue.x() + 4f, queue.y() + 4f, 17f,
                queueOpen ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
        String modeIcon = switch (manager.config().playMode()) {
            case SINGLE -> ClickGuiIcons.PLAYER_REPEAT_ONE;
            case LOOP_LIST -> ClickGuiIcons.PLAYER_REPEAT;
            case SHUFFLE -> ClickGuiIcons.PLAYER_SHUFFLE;
            case INTELLIGENCE -> ClickGuiIcons.SPARKLES;
        };
        icon(context, modeIcon, mode.x() + 5f, mode.y() + 5f, 15f, ClickGuiTheme.MUTED);
        hits.add(new Hit(queue, Action.TOGGLE_QUEUE, null));
        hits.add(new Hit(mode, Action.MODE, null));
    }

    private void renderQueue(UiContext context, UiRect bounds, PlaybackState playback) {
        queueBounds = queueRect(bounds);
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

    private UiRect queueRect(UiRect bounds) {
        float width = Math.min(300f, bounds.width() * .4f);
        float x = bounds.x() + bounds.width() - width + (1f - queueProgress) * width;
        boolean immersive = immersiveRoute(session.musicRoute());
        float topInset = immersive
                ? 0f : HEADER_HEIGHT;
        return new UiRect(x, bounds.y() + topInset,
                width, bounds.height() - topInset - (immersive ? 0f : PLAYER_HEIGHT));
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

    private float albumGrid(UiContext context, UiRect bounds, float y, List<Album> albums) {
        int columns = Math.clamp((int) (bounds.width() / 172f), 3, 5);
        float gap = 14f;
        float width = (bounds.width() - 36f - gap * (columns - 1)) / columns;
        float rowHeight = width + 50f;
        for (int index = 0; index < albums.size(); index++) {
            Album album = albums.get(index);
            int column = index % columns;
            int row = index / columns;
            float x = bounds.x() + 18f + column * (width + gap);
            float top = y + row * rowHeight;
            UiRect hit = new UiRect(x, top, width, rowHeight - 6f);
            if (!hit.intersects(bodyBounds)) continue;
            UiRect cover = new UiRect(x, top, width, width);
            drawArtwork(context, data.artwork(album, 512), "artist-album:" + album.id(), cover, 4f, 255);
            float hover = hover(context, "artist-album:" + album.id(),
                    hit.contains(context.mouseX(), context.mouseY()));
            if (hover > .01f) context.frame().draw().outline(cover.x(), cover.y(), cover.width(), cover.height(),
                    4f, 1f, alpha(0xFFFFFFFF, Math.round(36f * hover)));
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10.8f,
                    ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.8f, album.name(), width),
                    x, top + width + 8f, ClickGuiTheme.TEXT);
            context.draw(NimbusFonts.CONTENT, 9f,
                    ellipsizeFont(context, NimbusFonts.CONTENT, 9f, album.artistsText(), width),
                    x, top + width + 28f, ClickGuiTheme.MUTED);
            hits.add(new Hit(hit, Action.OPEN_ALBUM, album, bodyBounds));
        }
        int rows = (albums.size() + columns - 1) / columns;
        return y + rows * rowHeight;
    }

    private float songList(UiContext context, UiRect bounds, float y, List<Music> songs) {
        return songList(context, bounds, y, songs, bodyBounds);
    }

    private float songList(UiContext context, UiRect bounds, float y, List<Music> songs,
                           UiRect interactionBounds) {
        for (int index = 0; index < songs.size(); index++) {
            Music music = songs.get(index);
            UiRect row = new UiRect(bounds.x() + 14f, y, bounds.width() - 28f, 48f);
            if (row.intersects(interactionBounds)) {
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
                hits.add(new Hit(row, Action.PLAY_SONG,
                        new SongSelection(songs, index), interactionBounds));
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

    private void playAllButton(UiContext context, UiRect bounds) {
        boolean hovered = bounds.contains(context.mouseX(), context.mouseY());
        float hover = hover(context, "play-all:" + Math.round(bounds.x()) + ':' + Math.round(bounds.y()), hovered);
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                alpha(0xFFFFFFFF, Math.round(14f + 12f * hover)));
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        String label = context.translate("nimbus.music.play_all", "Play All");
        float iconSize = 19f;
        float gap = 6f;
        float textWidth = context.width(UiContext.FONT, 10f, label);
        float x = bounds.x() + (bounds.width() - iconSize - gap - textWidth) * .5f;
        icon(context, ClickGuiIcons.PLAYER_PLAY, x, bounds.y() + (bounds.height() - iconSize) * .5f,
                iconSize, ClickGuiTheme.TEXT);
        context.visualTextIn(label, new UiRect(x + iconSize + gap, bounds.y(), textWidth, bounds.height()),
                10f, ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, Action.PLAY_ALL, null));
    }

    private void renderArtistLinks(UiContext context, List<Artist> artists,
                                   float x, float y, float maximumWidth) {
        if (artists.isEmpty()) {
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f,
                    context.translate("nimbus.music.unknown_artist", "Unknown Artist"),
                    x, y, 0xC0FFFFFF);
            return;
        }
        float cursor = x;
        float right = x + maximumWidth;
        for (int index = 0; index < artists.size(); index++) {
            Artist artist = artists.get(index);
            String separator = index == artists.size() - 1 ? "" : ", ";
            float nameWidth = context.width(NimbusFonts.CONTENT_MEDIUM, 10.5f, artist.name());
            float separatorWidth = context.width(NimbusFonts.CONTENT_MEDIUM, 10.5f, separator);
            if (cursor + nameWidth > right) {
                context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f,
                        ellipsizeFont(context, NimbusFonts.CONTENT_MEDIUM, 10.5f,
                                artist.name(), Math.max(1f, right - cursor)),
                        cursor, y, 0xC0FFFFFF);
                break;
            }
            UiRect hit = new UiRect(cursor, y - 3f, nameWidth, 21f);
            int color = hit.contains(context.mouseX(), context.mouseY()) ? 0xFFFFFFFF : 0xC0FFFFFF;
            context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f, artist.name(), cursor, y, color);
            hits.add(new Hit(hit, Action.OPEN_ARTIST, artist));
            cursor += nameWidth;
            if (!separator.isEmpty() && cursor + separatorWidth <= right) {
                context.draw(NimbusFonts.CONTENT_MEDIUM, 10.5f, separator, cursor, y, 0x8FFFFFFF);
                cursor += separatorWidth;
            }
        }
    }

    private void control(UiContext context, UiRect bounds, String svg, Action action) {
        control(context, bounds, svg, action, null);
    }

    private void control(UiContext context, UiRect bounds, String svg, Action action, UiRect clip) {
        boolean hovered = bounds.contains(context.mouseX(), context.mouseY());
        float hover = hover(context, "control:" + action + ':' + Math.round(bounds.x()) + ':'
                + Math.round(bounds.y()), hovered);
        if (hover > .01f) {
            context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    bounds.width() * .5f, alpha(0xFFFFFFFF, Math.round(20f * hover)));
        }
        float baseSize = Math.min(20f, bounds.width() - 6f);
        float size = baseSize * (.94f + .06f * hover);
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
        float knobSize = 6f;
        float knobX = bounds.x() + bounds.width() * value - knobSize * .5f;
        float knobY = bounds.y() + bounds.height() * .5f - knobSize * .5f;
        context.frame().draw().roundedRect(knobX, knobY, knobSize, knobSize, knobSize * .5f, 0xFFFFFFFF);
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
            float progress = smoothStep(coverBlend);
            int clip = context.clip(bounds, 4f);
            try {
                UiRect outgoing = new UiRect(bounds.x() - artworkDirection * bounds.width() * progress,
                        bounds.y(), bounds.width(), bounds.height());
                drawArtwork(context, previous, "current-previous", outgoing, 4f,
                        Math.round(255f * (1f - progress * .22f)));
                if (current != null) {
                    UiRect incoming = new UiRect(bounds.x() + artworkDirection * bounds.width() * (1f - progress),
                            bounds.y(), bounds.width(), bounds.height());
                    drawArtwork(context, current, "current:" + music.id(), incoming, 4f, 255);
                }
            } finally {
                context.restore(clip);
            }
        } else if (current != null) {
            drawArtwork(context, current, "current:" + music.id(), bounds, 4f, 255);
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
        if (queueProgress > .005f && queueBounds != null && queueBounds.contains(x, y)) {
            for (int index = hits.size() - 1; index >= 0; index--) {
                Hit hit = hits.get(index);
                if (hit.action() != Action.PLAY_AT || !hit.bounds().contains(x, y)
                        || hit.clip() != null && !hit.clip().contains(x, y)) continue;
                act(hit, x, y);
                return true;
            }
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
            act(hit, x, y);
            return true;
        }
        focus.clear();
        return true;
    }

    private void act(Hit hit, float mouseX, float mouseY) {
        switch (hit.action()) {
            case NAVIGATE -> {
                ClickGuiSession.MusicRoute route = (ClickGuiSession.MusicRoute) hit.value();
                if (route == session.musicRoute()) break;
                routeHistory.clear();
                if (route == ClickGuiSession.MusicRoute.COVER_FLOW) routeHistory.push(currentEntry());
                transition(route, null, 1f);
            }
            case OPEN_PLAYLIST -> openPlaylist((Playlist) hit.value());
            case OPEN_ALBUM -> openAlbum((Album) hit.value());
            case OPEN_ARTIST -> openArtist((Artist) hit.value());
            case TOGGLE_ARTIST_SONGS -> artistSongsExpanded = !artistSongsExpanded;
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
            case PREVIOUS -> { artworkDirection = -1; manager.previous(); }
            case NEXT -> { artworkDirection = 1; manager.next(); }
            case TOGGLE_PLAYBACK -> manager.togglePause();
            case MODE -> cycleMode();
            case TOGGLE_QUEUE -> {
                queueOpen = !queueOpen;
                if (!queueOpen) {
                    queueScroll = 0f;
                    targetQueueScroll = 0f;
                    queueScrollVelocity = 0f;
                }
            }
            case PLAY_AT -> manager.playAt((int) hit.value());
            case SEEK -> { draggingSeek = true; updateSeek(mouseX); }
            case VOLUME -> { draggingVolume = true; updateVolume(mouseX); }
            case NOW_PLAYING -> navigate(ClickGuiSession.MusicRoute.NOW_PLAYING, null);
            case BACK_FROM_NOW_PLAYING -> back();
            case PREVIEW_COVER -> coverPreview = true;
            case CLOSE_PREVIEW -> coverPreview = false;
            case LIKE_CURRENT -> likeCurrent();
            case CACHE_CURRENT -> cacheCurrent();
            case SEEK_LYRIC -> {
                manager.seek((int) hit.value() / 1000d);
                lyricPosition = Float.NaN;
                lyricVelocity = 0f;
                lyricScroll = 0f;
                lyricScrollVelocity = 0f;
                targetLyricScroll = 0f;
                lyricFollowAt = 0L;
            }
            case LYRIC_SCROLL -> {
                draggingLyrics = true;
                updateLyricScroll(mouseY);
            }
            case COVER_PLAYLISTS -> {
                coverFlowKind = CoverFlowKind.PLAYLISTS;
                coverFlow.reset();
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
                routeProgress = 0f;
            }
            case COVER_ALBUMS -> {
                coverFlowKind = CoverFlowKind.ALBUMS;
                coverFlow.reset();
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
                routeProgress = 0f;
            }
            case COVER_PREVIOUS -> {
                coverFlow.select(coverFlow.selectedIndex(coverFlowItemCount()) - 1, coverFlowItemCount());
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
            }
            case COVER_NEXT -> {
                coverFlow.select(coverFlow.selectedIndex(coverFlowItemCount()) + 1, coverFlowItemCount());
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
            }
            case COVER_ITEM -> {
                pressedCoverFlowCard = (CoverFlowCard) hit.value();
                coverFlow.beginDrag(mouseX);
            }
            case COVER_PLAY -> playCoverFlowItem(hit.value());
            case COVER_OPEN -> {
                coverFlowFlipped = false;
                openCoverFlowItem(hit.value());
            }
            case TOGGLE_TRANSLATION -> translationsEnabled = !translationsEnabled;
            case QUALITY -> cycleQuality();
            case CYCLE_RATE -> cycleRate();
            case PREFETCH -> manager.prefetch(!manager.config().prefetch());
            case DYNAMIC_BACKGROUND -> manager.dynamicBackground(!manager.config().dynamicBackground());
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
                navigate(ClickGuiSession.MusicRoute.LOGIN, null);
            }
        }
    }

    @Override
    public void mouseMoved(float x, float y, boolean leftDown) {
        if (!leftDown) {
            draggingSeek = false;
            draggingVolume = false;
            draggingLyrics = false;
            return;
        }
        if (draggingSeek) updateSeek(x);
        if (draggingVolume) updateVolume(x);
        if (draggingLyrics) updateLyricScroll(y);
        if (coverFlow.dragging()) coverFlow.drag(x, coverFlowItemSpacing, coverFlowItemCount());
    }

    @Override
    public void mouseReleased() {
        if (coverFlow.dragging()) {
            boolean click = coverFlow.endDrag(coverFlowItemCount());
            if (click && pressedCoverFlowCard != null) {
                if (pressedCoverFlowCard.index() == coverFlow.selectedIndex(coverFlowItemCount())) {
                    if (flippedCoverFlowIndex == pressedCoverFlowCard.index() && coverFlowFlipProgress > .85f) {
                        coverFlowFlipped = false;
                    } else {
                        flippedCoverFlowIndex = pressedCoverFlowCard.index();
                        coverFlowFlipped = true;
                        requestCoverFlowTracks(pressedCoverFlowCard.value());
                    }
                } else {
                    coverFlow.select(pressedCoverFlowCard.index(), coverFlowItemCount());
                    coverFlowFlipped = false;
                    flippedCoverFlowIndex = -1;
                }
            } else if (!click) {
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
            }
            pressedCoverFlowCard = null;
        }
        draggingSeek = false;
        draggingVolume = false;
        draggingLyrics = false;
    }

    @Override
    public boolean mouseScrolled(float x, float y, double delta) {
        if (queueOpen && queueBounds != null && queueBounds.contains(x, y)) {
            targetQueueScroll = Math.clamp(targetQueueScroll + (float) delta * 34f,
                    -maximumQueueScroll, 0f);
            return true;
        }
        if (lyricsViewport != null && lyricsViewport.contains(x, y) && lyricLineCount > 1) {
            targetLyricScroll += (float) delta * .85f;
            clampLyricScroll();
            lyricFollowAt = System.currentTimeMillis() + 5000L;
            return true;
        }
        if (coverFlowBounds != null && coverFlowBounds.contains(x, y)) {
            coverFlow.scroll(delta, coverFlowItemCount());
            coverFlowFlipped = false;
            flippedCoverFlowIndex = -1;
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
        if (session.musicRoute() == ClickGuiSession.MusicRoute.COVER_FLOW) {
            if (event.key() == GLFW.GLFW_KEY_LEFT) {
                coverFlow.select(coverFlow.selectedIndex(coverFlowItemCount()) - 1, coverFlowItemCount());
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_RIGHT) {
                coverFlow.select(coverFlow.selectedIndex(coverFlowItemCount()) + 1, coverFlowItemCount());
                coverFlowFlipped = false;
                flippedCoverFlowIndex = -1;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                openSelectedCoverFlowItem();
                return true;
            }
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
        if (coverFlowFlipped) { coverFlowFlipped = false; return true; }
        if (!routeHistory.isEmpty()) {
            back();
            return true;
        }
        if (detailRoute(session.musicRoute())) {
            transition(ClickGuiSession.MusicRoute.RECOMMENDED, null, -1f);
            return true;
        }
        return false;
    }

    @Override
    public boolean showHostCloseButton() {
        return !immersiveRoute(session.musicRoute());
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
        queueScrollVelocity = 0f;
        coverPreview = false;
        coverFlow.reset();
        coverFlowKind = CoverFlowKind.PLAYLISTS;
        pressedCoverFlowCard = null;
        coverFlowFlipped = false;
        flippedCoverFlowIndex = -1;
        coverFlowFlipProgress = 0f;
        coverFlowTracks.clear();
        pendingCoverFlowTracks.clear();
        lyricPosition = Float.NaN;
        lyricVelocity = 0f;
        lyricScroll = 0f;
        lyricScrollVelocity = 0f;
        targetLyricScroll = 0f;
        draggingLyrics = false;
        lyricFollowAt = 0L;
        bindingHotkey = null;
        contentScroll = 0f;
        targetContentScroll = 0f;
        contentScrollVelocity = 0f;
        sidebarScroll = 0f;
        targetSidebarScroll = 0f;
        sidebarScrollVelocity = 0f;
        encoded.clear();
        hovers.clear();
        imageFades.clear();
        message = "";
        translationsEnabled = true;
        artistSongsExpanded = false;
        artistSongsProgress = 0f;
        routeHistory.clear();
    }

    private void navigate(ClickGuiSession.MusicRoute route, Object payload) {
        ClickGuiSession.MusicRoute currentRoute = session.musicRoute();
        if (route == currentRoute && payload == null) return;
        // Keep one semantic parent only; ESC must not replay the entire browsing history.
        routeHistory.clear();
        routeHistory.push(currentEntry());
        transition(route, payload, 1f);
    }

    private void back() {
        if (routeHistory.isEmpty()) return;
        RouteEntry entry = routeHistory.pop();
        transition(entry.route(), entry.payload(), -1f);
    }

    private void transition(ClickGuiSession.MusicRoute route, Object payload, float direction) {
        ClickGuiSession.MusicRoute currentRoute = session.musicRoute();
        if (session.musicRoute() == ClickGuiSession.MusicRoute.LOGIN && route != ClickGuiSession.MusicRoute.LOGIN) {
            manager.cancelLogin();
            qrArtwork = null;
            qrRequesting = false;
            qrChecking = false;
        }
        session.musicRoute(route);
        contentScroll = 0f;
        targetContentScroll = 0f;
        contentScrollVelocity = 0f;
        routeProgress = 0f;
        routeDirection = direction;
        switch (route) {
            case NOW_PLAYING -> { }
            case PLAYLIST -> data.playlist((Playlist) payload);
            case ALBUM -> data.album((Album) payload);
            case ARTIST -> data.artist((Artist) payload);
            case SEARCH -> {
                String query = payload instanceof String value ? value.strip() : search.value().strip();
                if (query.isEmpty()) data.load(route);
                else data.search(query);
            }
            default -> data.load(route);
        }
    }

    private void openPlaylist(Playlist playlist) {
        navigate(ClickGuiSession.MusicRoute.PLAYLIST, playlist);
    }

    private void openAlbum(Album album) {
        navigate(ClickGuiSession.MusicRoute.ALBUM, album);
    }

    private void openArtist(Artist artist) {
        if (artist == null || artist.id() <= 0L) return;
        artistSongsExpanded = false;
        artistSongsProgress = 0f;
        navigate(ClickGuiSession.MusicRoute.ARTIST, artist);
    }

    private void openCoverFlowItem(Object value) {
        if (value instanceof Playlist playlist) openPlaylist(playlist);
        else if (value instanceof Album album) openAlbum(album);
    }

    private void playCoverFlowItem(Object value) {
        artworkDirection = 1;
        List<Music> tracks = coverFlowTracks.get(coverFlowCollectionKey(value));
        if (tracks != null && !tracks.isEmpty()) {
            manager.play(tracks, 0);
            return;
        }
        if (value instanceof Playlist playlist) {
            run(manager.playPlaylist(playlist.id(), 0), loaded -> coverFlowTracks.put(
                    coverFlowCollectionKey(value), loaded));
        } else if (value instanceof Album album) {
            run(manager.playAlbum(album.id(), 0), loaded -> coverFlowTracks.put(
                    coverFlowCollectionKey(value), loaded));
        }
    }

    private void requestCoverFlowTracks(Object value) {
        String key = coverFlowCollectionKey(value);
        if (coverFlowTracks.containsKey(key) || !pendingCoverFlowTracks.add(key)) return;
        long owner = generation;
        CompletableFuture<List<Music>> future;
        if (value instanceof Playlist playlist) {
            future = manager.playlistSongs(playlist.id());
        } else if (value instanceof Album album) {
            future = manager.album(album.id()).thenApply(details -> details.songs());
        } else {
            pendingCoverFlowTracks.remove(key);
            return;
        }
        future.whenComplete((tracks, failure) -> main(() -> {
            pendingCoverFlowTracks.remove(key);
            if (disposed || owner != generation || failure != null) return;
            coverFlowTracks.put(key, tracks == null ? List.of() : List.copyOf(tracks));
        }));
    }

    private static String coverFlowCollectionKey(Object value) {
        if (value instanceof Playlist playlist) return "playlist:" + playlist.id();
        if (value instanceof Album album) return "album:" + album.id();
        return "unknown";
    }

    private void openSelectedCoverFlowItem() {
        int count = coverFlowItemCount();
        if (count == 0) return;
        int index = coverFlow.selectedIndex(count);
        openCoverFlowItem(coverFlowKind == CoverFlowKind.PLAYLISTS
                ? data.playlists().get(index) : data.albums().get(index));
    }

    private int coverFlowItemCount() {
        return coverFlowKind == CoverFlowKind.PLAYLISTS
                ? data.playlists().size() : data.albums().size();
    }

    private void startSearch() {
        String query = search.value().strip();
        if (query.isEmpty()) return;
        navigate(ClickGuiSession.MusicRoute.SEARCH, query);
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
        manager.volume(progress);
    }

    private void cycleRate() {
        float[] values = {.5f, .75f, 1f, 1.25f, 1.5f, 2f};
        float current = manager.state().rate();
        int next = 0;
        for (int index = 0; index < values.length; index++) {
            if (Math.abs(values[index] - current) < .01f) {
                next = (index + 1) % values.length;
                break;
            }
            if (values[index] > current) {
                next = index;
                break;
            }
        }
        manager.rate(values[next]);
    }

    private RouteEntry currentEntry() {
        return switch (session.musicRoute()) {
            case PLAYLIST -> new RouteEntry(session.musicRoute(), data.selectedPlaylist());
            case ALBUM -> new RouteEntry(session.musicRoute(), data.selectedAlbum());
            case ARTIST -> new RouteEntry(session.musicRoute(),
                    data.selectedArtist() == null ? null : data.selectedArtist().artist());
            case SEARCH -> new RouteEntry(session.musicRoute(), search.value());
            default -> new RouteEntry(session.musicRoute(), null);
        };
    }

    private void updateLyricScroll(float mouseY) {
        if (lyricScrollTrack == null || lyricLineCount <= 1 || !Float.isFinite(lyricPosition)) return;
        float thumbHeight = lyricScrollThumb == null ? 0f : lyricScrollThumb.height();
        float travel = Math.max(1f, lyricScrollTrack.height() - thumbHeight);
        float progress = Math.clamp((mouseY - lyricScrollTrack.y() - thumbHeight * .5f) / travel,
                0f, 1f);
        float viewPosition = progress * (lyricLineCount - 1f);
        targetLyricScroll = lyricPosition - viewPosition;
        lyricScroll = targetLyricScroll;
        clampLyricScroll();
        lyricFollowAt = System.currentTimeMillis() + 5000L;
    }

    private void clampLyricScroll() {
        if (lyricLineCount <= 1 || !Float.isFinite(lyricPosition)) {
            lyricScroll = 0f;
            lyricScrollVelocity = 0f;
            targetLyricScroll = 0f;
            return;
        }
        float minimum = -(lyricLineCount - 1f - lyricPosition);
        float maximum = lyricPosition;
        targetLyricScroll = Math.clamp(targetLyricScroll, minimum, maximum);
        lyricScroll = Math.clamp(lyricScroll, minimum, maximum);
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
                routeHistory.clear();
                transition(ClickGuiSession.MusicRoute.RECOMMENDED, null, -1f);
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
                routeHistory.clear();
                transition(ClickGuiSession.MusicRoute.RECOMMENDED, null, -1f);
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
        float bottomInset = immersiveRoute(session.musicRoute()) ? 12f : PLAYER_HEIGHT + 12f;
        UiRect toast = new UiRect(bounds.x() + (bounds.width() - width) * .5f,
                bounds.y() + bounds.height() - bottomInset - 29f, width, 29f);
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

    private static float smoothStep(float value) {
        float bounded = Math.clamp(value, 0f, 1f);
        return bounded * bounded * (3f - 2f * bounded);
    }

    private static boolean immersiveRoute(ClickGuiSession.MusicRoute route) {
        return route == ClickGuiSession.MusicRoute.NOW_PLAYING
                || route == ClickGuiSession.MusicRoute.COVER_FLOW;
    }

    private static boolean detailRoute(ClickGuiSession.MusicRoute route) {
        return route == ClickGuiSession.MusicRoute.PLAYLIST
                || route == ClickGuiSession.MusicRoute.ALBUM
                || route == ClickGuiSession.MusicRoute.ARTIST
                || immersiveRoute(route);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * Math.clamp(progress, 0f, 1f);
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

    private static WordLine wordLineForLine(List<WordLine> lines, int lineStart, int nextLineStart) {
        int low = 0;
        int high = lines.size() - 1;
        int first = lines.size();
        int minimumStart = lineStart - 120;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            WordLine value = lines.get(middle);
            if (value.startMs() >= minimumStart) {
                first = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        if (first >= lines.size()) return null;
        WordLine candidate = lines.get(first);
        return candidate.startMs() < nextLineStart ? candidate : null;
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
    private static String modeIcon(PlayMode mode) {
        return switch (mode) {
            case SINGLE -> ClickGuiIcons.PLAYER_REPEAT_ONE;
            case LOOP_LIST -> ClickGuiIcons.PLAYER_REPEAT;
            case SHUFFLE -> ClickGuiIcons.PLAYER_SHUFFLE;
            case INTELLIGENCE -> ClickGuiIcons.SPARKLES;
        };
    }
    private static List<String> wrap(UiContext context, String font, float size, String value,
                                     float width, int maximumLines) {
        if (value == null || value.isBlank() || maximumLines <= 0) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && context.width(font, size, candidate) > width) {
                result.add(line.toString());
                line.setLength(0);
                if (result.size() == maximumLines) break;
            }
            if (line.isEmpty() && context.width(font, size, word) > width) {
                line.append(ellipsizeFont(context, font, size, word, width));
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && result.size() < maximumLines) result.add(line.toString());
        return List.copyOf(result);
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
        NAVIGATE, OPEN_PLAYLIST, OPEN_ALBUM, OPEN_ARTIST, TOGGLE_ARTIST_SONGS,
        PLAY_SONG, PLAY_ALL, PLAY_CACHED, DELETE_CACHED,
        PREVIOUS, NEXT, TOGGLE_PLAYBACK, MODE, TOGGLE_QUEUE, PLAY_AT, SEEK, VOLUME,
        NOW_PLAYING, BACK_FROM_NOW_PLAYING, PREVIEW_COVER, CLOSE_PREVIEW,
        LIKE_CURRENT, CACHE_CURRENT, SEEK_LYRIC, LYRIC_SCROLL, TOGGLE_TRANSLATION,
        COVER_PLAYLISTS, COVER_ALBUMS, COVER_PREVIOUS, COVER_NEXT, COVER_ITEM, COVER_PLAY, COVER_OPEN,
        QUALITY, CYCLE_RATE, PREFETCH, DYNAMIC_BACKGROUND, CACHE_LIMIT,
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
    private enum CoverFlowKind { PLAYLISTS, ALBUMS }
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
    private record CoverFlowCard(int index, Object value) { }
    private record RouteEntry(ClickGuiSession.MusicRoute route, Object payload) { }
}
