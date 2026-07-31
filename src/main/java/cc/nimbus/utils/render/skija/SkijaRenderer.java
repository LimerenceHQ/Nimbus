package cc.nimbus.utils.render.skija;

import cc.nimbus.utils.render.skija.internal.SkijaBackend;
import cc.nimbus.utils.render.skija.image.SkijaImages;
import cc.nimbus.utils.render.skija.draw.SkijaIcons;
import cc.nimbus.utils.render.skija.post.PPHandler;
import cc.nimbus.utils.render.skija.post.PostProcessOptions;
import io.github.humbleui.skija.Canvas;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single owner and entry point for Nimbus's Skija integration. */
public final class SkijaRenderer implements AutoCloseable {
    private final Map<String, SkijaLayer> layers = new LinkedHashMap<>();
    private final SkijaHud hud = new SkijaHud(this);
    private boolean initialized;

    public synchronized void init() {
        if (initialized) return;
        SkijaSupport.initialize();
        initialized = true;
    }

    public void validateSupport() {
        SkijaSupport.validateBackend();
    }

    public boolean isSupported() {
        return SkijaSupport.isSupported();
    }

    public String unsupportedReason() {
        return SkijaSupport.getUnsupportedReason();
    }

    public synchronized SkijaLayer layer(String name) {
        if (!initialized) init();
        return layers.computeIfAbsent(name, SkijaLayer::new);
    }

    public SkijaHud hud() {
        return hud;
    }

    public void submitHud(SkijaFrameRenderer callback) {
        hud.submit(callback);
    }

    public void renderHud(int targetFps) {
        SkijaBackend.INSTANCE.callSurface(targetFps);
    }

    public void configurePostProcess(PostProcessOptions options) {
        SkijaBackend.INSTANCE.setPostProcessOptions(options);
    }

    public PostProcessOptions postProcessOptions() {
        return SkijaBackend.INSTANCE.getPostProcessOptions();
    }

    public void drawPostProcess(Canvas canvas) {
        PPHandler.drawAll(canvas, postProcessOptions());
    }

    public int width() {
        return SkijaBackend.INSTANCE.getCurrentWidth();
    }

    public int height() {
        return SkijaBackend.INSTANCE.getCurrentHeight();
    }

    public void beginFrame() {
        // Kept as the common frame boundary for future per-frame resources.
    }

    public void endFrame() {
        SkijaImages.drainPendingCloses();
    }

    @Override
    public synchronized void close() {
        for (SkijaLayer layer : layers.values()) {
            layer.close();
        }
        layers.clear();
        SkijaIcons.clear();
        SkijaBackend.INSTANCE.closeAll();
        SkijaSupport.close();
        initialized = false;
    }
}
