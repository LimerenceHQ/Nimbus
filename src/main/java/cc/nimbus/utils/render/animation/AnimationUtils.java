package cc.nimbus.utils.render.animation;

public class AnimationUtils {
    public static float moveUD(float current, float target, float speed) {
        float movement = (target - current) * speed;
        if (movement > 0.0f) {
            movement = Math.max(speed, movement);
            movement = Math.min(target - current, movement);
        } else if (movement < 0.0f) {
            movement = Math.min(-speed, movement);
            movement = Math.max(target - current, movement);
        }
        return current + movement;
    }
}

