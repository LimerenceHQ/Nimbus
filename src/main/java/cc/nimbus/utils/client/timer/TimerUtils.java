package cc.nimbus.utils.client.timer;

public class TimerUtils {
    private long lastTime;
    private long pausedTime;
    private boolean paused;

    private static volatile long cachedTime = System.currentTimeMillis();
    private static volatile long cachedNanoTime = System.nanoTime();

    public static final long SECOND_MS = 1000L;
    public static final long MINUTE_MS = 60000L;
    public static final long HOUR_MS = 3600000L;

    public TimerUtils() {
        this.reset();
    }

    public void reset() {
        this.lastTime = System.currentTimeMillis();
        this.paused = false;
        this.pausedTime = 0;
    }

    public long getTimePassed() {
        if (paused) {
            return pausedTime;
        }
        return System.currentTimeMillis() - this.lastTime;
    }

    public boolean hasTimePassed(long time) {
        if (time <= 0) {
            return true;
        }
        return getTimePassed() >= time;
    }

    public boolean hasTimePassedAndReset(long time) {
        if (hasTimePassed(time)) {
            reset();
            return true;
        }
        return false;
    }

    public long getTime() {
        return getTimePassed();
    }

    public void setTime(final long time) {
        this.lastTime = time;
    }

    public void pause() {
        if (!paused) {
            pausedTime = getTimePassed();
            paused = true;
        }
    }

    public void resume() {
        if (paused) {
            lastTime = System.currentTimeMillis() - pausedTime;
            paused = false;
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public float getSeconds() {
        return getTimePassed() / 1000f;
    }

    public static void updateCachedTime() {
        cachedTime = System.currentTimeMillis();
        cachedNanoTime = System.nanoTime();
    }

    public static long getCachedTime() {
        return cachedTime;
    }

    public static long getCachedNanoTime() {
        return cachedNanoTime;
    }
}
