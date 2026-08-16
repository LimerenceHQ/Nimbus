package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;

public class EventMoveInput extends Cancelable {
    private float forward;
    private float strafe;
    private boolean jump;

    public EventMoveInput() {
    }

    public EventMoveInput(float forward, float strafe, boolean jump) {
        set(forward, strafe, jump);
    }

    public EventMoveInput set(float forward, float strafe, boolean jump) {
        reset();
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        return this;
    }

    public float getForward() {
        return forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public boolean getJump() {
        return jump;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }
}
