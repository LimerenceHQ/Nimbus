package cc.nimbus.utils.render.skija.internal.image;

import cc.nimbus.utils.render.skija.internal.resource.CachePool;
import cc.nimbus.utils.render.skija.internal.resource.ResourceDataCache;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.types.Rect;

public final class SvgTextureCache {
    private static final CachePool<Key, Image> IMAGES = new CachePool<>(512) {
        @Override
        protected void closeValue(Image image) {
            if (image != null && !image.isClosed()) image.close();
        }
    };

    private SvgTextureCache() {
    }

    public static void draw(Canvas canvas, String resource, float x, float y, float size, Paint paint) {
        canvas.drawImageRect(image(resource, size, size), Rect.makeXYWH(x, y, size, size), paint);
    }

    public static void clear() {
        IMAGES.clear();
    }

    private static Image image(String resource, float width, float height) {
        Key key = new Key(resource, width, height);
        return IMAGES.build(key, () -> rasterize(key));
    }

    private static Image rasterize(Key key) {
        ImageInfo info = new ImageInfo(key.width, key.height, ColorType.ALPHA_8, ColorAlphaType.PREMUL);
        try (Surface surface = Surface.makeRaster(info);
             SVGDOM dom = new SVGDOM(ResourceDataCache.data(key.resource))) {
            dom.setContainerSize(key.width, key.height);
            dom.render(surface.getCanvas());
            return surface.makeImageSnapshot();
        }
    }

    private record Key(String resource, int width, int height) {
        private Key(String resource, float width, float height) {
            this(resource, Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)));
        }
    }
}
