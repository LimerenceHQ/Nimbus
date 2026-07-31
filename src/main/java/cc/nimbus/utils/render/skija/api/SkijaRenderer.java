package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.effect.post.PostProcessPipeline;
import cc.nimbus.utils.render.skija.internal.image.ImageStore;
import cc.nimbus.utils.render.skija.internal.image.SvgIconStore;
import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.skija.internal.backend.SkijaSupport;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single owner and entry point for Nimbus's Skija integration. */
public final class SkijaRenderer implements AutoCloseable {
    private final Map<String, SkijaLayer> layers = new LinkedHashMap<>();
    private final SkijaHud hud = new SkijaHud(this);
    private final SkijaTextMetrics text = new SkijaTextMetrics();
    private final SkijaPostProcess postProcess = new SkijaPostProcess(null, this);
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
        return layers.computeIfAbsent(name, key -> new SkijaLayer(this, key));
    }

    public SkijaHud hud() {
        return hud;
    }

    public SkijaTextMetrics text() {
        return text;
    }

    public SkijaPostProcess postProcess() {
        return postProcess;
    }

    public void captureScene() {
        MinecraftSnapshot.capture();
    }

    void renderHud(int targetFps) {
        SkijaBackend.INSTANCE.callSurface(targetFps);
    }

    public void configurePostProcess(PostProcessOptions options) {
        SkijaBackend.INSTANCE.setPostProcessOptions(options);
    }

    public PostProcessOptions postProcessOptions() {
        return SkijaBackend.INSTANCE.getPostProcessOptions();
    }

    public int width() {
        return SkijaBackend.INSTANCE.getCurrentWidth();
    }

    public int height() {
        return SkijaBackend.INSTANCE.getCurrentHeight();
    }

    void finishFrame() {
        PostProcessPipeline.clear();
        ImageStore.drainPendingCloses();
    }

    @Override
    public synchronized void close() {
        for (SkijaLayer layer : layers.values()) {
            layer.close();
        }
        layers.clear();
        SvgIconStore.clear();
        SkijaBackend.INSTANCE.closeAll();
        SkijaSupport.close();
        initialized = false;
    }
}
