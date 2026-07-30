package cc.nimbus.utils.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.humbleui.skija.Color4f;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import cc.nimbus.utils.render.skija.create.Skia;
import cc.nimbus.utils.math.Vec3d;

import java.awt.Color;
import java.util.Optional;
import java.util.OptionalDouble;

@SuppressWarnings({"unused", "deprecation"})
public final class Render3DUtils {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static boolean savedDepthTest;
    private static boolean savedDepthMask;
    private static boolean savedBlend;
    private static boolean savedCull;
    private static int savedDepthFunc;

    public enum DepthMode { NONE, NORMAL, BOTH, GREATER }

    private Render3DUtils() {}

    public static void saveGLState() {
        savedDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }

    public static void restoreGLState() {
        setEnabled(GL11.GL_DEPTH_TEST, savedDepthTest);
        GL11.glDepthMask(savedDepthMask);
        setEnabled(GL11.GL_BLEND, savedBlend);
        setEnabled(GL11.GL_CULL_FACE, savedCull);
        GL11.glDepthFunc(savedDepthFunc);
    }

    @Deprecated
    public static void setup3D() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    @Deprecated
    public static void end3D() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Deprecated
    public static void setup3DFull() {
        setup3D();
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Deprecated
    public static void end3DFull() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void setup3D(DepthMode mode) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        applyDepthMode(mode);
    }

