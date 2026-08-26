package cc.nimbus.features.module.modules.player;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.input.EventMouseButton;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.KeySetting;
import cc.nimbus.utils.client.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        name = "Friend",
        description = "Marks players as friends with a configurable mouse button.",
        category = ModuleCategory.Player,
        enableOnStartUp = true
)
public final class Friend extends BasicModule {
    private static final Set<UUID> MANUAL_FRIENDS = new HashSet<>();
    private final Minecraft minecraft = Minecraft.getInstance();

    public final KeySetting keyMode = new KeySetting("Friend Key", KeySetting.Key.mouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));

    @EventTarget
    private void onMouseButton(EventMouseButton event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !keyMode.matchesMouse(event.getButton())
                || minecraft.screen != null || minecraft.player == null || minecraft.level == null) return;
        if (!(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof Player target)
                || target == minecraft.player) return;

        boolean added = toggleFriend(target);
        String message = Core.INSTANCE.getI18nManager().get(
                added ? "nimbus.module.friend.message.added" : "nimbus.module.friend.message.removed",
                added ? "Added friend: %s" : "Removed friend: %s",
                target.getName().getString());
        MessageUtils.addMessage((added ? "§a" : "§c") + message);
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

}
