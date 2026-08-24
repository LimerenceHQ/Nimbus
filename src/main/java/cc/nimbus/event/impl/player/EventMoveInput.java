package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;

public class EventMoveInput extends Cancelable {
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sprint;
    private boolean sneak;

    public EventMoveInput() {
    }

    public EventMoveInput(float forward, float strafe, boolean jump) {
        set(forward, strafe, jump, false,false);
    }

    public EventMoveInput(float forward, float strafe, boolean jump, boolean sprint, boolean sneak) {
        set(forward, strafe, jump, sprint, sneak);
    }

    public EventMoveInput set(float forward, float strafe, boolean jump) {
        return set(forward, strafe, jump, false,false);
    }

    public EventMoveInput set(float forward, float strafe, boolean jump, boolean sprint, boolean sneak) {
        reset();
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sprint = sprint;
        this.sneak = sneak;
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

    public boolean getSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    public boolean getSneak() {
        return sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }
}
