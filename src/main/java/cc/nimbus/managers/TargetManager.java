package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.features.module.modules.player.AntiBot;
import cc.nimbus.features.module.modules.player.IRCFriend;
import cc.nimbus.features.module.modules.player.Target;
import cc.nimbus.features.module.modules.player.Teams;
import cc.nimbus.features.rotation.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TargetManager implements InstanceManager {
    private final Minecraft minecraft;
    private final ModuleManager modules;
    private final Map<Double, List<LivingEntity>> rangeCache = new HashMap<>();
    private List<LivingEntity> candidates = List.of();

    public TargetManager(Minecraft minecraft, ModuleManager modules) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.modules = Objects.requireNonNull(modules, "modules");
    }

    @Override
    public synchronized void init() {
        clear();
        EventBus.register(this);
    }

    @Override
    public synchronized void shutdown() {
        EventBus.unregister(this);
        clear();
    }

    public synchronized List<LivingEntity> getCandidates() {
        return candidates;
    }

    public synchronized List<LivingEntity> getTargets(double range) {
        if (minecraft.player == null) return List.of();
        return rangeCache.computeIfAbsent(range, key -> candidates.stream()
                .filter(entity -> RotationMath.distanceToBox(minecraft.player, entity) <= key)
                .toList());
    }

    public synchronized boolean isTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        return accepts(living);
    }

    @EventTarget(priority = Priority.Highest)
    private synchronized void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        rebuild();
    }

    @EventTarget
    private synchronized void onWorldLoad(EventWorldLoad event) {
        clear();
    }

    private void rebuild() {
        rangeCache.clear();
        if (minecraft.level == null || minecraft.player == null) {
            candidates = List.of();
            return;
        }
        List<LivingEntity> next = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && accepts(living)) next.add(living);
        }
        candidates = List.copyOf(next);
    }

    private boolean accepts(LivingEntity entity) {
        if (minecraft.player == null || entity == minecraft.player || !entity.isAlive()
                || entity.isDeadOrDying() || entity.isSpectator()) return false;
        Target target = modules.getModule(Target.class);
        if (target == null || !target.isEnabled()) return false;
        if (!target.invisible.getValue() && entity.isInvisible()) return false;

        AntiBot antiBot = modules.getModule(AntiBot.class);
        if (antiBot != null && antiBot.isBot(entity)) return false;
        Teams teams = modules.getModule(Teams.class);
        if (entity instanceof Player player && teams != null && teams.isSameTeam(minecraft.player, player)) return false;
        if (entity instanceof Player player) {
            IRCFriend friends = modules.getModule(IRCFriend.class);
            if (friends != null && friends.isEnabled() && IRCFriend.isFriend(player)) return false;
            return target.player.getValue();
        }
        if (entity instanceof AbstractGolem) return target.golem.getValue();
        if (entity instanceof Villager) return target.villager.getValue();
        if (entity instanceof Animal) return target.animal.getValue();
        return entity instanceof Mob && target.mob.getValue();
    }

    private void clear() {
        candidates = List.of();
        rangeCache.clear();
    }
}