    public static void end3D(DepthMode mode) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void setup3DWithDepth(DepthMode mode) {
        setup3D(mode);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void end3DWithDepth() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void applyDepthMode(DepthMode mode) {
        switch (mode == null ? DepthMode.NORMAL : mode) {
            case NONE -> {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
            }
            case NORMAL -> {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(true);
            }
            case GREATER -> {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_GREATER);
                GL11.glDepthMask(false);
            }
            case BOTH -> {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(false);
            }
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
    }

    public static void drawBoxOutlineWithDepth(PoseStack stack, AABB box, int argb, float lineWidth, DepthMode mode) {
        if (stack == null || box == null) return;
        if (mode == DepthMode.BOTH) {
            drawBoxOutlineWithDepth(stack, box, withAlpha(argb, 0x40), lineWidth, DepthMode.GREATER);
            drawBoxOutlineWithDepth(stack, box, argb, lineWidth, DepthMode.NORMAL);
            return;
        }
        float[] b = getRelativeAABB(box);
        drawLines(stack, mode, lineWidth, sink -> emitBoxEdges(sink, b, argb));
    }

    public static void drawFilledBoxWithDepth(PoseStack stack, AABB box, int argb, DepthMode mode) {
        if (stack == null || box == null) return;
        if (mode == DepthMode.BOTH) {
            drawFilledBoxWithDepth(stack, box, withAlpha(argb, 0x20), DepthMode.GREATER);
            drawFilledBoxWithDepth(stack, box, argb, DepthMode.NORMAL);
            return;
        }
        float[] b = getRelativeAABB(box);
        drawQuads(stack, mode, sink -> emitBoxFaces(sink, b, argb));
    }

    public static void drawBoxOutlinesWithDepth(PoseStack stack, Iterable<AABB> boxes,
                                                 int argb, float lineWidth, DepthMode mode) {
        if (stack == null || boxes == null) return;
        if (mode == DepthMode.BOTH) {
            drawBoxOutlinesWithDepth(stack, boxes, withAlpha(argb, 0x40), lineWidth, DepthMode.GREATER);
            drawBoxOutlinesWithDepth(stack, boxes, argb, lineWidth, DepthMode.NORMAL);
            return;
        }
        java.util.List<float[]> relative = new java.util.ArrayList<>();
        for (AABB box : boxes) if (box != null) relative.add(getRelativeAABB(box));
        if (relative.isEmpty()) return;
        drawLines(stack, mode, lineWidth, sink -> {
            for (float[] box : relative) emitBoxEdges(sink, box, argb);
        });
    }

    public static void drawFilledBoxesWithDepth(PoseStack stack, Iterable<AABB> boxes,
                                                int argb, DepthMode mode) {
        if (stack == null || boxes == null) return;
        if (mode == DepthMode.BOTH) {
            drawFilledBoxesWithDepth(stack, boxes, withAlpha(argb, 0x20), DepthMode.GREATER);
            drawFilledBoxesWithDepth(stack, boxes, argb, DepthMode.NORMAL);
            return;
        }
        java.util.List<float[]> relative = new java.util.ArrayList<>();
        for (AABB box : boxes) if (box != null) relative.add(getRelativeAABB(box));
        if (relative.isEmpty()) return;
        drawQuads(stack, mode, sink -> {
            for (float[] box : relative) emitBoxFaces(sink, box, argb);
        });
    }

    public static void drawLineWithDepth(PoseStack stack, double x1, double y1, double z1,
                                         double x2, double y2, double z2, int argb,
                                         float lineWidth, DepthMode mode) {
        if (stack == null) return;
        if (mode == DepthMode.BOTH) {
            drawLineWithDepth(stack, x1, y1, z1, x2, y2, z2, withAlpha(argb, 0x40), lineWidth, DepthMode.GREATER);
            drawLineWithDepth(stack, x1, y1, z1, x2, y2, z2, argb, lineWidth, DepthMode.NORMAL);
            return;
        }
        float[] a = getRelativeCoords(x1, y1, z1);
        float[] b = getRelativeCoords(x2, y2, z2);
        drawLines(stack, mode, lineWidth, sink -> sink.line(a[0], a[1], a[2], b[0], b[1], b[2], argb));
    }

    public static void drawRingWithDepth(PoseStack stack, double cx, double cy, double cz,
                                         float radius, int segments, int argb,
                                         float lineWidth, DepthMode mode) {
        if (stack == null || radius <= 0f) return;
        int count = Math.max(3, segments);
        if (mode == DepthMode.BOTH) {
            drawRingWithDepth(stack, cx, cy, cz, radius, count, withAlpha(argb, 0x40), lineWidth, DepthMode.GREATER);
            drawRingWithDepth(stack, cx, cy, cz, radius, count, argb, lineWidth, DepthMode.NORMAL);
            return;
        }
        float[] c = getRelativeCoords(cx, cy, cz);
        drawLines(stack, mode, lineWidth, sink -> {
            for (int i = 0; i < count; i++) {
                double a1 = Math.PI * 2 * i / count;
                double a2 = Math.PI * 2 * (i + 1) / count;
                sink.line(c[0] + (float) Math.cos(a1) * radius, c[1], c[2] + (float) Math.sin(a1) * radius,
                        c[0] + (float) Math.cos(a2) * radius, c[1], c[2] + (float) Math.sin(a2) * radius, argb);
            }
        });
    }

    public static void renderESPBox(PoseStack stack, AABB box, int color, float lineWidth) {
        drawBoxOutlineWithDepth(stack, box, color, lineWidth, DepthMode.NONE);
    }

    public static void renderESPFilledBox(PoseStack stack, AABB box, int color) {
        drawFilledBoxWithDepth(stack, box, color, DepthMode.NONE);
    }

    public static void renderThroughWallBox(PoseStack stack, AABB box, int color, float lineWidth) {
        drawBoxOutlineWithDepth(stack, box, color, lineWidth, DepthMode.NONE);
    }

    public static void renderNormalBox(PoseStack stack, AABB box, int color, float lineWidth) {
        drawBoxOutlineWithDepth(stack, box, color, lineWidth, DepthMode.NORMAL);
    }

    public static void drawLine(float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
        drawLineWithDepth(new PoseStack(), x1, y1, z1, x2, y2, z2, argb, width, DepthMode.NONE);
    }

    public static void drawRing(float cx, float cy, float cz, float radius, int segments, int argb, float width) {
        drawRingWithDepth(new PoseStack(), cx, cy, cz, radius, segments, argb, width, DepthMode.NONE);
    }

    public static void drawNursultan(float cx, float cy, float cz, float radius, int segments, int argb, float width) {
        drawRing(cx, cy, cz, radius, segments, argb, width);
    }

    public static void renderTargetStrafe(float cx, float cy, float cz, float radius, int segments, int argb, float width) {
        drawRing(cx, cy, cz, radius, segments, argb, width);
    }

    public static void renderBlock(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int argb, float width) {
        renderESPBox(new PoseStack(), new AABB(minX, minY, minZ, maxX, maxY, maxZ), argb, width);
    }

    public static Vec3 getCameraPos() {
        var camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        return camera == null ? Vec3.ZERO : camera.position();
    }

    public static float[] getRelativeCoords(double x, double y, double z) {
        Vec3 camera = getCameraPos();
        return new float[]{(float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z)};
    }

    public static float[] getRelativeAABB(AABB box) {
        Vec3 camera = getCameraPos();
        return new float[]{(float) (box.minX - camera.x), (float) (box.minY - camera.y), (float) (box.minZ - camera.z),
                (float) (box.maxX - camera.x), (float) (box.maxY - camera.y), (float) (box.maxZ - camera.z)};
    }

    public static void setFilledBoxVertexes(PoseStack stack, LocalPlayer player, Color color) {
        if (player != null && color != null) setFilledBoxVertexes(stack, player.getBoundingBox(), color.getRGB());
    }

    public static void setFilledBoxVertexes(PoseStack stack, AABB box, int argb) {
        drawFilledBoxWithDepth(stack, box, argb, DepthMode.NONE);
    }

    public static void setFilledBoxVertexes(PoseStack stack, AABB box, int red, int green, int blue, int alpha) {
        setFilledBoxVertexes(stack, box, argb(red, green, blue, alpha));
    }

    public static void renderOutlinedShadedBox(PoseStack stack, AABB box, Color color,
                                                int shadedAlpha, float lineWidth) {
        if (color != null) renderOutlinedShadedBox(stack, box, color.getRGB(), shadedAlpha, lineWidth);
    }

    public static void renderOutlinedShadedBox(PoseStack stack, Vec3 position, Color color,
                                                int shadedAlpha, float lineWidth) {
        if (position == null) return;
        renderOutlinedShadedBox(stack, new AABB(position.x - .5, position.y, position.z - .5,
                position.x + .5, position.y + 1, position.z + .5), color, shadedAlpha, lineWidth);
    }

    public static void renderOutlinedShadedBox(PoseStack stack, AABB box, int argb,
                                                int shadedAlpha, float lineWidth) {
        drawFilledBoxWithDepth(stack, box, withAlpha(argb, shadedAlpha), DepthMode.NONE);
        drawBoxOutlineWithDepth(stack, box, argb, lineWidth, DepthMode.NONE);
    }

    public static void drawBoxOutlineBuffered(PoseStack stack, AABB box, int argb, float lineWidth) {
        drawBoxOutlineWithDepth(stack, box, argb, lineWidth, DepthMode.NONE);
    }

    public static void drawBoxOutlineBuffered(PoseStack stack, AABB box, int red, int green, int blue,
                                              int alpha, float lineWidth) {
        drawBoxOutlineBuffered(stack, box, argb(red, green, blue, alpha), lineWidth);
    }

    public static void drawTextureLocationZoom(Identifier location, float x, float y, float width, float height, float zoom) {
        Image image = Render2DUtils.getCachedImage(location);
        if (image == null) image = Render2DUtils.getGlImage(location);
        if (image == null || Skia.INSTANCE.getCanvas() == null) return;
        float scale = Math.max(.01f, zoom);
        float sourceW = image.getWidth() / scale;
        float sourceH = image.getHeight() / scale;
        float sourceX = (image.getWidth() - sourceW) / 2f;
        float sourceY = (image.getHeight() - sourceH) / 2f;
        try (Paint paint = new Paint()) {
            Skia.INSTANCE.getCanvas().drawImageRect(image,
                    Rect.makeXYWH(sourceX, sourceY, sourceW, sourceH),
                    Rect.makeXYWH(x, y, width, height), SamplingMode.LINEAR, paint, true);
        }
    }

    public static void drawSphereWireBuffered(PoseStack stack, double cx, double cy, double cz,
                                               float radius, int rings, int segments, int argb) {
        int ringCount = Math.max(2, rings);
        int segmentCount = Math.max(3, segments);
        for (int ring = 1; ring < ringCount; ring++) {
            double phi = -Math.PI / 2 + Math.PI * ring / ringCount;
            float ringRadius = (float) (radius * Math.cos(phi));
            double y = cy + radius * Math.sin(phi);
            drawRingWithDepth(stack, cx, y, cz, ringRadius, segmentCount, argb, 1f, DepthMode.NONE);
        }
        drawLines(stack, DepthMode.NONE, 1f, sink -> {
            float[] c = getRelativeCoords(cx, cy, cz);
            for (int segment = 0; segment < segmentCount; segment++) {
                double theta = Math.PI * 2 * segment / segmentCount;
                for (int ring = 0; ring < ringCount; ring++) {
                    double p1 = -Math.PI / 2 + Math.PI * ring / ringCount;
                    double p2 = -Math.PI / 2 + Math.PI * (ring + 1) / ringCount;
                    sink.line(c[0] + (float) (radius * Math.cos(p1) * Math.cos(theta)), c[1] + (float) (radius * Math.sin(p1)), c[2] + (float) (radius * Math.cos(p1) * Math.sin(theta)),
                            c[0] + (float) (radius * Math.cos(p2) * Math.cos(theta)), c[1] + (float) (radius * Math.sin(p2)), c[2] + (float) (radius * Math.cos(p2) * Math.sin(theta)), argb);
                }
            }
        });
    }

    public static void drawRingBuffered(PoseStack stack, double cx, double cy, double cz,
                                        float radius, int segments, int argb) {
        drawRingWithDepth(stack, cx, cy, cz, radius, segments, argb, 1f, DepthMode.NONE);
    }

    public static void drawRingBandBuffered(PoseStack stack, double cx, double cy, double cz,
                                            float radius, float thickness, int segments, int argb) {
        int count = Math.max(3, segments);
        float inner = Math.max(0f, radius - thickness / 2f);
        float outer = radius + thickness / 2f;
        float[] c = getRelativeCoords(cx, cy, cz);
        drawQuads(stack, DepthMode.NONE, sink -> {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                double b = Math.PI * 2 * (i + 1) / count;
                sink.quad(c[0] + (float) Math.cos(a) * outer, c[1], c[2] + (float) Math.sin(a) * outer,
                        c[0] + (float) Math.cos(b) * outer, c[1], c[2] + (float) Math.sin(b) * outer,
                        c[0] + (float) Math.cos(b) * inner, c[1], c[2] + (float) Math.sin(b) * inner,
                        c[0] + (float) Math.cos(a) * inner, c[1], c[2] + (float) Math.sin(a) * inner, argb);
            }
        });
    }

