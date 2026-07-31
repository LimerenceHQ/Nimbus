package cc.nimbus.utils.render.skija.data;

import cc.nimbus.utils.render.skija.resource.CachePool;
import cc.nimbus.utils.render.skija.resource.ResourceHelper;
import io.github.humbleui.skija.Data;

public class DataMemory {
    private static final CachePool<String, byte[]> BYTES_CACHE_POOL = new CachePool<>();

    private static final CachePool<String, Data> DATA_CACHE_POOL = new CachePool<>(128) {
        @Override
        protected boolean cleanable(Data value) {
            if (value != null && !value.isClosed()) {
                value.close();
            }
            return true;
        }
    };

    public static Data makeData(String resource) {
        return DATA_CACHE_POOL.build(resource, () -> {
            byte[] bytes = makeBytes(resource);
            return Data.makeFromBytes(bytes);
        });
    }

    public static byte[] makeBytes(String resource) {
        return BYTES_CACHE_POOL.build(resource, () -> ResourceHelper.classpath(resource));
    }

    public static void clear() {
        DATA_CACHE_POOL.clear();
        BYTES_CACHE_POOL.clear();
    }
}
