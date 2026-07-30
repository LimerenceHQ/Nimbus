package cc.nimbus.utils.render.resource;

import java.util.HashMap;
import java.util.LinkedHashMap;

@SuppressWarnings("unused")
public class CachePool<K, V> {
    private final HashMap<K, V> CACHE;

    public CachePool() {
        this(-1);
    }

    public CachePool(int maxSize) {
        if (maxSize < 0) {
            this.CACHE = new HashMap<>();
        } else {
            int initialCapacity = (maxSize == Integer.MAX_VALUE) ? 16 : (int) Math.ceil(maxSize / 0.75f) + 1;
            this.CACHE = new LinkedHashMap<>(initialCapacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(HashMap.Entry<K, V> eldest) {
                    if (size() > maxSize) {
                        return CachePool.this.cleanable(eldest.getValue());
                    }
                    return false;
                }
            };
        }
    }

    public V build(K key, ValueProvider<V> provider) {
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        V v = provider.value();
        CACHE.put(key, v);
        return v;
    }

    public V put(K key, V value) {
        return CACHE.put(key, value);
    }

    public V get(K key) {
        return CACHE.get(key);
    }

    public void clear() {
        CACHE.forEach((key, value) -> cleanable(value));
        CACHE.clear();
    }

    protected boolean cleanable(V value) {
        return false;
    }

    public interface ValueProvider<V> {
        V value();
    }
}
