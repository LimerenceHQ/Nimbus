package cc.nimbus.event;

import cc.nimbus.event.Cancelable;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.handler.Listener;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public final class EventBus {
    private static final Map<Class<? extends Cancelable>, ListenerData[]> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Cancelable>, List<ListenerData>> MUTABLE = new ConcurrentHashMap<>();
    private static final Set<Object> SUBSCRIBERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, ProfileCounter> LISTENER_PROFILE = new ConcurrentHashMap<>();
    private static final Map<String, ProfileCounter> EVENT_PROFILE = new ConcurrentHashMap<>();
    private static volatile boolean profiling;
    private static volatile long profileStart;

    private EventBus() {}

    public static synchronized void register(Object subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        if (!SUBSCRIBERS.add(subscriber)) return;
        for (Class<?> type = subscriber.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) registerMethod(subscriber, method);
            for (Field field : type.getDeclaredFields()) registerField(subscriber, field);
        }
    }

    private static void registerMethod(Object subscriber, Method method) {
        EventTarget target = method.getAnnotation(EventTarget.class);
        if (target == null || method.isBridge() || method.isSynthetic() || method.getReturnType() != void.class || method.getParameterCount() != 1) return;
        Class<?> parameter = method.getParameterTypes()[0];
        if (!Cancelable.class.isAssignableFrom(parameter)) return;
        try {
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(subscriber);
            add(cast(parameter), subscriber, event -> invoke(handle, event), target.priority().getValue(), method.getDeclaringClass().getName() + "#" + method.getName());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot register event handler " + method, e);
        }
    }

    private static void registerField(Object subscriber, Field field) {
        EventTarget target = field.getAnnotation(EventTarget.class);
        if (target == null || !Listener.class.isAssignableFrom(field.getType())) return;
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized) || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> eventType) || !Cancelable.class.isAssignableFrom(eventType)) return;
        try {
            field.setAccessible(true);
            Listener<?> listener = (Listener<?>) field.get(subscriber);
            if (listener != null) add(cast(eventType), subscriber, event -> invokeListener(listener, event), target.priority().getValue(), field.getDeclaringClass().getName() + "#" + field.getName());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot register event listener " + field, e);
        }
    }

    @SuppressWarnings("unchecked") private static Class<? extends Cancelable> cast(Class<?> type) { return (Class<? extends Cancelable>) type; }
    private static void invoke(MethodHandle handle, Cancelable event) { try { handle.invoke(event); } catch (Throwable error) { error.printStackTrace(); } }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void invokeListener(Listener listener, Cancelable event) { listener.handle(event); }

    private static void add(Class<? extends Cancelable> event, Object parent, Consumer<Cancelable> consumer, int priority, String name) {
        List<ListenerData> listeners = MUTABLE.computeIfAbsent(event, key -> new ArrayList<>());
        listeners.add(new ListenerData(parent, consumer, priority, name));
        listeners.sort(Comparator.comparingInt(ListenerData::priority));
        REGISTRY.put(event, listeners.toArray(ListenerData[]::new));
    }

    public static synchronized void unregister(Object subscriber) {
        if (!SUBSCRIBERS.remove(subscriber)) return;
        for (var entry : MUTABLE.entrySet()) {
            entry.getValue().removeIf(listener -> listener.parent() == subscriber);
            REGISTRY.put(entry.getKey(), entry.getValue().toArray(ListenerData[]::new));
        }
    }

    public static <T extends Cancelable> T call(T event) {
        ListenerData[] listeners = REGISTRY.get(event.getClass());
        if (listeners == null) return event;
        long eventStart = profiling ? System.nanoTime() : 0;
        for (ListenerData listener : listeners) {
            if (event.isCancel()) break;
            long start = profiling ? System.nanoTime() : 0;
            listener.consumer().accept(event);
            if (profiling) LISTENER_PROFILE.computeIfAbsent(listener.name(), key -> new ProfileCounter()).record(System.nanoTime() - start);
        }
        if (profiling) EVENT_PROFILE.computeIfAbsent(event.getClass().getName(), key -> new ProfileCounter()).record(System.nanoTime() - eventStart);
        return event;
    }

    public static boolean hasListeners(Class<? extends Cancelable> type) { ListenerData[] data = REGISTRY.get(type); return data != null && data.length > 0; }
    public static void startProfiling() { resetProfiling(); profileStart = System.nanoTime(); profiling = true; }
    public static void stopProfiling() { profiling = false; }
    public static boolean isProfiling() { return profiling; }
    public static void resetProfiling() { LISTENER_PROFILE.clear(); EVENT_PROFILE.clear(); profileStart = 0; }
    public static ProfileSnapshot getProfileSnapshot(int limit) { return new ProfileSnapshot(profiling, profileStart == 0 ? 0 : System.nanoTime() - profileStart, top(EVENT_PROFILE, limit), top(LISTENER_PROFILE, limit)); }
    private static List<ProfileEntry> top(Map<String, ProfileCounter> values, int limit) { return values.entrySet().stream().map(e -> e.getValue().entry(e.getKey())).sorted(Comparator.comparingLong(ProfileEntry::totalNanos).reversed()).limit(Math.max(1, limit)).toList(); }
    public static synchronized void clear() { REGISTRY.clear(); MUTABLE.clear(); SUBSCRIBERS.clear(); resetProfiling(); }

    private record ListenerData(Object parent, Consumer<Cancelable> consumer, int priority, String name) {}
    private static final class ProfileCounter {
        private final LongAdder calls = new LongAdder(); private final LongAdder total = new LongAdder(); private final AtomicLong max = new AtomicLong();
        void record(long nanos) { calls.increment(); total.add(nanos); max.accumulateAndGet(nanos, Math::max); }
        ProfileEntry entry(String name) { return new ProfileEntry(name, calls.sum(), total.sum(), max.get()); }
    }
    public record ProfileEntry(String name, long calls, long totalNanos, long maxNanos) { public long averageNanos() { return calls == 0 ? 0 : totalNanos / calls; } }
    public record ProfileSnapshot(boolean running, long durationNanos, List<ProfileEntry> events, List<ProfileEntry> listeners) {}
}
