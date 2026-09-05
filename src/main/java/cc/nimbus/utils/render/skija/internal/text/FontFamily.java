package cc.nimbus.utils.render.skija.internal.text;

import cc.nimbus.utils.render.font.FontFace;
import cc.nimbus.utils.render.font.FontSpec;
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
    private static final Map<FontFace, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();
    private static final Map<FontFace, FontFace> FALLBACKS = new ConcurrentHashMap<>();
    private static final Map<VariantKey, Typeface> VARIANT_TYPEFACE_CACHE =
            new LinkedHashMap<>(64, 0.75f, true);

    @ApiStatus.Internal
    private static final int MAX_FONTS = 256;
    private static final LinkedHashMap<FontKey, Font> FONT_CACHE = new LinkedHashMap<>(64, 0.75f, true);

    private static final TypefaceFontProvider FONT_PROVIDER = new TypefaceFontProvider();
    private static final FontCollection FONT_COLLECTION = new FontCollection();
    private static boolean closed;

    public static synchronized void initialize() {
        ensureOpen();
    }

    public static synchronized void registerTypeface(String fontPath, FontFace familyName) {
        ensureOpen();
        Objects.requireNonNull(fontPath, "fontPath");
        Objects.requireNonNull(familyName, "familyName");
        if (fontPath.isBlank()) throw new IllegalArgumentException("fontPath cannot be blank");
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
            FONT_PROVIDER.registerTypeface(typeface, familyName.name());
            TYPEFACE_CACHE.put(familyName, typeface);
        } catch (RuntimeException error) {
            if (!typeface.isClosed()) typeface.close();
            throw new IllegalStateException("Cannot register font family " + familyName + " from " + fontPath, error);
        }
    }

    static @NotNull Typeface getTypeface(FontFace familyName) {
        ensureOpen();
        Objects.requireNonNull(familyName, "familyName");
        Typeface tf = TYPEFACE_CACHE.get(familyName);
        if (tf == null) throw new IllegalArgumentException("Font is not registered: " + familyName);
        if (tf.isClosed()) throw new IllegalStateException("Font is closed: " + familyName);
        return tf;
    }

    public static synchronized Font getCachedFont(FontSpec fontSpec, float size) {
        Objects.requireNonNull(fontSpec, "fontSpec");
        if (!Float.isFinite(size) || size <= 0f) {
            throw new IllegalArgumentException("Font size must be finite and positive: " + size);
        }
        FontKey key = new FontKey(fontSpec, Float.floatToIntBits(size));
        Font cached = FONT_CACHE.get(key);
        if (cached != null && !cached.isClosed()) return cached;
        if (cached != null) {
            FONT_CACHE.remove(key);
            closeManaged(null, cached);
            releaseUnusedVariant(variantKey(key.fontSpec()));
        }

        Font font = new Font(getVariantTypeface(fontSpec), size);
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
            releaseUnusedVariant(variantKey(eldest.getKey().fontSpec()));
        }
        return font;
    }

    public static synchronized void registerFallback(FontFace familyName, FontFace fallbackFamily) {
        ensureOpen();
        getTypeface(familyName);
        getTypeface(fallbackFamily);
        FALLBACKS.put(familyName, fallbackFamily);
    }

    public static FontSpec fallback(FontSpec fontSpec) {
        FontFace fallback = FALLBACKS.get(fontSpec.family());
        return fallback == null ? null : new FontSpec(fallback, fontSpec.weight());
    }

    public static synchronized void close() {
        if (closed) return;
        closed = true;

        RuntimeException failure = null;
        for (Font font : FONT_CACHE.values()) {
            failure = closeManaged(failure, font);
        }
        FONT_CACHE.clear();
        for (Typeface typeface : VARIANT_TYPEFACE_CACHE.values()) {
            if (!isBaseTypeface(typeface)) failure = closeManaged(failure, typeface);
        }
        VARIANT_TYPEFACE_CACHE.clear();
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

    private static synchronized Typeface getVariantTypeface(FontSpec fontSpec) {
        VariantKey key = new VariantKey(fontSpec.family(), Float.floatToIntBits(fontSpec.weight()));
        Typeface cached = VARIANT_TYPEFACE_CACHE.get(key);
        if (cached != null && !cached.isClosed()) return cached;

        Typeface base = getTypeface(fontSpec.family());
        Typeface variant = base.makeClone(new FontVariation("wght", fontSpec.weight()));
        VARIANT_TYPEFACE_CACHE.put(key, variant);
        return variant;
    }

    private static VariantKey variantKey(FontSpec fontSpec) {
        return new VariantKey(fontSpec.family(), Float.floatToIntBits(fontSpec.weight()));
    }

    private static void releaseUnusedVariant(VariantKey key) {
        for (FontKey fontKey : FONT_CACHE.keySet()) {
            if (variantKey(fontKey.fontSpec()).equals(key)) return;
        }
        Typeface typeface = VARIANT_TYPEFACE_CACHE.remove(key);
        if (typeface != null && !isBaseTypeface(typeface)) closeManaged(null, typeface);
    }

    private static boolean isBaseTypeface(Typeface candidate) {
        for (Typeface base : TYPEFACE_CACHE.values()) {
            if (base == candidate) return true;
        }
        return false;
    }

    private record FontKey(FontSpec fontSpec, int sizeBits) {}

    private record VariantKey(FontFace family, int weightBits) {}

    static {
        FONT_COLLECTION.setDefaultFontManager(FontMgr.getDefault());
        FONT_COLLECTION.setAssetFontManager(FONT_PROVIDER);

        registerTypeface("assets/nimbus/fonts/Geist-VariableFont_wght.ttf", FontFace.GEIST);
        registerTypeface("assets/nimbus/fonts/Inter-VariableFont_opsz,wght.ttf", FontFace.INTER);
        registerTypeface("assets/nimbus/fonts/NotoSansSC-VariableFont_wght.ttf", FontFace.NOTO_SANS_SC);

        registerFallback(FontFace.GEIST, FontFace.NOTO_SANS_SC);
        registerFallback(FontFace.INTER, FontFace.NOTO_SANS_SC);
    }
}
