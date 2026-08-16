package cc.nimbus.utils.render.skija.internal.backend.gl;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL45.*;

public class State {
    private final int glVersion;
    private final Properties props = new Properties();

    public State(int glVersion) {
        this.glVersion = glVersion;
    }

    public State push() {
        glGetIntegerv(GL_ACTIVE_TEXTURE, props.lastActiveTexture);
        int textureUnits = Math.max(1, Math.min(32, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)));
        props.lastTextureBindings = new int[textureUnits];
        props.lastSamplerBindings = new int[textureUnits];
        boolean samplerObjects = glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects;
        for (int unit = 0; unit < textureUnits; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            props.lastTextureBindings[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
            if (samplerObjects) props.lastSamplerBindings[unit] = glGetInteger(GL_SAMPLER_BINDING);
        }
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_CURRENT_PROGRAM, props.lastProgram);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, props.lastTexture);
        if (samplerObjects) glGetIntegerv(GL_SAMPLER_BINDING, props.lastSampler);
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, props.lastArrayBuffer);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, props.lastVertexArrayObject);
        glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, props.lastElementArrayBuffer);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, props.lastDrawFramebuffer);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, props.lastReadFramebuffer);
        glGetIntegerv(GL_RENDERBUFFER_BINDING, props.lastRenderbuffer);
        if (glVersion >= 200) glGetIntegerv(GL_POLYGON_MODE, props.lastPolygonMode);
        glGetIntegerv(GL_CULL_FACE_MODE, props.lastCullFaceMode);
        glGetIntegerv(GL_FRONT_FACE, props.lastFrontFace);
        glGetIntegerv(GL_DEPTH_FUNC, props.lastDepthFunc);
        glGetIntegerv(GL_VIEWPORT, props.lastViewport);
        glGetIntegerv(GL_SCISSOR_BOX, props.lastScissorBox);
        glGetIntegerv(GL_COLOR_WRITEMASK, props.lastColorMask);
        glGetFloatv(0x8005, props.lastBlendColor);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, props.lastClearColor);
        glGetDoublev(GL_DEPTH_CLEAR_VALUE, props.lastClearDepth);
        glGetIntegerv(GL_STENCIL_CLEAR_VALUE, props.lastClearStencil);
        glGetFloatv(GL_LINE_WIDTH, props.lastLineWidth);
        glGetIntegerv(GL_BLEND_SRC_RGB, props.lastBlendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, props.lastBlendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, props.lastBlendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, props.lastBlendDstAlpha);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, props.lastBlendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, props.lastBlendEquationAlpha);
        props.setLastEnableBlend(glIsEnabled(GL_BLEND));
        props.setLastEnableCullFace(glIsEnabled(GL_CULL_FACE));
        props.setLastEnableDepthTest(glIsEnabled(GL_DEPTH_TEST));
        props.setLastEnableStencilTest(glIsEnabled(GL_STENCIL_TEST));
        props.setLastEnableScissorTest(glIsEnabled(GL_SCISSOR_TEST));
        if (glVersion >= 310) props.setLastEnablePrimitiveRestart(glIsEnabled(GL_PRIMITIVE_RESTART));
        props.setLastDepthMask(glGetBoolean(GL_DEPTH_WRITEMASK));
        props.setLastFramebufferSrgb(glIsEnabled(GL_FRAMEBUFFER_SRGB));
        props.setLastDither(glIsEnabled(GL_DITHER));
        props.setLastMultisample(glIsEnabled(GL_MULTISAMPLE));
        props.setLastColorLogicOp(glIsEnabled(GL_COLOR_LOGIC_OP));

        glGetIntegerv(GL_STENCIL_FUNC, props.lastStencilFunc);
        glGetIntegerv(GL_STENCIL_REF, props.lastStencilRef);
        glGetIntegerv(GL_STENCIL_VALUE_MASK, props.lastStencilValueMask);
        glGetIntegerv(GL_STENCIL_WRITEMASK, props.lastStencilWriteMask);
        glGetIntegerv(GL_STENCIL_FAIL, props.lastStencilFail);
        glGetIntegerv(GL_STENCIL_PASS_DEPTH_FAIL, props.lastStencilDepthFail);
        glGetIntegerv(GL_STENCIL_PASS_DEPTH_PASS, props.lastStencilDepthPass);
        glGetIntegerv(GL_STENCIL_BACK_FUNC, props.lastStencilBackFunc);
        glGetIntegerv(GL_STENCIL_BACK_REF, props.lastStencilBackRef);
        glGetIntegerv(GL_STENCIL_BACK_VALUE_MASK, props.lastStencilBackValueMask);
        glGetIntegerv(GL_STENCIL_BACK_WRITEMASK, props.lastStencilBackWriteMask);
        glGetIntegerv(GL_STENCIL_BACK_FAIL, props.lastStencilBackFail);
        glGetIntegerv(GL_STENCIL_BACK_PASS_DEPTH_FAIL, props.lastStencilBackDepthFail);
        glGetIntegerv(GL_STENCIL_BACK_PASS_DEPTH_PASS, props.lastStencilBackDepthPass);

        glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, props.lastPixelPackBufferBinding);
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, props.lastPixelUnpackBufferBinding);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glGetIntegerv(GL_PACK_SWAP_BYTES, props.lastPackSwapBytes);
        glGetIntegerv(GL_PACK_LSB_FIRST, props.lastPackLsbFirst);
        glGetIntegerv(GL_PACK_ROW_LENGTH, props.lastPackRowLength);
        glGetIntegerv(GL_PACK_SKIP_PIXELS, props.lastPackSkipPixels);
        glGetIntegerv(GL_PACK_SKIP_ROWS, props.lastPackSkipRows);
        glGetIntegerv(GL_PACK_ALIGNMENT, props.lastPackAlignment);
        glGetIntegerv(GL_UNPACK_SWAP_BYTES, props.lastUnpackSwapBytes);
        glGetIntegerv(GL_UNPACK_LSB_FIRST, props.lastUnpackLsbFirst);
        glGetIntegerv(GL_UNPACK_ALIGNMENT, props.lastUnpackAlignment);
        glGetIntegerv(GL_UNPACK_ROW_LENGTH, props.lastUnpackRowLength);
        glGetIntegerv(GL_UNPACK_SKIP_PIXELS, props.lastUnpackSkipPixels);
        glGetIntegerv(GL_UNPACK_SKIP_ROWS, props.lastUnpackSkipRows);
        if (glVersion >= 120) {
            glGetIntegerv(GL_PACK_IMAGE_HEIGHT, props.lastPackImageHeight);
            glGetIntegerv(GL_PACK_SKIP_IMAGES, props.lastPackSkipImages);
            glGetIntegerv(GL_UNPACK_IMAGE_HEIGHT, props.lastUnpackImageHeight);
            glGetIntegerv(GL_UNPACK_SKIP_IMAGES, props.lastUnpackSkipImages);
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        return this;
    }

    public State pop() {
        glUseProgram(props.lastProgram[0]);
        boolean samplerObjects = glVersion >= 330 || GL.getCapabilities().GL_ARB_sampler_objects;
        for (int unit = 0; unit < props.lastTextureBindings.length; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, props.lastTextureBindings[unit]);
            if (samplerObjects) glBindSampler(unit, props.lastSamplerBindings[unit]);
        }
        glActiveTexture(props.lastActiveTexture[0]);
        glBindVertexArray(props.lastVertexArrayObject[0]);
        glBindBuffer(GL_ARRAY_BUFFER, props.lastArrayBuffer[0]);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, props.lastElementArrayBuffer[0]);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, props.lastDrawFramebuffer[0]);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, props.lastReadFramebuffer[0]);
        glBindRenderbuffer(GL_RENDERBUFFER, props.lastRenderbuffer[0]);
        glBlendEquationSeparate(props.lastBlendEquationRgb[0], props.lastBlendEquationAlpha[0]);
        glBlendFuncSeparate(props.lastBlendSrcRgb[0], props.lastBlendDstRgb[0], props.lastBlendSrcAlpha[0], props.lastBlendDstAlpha[0]);
        glBlendColor(props.lastBlendColor[0], props.lastBlendColor[1], props.lastBlendColor[2], props.lastBlendColor[3]);
        setEnabled(GL_BLEND, props.getLastEnableBlend());
        setEnabled(GL_CULL_FACE, props.getLastEnableCullFace());
        setEnabled(GL_DEPTH_TEST, props.getLastEnableDepthTest());
        setEnabled(GL_STENCIL_TEST, props.getLastEnableStencilTest());
        setEnabled(GL_SCISSOR_TEST, props.getLastEnableScissorTest());
        if (glVersion >= 310) setEnabled(GL_PRIMITIVE_RESTART, props.getLastEnablePrimitiveRestart());
        setEnabled(GL_FRAMEBUFFER_SRGB, props.getLastFramebufferSrgb());
        setEnabled(GL_DITHER, props.getLastDither());
        setEnabled(GL_MULTISAMPLE, props.getLastMultisample());
        setEnabled(GL_COLOR_LOGIC_OP, props.getLastColorLogicOp());
        if (glVersion >= 200) glPolygonMode(GL_FRONT_AND_BACK, props.lastPolygonMode[0]);
        glCullFace(props.lastCullFaceMode[0]);
        glFrontFace(props.lastFrontFace[0]);
        glDepthFunc(props.lastDepthFunc[0]);
        glColorMask(props.lastColorMask[0] != 0, props.lastColorMask[1] != 0,
                props.lastColorMask[2] != 0, props.lastColorMask[3] != 0);
        glViewport(props.lastViewport[0], props.lastViewport[1], props.lastViewport[2], props.lastViewport[3]);
        glScissor(props.lastScissorBox[0], props.lastScissorBox[1], props.lastScissorBox[2], props.lastScissorBox[3]);
        glClearColor(props.lastClearColor[0], props.lastClearColor[1], props.lastClearColor[2], props.lastClearColor[3]);
        glClearDepth(props.lastClearDepth[0]);
        glClearStencil(props.lastClearStencil[0]);
        glLineWidth(props.lastLineWidth[0]);
        glStencilFuncSeparate(GL_FRONT, props.lastStencilFunc[0], props.lastStencilRef[0], props.lastStencilValueMask[0]);
        glStencilMaskSeparate(GL_FRONT, props.lastStencilWriteMask[0]);
        glStencilOpSeparate(GL_FRONT, props.lastStencilFail[0], props.lastStencilDepthFail[0], props.lastStencilDepthPass[0]);
        glStencilFuncSeparate(GL_BACK, props.lastStencilBackFunc[0], props.lastStencilBackRef[0], props.lastStencilBackValueMask[0]);
        glStencilMaskSeparate(GL_BACK, props.lastStencilBackWriteMask[0]);
        glStencilOpSeparate(GL_BACK, props.lastStencilBackFail[0], props.lastStencilBackDepthFail[0], props.lastStencilBackDepthPass[0]);
        glPixelStorei(GL_PACK_SWAP_BYTES, props.lastPackSwapBytes[0]);
        glPixelStorei(GL_PACK_LSB_FIRST, props.lastPackLsbFirst[0]);
        glPixelStorei(GL_PACK_ROW_LENGTH, props.lastPackRowLength[0]);
        glPixelStorei(GL_PACK_SKIP_PIXELS, props.lastPackSkipPixels[0]);
        glPixelStorei(GL_PACK_SKIP_ROWS, props.lastPackSkipRows[0]);
        glPixelStorei(GL_PACK_ALIGNMENT, props.lastPackAlignment[0]);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, props.lastPixelPackBufferBinding[0]);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, props.lastPixelUnpackBufferBinding[0]);
        glPixelStorei(GL_UNPACK_SWAP_BYTES, props.lastUnpackSwapBytes[0]);
        glPixelStorei(GL_UNPACK_LSB_FIRST, props.lastUnpackLsbFirst[0]);
        glPixelStorei(GL_UNPACK_ALIGNMENT, props.lastUnpackAlignment[0]);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, props.lastUnpackRowLength[0]);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, props.lastUnpackSkipPixels[0]);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, props.lastUnpackSkipRows[0]);
        if (glVersion >= 120) {
            glPixelStorei(GL_PACK_IMAGE_HEIGHT, props.lastPackImageHeight[0]);
            glPixelStorei(GL_PACK_SKIP_IMAGES, props.lastPackSkipImages[0]);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, props.lastUnpackImageHeight[0]);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, props.lastUnpackSkipImages[0]);
        }
        glDepthMask(props.getLastDepthMask());
        return this;
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) glEnable(capability); else glDisable(capability);
    }
}
