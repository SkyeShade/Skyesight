package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** Caller output state retained across secondary rendering and target allocation. */
public record SecondarySceneOutputState(
        int framebuffer,
        int[] viewport,
        boolean scissorEnabled,
        int[] scissorBox,
        Matrix4f projection,
        VertexSorting vertexSorting,
        Matrix4f modelView,
        boolean depthEnabled,
        int depthFunc,
        boolean depthMask,
        boolean stencilEnabled,
        int stencilFunc,
        int stencilRef,
        int stencilValueMask,
        int stencilWriteMask,
        boolean blendEnabled,
        boolean cullEnabled,
        boolean[] colorMask,
        float[] shaderColor,
        float[] clearColor,
        float fogStart, float fogEnd, FogShape fogShape, float[] fogColor,
        int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
        int stencilFail, int stencilDepthFail, int stencilDepthPass,
        ShaderInstance shader
) {
    public static SecondarySceneOutputState capture() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        return new SecondarySceneOutputState(
                GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                viewport,
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                scissorBox,
                new Matrix4f(RenderSystem.getProjectionMatrix()),
                RenderSystem.getVertexSorting(),
                new Matrix4f(RenderSystem.getModelViewStack()),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                GL11.glGetInteger(GL11.GL_STENCIL_FUNC),
                GL11.glGetInteger(GL11.GL_STENCIL_REF),
                GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK),
                GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                readColorMask(),
                RenderSystem.getShaderColor().clone(),
                readClearColor(),
                RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogShape(), RenderSystem.getShaderFogColor().clone(),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glGetInteger(GL11.GL_STENCIL_FAIL), GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL), GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS),
                RenderSystem.getShader()
        );
    }

    public void restore() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        if (this.scissorEnabled) {
            RenderSystem.enableScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        } else {
            RenderSystem.disableScissor();
        }
        RenderSystem.setProjectionMatrix(this.projection, this.vertexSorting);
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        modelViewStack.mul(this.modelView);
        RenderSystem.applyModelViewMatrix();
        if (this.depthEnabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthFunc(this.depthFunc);
        RenderSystem.depthMask(this.depthMask);
        RenderSystem.colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
        if (this.stencilEnabled) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        RenderSystem.stencilMask(this.stencilWriteMask);
        RenderSystem.stencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
        RenderSystem.stencilOp(this.stencilFail, this.stencilDepthFail, this.stencilDepthPass);
        if (this.blendEnabled) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        if (this.cullEnabled) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.setShaderColor(this.shaderColor[0], this.shaderColor[1], this.shaderColor[2], this.shaderColor[3]);
        RenderSystem.clearColor(this.clearColor[0], this.clearColor[1], this.clearColor[2], this.clearColor[3]);
        RenderSystem.setShaderFogStart(this.fogStart);
        RenderSystem.setShaderFogEnd(this.fogEnd);
        RenderSystem.setShaderFogShape(this.fogShape);
        RenderSystem.setShaderFogColor(this.fogColor[0], this.fogColor[1], this.fogColor[2], this.fogColor[3]);
        RenderSystem.blendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
        if (this.shader != null) {
            RenderSystem.setShader(() -> this.shader);
        }
    }


    private static float[] readClearColor() {
        float[] color = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, color);
        return color;
    }

    private static boolean[] readColorMask() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
        return new boolean[] {
                buffer.get(0) != 0,
                buffer.get(1) != 0,
                buffer.get(2) != 0,
                buffer.get(3) != 0
        };
    }
}
