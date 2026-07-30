package cc.nimbus.utils.render.skija.text;

import cc.nimbus.utils.render.skija.data.DataMemory;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.FontCollection;
import io.github.humbleui.skija.paragraph.TypefaceFontProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/* *
作者：ItzAg2o


本来想换我的新字体渲染的，但是要改一堆东西，算了吧，。
* */
public final class FontFamily {
    private FontFamily() {
    }

    @ApiStatus.Internal
    private static final ArrayList<String> FAMILY_LIST = new ArrayList<>();

    @ApiStatus.Internal
    private static final Map<String, Typeface> TYPEFACE_CACHE = new ConcurrentHashMap<>();

    @ApiStatus.Internal
    private static final Map<FontKey, Font> FONT_CACHE = new ConcurrentHashMap<>(64);

    public static final TypefaceFontProvider FONT_PROVIDER = new TypefaceFontProvider();
    public static final FontCollection FONT_COLLECTION = new FontCollection();

    public static void registerTypeface(String fontPath, String familyName) {
        if (TYPEFACE_CACHE.containsKey(familyName)) {
            System.out.println("[Nimbus-ERROR] Font family already registered: " + familyName);
            return;
        }

        Data data = DataMemory.makeData(fontPath);
        if (data == null || data.isClosed()) {
            System.out.println("[Nimbus-ERROR] Cannot load font: " + fontPath);
            return;
        }

        Typeface typeface = FontMgr.getDefault().makeFromData(data);
        FONT_PROVIDER.registerTypeface(typeface, familyName);
        FAMILY_LIST.add(familyName);
        TYPEFACE_CACHE.put(familyName, typeface);
    }

    public static @NotNull Typeface getTypeface(String familyName) {
        Typeface tf = TYPEFACE_CACHE.get(familyName);
        if (tf == null || tf.isClosed()) {
            return Objects.requireNonNull(
                    FontMgr.getDefault().matchFamilyStyle(null, FontStyle.NORMAL)
            );
        }
        return tf;
    }

    public static Font getCachedFont(String familyName, float size) {
        Typeface tf = getTypeface(familyName);
        FontKey key = new FontKey(tf, size);

        return FONT_CACHE.computeIfAbsent(key, k -> {
            synchronized (FontFamily.class) {
                Font font = new Font(tf, size);
                font.setSubpixel(true);
                font.setEdging(FontEdging.ANTI_ALIAS);
                font.setHinting(FontHinting.SLIGHT);
                return font;
            }
        });
    }

    public static String getFamily(int index) {
        return FAMILY_LIST.get(index);
    }

    public static String getFirstFamily() {
        return FAMILY_LIST.get(0);
    }

    public static String getLastFamily() {
        return FAMILY_LIST.get(FAMILY_LIST.size() - 1);
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
