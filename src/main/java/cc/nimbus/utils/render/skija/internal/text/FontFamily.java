package cc.nimbus.utils.render.skija.internal.text;

import cc.nimbus.utils.render.skija.internal.resource.ResourceDataCache;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FontFamily {
    private FontFamily() {
    }

    @ApiStatus.Internal
    private static final Map<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> FALLBACKS = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    private static final int MAX_FONTS = 256;
    private static final LinkedHashMap<FontKey, Font> FONT_CACHE = new LinkedHashMap<>(64, 0.75f, true);

    private static final TypefaceFontProvider FONT_PROVIDER = new TypefaceFontProvider();
    private static final FontCollection FONT_COLLECTION = new FontCollection();
    private static boolean closed;

    public static synchronized void initialize() {
        ensureOpen();
    }

    public static synchronized void registerTypeface(String fontPath, String familyName) {
        ensureOpen();
        Objects.requireNonNull(fontPath, "fontPath");
        Objects.requireNonNull(familyName, "familyName");
        if (fontPath.isBlank()) throw new IllegalArgumentException("fontPath cannot be blank");
        if (familyName.isBlank()) throw new IllegalArgumentException("familyName cannot be blank");
        if (TYPEFACE_CACHE.containsKey(familyName)) {
            throw new IllegalStateException("Font family is already registered: " + familyName);
        }

        Data data = ResourceDataCache.data(fontPath);
        if (data == null || data.isClosed()) {
            throw new IllegalStateException("Cannot load font resource: " + fontPath);
        }

        Typeface typeface = FontMgr.getDefault().makeFromData(data);
        if (typeface == null) {
            throw new IllegalStateException("Skija rejected font resource " + fontPath + " for family " + familyName);
        }
        try {
            FONT_PROVIDER.registerTypeface(typeface, familyName);
            TYPEFACE_CACHE.put(familyName, typeface);
        } catch (RuntimeException error) {
            if (!typeface.isClosed()) typeface.close();
            throw new IllegalStateException("Cannot register font family " + familyName + " from " + fontPath, error);
        }
    }

    public static @NotNull Typeface getTypeface(String familyName) {
        ensureOpen();
        Objects.requireNonNull(familyName, "familyName");
        Typeface tf = TYPEFACE_CACHE.get(familyName);
        if (tf == null) throw new IllegalArgumentException("Font is not registered: " + familyName);
        if (tf.isClosed()) throw new IllegalStateException("Font is closed: " + familyName);
        return tf;
    }

    public static synchronized Font getCachedFont(String familyName, float size) {
        if (!Float.isFinite(size) || size <= 0f) {
            throw new IllegalArgumentException("Font size must be finite and positive: " + size);
        }
        Typeface tf = getTypeface(familyName);
        FontKey key = new FontKey(tf, size);
        Font cached = FONT_CACHE.get(key);
        if (cached != null && !cached.isClosed()) return cached;

        Font font = new Font(tf, size);
        font.setSubpixel(true);

        font.setMetricsLinear(true);
        font.setBaselineSnapped(false);

        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        font.setHinting(FontHinting.FULL);
        FONT_CACHE.put(key, font);
        while (FONT_CACHE.size() > MAX_FONTS) {
            Map.Entry<FontKey, Font> eldest = FONT_CACHE.entrySet().iterator().next();
            FONT_CACHE.remove(eldest.getKey());
            if (!eldest.getValue().isClosed()) eldest.getValue().close();
        }
        return font;
    }

    public static synchronized void registerFallback(String familyName, String fallbackFamily) {
        ensureOpen();
        getTypeface(familyName);
        getTypeface(fallbackFamily);
        FALLBACKS.put(familyName, fallbackFamily);
    }

    public static String fallbackFamily(String familyName) {
        return FALLBACKS.get(familyName);
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;

        RuntimeException failure = null;
        for (Font font : FONT_CACHE.values()) {
            failure = closeManaged(failure, font);
        }
        FONT_CACHE.clear();
        failure = closeManaged(failure, FONT_COLLECTION);
        failure = closeManaged(failure, FONT_PROVIDER);
        for (Typeface typeface : TYPEFACE_CACHE.values()) {
            failure = closeManaged(failure, typeface);
        }
        TYPEFACE_CACHE.clear();
        FALLBACKS.clear();
        if (failure != null) throw failure;
    }

    private static void ensureOpen() {
        if (closed) throw new IllegalStateException("Font subsystem is closed");
    }

    private static RuntimeException closeManaged(RuntimeException failure, io.github.humbleui.skija.impl.Managed value) {
        try {
            if (value != null && !value.isClosed()) value.close();
        } catch (RuntimeException error) {
            if (failure == null) return error;
            failure.addSuppressed(error);
        }
        return failure;
    }

    private static class FontKey {
        public final Typeface typeface;
        public final int sizeBits;

        public FontKey(Typeface tf, float size) {
            this.typeface = tf;
            this.sizeBits = Float.floatToIntBits(size);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(typeface) + sizeBits;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FontKey k)) return false;
            return k.typeface == typeface && k.sizeBits == sizeBits;
        }
    }

    static {
        FONT_COLLECTION.setDefaultFontManager(FontMgr.getDefault());
        FONT_COLLECTION.setAssetFontManager(FONT_PROVIDER);

        registerTypeface("assets/nimbus/fonts/Geist-SemiBold.ttf", "Geist-SemiBold");
        registerTypeface("assets/nimbus/fonts/Inter_18pt-Regular.ttf", "Inter-Regular");
        registerTypeface("assets/nimbus/fonts/Inter_18pt-Medium.ttf", "Inter-Medium");
        registerTypeface("assets/nimbus/fonts/Inter_18pt-SemiBold.ttf", "Inter-SemiBold");
        registerTypeface("assets/nimbus/fonts/NotoSans-Regular.ttf", "NotoSans-Regular");
        registerTypeface("assets/nimbus/fonts/NotoSans-Medium.ttf", "NotoSans-Medium");
        registerTypeface("assets/nimbus/fonts/NotoSansCJKsc-Regular.otf", "NotoSansCJKsc-Regular");
        registerTypeface("assets/nimbus/fonts/NotoSansCJKsc-Medium.otf", "NotoSansCJKsc-Medium");

        registerFallback("Geist-SemiBold", "NotoSansCJKsc-Medium");
        registerFallback("Inter-Regular", "NotoSansCJKsc-Regular");
        registerFallback("Inter-Medium", "NotoSansCJKsc-Medium");
        registerFallback("Inter-SemiBold", "NotoSansCJKsc-Medium");
        registerFallback("NotoSans-Regular", "NotoSansCJKsc-Regular");
        registerFallback("NotoSans-Medium", "NotoSansCJKsc-Medium");
    }
}
