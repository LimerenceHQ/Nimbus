package cc.nimbus.utils.render.animation.impl;

import cc.nimbus.utils.render.animation.Animation;
import cc.nimbus.utils.render.animation.Direction;

public class SmoothStepAnimation extends Animation
{
    public SmoothStepAnimation(int ms, double endPoint) {
        super(ms, endPoint);
    }
    
    public SmoothStepAnimation(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }
    
    protected double getEquation(double x) {
        return -2.0 * Math.pow(x, 3.0) + 3.0 * Math.pow(x, 2.0);
    }
}

