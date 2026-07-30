package cc.nimbus.utils.render.skija.image;

import cc.nimbus.utils.render.resource.CachePool;
import cc.nimbus.utils.render.skija.data.DataMemory;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.svg.SVGDOM;
import io.github.humbleui.skija.svg.SVGSVG;
import io.github.humbleui.types.Rect;

import java.util.Objects;

/* *
作者：ItzAg2o

* */
@SuppressWarnings({"unused", "DuplicatedCode"})
public class SvgFactory {
    private static final CachePool<Key, Image> SVG_TO_IMAGE_CACHE = new CachePool<>(512) {
        @Override
        protected boolean cleanable(Image value) {
            if (value != null && !value.isClosed()) {
                value.close();
            }
            return true;
        }
    };

    private static SVGDOM makeSvg(String svg) {
        Data data = DataMemory.makeData(svg);
        return new SVGDOM(data);
    }

    private static Image makeSvgToImage(String svg, float width, float height) {
        return SVG_TO_IMAGE_CACHE.build(new Key(svg, width, height), () -> {
            ImageInfo info = new ImageInfo(
                    (int) width,
                    (int) height,
                    ColorType.ALPHA_8,
                    ColorAlphaType.UNPREMUL
            );
            try (Surface surface = Surface.makeRaster(info)) {
                Canvas canvas = surface.getCanvas();
                try (SVGDOM svgDom = makeSvg(svg)) {
                    SVGSVG svgsvg = svgDom.getRoot();
                    if (svgsvg != null) {
                        float intrinsicW = svgsvg.getWidth().getValue();
                        float intrinsicH = svgsvg.getHeight().getValue();
                        if (intrinsicW <= 0) {
                            intrinsicW = 600;
                        }
                        if (intrinsicH <= 0) {
                            intrinsicH = 600;
                        }

                        float scaleX = width / intrinsicW;
                        float scaleY = height / intrinsicH;

                        canvas.scale(scaleX, scaleY);
                        svgsvg.setViewBox(Rect.makeWH(intrinsicW, intrinsicH));
                    }
                    svgDom.render(canvas);
                    return surface.makeImageSnapshot();
                }
            }
        });
    }

    public static void drawSvg(Canvas canvas, String svg, float x, float y, float size) {
        drawSvg(canvas, svg, x, y, size, size);
    }

    public static void drawSvg(Canvas canvas, String svg, float x, float y, float width, float height) {
        try (SVGDOM svgDom = makeSvg(svg)) {
            canvas.save();
            canvas.translate(x, y);
            SVGSVG svgsvg = svgDom.getRoot();
            if (svgsvg != null) {
                float intrinsicW = svgsvg.getWidth().getValue();
                float intrinsicH = svgsvg.getHeight().getValue();
                if (intrinsicW <= 0) {
                    intrinsicW = 600;
                }
                if (intrinsicH <= 0) {
                    intrinsicH = 600;
                }

                float scaleX = width / intrinsicW;
                float scaleY = height / intrinsicH;

                canvas.scale(scaleX, scaleY);
                svgsvg.setViewBox(Rect.makeWH(intrinsicW, intrinsicH));
            }
            svgDom.render(canvas);
            canvas.restore();
        }
    }

    public static void drawSvg(Canvas canvas, String svg, float x, float y) {
        try (SVGDOM svgDom = makeSvg(svg)) {
            SVGSVG svgsvg = svgDom.getRoot();
            canvas.save();
            canvas.translate(x, y);
            svgDom.render(canvas);
            canvas.restore();
        }
    }

    public static void drawSvgFromImage(Canvas canvas, String svg, float x, float y, float size) {
        canvas.drawImageRect(makeSvgToImage(svg, size, size), Rect.makeXYWH(x, y, size, size));
    }

    public static void drawSvgFromImage(Canvas canvas, String svg, float x, float y, float size, Paint paint) {
        canvas.drawImageRect(makeSvgToImage(svg, size, size), Rect.makeXYWH(x, y, size, size), paint);
    }

    public static void drawSvgFromImage(Canvas canvas, String svg, float x, float y, float width, float height) {
        canvas.drawImageRect(makeSvgToImage(svg, width, height), Rect.makeXYWH(x, y, width, height));
    }

    public static void drawSvgFromImage(Canvas canvas, String svg, float x, float y, float width, float height, Paint paint) {
        canvas.drawImageRect(makeSvgToImage(svg, width, height), Rect.makeXYWH(x, y, width, height), paint);
    }

    private static class Key {
        public final String svg;
        public final int widthPx;
        public final int heightPx;

        public Key(String svg, float width, float height) {
            this.svg = svg;
            this.widthPx = (int) (width * 100);
            this.heightPx = (int) (height * 100);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return widthPx == key.widthPx && heightPx == key.heightPx && Objects.equals(svg, key.svg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(svg, widthPx, heightPx);
        }
    }
}
