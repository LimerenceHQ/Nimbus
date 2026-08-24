package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.hud.Draggable;
import cc.nimbus.features.hud.HudRect;
import cc.nimbus.features.hud.HudSize;
import cc.nimbus.features.hud.HudViewport;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.features.music.lyrics.CurrentLyricsState;
import cc.nimbus.features.music.lyrics.LyricLine;
import cc.nimbus.features.music.lyrics.LyricVisuals;
import cc.nimbus.features.music.lyrics.LyricsTimeline;
import cc.nimbus.features.music.playback.PlaybackState;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.utils.render.font.NimbusFonts;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.List;

@ModuleInfo(
        name = "MusicLyrics",
        displayName = "Music Lyrics",
        description = "Displays synchronized Nimbus Music lyrics.",
        category = ModuleCategory.HUD
)
public final class MusicLyrics extends BasicModule {
    private static final String FONT = NimbusFonts.CONTENT_MEDIUM;
    private static final float WIDTH = 520f;
    private static final float SINGLE_HEIGHT = 88f;
    private static final float MULTIPLE_HEIGHT = 148f;
    private static final float FONT_SIZE = 22f;
    private static final float TRANSLATION_GAP = 2f;
    private static final int TRANSITION_MILLIS = 240;

    public final ModeValue display = new ModeValue(
            "Display", new String[]{"Single", "Multiple"}, "Single"
    );
    public final ModeValue lineAnimation = new ModeValue(
            "Line Animation", new String[]{"Rise", "Fade", "Slide", "Scale"}, "Rise",
            () -> display.is("Single")
    );
    public final ModeValue wordAnimation = new ModeValue(
            "Word Animation", new String[]{"Fill", "Word", "Wave"}, "Fill",
            () -> display.is("Single")
    );
    public final ModeValue alignment = new ModeValue(
            "Alignment", new String[]{"Left", "Center", "Right"}, "Center"
    );
    public final BooleanSetting translation = new BooleanSetting("Translation", true);
    public final BooleanSetting background = new BooleanSetting("Background", false);

    private long songId = -1L;
    private int currentLine = -1;
    private int previousLine = -1;
    private long transitionStartedNanos;
    private long lastFrameNanos;
    private float multiplePosition = Float.NaN;
    private LyricsTimeline translationTimeline;
    private boolean timelineHasTranslation;

    private final Draggable drag = new Draggable("MusicLyrics", 0.64, 0.82) {
        @Override
        public HudSize getSize() {
            return new HudSize(WIDTH, display.is("Multiple") ? MULTIPLE_HEIGHT : SINGLE_HEIGHT);
        }
    };

