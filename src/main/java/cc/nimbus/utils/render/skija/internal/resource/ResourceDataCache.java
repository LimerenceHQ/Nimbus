package cc.nimbus.utils.render.skija.internal.resource;

import io.github.humbleui.skija.Data;

/** Bounded native-data cache for classpath resources. */
public final class ResourceDataCache {
    private static final CachePool<String, byte[]> BYTES = new CachePool<>();
    private static final CachePool<String, Data> DATA = new CachePool<>(128) {
        @Override
        protected void closeValue(Data value) {
            if (value != null && !value.isClosed()) value.close();
        }
    };

    private ResourceDataCache() {
    }

    public static Data data(String resource) {
        return DATA.build(resource, () -> Data.makeFromBytes(bytes(resource)));
    }

    public static byte[] bytes(String resource) {
        return BYTES.build(resource, () -> ResourceHelper.classpath(resource));
    }

    public static void clear() {
        DATA.clear();
        BYTES.clear();
    }
}
