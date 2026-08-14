package cc.nimbus.features.module.modules.render;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

@ModuleInfo(name = "BetterThirdPerson", description = "Adds an adjustable, smoothly animated third-person camera.", category = ModuleCategory.Render)
public final class BetterThirdPerson extends BasicModule {
    private static final double SMOOTHING_SPEED = 12.0;

    public final BooleanSetting antiFront = new BooleanSetting("Anti front", false);
    public final BooleanSetting smooth = new BooleanSetting("Smooth", true);
    public final NumberSetting distance = new NumberSetting("Distance", 4, 1, 20, 0.1);

    private float animatedDistance = Float.NaN;
    private long lastUpdateNanos;

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.PRE) return;
        Minecraft minecraft = Minecraft.getInstance();
        CameraType cameraType = minecraft.options.getCameraType();
        if (cameraType == CameraType.FIRST_PERSON && smooth.getValue()) {
            animatedDistance = 1.0f;
            lastUpdateNanos = System.nanoTime();
        }
        if (cameraType == CameraType.THIRD_PERSON_FRONT && antiFront.getValue()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public float renderDistance(float vanillaDistance) {
        float target = distance.getValue().floatValue();
        if (!smooth.getValue()) {
            animatedDistance = target;
            lastUpdateNanos = System.nanoTime();
            return target;
        }

        long now = System.nanoTime();
        if (!Float.isFinite(animatedDistance)) {
            animatedDistance = vanillaDistance;
            lastUpdateNanos = now;
            return animatedDistance;
        }

        double elapsedSeconds = Math.min((now - lastUpdateNanos) / 1_000_000_000.0, 0.05);
        float factor = (float) (1.0 - Math.exp(-SMOOTHING_SPEED * elapsedSeconds));
        animatedDistance += (target - animatedDistance) * factor;
        lastUpdateNanos = now;
        return animatedDistance;
    }

    @Override
    protected void onEnable() {
        resetAnimation();
    }

    @Override
    protected void onDisable() {
        resetAnimation();
    }

    private void resetAnimation() {
        animatedDistance = Float.NaN;
        lastUpdateNanos = 0L;
    }
}
