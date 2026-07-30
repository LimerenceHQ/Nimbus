package cc.nimbus.module.impl.render;

import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleCategory;
import cc.nimbus.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.module.*;
import cc.nimbus.utils.render.NativeSupport;
import cc.nimbus.ui.clickgui.neutron.NeutronScreen;
import cc.nimbus.ui.screen.UnsupportedPlatformScreen;

@ModuleInfo(name = "ClickGui", displayName = "Click GUI", description = "Opens the Nimbus module interface.", category = ModuleCategory.Render, keyBind = GLFW.GLFW_KEY_INSERT)
public final class ClickGui extends BasicModule {
    @Override protected void onEnable() {
        Minecraft.getInstance().schedule(() -> {
            NativeSupport.validateBackend();
            Minecraft.getInstance().gui.setScreen(NativeSupport.isSupported() ? new NeutronScreen() : new UnsupportedPlatformScreen(NativeSupport.getUnsupportedReason()));
        });
        setEnabled(false);
    }
}
