package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.utils.render.skija.SkijaRenderer;

public final class RenderManager implements InstanceManager {
    private final SkijaRenderer skija = new SkijaRenderer();

    @Override
    public void init() {
        skija.init();
    }

    @Override
    public void shutdown() {
        skija.close();
    }

    public SkijaRenderer skija() {
        return skija;
    }
}
