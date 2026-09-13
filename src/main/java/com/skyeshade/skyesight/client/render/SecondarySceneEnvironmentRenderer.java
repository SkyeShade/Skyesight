package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.client.compat.iris.SkyesightIrisCompat;
import com.skyeshade.skyesight.client.render.fog.SkyesightFogRenderer;
import com.skyeshade.skyesight.mixin.client.CameraInvoker;
import com.skyeshade.skyesight.mixin.client.LevelRendererAccessor;
import com.skyeshade.skyesight.mixin.client.LevelRendererSkyInvoker;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Shared destination sky and cloud draws; no aperture or projection construction. */
public final class SecondarySceneEnvironmentRenderer {
    private SecondarySceneEnvironmentRenderer() {}

    public static String renderSky(Minecraft minecraft, ClientLevel level, Camera camera,
            Matrix4f modelView, Matrix4f projection, float partialTick, int radius, boolean preferLevelRenderer) {
        RenderSystem.defaultBlendFunc();
        return preferLevelRenderer
                ? renderVanillaSkyCapture(minecraft, level, camera, modelView, projection, partialTick, radius)
                : renderClonedTargetLevelSkyCapture(minecraft, level, camera, modelView, projection, partialTick, radius);
    }

    public static Vec3 skyPosition(ClientLevel level, Vec3 position) {
        double horizon = level.getLevelData().getHorizonHeight(level);
        return position.y < horizon ? new Vec3(position.x, horizon + 8, position.z) : position;
    }

    public static float[] prepareBackground(Minecraft minecraft, ClientLevel level, Camera camera, float partialTick, int radius) {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        FogRenderer.setupColor(camera, partialTick, level, radius, minecraft.gameRenderer.getDarkenWorldAmount(partialTick));
        FogRenderer.levelFogColor();
        float[] color = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, color);
        RenderSystem.clearColor(color[0], color[1], color[2], 1);
        return color;
    }
    public static void renderBackground(SecondarySceneFrame scene, SkyesightIsolatedCloudRenderer clouds) {
        if (scene.visualWorld() != null && !scene.visualWorld().environmentReady()) return;
        var minecraft = Minecraft.getInstance();
        var frame = scene.view();
        var camera = frame.camera();
        var level = scene.level();
        var position = camera.getPosition();
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try (var ignored = SkyesightSecondaryRenderContext.push(scene.output(), camera, minecraft.getMainRenderTarget())) {
            stack.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            // Match the established secondary sky horizon policy without changing terrain's eye.
            if (!PlayerPerspectiveViews.contains(scene.viewId()))
                ((CameraInvoker) camera).skyesight$setPosition(skyPosition(level, position));
            prepareBackground(minecraft, level, camera, scene.partialTick(), scene.options().terrainRadius());
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            renderSky(minecraft, level, camera, frame.modelViewMatrix(), frame.projectionMatrix(), scene.partialTick(),
                    scene.options().terrainRadius(), level == minecraft.level && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL);
            ((CameraInvoker) camera).skyesight$setPosition(position);
            SkyesightFogRenderer.setupForPlayerTerrain(
                    level, camera, scene.partialTick(), scene.options().terrainRadius());
            renderClouds(clouds, scene.viewId().toString(), minecraft, level, frame.modelViewMatrix(), frame.projectionMatrix(),
                    position, scene.partialTick(), minecraft.levelRenderer.getTicks(), scene.output().frameBufferId);
        } finally {
            ((CameraInvoker) camera).skyesight$setPosition(position);
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    public static SkyesightIsolatedCloudRenderer.RenderResult renderClouds(SkyesightIsolatedCloudRenderer clouds,
            String key, Minecraft minecraft, ClientLevel level, Matrix4f modelView, Matrix4f projection,
            Vec3 position, float partialTick, int renderFrame, int output) {
        if (minecraft.options.getCloudsType() == CloudStatus.OFF
                || SkyesightIrisCompat.isShaderPackInUse()) return null;
        var accessor = (LevelRendererAccessor) minecraft.levelRenderer;
        var previousLevel = accessor.skyesight$getLevel();
        try {
            // Only the cloud invoker observes this scoped level; its mesh/cache is isolated per view.
            accessor.skyesight$setLevel(level);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewStack().identity();
            RenderSystem.applyModelViewMatrix();
            FogRenderer.levelFogColor();
            return clouds.render(key, minecraft.levelRenderer, new PoseStack(), modelView, projection, partialTick,
                    position.x, position.y, position.z, renderFrame, output);
        } finally {
            accessor.skyesight$setLevel(previousLevel);
        }
    }
    private static String renderVanillaSkyCapture(
            Minecraft minecraft,
            ClientLevel level,
            Camera camera,
            Matrix4f frustum,
            Matrix4f projection,
            float partialTick, int renderDistanceChunks
    ) {
        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                renderDistanceChunks,
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();

        float renderDistance = renderDistanceChunks * 16.0F;
        boolean foggy = level.effects().isFoggyAt(
                Mth.floor(camera.getPosition().x()),
                Mth.floor(camera.getPosition().y())
        ) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();

        Runnable skyFogSetup = () -> {
            FogRenderer.setupFog(
                    camera,
                    FogRenderer.FogMode.FOG_SKY,
                    renderDistance,
                    foggy,
                    partialTick
            );
            RenderSystem.setShader(GameRenderer::getPositionShader);
        };

        try {
            skyFogSetup.run();
            RenderSystem.setShader(GameRenderer::getPositionShader);
            ((LevelRendererSkyInvoker) minecraft.levelRenderer).skyesight$renderSky(
                    frustum,
                    projection,
                    partialTick,
                    camera,
                    foggy,
                    skyFogSetup
            );
            return "vanillaLevelRenderer";
        } catch (RuntimeException exception) {
            SkyesightClonedSkyRenderer.renderSky(level, camera, frustum, projection, partialTick, skyFogSetup);
            return "clonedFallbackAfterVanillaFailure:" + exception.getClass().getSimpleName();
        }
    }

    private static String renderClonedTargetLevelSkyCapture(
            Minecraft minecraft,
            ClientLevel level,
            Camera camera,
            Matrix4f frustum,
            Matrix4f projection,
            float partialTick, int renderDistanceChunks
    ) {
        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                renderDistanceChunks,
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();

        float renderDistance = renderDistanceChunks * 16.0F;
        boolean foggy = level.effects().isFoggyAt(
                Mth.floor(camera.getPosition().x()),
                Mth.floor(camera.getPosition().y())
        ) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();

        Runnable skyFogSetup = () -> {
            FogRenderer.setupFog(
                    camera,
                    FogRenderer.FogMode.FOG_SKY,
                    renderDistance,
                    foggy,
                    partialTick
            );
            RenderSystem.setShader(GameRenderer::getPositionShader);
        };

        try {
            skyFogSetup.run();
            SkyesightClonedSkyRenderer.renderSky(level, camera, frustum, projection, partialTick, skyFogSetup);
            return "clonedTargetLevel";
        } catch (RuntimeException exception) {
            return "clonedTargetLevelFailed:" + exception.getClass().getSimpleName();
        }
    }

}
