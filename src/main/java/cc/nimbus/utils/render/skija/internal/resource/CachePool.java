package cc.nimbus.utils.render.skija.internal.resource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

public class CachePool<K, V> {
    private final HashMap<K, V> cache;

    public CachePool() {
        this(-1);
    }

    public CachePool(int maxSize) {
        if (maxSize < 0) {
            this.cache = new HashMap<>();
        } else {
            int initialCapacity = (maxSize == Integer.MAX_VALUE) ? 16 : (int) Math.ceil(maxSize / 0.75f) + 1;
            this.cache = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(HashMap.Entry<K, V> eldest) {
                    if (size() > maxSize) {
                        CachePool.this.closeValue(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };
        }
    }

    public V build(K key, Supplier<? extends V> provider) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        V value = provider.get();
        cache.put(key, value);
        return value;
    }

    public V put(K key, V value) {
        V previous = cache.put(key, value);
        if (previous != null && previous != value) closeValue(previous);
        return previous;
    }

    public V get(K key) {
        return cache.get(key);
    }

    public void clear() {
        cache.values().forEach(this::closeValue);
        cache.clear();
    }

    protected void closeValue(V value) {
    }
}
