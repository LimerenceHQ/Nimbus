package cc.nimbus.utils.render.refresh.impl;

import net.minecraft.client.Minecraft;
import cc.nimbus.utils.render.refresh.Refresh;

public class Refresh_VSync extends Refresh {
    public Refresh_VSync() {
        super(currentRefreshRate());
    }

    @Override
    public int fps() {
        return currentRefreshRate();
    }

    private static int currentRefreshRate() {
        int fps = Minecraft.getInstance().getWindow().getRefreshRate();
        return fps > 0 ? fps : 165;
    }
}
