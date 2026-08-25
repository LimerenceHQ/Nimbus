package cc.nimbus.features.module.modules.player;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

@ModuleInfo(
        name = "Teams",
        description = "Prevents combat modules from selecting teammates.",
        category = ModuleCategory.Player
)
public final class Teams extends BasicModule {
    public final ModeValue mode = new ModeValue(
            "Mode", new String[]{"Scoreboard", "Color"}, "Scoreboard");

    public boolean isSameTeam(Player self, Player target) {
        if (!isEnabled() || self == target) return false;
        return switch (mode.getValue()) {
            case "Color" -> Integer.valueOf(target.getTeamColor()).equals(self.getTeamColor());
            default -> Objects.equals(getTeam(self), getTeam(target));
        };
    }

    private static String getTeam(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || entity == null) return null;
        PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(entity.getUUID());
        return playerInfo == null || playerInfo.getTeam() == null
                ? null : playerInfo.getTeam().getName();
    }
}
