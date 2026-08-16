package cc.nimbus.features.module.modules.render;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.render.EventRender3D;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.utils.render.Render3DUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@ModuleInfo(name = "Particles", description = "Adds textured ambient particles around the player.", category = ModuleCategory.Render)
public final class Particles extends BasicModule {
    private static final Identifier FIREFLY_TEXTURE = texture("firefly.png");
    private static final Identifier SNOWFLAKE_TEXTURE = texture("snowflake.png");
    private static final Identifier STAR_TEXTURE = texture("star.png");
    private static final Identifier HEART_TEXTURE = texture("heart.png");
    private static final Identifier DOLLAR_TEXTURE = texture("dollar.png");
    private static final int MAX_SPAWN_PER_TICK = 16;
    private static final int FIREFLY_TRAIL_LENGTH = 10;

    public final BooleanSetting fireflies = new BooleanSetting("Fireflies", true);
    public final NumberSetting fireflyCount = new NumberSetting("Firefly count", 30, 5, 120, 1,
            fireflies::getValue);
    public final NumberSetting fireflySize = new NumberSetting("Firefly size", 1.0, 0.1, 2.0, 0.1,
            fireflies::getValue);
    public final ModeSetting mode = new ModeSetting("Mode",
            new String[]{"Off", "Snowflakes", "Stars", "Hearts", "Dollars", "Bloom"}, "Off");
    public final NumberSetting count = new NumberSetting("Count", 100, 20, 500, 10,
            () -> !mode.is("Off"));
    public final NumberSetting size = new NumberSetting("Size", 1.0, 0.1, 6.0, 0.1,
            () -> !mode.is("Off"));
    public final ModeSetting movement = new ModeSetting("Movement", new String[]{"Drop", "Fly"}, "Drop",
            () -> !mode.is("Off"));
    public final NumberSetting radius = new NumberSetting("Radius", 48, 4, 64, 1);
    public final NumberSetting height = new NumberSetting("Height", 48, 2, 64, 1);
    public final NumberSetting speed = new NumberSetting("Speed", 0.08, 0.01, 0.4, 0.01);
    public final ModeSetting colorMode = new ModeSetting("Color mode", new String[]{"Sync", "Custom"}, "Sync");
    public final ColorSetting color = new ColorSetting("Color", 0xFF37B1BA, () -> colorMode.is("Custom"), false);

