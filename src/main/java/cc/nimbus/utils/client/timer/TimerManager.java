package cc.nimbus.utils.client.timer;
public final class TimerManager { private static volatile float timerSpeed=1f; private TimerManager(){} public static float getTimerSpeed(){return timerSpeed;} public static void setTimerSpeed(float value){timerSpeed=Math.max(0.01f,value);} }