    @EventTarget
    private void onRender2D(EventRender2D event) {
        if (event.isPost()) return;
        MusicManager music = Core.INSTANCE.getMusicManager();
        PlaybackState playback = music.state();
        CurrentLyricsState state = music.currentLyrics();
        if (playback.music() == null || state.musicId() != playback.music().id()) {
            reset(playback.music() == null ? -1L : playback.music().id());
            return;
        }
        LyricsTimeline timeline = state.timeline();
        List<LyricLine> lines = timeline.lines();
        if (lines.isEmpty()) return;
        if (translationTimeline != timeline) {
            translationTimeline = timeline;
            timelineHasTranslation = lines.stream().anyMatch(line -> !line.translation().isBlank());
        }

        int nowMs = Math.round(playback.progressSeconds() * 1000f);
        int active = timeline.activeLine(nowMs);
        if (songId != state.musicId()) reset(state.musicId());
        if (active != currentLine) {
            previousLine = currentLine;
            currentLine = active;
            transitionStartedNanos = System.nanoTime();
        }
        if (currentLine < 0 || currentLine >= lines.size()) return;
        updateMultiplePosition(active);

        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
        HudRect bounds = drag.getRect(viewport);
        float height = display.is("Multiple") ? MULTIPLE_HEIGHT : SINGLE_HEIGHT;
        if (background.getValue()) {
            skija.postProcess().add(RRect.makeXYWH(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    4f * drag.getRenderScale(viewport)));
        }

        long colorTime = System.currentTimeMillis();
        skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
            if (background.getValue()) {
                local.draw().roundedRect(0f, 0f, WIDTH, height, 4f, 0x52101316);
            }
            if (display.is("Multiple")) {
                drawMultiple(local, lines, active, nowMs, colorTime, timelineHasTranslation);
            } else {
                drawSingle(local, lines, nowMs, colorTime);
            }
        }));
    }

    @Override
    protected void onDisable() {
        reset(-1L);
    }

    private void drawSingle(SkijaFrame frame, List<LyricLine> lines, int nowMs, long colorTime) {
        LyricLine current = lines.get(currentLine);
        LyricLine previous = previousLine >= 0 && previousLine < lines.size()
                ? lines.get(previousLine) : null;
        float transition = transitionProgress();
        if (previous != null && transition < 1f) {
            drawSingleLine(frame, previous, nowMs, 1f - transition, true, colorTime);
        }
        drawSingleLine(frame, current, nowMs, transition, false, colorTime);
    }

    private void drawSingleLine(
            SkijaFrame frame, LyricLine line, int nowMs, float progress, boolean outgoing, long colorTime
    ) {
        float eased = smoothStep(progress);
        if (eased <= .001f) return;
        float offsetX = 0f;
        float offsetY = 0f;
        if (lineAnimation.is("Rise")) {
            offsetY = outgoing ? -8f * (1f - progress) : 8f * (1f - progress);
        } else if (lineAnimation.is("Slide")) {
            offsetX = outgoing ? -18f * (1f - progress) : 18f * (1f - progress);
        }
        float size = lineAnimation.is("Scale")
                ? FONT_SIZE * (outgoing ? 1f - .04f * (1f - progress) : .94f + .06f * progress)
                : FONT_SIZE;
        drawLine(frame, line, nowMs, 0f, SINGLE_HEIGHT, size,
                eased, 1f, offsetX, offsetY, true, colorTime);
    }

    private void drawMultiple(
            SkijaFrame frame, List<LyricLine> lines, int active, int nowMs, long colorTime,
            boolean hasTranslation
    ) {
        boolean translated = translation.getValue() && hasTranslation;
        float rowHeight = translated ? 44f : 34f;
        int first = Math.max(0, (int) Math.floor(multiplePosition) - 1);
        int last = Math.min(lines.size() - 1, (int) Math.ceil(multiplePosition) + 1);
        int clip = frame.canvas().save();
        frame.canvas().clipRect(Rect.makeXYWH(0f, 0f, WIDTH, MULTIPLE_HEIGHT), ClipMode.INTERSECT, false);
        try {
            for (int index = first; index <= last; index++) {
                float distance = index - multiplePosition;
                float focus = smoothStep(1f - Math.clamp(Math.abs(index - multiplePosition), 0f, 1f));
                float edge = 1f - Math.clamp(Math.abs(distance) / 2.2f, 0f, 1f);
                float alpha = (.28f + .72f * focus) * smoothStep(edge);
                if (alpha <= .01f) continue;
                float rowY = MULTIPLE_HEIGHT * .5f + distance * rowHeight - rowHeight * .5f;
                drawLine(frame, lines.get(index), nowMs, rowY, rowHeight,
                        18f, alpha, focus, 0f, 0f,
                        index <= active, colorTime);
            }
        } finally {
            frame.canvas().restoreToCount(clip);
        }
    }

    private void drawLine(
            SkijaFrame frame, LyricLine line, int nowMs, float rowY, float rowHeight, float size,
            float opacity, float highlight, float offsetX, float offsetY,
            boolean allowProgress, long colorTime
    ) {
        String text = line.wordLine() != null && !line.wordLine().text().isBlank()
                ? line.wordLine().text() : line.text();
        float available = WIDTH - 28f;
        size = fitSize(frame, text, size, 11f, available);
        float textHeight = frame.text().height(FONT, size, text);
        boolean showTranslation = translation.getValue() && !line.translation().isBlank();
        float translationSize = Math.max(10f, size * .58f);
        float translationHeight = showTranslation
                ? frame.text().height(FONT, translationSize, line.translation()) : 0f;
        float groupHeight = textHeight + (showTranslation ? TRANSLATION_GAP + translationHeight : 0f);
        float y = rowY + (rowHeight - groupHeight) * .5f + offsetY;
        float x = alignedX(frame, text, size, available) + offsetX;
        if (allowProgress) {
            if (line.wordLine() != null) {
                LyricVisuals.draw(frame, Core.INSTANCE.getConfigManager().clientSettings(), FONT, size,
                        text, x, y, line.wordLine(), nowMs, 0xB0FFFFFF, opacity,
                        highlight, colorTime, progressStyle());
            } else {
                LyricVisuals.drawFlowing(frame, Core.INSTANCE.getConfigManager().clientSettings(), FONT, size,
                        text, x, y, 0xB0FFFFFF, opacity, highlight, colorTime);
            }
        } else {
            frame.text().draw(FONT, size, text, x, y, multiplyAlpha(0xFFFFFFFF, opacity));
        }

        if (showTranslation) {
            float translatedX = alignedX(frame, line.translation(), translationSize, available) + offsetX;
            frame.text().draw(FONT, translationSize, line.translation(), translatedX,
                    y + textHeight + TRANSLATION_GAP, multiplyAlpha(0xC8E2E5EA, opacity));
        }
    }

    private float alignedX(SkijaFrame frame, String text, float size, float available) {
        float textWidth = frame.text().width(FONT, size, text);
        return switch (alignment.getValue()) {
            case "Left" -> 14f;
            case "Right" -> 14f + available - textWidth;
            default -> 14f + (available - textWidth) * .5f;
        };
    }

    private LyricVisuals.ProgressStyle progressStyle() {
        if (display.is("Multiple")) return LyricVisuals.ProgressStyle.FILL;
        return switch (wordAnimation.getValue()) {
            case "Word" -> LyricVisuals.ProgressStyle.WORD;
            case "Wave" -> LyricVisuals.ProgressStyle.WAVE;
            default -> LyricVisuals.ProgressStyle.FILL;
        };
    }

    private void updateMultiplePosition(int active) {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        if (!Float.isFinite(multiplePosition) || Math.abs(multiplePosition - active) > 6f) {
            multiplePosition = active;
            return;
        }
        multiplePosition += (active - multiplePosition) * (1f - (float) Math.exp(-10f * delta));
        if (Math.abs(active - multiplePosition) < .001f) multiplePosition = active;
    }

    private static float fitSize(SkijaFrame frame, String text, float preferred, float minimum, float width) {
        float measured = frame.text().width(FONT, preferred, text);
        if (measured <= width || measured <= .01f) return preferred;
        return Math.max(minimum, preferred * width / measured);
    }

    private float transitionProgress() {
        if (transitionStartedNanos == 0L) return 1f;
        return Math.clamp((System.nanoTime() - transitionStartedNanos)
                / (TRANSITION_MILLIS * 1_000_000f), 0f, 1f);
    }

    private void reset(long nextSongId) {
        songId = nextSongId;
        currentLine = -1;
        previousLine = -1;
        transitionStartedNanos = 0L;
        lastFrameNanos = 0L;
        multiplePosition = Float.NaN;
        translationTimeline = null;
        timelineHasTranslation = false;
    }

    private static float smoothStep(float value) {
        float clamped = Math.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static int multiplyAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }
}