    private final RandomSource random = RandomSource.create();
    private final List<AmbientParticle> fireflyParticles = new ArrayList<>();
    private final List<AmbientParticle> particles = new ArrayList<>();
    private ClientLevel particleLevel;

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            clearParticles();
            return;
        }
        if (particleLevel != level) {
            clearParticles();
            particleLevel = level;
        }

        tickParticles(fireflyParticles, level, player, true);
        tickParticles(particles, level, player, false);
        if (fireflies.getValue()) {
            fillParticles(fireflyParticles, fireflyCount.getValue().intValue(), level, player, true);
        } else {
            fireflyParticles.clear();
        }
        if (!mode.is("Off")) {
            fillParticles(particles, count.getValue().intValue(), level, player, false);
        } else {
            particles.clear();
        }
    }

    @EventTarget
    private void onRender3D(EventRender3D event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long time = System.currentTimeMillis();
        if (!fireflyParticles.isEmpty()) {
            Render3DUtils.drawBillboards(event.getPoseStack(), FIREFLY_TEXTURE,
                    billboards(fireflyParticles, fireflySize.getValue().floatValue(),
                            event.getPartialTick(), time, true));
        }
        if (!particles.isEmpty() && !mode.is("Off")) {
            List<Render3DUtils.Billboard> shapes = billboards(
                    particles, size.getValue().floatValue(), event.getPartialTick(), time, false);
            Identifier texture = selectedTexture();
            if (!mode.is("Bloom")) {
                Render3DUtils.drawAdditiveBillboards(event.getPoseStack(), texture, glowBillboards(shapes));
            }
            Render3DUtils.drawBillboards(event.getPoseStack(), texture, shapes);
        }
    }

    private List<Render3DUtils.Billboard> glowBillboards(List<Render3DUtils.Billboard> shapes) {
        List<Render3DUtils.Billboard> glows = new ArrayList<>(shapes.size() * 3);
        for (Render3DUtils.Billboard shape : shapes) {
            int alpha = shape.argb() >>> 24;
            appendGlow(glows, shape, alpha, 1.10f, 0.22f);
            appendGlow(glows, shape, alpha, 1.22f, 0.12f);
            appendGlow(glows, shape, alpha, 1.36f, 0.06f);
        }
        return glows;
    }

    private void appendGlow(List<Render3DUtils.Billboard> glows, Render3DUtils.Billboard shape,
                            int alpha, float scale, float opacity) {
        int glowAlpha = Math.clamp(Math.round(alpha * opacity), 0, 255);
        if (glowAlpha == 0) return;
        glows.add(new Render3DUtils.Billboard(
                shape.x(), shape.y(), shape.z(), shape.size() * scale,
                (glowAlpha << 24) | (shape.argb() & 0x00FFFFFF)));
    }

    private void tickParticles(List<AmbientParticle> values, ClientLevel level, Player player, boolean firefly) {
        double maxDistance = radius.getValue().doubleValue() * 2.5;
        double maxDistanceSquared = maxDistance * maxDistance;
        boolean drop = !firefly && movement.is("Drop");
        values.removeIf(particle -> particle.tick(level, firefly, drop)
                || player.distanceToSqr(particle.x, particle.y, particle.z) > maxDistanceSquared);
    }

    private void fillParticles(List<AmbientParticle> values, int target, ClientLevel level,
                               Player player, boolean firefly) {
        int spawnCount = Math.min(Math.max(0, target - values.size()), MAX_SPAWN_PER_TICK);
        for (int index = 0; index < spawnCount; index++) {
            AmbientParticle particle = createParticle(level, player, firefly);
            if (particle != null) values.add(particle);
        }
    }

    private AmbientParticle createParticle(ClientLevel level, Player player, boolean firefly) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * radius.getValue().doubleValue();
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getY() + (firefly ? 1.0 : 2.0)
                    + random.nextDouble() * height.getValue().doubleValue();
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;

            double configuredSpeed = speed.getValue().doubleValue();
            boolean drop = !firefly && movement.is("Drop");
            double velocityScale = firefly ? configuredSpeed * 0.5 : configuredSpeed;
            double velocityX = drop ? 0.0 : randomBetween(-velocityScale, velocityScale);
            double velocityY = drop
                    ? randomBetween(-configuredSpeed, -configuredSpeed * 0.35)
                    : randomBetween(-velocityScale * 0.4, velocityScale * 0.8);
            double velocityZ = drop ? 0.0 : randomBetween(-velocityScale, velocityScale);
            int lifetime = firefly ? randomBetween(120, 260) : randomBetween(100, 240);
            return new AmbientParticle(x, y, z, velocityX, velocityY, velocityZ, lifetime);
        }
        return null;
    }

    private List<Render3DUtils.Billboard> billboards(List<AmbientParticle> values, float particleSize,
                                                     float partialTick, long time, boolean firefly) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        List<AmbientParticle> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingDouble((AmbientParticle particle) -> particle.distanceSquared(camera)).reversed());
        List<Render3DUtils.Billboard> result = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            AmbientParticle particle = sorted.get(index);
            float alpha = particle.alpha();
            if (alpha <= 0.01f) continue;
            int rgb = selectedColor(index, time) & 0x00FFFFFF;
            if (firefly && !particle.trail.isEmpty()) {
                int trailIndex = 0;
                int trailSize = particle.trail.size();
                for (TrailPoint point : particle.trail) {
                    float progress = (trailIndex + 1.0f) / (trailSize + 1.0f);
                    float trailAlpha = alpha * progress * progress * 0.22f;
                    int trailArgb = (Math.clamp(Math.round(trailAlpha * 255.0f), 0, 255) << 24) | rgb;
                    result.add(new Render3DUtils.Billboard(point.x, point.y, point.z,
                            particleSize * (0.28f + progress * 0.42f), trailArgb));
                    trailIndex++;
                }
            }
            float mainAlpha = alpha * (firefly ? 0.48f : 0.72f);
            int argb = (Math.clamp(Math.round(mainAlpha * 255.0f), 0, 255) << 24) | rgb;
            result.add(new Render3DUtils.Billboard(
                    lerp(partialTick, particle.previousX, particle.x),
                    lerp(partialTick, particle.previousY, particle.y),
                    lerp(partialTick, particle.previousZ, particle.z),
                    particleSize,
                    argb));
        }
        return result;
    }

    private int selectedColor(int index, long time) {
        if (colorMode.is("Custom")) return color.getValue();
        return Core.INSTANCE.getConfigManager().clientSettings().accentColor(index, time);
    }

    private Identifier selectedTexture() {
        return switch (mode.getValue()) {
            case "Snowflakes" -> SNOWFLAKE_TEXTURE;
            case "Stars" -> STAR_TEXTURE;
            case "Hearts" -> HEART_TEXTURE;
            case "Dollars" -> DOLLAR_TEXTURE;
            default -> FIREFLY_TEXTURE;
        };
    }

    private double randomBetween(double minimum, double maximum) {
        return minimum + (maximum - minimum) * random.nextDouble();
    }

    private int randomBetween(int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static double lerp(float delta, double start, double end) {
        return start + (end - start) * delta;
    }

    @Override
    protected void onDisable() {
        clearParticles();
    }

    private void clearParticles() {
        fireflyParticles.clear();
        particles.clear();
        particleLevel = null;
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("nimbus", "textures/particles/" + name);
    }

    private static final class AmbientParticle {
        private double previousX;
        private double previousY;
        private double previousZ;
        private double x;
        private double y;
        private double z;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private int age;
        private final int maximumAge;
        private final Deque<TrailPoint> trail = new ArrayDeque<>(FIREFLY_TRAIL_LENGTH);

        private AmbientParticle(double x, double y, double z, double velocityX, double velocityY,
                                double velocityZ, int maximumAge) {
            this.previousX = this.x = x;
            this.previousY = this.y = y;
            this.previousZ = this.z = z;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.maximumAge = maximumAge;
            this.age = maximumAge;
        }

        private boolean tick(ClientLevel level, boolean firefly, boolean drop) {
            previousX = x;
            previousY = y;
            previousZ = z;
            if (firefly) {
                trail.addLast(new TrailPoint(x, y, z));
                while (trail.size() > FIREFLY_TRAIL_LENGTH) trail.removeFirst();
            }
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) return true;
            if (firefly) {
                velocityX *= 0.99;
                velocityY *= 0.99;
                velocityZ *= 0.99;
            } else if (drop) {
                velocityX *= 0.96;
                velocityY -= 0.0015;
                velocityZ *= 0.96;
            } else {
                velocityX *= 0.96;
                velocityY *= 0.98;
                velocityZ *= 0.96;
            }
            return --age <= 0;
        }

        private float alpha() {
            int elapsed = maximumAge - age;
            float fadeIn = Math.min(1.0f, elapsed / 15.0f);
            float fadeOut = Math.min(1.0f, age / 20.0f);
            return Math.min(fadeIn, fadeOut);
        }

        private double distanceSquared(Vec3 point) {
            double dx = x - point.x;
            double dy = y - point.y;
            double dz = z - point.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record TrailPoint(double x, double y, double z) {}
}
