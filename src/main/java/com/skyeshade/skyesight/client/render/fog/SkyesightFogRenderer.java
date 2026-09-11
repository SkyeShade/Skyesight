package com.skyeshade.skyesight.client.render.fog;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;

public final class SkyesightFogRenderer {
    private SkyesightFogRenderer() {}

    public static void setupForPlayerTerrain(ClientLevel level, Camera camera, float partialTick, int radius) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        FogRenderer.setupColor(camera, partialTick, level, radius, mc.gameRenderer.getDarkenWorldAmount(partialTick));
        FogRenderer.levelFogColor();
        boolean foggy = level.effects().isFoggyAt(net.minecraft.util.Mth.floor(camera.getPosition().x),
                net.minecraft.util.Mth.floor(camera.getPosition().y)) || mc.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, radius * 16F, foggy, partialTick);
    }

    public static void setupForTerrain(
            ClientLevel level,
            Camera camera,
            float partialTick,
            int fogDistanceChunks
    ) {
        float farPlaneDistance = fogDistanceChunks * 16.0F;

        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                fogDistanceChunks,
                0.0F
        );

        FogRenderer.levelFogColor();

        float start = farPlaneDistance * 0.65F;
        float end = farPlaneDistance;

        if (!level.dimensionType().hasSkyLight()) {
            start = farPlaneDistance * 0.45F;
            end = farPlaneDistance * 0.95F;
        }

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
    }

    public static void setupForSky(
            ClientLevel level,
            Camera camera,
            float partialTick,
            int fogDistanceChunks
    ) {
        float farPlaneDistance = fogDistanceChunks * 16.0F;

        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                fogDistanceChunks,
                0.0F
        );

        FogRenderer.levelFogColor();

        RenderSystem.setShaderFogStart(0.0F);
        RenderSystem.setShaderFogEnd(farPlaneDistance);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
    }

    public static void clear() {
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
