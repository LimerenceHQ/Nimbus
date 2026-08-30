package cc.nimbus.features.command.commands;

import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.ui.gateway.GatewayDiagnosticsScreen;
import net.minecraft.client.Minecraft;

/** Opens the development-only Nimbus Gateway diagnostics screen. */
public final class GatewayCommand extends BasicCommand {
    public GatewayCommand() {
        super("Gateway", "打开 Nimbus Gateway 登录与诊断界面。", "gw");
    }

    @Override
    public void execute(String[] args) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new GatewayDiagnosticsScreen(minecraft.screen));
    }
}
