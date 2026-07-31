package cc.nimbus.utils.render.skija.internal.text;

import cc.nimbus.utils.render.skija.internal.resource.ResourceDataCache;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FontFamily {
    private FontFamily() {
    }

    @ApiStatus.Internal
    private static final Map<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    private static final int MAX_FONTS = 256;
    private static final LinkedHashMap<FontKey, Font> FONT_CACHE = new LinkedHashMap<>(64, 0.75f, true);

    public static final TypefaceFontProvider FONT_PROVIDER = new TypefaceFontProvider();
    public static final FontCollection FONT_COLLECTION = new FontCollection();

    public static void registerTypeface(String fontPath, String familyName) {
        if (TYPEFACE_CACHE.containsKey(familyName)) {
            System.out.println("[Nimbus-ERROR] Font family already registered: " + familyName);
            return;
        }

        Data data = ResourceDataCache.data(fontPath);
        if (data == null || data.isClosed()) {
            System.out.println("[Nimbus-ERROR] Cannot load font: " + fontPath);
            return;
        }

        Typeface typeface = FontMgr.getDefault().makeFromData(data);
        FONT_PROVIDER.registerTypeface(typeface, familyName);
        TYPEFACE_CACHE.put(familyName, typeface);
    }

    public static @NotNull Typeface getTypeface(String familyName) {
        Typeface tf = TYPEFACE_CACHE.get(familyName);
        if (tf == null) throw new IllegalArgumentException("Font is not registered: " + familyName);
        if (tf.isClosed()) throw new IllegalStateException("Font is closed: " + familyName);
        return tf;
    }

    public static synchronized Font getCachedFont(String familyName, float size) {
        Typeface tf = getTypeface(familyName);
        FontKey key = new FontKey(tf, size);
        Font cached = FONT_CACHE.get(key);
        if (cached != null && !cached.isClosed()) return cached;

        Font font = new Font(tf, size);
        font.setSubpixel(true);
        font.setEdging(FontEdging.ANTI_ALIAS);
        font.setHinting(FontHinting.SLIGHT);
        FONT_CACHE.put(key, font);
        while (FONT_CACHE.size() > MAX_FONTS) {
            Map.Entry<FontKey, Font> eldest = FONT_CACHE.entrySet().iterator().next();
            FONT_CACHE.remove(eldest.getKey());
            if (!eldest.getValue().isClosed()) eldest.getValue().close();
        }
        return font;
    }

    private static class FontKey {
        public final Typeface typeface;
        public final int size100;

        public FontKey(Typeface tf, float size) {
            this.typeface = tf;
            this.size100 = (int) (size * 100);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(typeface) + size100;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FontKey k)) return false;
            return k.typeface == typeface && k.size100 == size100;
        }
    }

    static {
        FONT_COLLECTION.setDefaultFontManager(FontMgr.getDefault());
        FONT_COLLECTION.setAssetFontManager(FONT_PROVIDER);

        registerTypeface("assets/nimbus/fonts/HarmonyOS_Sans_SC-Medium.ttf", "HarmonyOS_Sans_SC-Medium");
        registerTypeface("assets/nimbus/fonts/HarmonyOS_Sans_SC-Regular.ttf", "HarmonyOS_Sans_SC-Regular");

        registerTypeface("assets/nimbus/fonts/SarasaUiSC-Bold.ttf", "SarasaUiSC-Bold");
        registerTypeface("assets/nimbus/fonts/SarasaUiSC-Regular.ttf", "SarasaUiSC-Regular");
        registerTypeface("assets/nimbus/fonts/SarasaUiSC-SemiBold.ttf", "SarasaUiSC-SemiBold");
        registerTypeface("assets/nimbus/fonts/SarasaUiSC-Light.ttf", "SarasaUiSC-Light");
    }
}
