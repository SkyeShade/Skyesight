package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.render.vanilla.LevelRendererSecondaryTerrainBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class PortalSecondaryVanillaTerrainRenderer {
    private static boolean unavailableLogged;
    private static boolean failureLogged;
    private static long lastBootstrapSummaryMillis;

    private PortalSecondaryVanillaTerrainRenderer() {
    }

    static boolean render(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        frame.diagnostics().setBackend("VANILLA_TERRAIN");
        if (!frame.diagnostics().renderTerrain()) {
            return false;
        }
        if (minecraft.level == null || minecraft.player == null) {
            markUnavailable(frame, "missing main client level/player");
            return false;
        }

        ClientLevel level = minecraft.level;
        Camera camera = frame.camera();
        Matrix4f projection = frame.projectionMatrix();
        Matrix4f modelView = frame.modelViewMatrix();
        Vec3 cameraPosition = camera.getPosition();

        try {
            VanillaSecondaryViewState state = VanillaSecondaryViewState.getOrCreate(context, minecraft);
            LevelRenderer levelRenderer = state.rendererFor(level);
            LevelRendererSecondaryTerrainBridge bridge = (LevelRendererSecondaryTerrainBridge) levelRenderer;

            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            bridge.skyesight$setupSecondaryTerrain(camera, frame.frustum(), minecraft.player.isSpectator());
            VanillaSecondaryViewState.BootstrapResult bootstrap = state.bootstrapLoadedChunks(
                    level,
                    cameraPosition,
                    PortalSecondaryWorldRenderer.configuredTerrainChunkRadius(frame)
            );
            logBootstrapSummaryIfNeeded(frame, bootstrap);
            bridge.skyesight$compileSecondarySections(camera);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();

            if (frame.diagnostics().renderToCurrentTarget()) {
                PortalSecondaryWorldRenderer.applyDirectDepthModeAtSodiumDrawPoint();
            }

            bridge.skyesight$renderSecondarySectionLayer(
                    RenderType.solid(),
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z(),
                    modelView,
                    projection
            );
            minecraft.getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .setBlurMipmap(false, minecraft.options.mipmapLevels().get() > 0);
            try {
                bridge.skyesight$renderSecondarySectionLayer(
                        RenderType.cutoutMipped(),
                        cameraPosition.x(),
                        cameraPosition.y(),
                        cameraPosition.z(),
                        modelView,
                        projection
                );
            } finally {
                minecraft.getModelManager()
                        .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .restoreLastBlurMipmap();
            }
            bridge.skyesight$renderSecondarySectionLayer(
                    RenderType.cutout(),
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z(),
                    modelView,
                    projection
            );
            return true;
        } catch (RuntimeException exception) {
            markUnavailable(frame, "vanilla terrain render failed");
            logFailureOnce(exception);
            return false;
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    static void close(SecondaryViewContext context) {
        VanillaSecondaryViewState.close(context);
    }

    static boolean scheduleBlockUpdate(SecondaryViewContext context, BlockPos pos) {
        Object state = context == null ? null : context.vanillaState();
        if (!(state instanceof VanillaSecondaryViewState vanillaState) || vanillaState.boundLevel() == null || pos == null) {
            return false;
        }

        LevelRenderer levelRenderer = vanillaState.rendererFor(vanillaState.boundLevel());
        levelRenderer.setSectionDirtyWithNeighbors(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
        return true;
    }

    static boolean scheduleTerrainUpdate(SecondaryViewContext context) {
        Object state = context == null ? null : context.vanillaState();
        if (!(state instanceof VanillaSecondaryViewState vanillaState) || vanillaState.boundLevel() == null) {
            return false;
        }

        vanillaState.invalidateBootstrap();
        vanillaState.rendererFor(vanillaState.boundLevel()).needsUpdate();
        return true;
    }

    private static void markUnavailable(SecondaryViewFrame frame, String reason) {
        frame.diagnostics().setBackend("UNAVAILABLE_VANILLA_TERRAIN:" + reason);
        if (!unavailableLogged) {
            unavailableLogged = true;
            Skyesight.LOGGER.info("[Skyesight] Vanilla secondary terrain unavailable: {}", reason);
        }
    }

    private static void logFailureOnce(RuntimeException exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        Skyesight.LOGGER.warn("[Skyesight] Vanilla secondary terrain render failed", exception);
    }

    private static void logBootstrapSummaryIfNeeded(
            SecondaryViewFrame frame,
            VanillaSecondaryViewState.BootstrapResult bootstrap
    ) {
        if (bootstrap == null || !bootstrap.ran()) {
            return;
        }
        if (!SkyesightDebugConfig.VERBOSE_RENDER) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBootstrapSummaryMillis < 1000L) {
            return;
        }
        lastBootstrapSummaryMillis = now;
        ChunkPos center = bootstrap.center();
        Skyesight.LOGGER.info(
                "[Skyesight] Vanilla secondary terrain bootstrap viewId={} center={},{} radius={} loadedChunks={} notifiedChunks={} dirtiedSections={}",
                frame.diagnostics().entityWatchRegionId(),
                center.x,
                center.z,
                bootstrap.radius(),
                bootstrap.loadedChunksFound(),
                bootstrap.chunksNotified(),
                bootstrap.sectionsDirtied()
        );
    }
}