    public static void drawSphereFilledBuffered(PoseStack stack, double cx, double cy, double cz,
                                                 float radius, int rings, int segments, int argb) {
        int ringCount = Math.max(2, rings);
        int segmentCount = Math.max(3, segments);
        float[] c = getRelativeCoords(cx, cy, cz);
        drawQuads(stack, DepthMode.NONE, sink -> {
            for (int ring = 0; ring < ringCount; ring++) {
                double p1 = -Math.PI / 2 + Math.PI * ring / ringCount;
                double p2 = -Math.PI / 2 + Math.PI * (ring + 1) / ringCount;
                for (int segment = 0; segment < segmentCount; segment++) {
                    double t1 = Math.PI * 2 * segment / segmentCount;
                    double t2 = Math.PI * 2 * (segment + 1) / segmentCount;
                    sink.quad(sx(c[0], radius, p1, t1), sy(c[1], radius, p1), sz(c[2], radius, p1, t1),
                            sx(c[0], radius, p1, t2), sy(c[1], radius, p1), sz(c[2], radius, p1, t2),
                            sx(c[0], radius, p2, t2), sy(c[1], radius, p2), sz(c[2], radius, p2, t2),
                            sx(c[0], radius, p2, t1), sy(c[1], radius, p2), sz(c[2], radius, p2, t1), argb);
                }
            }
        });
    }

