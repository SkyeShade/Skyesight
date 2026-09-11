package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Final WORLD image policy; run after all RGB blending, never on direct portal targets. */
public final class SkyesightCameraOutput {
    private SkyesightCameraOutput() {}

    public static void makeOpaque(RenderTarget target) {
        RenderSystem.assertOnRenderThread();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            try {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                RenderSystem.colorMask(false, false, false, true);
                // ClearBuffer respects the color mask and does not change the clear color.
                // Depth, stencil, and the already composited RGB remain untouched.
                GL30.glClearBufferfv(GL11.GL_COLOR, 0, stack.floats(0, 0, 0, 1));
            } finally {
                RenderSystem.colorMask(mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            }
        }
    }
}
