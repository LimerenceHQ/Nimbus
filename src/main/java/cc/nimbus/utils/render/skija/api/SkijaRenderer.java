package cc.nimbus.utils.render.skija.api;

import cc.nimbus.utils.render.skija.internal.backend.SkijaBackend;
import cc.nimbus.utils.render.skija.internal.effect.post.PostProcessPipeline;
import cc.nimbus.utils.render.skija.internal.image.ImageStore;
import cc.nimbus.utils.render.skija.internal.image.SvgIconStore;
import cc.nimbus.utils.render.skija.internal.image.minecraft.MinecraftSnapshot;
import cc.nimbus.utils.render.skija.internal.backend.SkijaSupport;
import cc.nimbus.utils.render.skija.internal.text.FontFamily;
import cc.nimbus.utils.render.skija.internal.text.TextFactory;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkijaRenderer {
    private final Map<String, SkijaLayer> layers = new LinkedHashMap<>();
    private final SkijaHud hud = new SkijaHud(this);
    private final SkijaTextMetrics text = new SkijaTextMetrics(this);
    private final SkijaPostProcess postProcess = new SkijaPostProcess(null, this);
    private boolean initialized;
    private boolean fontsInitialized;
    private boolean closed;

    @ApiStatus.Internal
    public synchronized void init() {
        if (initialized) return;
        if (closed) throw new IllegalStateException("Skija renderer is closed and cannot be restarted");
        SkijaSupport.initialize();
        initialized = true;
    }

    public void validateSupport() {
        requireInitialized();
        SkijaSupport.validateBackend();
        if (SkijaSupport.isSupported()) ensureFontsInitialized();
    }

    public boolean isSupported() {
        validateSupport();
        return SkijaSupport.isSupported();
    }

    public String unsupportedReason() {
        validateSupport();
        return SkijaSupport.getUnsupportedReason();
    }

    public synchronized SkijaLayer layer(String name) {
        requireReady();
        return layers.computeIfAbsent(name, key -> new SkijaLayer(this, key));
    }

    public SkijaHud hud() {
        requireReady();
        return hud;
    }

    public SkijaTextMetrics text() {
        requireReady();
        return text;
    }

    public SkijaPostProcess postProcess() {
        requireReady();
        return postProcess;
    }

    public void captureScene() {
        requireReady();
        MinecraftSnapshot.capture();
    }

    public void captureScene(int targetFps) {
        requireReady();
        MinecraftSnapshot.capture(targetFps);
    }

    public void invalidateScene() {
        RenderSystem.assertOnRenderThread();
        requireReady();
        MinecraftSnapshot.invalidate();
        SkijaBackend.INSTANCE.invalidateScene();
    }

    void renderHud(int targetFps) {
        SkijaBackend.INSTANCE.callSurface(targetFps);
    }

    public void configurePostProcess(PostProcessOptions options) {
        requireReady();
        SkijaBackend.INSTANCE.setPostProcessOptions(options);
    }

    public PostProcessOptions postProcessOptions() {
        requireReady();
        return SkijaBackend.INSTANCE.getPostProcessOptions();
    }

    public int width() {
        requireReady();
        return SkijaBackend.INSTANCE.getCurrentWidth();
    }

    public int height() {
        requireReady();
        return SkijaBackend.INSTANCE.getCurrentHeight();
    }

    void finishFrame() {
        PostProcessPipeline.clear();
        ImageStore.drainPendingCloses();
    }

    @ApiStatus.Internal
    public synchronized void close() {
        if (closed) return;
        closed = true;
        initialized = false;

        RuntimeException failure = null;
        for (SkijaLayer layer : layers.values()) {
            failure = cleanup(failure, layer::shutdown);
        }
        layers.clear();
        failure = cleanup(failure, SvgIconStore::clear);
        if (fontsInitialized) {
            failure = cleanup(failure, TextFactory::close);
            failure = cleanup(failure, FontFamily::close);
            fontsInitialized = false;
        }
        failure = cleanup(failure, SkijaBackend.INSTANCE::closeAll);
        failure = cleanup(failure, SkijaSupport::close);
        if (failure != null) throw failure;
    }

    synchronized void requireReady() {
        requireInitialized();
        SkijaSupport.validateBackend();
        if (!SkijaSupport.isSupported()) {
            throw new IllegalStateException(SkijaSupport.getUnsupportedReason());
        }
        ensureFontsInitialized();
    }

    private synchronized void ensureFontsInitialized() {
        if (fontsInitialized) return;
        FontFamily.initialize();
        fontsInitialized = true;
    }

    private void requireInitialized() {
        if (closed) throw new IllegalStateException("Skija renderer is closed");
        if (!initialized) throw new IllegalStateException("Skija renderer is not initialized");
    }

    private static RuntimeException cleanup(RuntimeException failure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (failure == null) return error;
            failure.addSuppressed(error);
        }
        return failure;
    }
}