    public static void drawLineBuffered(PoseStack stack, double x1, double y1, double z1,
                                        double x2, double y2, double z2, int argb, float lineWidth) {
        drawLineWithDepth(stack, x1, y1, z1, x2, y2, z2, argb, lineWidth, DepthMode.NONE);
    }

    public static void renderTargetCircle(PoseStack stack, Vec3d lastPos, Vec3d pos, boolean floating,
                                          double radius, Color4f color, float partialTicks) {
        if (stack == null || lastPos == null || pos == null || color == null) return;
        double wave = floating ? Math.sin(System.currentTimeMillis() / 200.0) : 0.0;
        double x = lastPos.getX() + (pos.getX() - lastPos.getX()) * partialTicks;
        double y = lastPos.getY() + (pos.getY() - lastPos.getY()) * partialTicks + 1 + wave;
        double z = lastPos.getZ() + (pos.getZ() - lastPos.getZ()) * partialTicks;
        int opaque = argb((int) (color.getR() * 255), (int) (color.getG() * 255), (int) (color.getB() * 255), (int) (color.getA() * 255));
        int transparent = opaque & 0x00FFFFFF;
        float[] c = getRelativeCoords(x, y, z);
        int count = 64;
        float drop = (float) wave * .2f;
        drawQuads(stack, DepthMode.NONE, sink -> {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                double b = Math.PI * 2 * (i + 1) / count;
                float ax = c[0] + (float) Math.cos(a) * (float) radius;
                float az = c[2] + (float) Math.sin(a) * (float) radius;
                float bx = c[0] + (float) Math.cos(b) * (float) radius;
                float bz = c[2] + (float) Math.sin(b) * (float) radius;
                sink.quad(ax, c[1] - drop, az, bx, c[1] - drop, bz, bx, c[1], bz, ax, c[1], az,
                        opaque, opaque, transparent, transparent);
            }
        });
    }

    private static void drawQuads(PoseStack stack, DepthMode mode, QuadEmitter emitter) {
        drawMesh(stack, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR, pipeline(false, mode),
                builder -> emitter.emit(new QuadSink(builder, stack.last().pose())));
    }

    private static void drawLines(PoseStack stack, DepthMode mode, float width, LineEmitter emitter) {
        drawMesh(stack, PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, pipeline(true, mode),
                builder -> emitter.emit(new LineSink(builder, stack.last().pose(), width)));
    }

    private static void drawMesh(PoseStack stack, PrimitiveTopology topology, VertexFormat format,
                                 RenderPipeline pipeline, BuilderEmitter emitter) {
        RenderSystem.assertOnRenderThread();
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(256 * 1024)) {
            BufferBuilder builder = new BufferBuilder(allocator, topology, format);
            emitter.emit(builder);
            MeshData mesh = builder.build();
            if (mesh == null) return;
            try (mesh; GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "Nimbus 3D vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
                var indices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                GpuBuffer indexBuffer = indices.getBuffer(mesh.drawState().indexCount());
                GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
                        IDENTITY, new Vector4f(1, 1, 1, 1), new Vector3f(),
                        TextureTransform.DEFAULT_TEXTURING.createMatrix());
                RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Nimbus 3D", target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", transform);
                    pass.setVertexBuffer(0, new GpuBufferSlice(vertices, 0, vertices.size()));
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    pass.drawIndexed(mesh.drawState().indexCount(), 1, 0, 0, 0);
                }
            }
        }
    }

    private static RenderPipeline pipeline(boolean lines, DepthMode mode) {
        if (lines) return switch (mode) {
            case NORMAL -> Pipelines.LINES_NORMAL;
            case GREATER -> Pipelines.LINES_GREATER;
            default -> Pipelines.LINES_ALWAYS;
        };
        return switch (mode) {
            case NORMAL -> Pipelines.QUADS_NORMAL;
            case GREATER -> Pipelines.QUADS_GREATER;
            default -> Pipelines.QUADS_ALWAYS;
        };
    }

    private static final class Pipelines {
        private static final RenderPipeline QUADS_ALWAYS = quad("quads_always", CompareOp.ALWAYS_PASS);
        private static final RenderPipeline QUADS_NORMAL = quad("quads_normal", CompareOp.LESS_THAN_OR_EQUAL);
        private static final RenderPipeline QUADS_GREATER = quad("quads_greater", CompareOp.GREATER_THAN);
        private static final RenderPipeline LINES_ALWAYS = line("lines_always", CompareOp.ALWAYS_PASS);
        private static final RenderPipeline LINES_NORMAL = line("lines_normal", CompareOp.LESS_THAN_OR_EQUAL);
        private static final RenderPipeline LINES_GREATER = line("lines_greater", CompareOp.GREATER_THAN);

        private static RenderPipeline quad(String name, CompareOp compare) {
            return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("nimbus", "pipeline/" + name))
                    .withDepthStencilState(new DepthStencilState(compare, false)).withCull(false).build();
        }

        private static RenderPipeline line(String name, CompareOp compare) {
            return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("nimbus", "pipeline/" + name))
                    .withDepthStencilState(new DepthStencilState(compare, false)).withCull(false).build();
        }
    }

    private static void emitBoxEdges(LineSink s, float[] b, int color) {
        float x0=b[0], y0=b[1], z0=b[2], x1=b[3], y1=b[4], z1=b[5];
        s.line(x0,y0,z0,x1,y0,z0,color); s.line(x1,y0,z0,x1,y0,z1,color);
        s.line(x1,y0,z1,x0,y0,z1,color); s.line(x0,y0,z1,x0,y0,z0,color);
        s.line(x0,y1,z0,x1,y1,z0,color); s.line(x1,y1,z0,x1,y1,z1,color);
        s.line(x1,y1,z1,x0,y1,z1,color); s.line(x0,y1,z1,x0,y1,z0,color);
        s.line(x0,y0,z0,x0,y1,z0,color); s.line(x1,y0,z0,x1,y1,z0,color);
        s.line(x1,y0,z1,x1,y1,z1,color); s.line(x0,y0,z1,x0,y1,z1,color);
    }

    private static void emitBoxFaces(QuadSink s, float[] b, int color) {
        float x0=b[0], y0=b[1], z0=b[2], x1=b[3], y1=b[4], z1=b[5];
        s.quad(x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0,color);
        s.quad(x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,color);
        s.quad(x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,color);
        s.quad(x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,color);
        s.quad(x0,y0,z1,x0,y1,z1,x1,y1,z1,x1,y0,z1,color);
        s.quad(x0,y0,z0,x0,y1,z0,x0,y1,z1,x0,y0,z1,color);
    }

    private record QuadSink(BufferBuilder builder, Matrix4f matrix) {
        void vertex(float x,float y,float z,int c) { builder.addVertex(matrix,x,y,z).setColor(c); }
        void quad(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,int c) {
            quad(x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4,c,c,c,c);
        }
        void quad(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,int c1,int c2,int c3,int c4) {
            vertex(x1,y1,z1,c1); vertex(x2,y2,z2,c2); vertex(x3,y3,z3,c3); vertex(x4,y4,z4,c4);
        }
    }

    private record LineSink(BufferBuilder builder, Matrix4f matrix, float width) {
        void line(float x1,float y1,float z1,float x2,float y2,float z2,int c) {
            float nx=x2-x1, ny=y2-y1, nz=z2-z1;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(length < 1e-5f) return;
            nx/=length; ny/=length; nz/=length;
            builder.addVertex(matrix,x1,y1,z1).setColor(c).setNormal(nx,ny,nz).setLineWidth(width);
            builder.addVertex(matrix,x2,y2,z2).setColor(c).setNormal(nx,ny,nz).setLineWidth(width);
        }
    }

    private interface BuilderEmitter { void emit(BufferBuilder builder); }
    private interface QuadEmitter { void emit(QuadSink sink); }
    private interface LineEmitter { void emit(LineSink sink); }

    private static int argb(int r,int g,int b,int a) { return (clamp(a)<<24)|(clamp(r)<<16)|(clamp(g)<<8)|clamp(b); }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static int withAlpha(int color,int alpha) { return (color & 0x00FFFFFF) | (clamp(alpha) << 24); }
    private static float sx(float c,float r,double p,double t) { return c+(float)(r*Math.cos(p)*Math.cos(t)); }
    private static float sy(float c,float r,double p) { return c+(float)(r*Math.sin(p)); }
    private static float sz(float c,float r,double p,double t) { return c+(float)(r*Math.cos(p)*Math.sin(t)); }
}
