package cc.nimbus.utils.render.animation.impl;


import cc.nimbus.utils.render.animation.Animation;
import cc.nimbus.utils.render.animation.Direction;

public class EaseBackAnimation extends Animation
{
    private final double easeAmount;

    public EaseBackAnimation(int ms, double endPoint, double easeAmount) {
        super(ms, endPoint);
        this.easeAmount = easeAmount;
    }

    public EaseBackAnimation(int ms, double endPoint, double easeAmount, Direction direction) {
        super(ms, endPoint, direction);
        this.easeAmount = easeAmount;
    }

    protected boolean correctOutput() {
        return true;
    }

    protected double getEquation(double x) {
        final double shrink = this.easeAmount + 1.0f;
        return Math.max(0.0, 1.0 + shrink * Math.pow(x - 1.0, 3.0) + this.easeAmount * Math.pow(x - 1.0, 2.0));
    }
}

