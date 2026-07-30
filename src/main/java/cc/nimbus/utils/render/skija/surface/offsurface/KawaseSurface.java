package cc.nimbus.utils.render.skija.surface.offsurface;

import cc.nimbus.utils.render.skija.shader.Shaders;
import cc.nimbus.utils.render.skija.image.minecraft.MinecraftSnapshot;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"SpellCheckingInspection"})
public final class KawaseSurface extends AbstractOffSurface<KawaseSurface.Target> {
    public KawaseSurface(int targetFps) {
        super(targetFps);
    }

    @Override
    protected ImageInfo imageInfo(int width, int height, Target target) {
        return ImageInfo.makeN32Premul(width, height);
    }

    @Override
    protected void onCanvas(DirectContext context, Surface surface, Canvas canvas, @Nullable Path path, @Nullable List<Rect> srcs, @Nullable Target target) {
        if (target == null) {
            return;
        }

        Image snapshot = MinecraftSnapshot.getScreenshot();

        if (snapshot == null || snapshot.isClosed()) {
            return;
        }

        int passes = Math.max(1, target.pass());
        float width = snapshot.getWidth();
        float height = snapshot.getHeight();

        Surface[] pyraSurfaces = new Surface[passes];
        Image[] imagesToClose = new Image[passes];

        try {
            Image currentInput = snapshot;

            for (int i = 0; i < passes; i++) {
                int targetW = (int) Math.max(1, width / Math.pow(2, i + 1));
                int targetH = (int) Math.max(1, height / Math.pow(2, i + 1));

                Surface passSurface = Surface.makeRenderTarget(context, false, ImageInfo.makeN32Premul(targetW, targetH));
                Canvas passCanvas = passSurface.getCanvas();

                float sx = (float) targetW / currentInput.getWidth();
                float sy = (float) targetH / currentInput.getHeight();

                try (Shader child = currentInput.makeShader(Matrix33.makeScale(sx, sy));
                     Shader effect = Shaders.kawase_downsample.setUniform(target.downSample()).makeShader(child);
                     Paint paint = new Paint().setShader(effect)) {
                    passCanvas.drawRect(Rect.makeXYWH(0, 0, targetW, targetH), paint);
                }

                pyraSurfaces[i] = passSurface;
                currentInput = imagesToClose[i] = passSurface.makeImageSnapshot();
            }

            Image upInput = imagesToClose[passes - 1];

            for (int i = passes - 2; i >= 0; i--) {
                Surface destSurface = pyraSurfaces[i];
                Canvas destCanvas = destSurface.getCanvas();
                int destW = destSurface.getWidth();
                int destH = destSurface.getHeight();

                float sx = (float) destW / upInput.getWidth();
                float sy = (float) destH / upInput.getHeight();

                try (Shader child = upInput.makeShader(Matrix33.makeScale(sx, sy));
                     Shader effect = Shaders.kawase_upsample.setUniform(target.upSample()).makeShader(child);
                     Paint paint = new Paint().setShader(effect)) {
                    destCanvas.drawRect(Rect.makeXYWH(0, 0, destW, destH), paint);
                }

                imagesToClose[i].close();
                upInput = imagesToClose[i] = destSurface.makeImageSnapshot();
            }

            float finalSx = width / upInput.getWidth();
            float finalSy = height / upInput.getHeight();
            try (Shader child = upInput.makeShader(Matrix33.makeScale(finalSx, finalSy));
                 Shader effect = Shaders.kawase_upsample.setUniform(target.upSample()).makeShader(child);
                 Paint paint = new Paint().setShader(effect)) {
                canvas.drawRect(Rect.makeXYWH(0, 0, width, height), paint);
            }

        } finally {
            for (Image img : imagesToClose) {
                if (img != null && !img.isClosed()) {
                    img.close();
                }
            }
            for (Surface surf : pyraSurfaces) {
                if (surf != null) {
                    surf.close();
                }
            }
        }
    }

    public record Target(
            int pass,
            float downSample,
            float upSample
    ) {
    }
}
