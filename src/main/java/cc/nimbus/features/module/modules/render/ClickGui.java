package cc.nimbus.features.module.modules.render;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import cc.nimbus.core.Core;
import cc.nimbus.ui.clickgui.ClickGuiScreen;
import cc.nimbus.ui.clickgui.ClickGuiSession;
import cc.nimbus.ui.screen.UnsupportedPlatformScreen;

@ModuleInfo(name = "ClickGui", displayName = "Click GUI", description = "Opens the Nimbus module interface.", category = ModuleCategory.Render, keyBind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public final class ClickGui extends BasicModule {
    private final ClickGuiSession session = new ClickGuiSession();

    @Override protected void onEnable() {
        Minecraft.getInstance().schedule(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            var skija = Core.INSTANCE.getRenderManager().skija();
            skija.validateSupport();
            minecraft.setScreen(skija.isSupported()
                    ? new ClickGuiScreen(session)
                    : new UnsupportedPlatformScreen(skija.unsupportedReason()));
        });
        setEnabled(false);
    }
}
