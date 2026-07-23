package com.skyeshade.skyesight.client.render.env;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.client.render.SkyesightClonedSkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Disabled legacy diagnostic renderer.
 *
 * <p>The active direct-stencil portal environment path is
 * {@code PortalSkyCaptureManager}: it captures sky/clouds into an offscreen
 * texture with vanilla caller preconditions and composites that texture through
 * stencil. This class clears the bound framebuffer color/depth directly and is
 * not safe for the stabilized portal pass.</p>
 */
public final class SkyesightVanillaEnvironmentRenderer implements SkyesightEnvironmentRenderer {
    @Override
    public void renderSky(
            ClientLevel level,
            Camera camera,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        Vec3 skyColor = level.getSkyColor(camera.getPosition(), partialTick);

        RenderSystem.clearColor(
                (float) skyColor.x(),
                (float) skyColor.y(),
                (float) skyColor.z(),
                1.0F
        );

        RenderSystem.clear(
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                Minecraft.ON_OSX
        );

        SkyesightClonedSkyRenderer.renderSky(
                level,
                camera,
                modelViewMatrix,
                projectionMatrix,
                partialTick
        );

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }
}
