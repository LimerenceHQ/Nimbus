package cc.nimbus.utils.render.animation;

import cc.nimbus.utils.client.timer.TimerUtils;

public abstract class Animation
{
    protected TimerUtils timerUtil;
    protected int duration;
    protected double endPoint;
    protected Direction direction;

    protected double startPoint;

    public Animation(int ms, double initialValue, double endPoint, Direction direction) {
        this.timerUtil = new TimerUtils();
        this.duration = ms;
        this.startPoint = initialValue;
        this.endPoint = endPoint;
        this.direction = direction;
    }

    public Animation(int ms, double initialValue, double endPoint) {
        this(ms, initialValue, endPoint, Direction.FORWARDS);
    }

    public Animation(int ms, double endPoint) {
        this(ms, 0.0, endPoint, Direction.FORWARDS);
    }

    public Animation(int ms, double endPoint, Direction direction) {
        this(ms, 0.0, endPoint, direction);
    }

    public boolean finished(Direction direction) {
        return this.isDone() && this.direction == direction;
    }

    public void setEndPoint(double endPoint) {
        if (this.endPoint != endPoint) {
            this.startPoint = getValue().doubleValue();
            this.endPoint = endPoint;
            this.timerUtil.reset();
        }
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            this.startPoint = getValue().doubleValue();
            this.direction = direction;
            this.timerUtil.reset();
        }
    }

    public Number getValue() {
        if (duration <= 0 || isDone()) {
            return direction.forwards() ? endPoint : 0.0;
        }

        double progress = Math.min(1.0, timerUtil.getTime() / (double) duration);
        double x = getEquation(progress);

        double target = direction.forwards() ? endPoint : 0.0;
        return startPoint + (target - startPoint) * x;
    }

    public double getOutput() {
        return getValue().doubleValue();
    }

    public boolean isDone() {
        return timerUtil.hasTimePassed(duration);
    }

    protected abstract double getEquation(double x);

    public void reset() {
        this.timerUtil.reset();
    }

    public Direction getDirection() {
        return direction;
    }
}
