package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import com.skyeshade.skyesight.client.render.entity.PortalDimensionEntitySources;
import com.skyeshade.skyesight.client.render.entity.PortalRenderableEntity;
import com.skyeshade.skyesight.client.render.fog.SkyesightFogRenderer;
import com.skyeshade.skyesight.client.render.light.SkyesightLightTextureUpdater;
import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import com.skyeshade.skyesight.client.world.SecondaryParticleViews;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/** Scene contents shared by every secondary view, independent of its projection and aperture. */
public final class SecondarySceneRenderer {
    private SecondarySceneRenderer() {}

    public static boolean renderContents(SecondarySceneFrame scene) {
        return renderContents(scene, null);
    }
    public static boolean renderContents(SecondarySceneFrame scene, Runnable beforeTranslucent) {
        if (scene.visualWorld() != null && !scene.visualWorld().environmentReady()) return false;
        boolean terrainRendered = true;
        var minecraft = Minecraft.getInstance();
        var frame = scene.view();
        var options = scene.options();
        float partialTick = scene.partialTick();
        if (!options.particles()) SecondaryParticleViews.close(scene.viewId());
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        var fogShape = RenderSystem.getShaderFogShape();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        try (var ignored = SkyesightSecondaryRenderContext.push(scene.output(), frame.camera(), minecraft.getMainRenderTarget())) {
            if (PlayerPerspectiveViews.contains(scene.viewId())) {
                SkyesightFogRenderer.setupForPlayerTerrain(scene.level(), frame.camera(), partialTick,
                        PlayerPerspectiveViews.radius(scene.viewId(), options.terrainRadius()));
            } else SkyesightFogRenderer.setupForTerrain(scene.level(), frame.camera(), partialTick, options.terrainRadius());
            SkyesightLightTextureUpdater.updateFor(scene.level(), frame.camera(), partialTick);
            minecraft.gameRenderer.lightTexture().turnOnLightLayer();
            for (var pass : options.contentPasses()) {
                switch (pass) {
                    case TERRAIN -> {
                        prepare(scene);
                        try (var composition = new SecondaryTerrainComposition(beforeTranslucent)) {
                            if (scene.visualWorld() != null) {
                                scene.visualWorld().renderTerrain(frame.camera(), frame.frustum(), frame.modelViewMatrix(),
                                        frame.projectionMatrix(), options.terrainRadius(), options.translucent());
                            } else {
                                terrainRendered = SecondarySodiumTerrainPass.render(frame, scene.context(), minecraft, partialTick);
                            }
                        }
                    }
                    // Preserve the established direct secondary order: terrain (including translucent),
                    // block entities, entities, then visual particles.
                    case BLOCK_ENTITIES -> {
                        prepare(scene);
                        if (scene.visualWorld() != null) {
                            scene.visualWorld().renderBlockEntities(scene.viewId(), frame.camera(), frame.modelViewMatrix(),
                                    frame.projectionMatrix(), partialTick, options.blockEntityRadius(), frame.frustum());
                        } else {
                            SecondaryBlockEntityPass.render(frame, minecraft, new ChunkPos(BlockPos.containing(frame.camera().getPosition())),
                                    options.blockEntityRadius(), partialTick);
                        }
                    }
                    case ENTITIES -> {
                        prepare(scene);
                        renderEntities(scene);
                    }
                    case PARTICLES -> {
                        prepare(scene);
                        var particles = SecondaryParticleViews.touch(scene);
                        if (particles != null && particles.usesMainParticles()) {
                            SecondaryParticlePass.render(frame, minecraft, partialTick, SecondaryParticlePass.RenderGroup.ALL);
                        } else if (particles != null) {
                            SecondaryParticlePass.renderVisualWorldParticles(frame, minecraft, scene.level(), particles.particles(), partialTick,
                                    SecondaryParticlePass.RenderGroup.ALL, scene.output().frameBufferId, 0);
                        }
                    }
                }
            }
            if (terrainRendered) {
                prepare(scene);
                PortalInteractionClient.render(scene);
                RecursivePortalRenderer.render(scene);
            }
        } finally {
            minecraft.gameRenderer.lightTexture().turnOffLightLayer();
            SkyesightLightTextureUpdater.restoreMain(partialTick);
            if (minecraft.level != null && minecraft.player != null) {
                FogRenderer.setupColor(minecraft.gameRenderer.getMainCamera(), partialTick,
                        minecraft.level, minecraft.options.getEffectiveRenderDistance(),
                        minecraft.gameRenderer.getDarkenWorldAmount(partialTick));
            }
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        }
        if (terrainRendered && !RecursivePortalRenderer.isNestedScene()) SecondaryTransition.capture(scene);
        return terrainRendered;
    }

    private static void prepare(SecondarySceneFrame scene) {
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != scene.output().frameBufferId) {
            scene.output().bindWrite(false);
        }
        scene.prepareOutput().run();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }

    private static void renderEntities(SecondarySceneFrame scene) {
        var frame = scene.view();
        var camera = frame.camera().getPosition();
        double radius = scene.options().entityRadius() * 16.0;
        var bounds = new AABB(camera.x - radius, scene.level().getMinBuildHeight(), camera.z - radius,
                camera.x + radius, scene.level().getMaxBuildHeight(), camera.z + radius);
        List<PortalRenderableEntity> entities;
        if (scene.visualWorld() == null) {
            entities = PortalDimensionEntitySources.renderableMainLevelEntitiesForDimension(scene.level(),
                    scene.level().dimension(), bounds, frame.frustum());
        } else {
            entities = PortalDimensionEntitySources.renderableVisualEntitiesForDimension(scene.viewId(), scene.visualWorld(),
                    scene.level().dimension(), bounds, frame.frustum());
        }
        if (!RecursivePortalRenderer.isNestedScene() && SecondaryTransition.renderingPrimaryPresentation() && Minecraft.getInstance().player != null
                && Minecraft.getInstance().options.getCameraType().isFirstPerson())
            entities = entities.stream().filter(entry -> !entry.entity().getUUID().equals(Minecraft.getInstance().player.getUUID())).toList();
        SecondaryEntityPass.renderSceneEntities(frame, Minecraft.getInstance(), scene.level(), entities,
                scene.options().entityRadius(), scene.partialTick(), false, false, false, scene.output().frameBufferId);
    }
}
