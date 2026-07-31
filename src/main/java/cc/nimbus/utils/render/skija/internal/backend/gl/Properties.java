package cc.nimbus.utils.render.skija.internal.backend.gl;

import java.util.BitSet;

public class Properties {
    public final int[] lastActiveTexture = new int[1];
    public final int[] lastProgram = new int[1];
    public final int[] lastTexture = new int[1];
    public final int[] lastSampler = new int[1];
    public int[] lastTextureBindings = new int[0];
    public int[] lastSamplerBindings = new int[0];
    public final int[] lastArrayBuffer = new int[1];
    public final int[] lastElementArrayBuffer = new int[1];
    public final int[] lastPixelPackBufferBinding = new int[1];
    public final int[] lastVertexArrayObject = new int[1];
    public final int[] lastDrawFramebuffer = new int[1];
    public final int[] lastReadFramebuffer = new int[1];
    public final int[] lastRenderbuffer = new int[1];
    public final int[] lastPolygonMode = new int[2];
    public final int[] lastCullFaceMode = new int[1];
    public final int[] lastFrontFace = new int[1];
    public final int[] lastDepthFunc = new int[1];
    public final int[] lastViewport = new int[4];
    public final int[] lastScissorBox = new int[4];
    public final int[] lastColorMask = new int[4];
    public final float[] lastBlendColor = new float[4];
    public final float[] lastClearColor = new float[4];
    public final double[] lastClearDepth = new double[1];
    public final int[] lastClearStencil = new int[1];
    public final float[] lastLineWidth = new float[1];
    public final int[] lastBlendSrcRgb = new int[1];
    public final int[] lastBlendDstRgb = new int[1];
    public final int[] lastBlendSrcAlpha = new int[1];
    public final int[] lastBlendDstAlpha = new int[1];
    public final int[] lastBlendEquationRgb = new int[1];
    public final int[] lastBlendEquationAlpha = new int[1];
    public final int[] lastPixelUnpackBufferBinding = new int[1];
    public final int[] lastUnpackAlignment = new int[1];
    public final int[] lastUnpackRowLength = new int[1];
    public final int[] lastUnpackSkipPixels = new int[1];
    public final int[] lastUnpackSkipRows = new int[1];
    public final int[] lastPackSwapBytes = new int[1];
    public final int[] lastPackLsbFirst = new int[1];
    public final int[] lastPackRowLength = new int[1];
    public final int[] lastPackImageHeight = new int[1];
    public final int[] lastPackSkipPixels = new int[1];
    public final int[] lastPackSkipRows = new int[1];
    public final int[] lastPackSkipImages = new int[1];
    public final int[] lastPackAlignment = new int[1];
    public final int[] lastUnpackSwapBytes = new int[1];
    public final int[] lastUnpackLsbFirst = new int[1];
    public final int[] lastUnpackImageHeight = new int[1];
    public final int[] lastUnpackSkipImages = new int[1];

    public final int[] lastStencilFunc = new int[1];
    public final int[] lastStencilRef = new int[1];
    public final int[] lastStencilValueMask = new int[1];
    public final int[] lastStencilWriteMask = new int[1];
    public final int[] lastStencilFail = new int[1];
    public final int[] lastStencilDepthFail = new int[1];
    public final int[] lastStencilDepthPass = new int[1];
    public final int[] lastStencilBackFunc = new int[1];
    public final int[] lastStencilBackRef = new int[1];
    public final int[] lastStencilBackValueMask = new int[1];
    public final int[] lastStencilBackWriteMask = new int[1];
    public final int[] lastStencilBackFail = new int[1];
    public final int[] lastStencilBackDepthFail = new int[1];
    public final int[] lastStencilBackDepthPass = new int[1];

    private final BitSet flags = new BitSet(11);

    public boolean getLastEnableBlend() { return flags.get(0); }
    public void setLastEnableBlend(boolean value) { flags.set(0, value); }
    public boolean getLastEnableCullFace() { return flags.get(1); }
    public void setLastEnableCullFace(boolean value) { flags.set(1, value); }
    public boolean getLastEnableDepthTest() { return flags.get(2); }
    public void setLastEnableDepthTest(boolean value) { flags.set(2, value); }
    public boolean getLastEnableStencilTest() { return flags.get(3); }
    public void setLastEnableStencilTest(boolean value) { flags.set(3, value); }
    public boolean getLastEnableScissorTest() { return flags.get(4); }
    public void setLastEnableScissorTest(boolean value) { flags.set(4, value); }
    public boolean getLastEnablePrimitiveRestart() { return flags.get(5); }
    public void setLastEnablePrimitiveRestart(boolean value) { flags.set(5, value); }
    public boolean getLastDepthMask() { return flags.get(6); }
    public void setLastDepthMask(boolean value) { flags.set(6, value); }
    public boolean getLastFramebufferSrgb() { return flags.get(7); }
    public void setLastFramebufferSrgb(boolean value) { flags.set(7, value); }
    public boolean getLastDither() { return flags.get(8); }
    public void setLastDither(boolean value) { flags.set(8, value); }
    public boolean getLastMultisample() { return flags.get(9); }
    public void setLastMultisample(boolean value) { flags.set(9, value); }
    public boolean getLastColorLogicOp() { return flags.get(10); }
    public void setLastColorLogicOp(boolean value) { flags.set(10, value); }
}
