package cc.nimbus.utils.render.animation.impl;

import cc.nimbus.utils.render.animation.Animation;
import cc.nimbus.utils.render.animation.Direction;

public class DecelerateAnimation extends Animation {
    public DecelerateAnimation(int ms, double endPoint) {
        super(ms, endPoint);
    }

    public DecelerateAnimation(int ms, double initialValue, double endPoint) {
        super(ms, initialValue, endPoint);
    }

    public DecelerateAnimation(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }

    public DecelerateAnimation(int ms, double initialValue, double endPoint, Direction direction) {
        super(ms, initialValue, endPoint, direction);
    }

    @Override
    protected double getEquation(double x) {
        return 1.0 - Math.pow(1.0 - x, 2.0);
    }
}

