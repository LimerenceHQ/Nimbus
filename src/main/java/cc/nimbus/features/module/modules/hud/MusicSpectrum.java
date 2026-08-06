package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.render.EventRender2D;
import cc.nimbus.features.client.ClientSettings;
import cc.nimbus.features.music.playback.PlaybackPhase;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.utils.render.skija.api.SkijaRenderer;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;

@ModuleInfo(
        name = "MusicSpectrum",
        displayName = "Music Spectrum",
        description = "Displays the current Nimbus Music spectrum along the bottom edge.",
        category = ModuleCategory.HUD
)
public final class MusicSpectrum extends BasicModule {
    private static final int BAND_COUNT = 160;
    private static final float HEIGHT = 96f;
    private static final float SENSITIVITY = 1.15f;
    private static final float RESPONSE = .7f;
    private static final float OPACITY = .72f;

    public final ModeSetting style = new ModeSetting(
            "Style", new String[]{"Bars", "Line"}, "Bars"
    );

    private float[] displayed = new float[0];
    private long lastFrameNanos;

    @EventTarget
    private void onRender2D(EventRender2D event) {
        MusicManager music = Core.INSTANCE.getMusicManager();
        SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
        ensureCapacity(BAND_COUNT);

        float delta = frameDeltaSeconds();
        float[] spectrum = music.spectrum();
        boolean playing = music.state().phase() == PlaybackPhase.PLAYING;
        float maximumHeight = HEIGHT
                * Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height()).scale();
        float gain = SENSITIVITY;
        float attack = easing(24f, delta);
        float release = easing(7f, delta);
        for (int index = 0; index < BAND_COUNT; index++) {
            float sample = playing ? sample(spectrum, index, BAND_COUNT) : 0f;
            float target = (float) Math.pow(Math.clamp(sample * gain, 0f, 1f), RESPONSE) * maximumHeight;
            float follow = target > displayed[index] ? attack : release;
            displayed[index] += (target - displayed[index]) * follow;
            if (displayed[index] < .05f) displayed[index] = 0f;
        }

        float[] frameBars = displayed.clone();
        float width = skija.width();
        float screenHeight = skija.height();
        ClientSettings theme = Core.INSTANCE.getConfigManager().clientSettings();
        long colorTime = System.currentTimeMillis();
        int[] colors = new int[17];
        for (int index = 0; index < colors.length; index++) {
            colors[index] = withAlpha(theme.accentColorAt(index / (float) (colors.length - 1), colorTime), OPACITY);
        }

        skija.hud().submit(frame -> {
            try (Paint paint = new Paint().setAntiAlias(true).setShader(frame.effects().linearGradient(
                    new Point(0f, screenHeight), new Point(width, screenHeight), colors))) {
                if (style.is("Line")) {
                    drawLine(frameBars, width, screenHeight, frame, paint);
                    return;
                }
                float cellWidth = width / frameBars.length;
                float gap = Math.clamp(cellWidth * .22f, .75f, 2.2f);
                float barWidth = Math.max(.75f, cellWidth - gap);
                for (int index = 0; index < frameBars.length; index++) {
                    float barHeight = frameBars[index];
                    if (barHeight <= .05f) continue;
                    float x = index * cellWidth + gap * .5f;
                    float radius = Math.min(barWidth * .5f, 2.5f);
                    frame.canvas().drawRRect(RRect.makeXYWH(
                            x, screenHeight - barHeight, barWidth, barHeight + radius, radius), paint);
                }
            }
        });
    }

    private static void drawLine(
            float[] values, float width, float screenHeight,
            cc.nimbus.utils.render.skija.api.SkijaFrame frame, Paint paint
    ) {
        if (values.length < 2) return;
        float peak = 0f;
        for (float value : values) peak = Math.max(peak, value);
        if (peak <= .05f) return;
        paint.setMode(PaintMode.STROKE)
                .setStrokeWidth(2.2f)
                .setStrokeCap(PaintStrokeCap.ROUND)
                .setStrokeJoin(PaintStrokeJoin.ROUND);
        try (PathBuilder builder = new PathBuilder()) {
            float previousX = 0f;
            float baseline = screenHeight - 1.25f;
            float previousY = baseline - values[0];
            builder.moveTo(previousX, previousY);
            for (int index = 1; index < values.length; index++) {
                float x = index * width / (values.length - 1f);
                float y = baseline - values[index];
                float middleX = (previousX + x) * .5f;
                float middleY = (previousY + y) * .5f;
                builder.quadTo(previousX, previousY, middleX, middleY);
                previousX = x;
                previousY = y;
            }
            builder.lineTo(previousX, previousY);
            try (Path path = builder.detach()) {
                frame.canvas().drawPath(path, paint);
            }
        }
    }

    @Override
    protected void onDisable() {
        displayed = new float[0];
        lastFrameNanos = 0L;
    }

    private void ensureCapacity(int count) {
        if (displayed.length == count) return;
        float[] resized = new float[count];
        if (displayed.length > 0) {
            for (int index = 0; index < count; index++) {
                resized[index] = sample(displayed, index, count);
            }
        }
        displayed = resized;
    }

    private static float sample(float[] values, int index, int count) {
        if (values == null || values.length == 0 || count <= 0) return 0f;
        float start = index * values.length / (float) count;
        float end = (index + 1f) * values.length / count;
        int first = Math.clamp((int) Math.floor(start), 0, values.length - 1);
        int last = Math.clamp((int) Math.ceil(end), first + 1, values.length);
        float sum = 0f;
        for (int source = first; source < last; source++) sum += values[source];
        return sum / (last - first);
    }

    private float frameDeltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1f / 60f
                : Math.min(.05f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
    }

    private static float easing(float rate, float deltaSeconds) {
        return 1f - (float) Math.exp(-rate * deltaSeconds);
    }

    private static int withAlpha(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        int alpha = Math.round(sourceAlpha * Math.clamp(opacity, 0f, 1f));
        return color & 0x00FFFFFF | alpha << 24;
    }
}
