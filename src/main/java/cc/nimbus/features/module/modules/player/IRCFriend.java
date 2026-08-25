package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.input.EventMouseButton;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.submodule.ModeValue;
import cc.nimbus.utils.client.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        name = "IRCFriend",
        description = "Marks players as friends with a configurable mouse button.",
        category = ModuleCategory.Player,
        enableOnStartUp = true
)
public final class IRCFriend extends BasicModule {
    private static final Set<UUID> MANUAL_FRIENDS = new HashSet<>();
    private final Minecraft minecraft = Minecraft.getInstance();

    public final ModeValue keyMode = new ModeValue(
            "Friend Key", new String[]{"Middle", "Mouse3", "Mouse4"}, "Middle");

    @EventTarget
    private void onMouseButton(EventMouseButton event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != configuredButton()
                || minecraft.screen != null || minecraft.player == null || minecraft.level == null) return;
        if (!(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof Player target)
                || target == minecraft.player) return;

        boolean added = toggleFriend(target);
        MessageUtils.addMessage(added
                ? "§a已添加好友: §f" + target.getName().getString()
                : "§c已移除好友: §f" + target.getName().getString());
    }

    public static boolean isFriend(Player player) {
        return player != null && MANUAL_FRIENDS.contains(player.getUUID());
    }

    public static boolean toggleFriend(Player player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        if (!MANUAL_FRIENDS.add(id)) {
            MANUAL_FRIENDS.remove(id);
            return false;
        }
        return true;
    }

    private int configuredButton() {
        return switch (keyMode.getValue()) {
            case "Mouse3" -> GLFW.GLFW_MOUSE_BUTTON_4;
            case "Mouse4" -> GLFW.GLFW_MOUSE_BUTTON_5;
            default -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        };
    }
}
