package cc.nimbus.utils.helper;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Schedulers {
    private static final int THREADS = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 4));
    private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(THREADS, factory());
    static { EXECUTOR.setRemoveOnCancelPolicy(true); EXECUTOR.setExecuteExistingDelayedTasksAfterShutdownPolicy(false); }
    private Schedulers() {}
    public static ScheduledFuture<?> schedule(Runnable task,long delay,TimeUnit unit){return submitScheduled(() -> EXECUTOR.schedule(Objects.requireNonNull(task),delay,Objects.requireNonNull(unit)));}
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task,long initial,long period,TimeUnit unit){return submitScheduled(() -> EXECUTOR.scheduleAtFixedRate(Objects.requireNonNull(task),initial,period,Objects.requireNonNull(unit)));}
    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,long initial,long delay,TimeUnit unit){return submitScheduled(() -> EXECUTOR.scheduleWithFixedDelay(Objects.requireNonNull(task),initial,delay,Objects.requireNonNull(unit)));}
    public static Future<?> submit(Runnable task){if(EXECUTOR.isShutdown())return null;try{return EXECUTOR.submit(Objects.requireNonNull(task));}catch(RejectedExecutionException ignored){return null;}}
    public static <V> Future<V> submit(Callable<V> task){if(EXECUTOR.isShutdown())return null;try{return EXECUTOR.submit(Objects.requireNonNull(task));}catch(RejectedExecutionException ignored){return null;}}
    public static ScheduledExecutorService executor(){return EXECUTOR;}
    public static boolean isShutdown(){return EXECUTOR.isShutdown();}
    public static void shutdown(){EXECUTOR.shutdown();}
    public static void shutdownNow(){EXECUTOR.shutdownNow();}
    private static <T> T submitScheduled(Callable<T> action){if(EXECUTOR.isShutdown())return null;try{return action.call();}catch(RejectedExecutionException ignored){return null;}catch(Exception error){throw new IllegalStateException(error);}}
    private static ThreadFactory factory(){AtomicInteger index=new AtomicInteger();return task->{Thread thread=new Thread(task,"Nimbus-TaskScheduler-"+index.incrementAndGet());thread.setDaemon(true);return thread;};}
}
