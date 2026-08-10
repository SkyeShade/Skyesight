package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.render.entity.SkyesightNameTagSuppressor;
import com.skyeshade.skyesight.client.world.SkyesightRemoteChunkReceiver;
import com.skyeshade.skyesight.client.world.SkyesightVisualEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class SkyesightVisualFeatureRenderer {
    private SkyesightVisualFeatureRenderer() {
    }

    public static void renderEntities(
            ClientLevel level,
            Iterable<SkyesightVisualEntity> entities,
            Camera camera,
            Matrix4f modelMatrix,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.player == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        ClientLevel previousLevel = minecraft.level;

        try {
            minecraft.level = level;
            minecraft.getEntityRenderDispatcher().prepare(
                    level,
                    camera,
                    minecraft.crosshairPickEntity
            );

            try (SkyesightNameTagSuppressor.Scope ignored =
                         SkyesightNameTagSuppressor.suppressOwner(minecraft.player.getUUID())) {
                for (SkyesightVisualEntity visualEntity : entities) {
                    visualEntity.applyInterpolated();

                    Entity entity = visualEntity.entity();
                    int packedLight = getPackedEntityLight(level, entity, partialTick);
                    minecraft.getEntityRenderDispatcher().render(
                            entity,
                            entity.getX() - cameraPos.x(),
                            entity.getY() - cameraPos.y(),
                            entity.getZ() - cameraPos.z(),
                            entity.getYRot(),
                            partialTick,
                            poseStack,
                            bufferSource,
                            packedLight
                    );
                }
            }

            bufferSource.endBatch();
        } finally {
            minecraft.level = previousLevel;
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void renderBlockEntities(
            ClientLevel level,
            ResourceLocation viewId,
            SkyesightRemoteChunkReceiver chunkReceiver,
            Camera camera,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.player == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        ClientLevel previousLevel = minecraft.level;
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting vertexSortingBefore = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();
        Vec3 mainCameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean crossDimension = previousLevel != null && !previousLevel.dimension().equals(level.dimension());

        modelViewStack.pushMatrix();

        try {
            minecraft.level = level;
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(modelMatrix);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            minecraft.getBlockEntityRenderDispatcher().prepare(
                    level,
                    camera,
                    minecraft.hitResult
            );

            chunkReceiver.forEachLoadedChunk(packed -> {
                ChunkPos chunkPos = new ChunkPos(packed);
                LevelChunk chunk = level.getChunkSource().getChunk(
                        chunkPos.x,
                        chunkPos.z,
                        false
                );
                if (chunk == null) {
                    return;
                }

                for (BlockPos blockEntityPos : chunk.getBlockEntitiesPos()) {
                    BlockEntity blockEntity = level.getBlockEntity(blockEntityPos);
                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }

                    Vec3 renderOffset = cameraRelativeOffset(blockEntityPos, cameraPos);
                    BlockState blockState = level.getBlockState(blockEntityPos);
                    renderBlockEntity(
                            minecraft,
                            blockEntity,
                            renderOffset,
                            poseStack,
                            bufferSource,
                            partialTick
                    );
                    if (SkyesightDebugConfig.WATCH_DEBUG && crossDimension) {
                        logBlockEntityDebug(
                                viewId,
                                blockEntity,
                                blockEntityPos,
                                blockState,
                                previousLevel,
                                level,
                                framebufferBefore,
                                camera,
                                cameraPos,
                                mainCameraPos,
                                renderOffset
                        );
                    }
                }
            });

            flushBlockEntityBuffers(bufferSource);
        } finally {
            try {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            } catch (RuntimeException ignored) {
                // Preserve the original exception, if any, from BE rendering.
            }
            RenderSystem.setProjectionMatrix(projectionBefore, vertexSortingBefore);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(
                    shaderColorBefore[0],
                    shaderColorBefore[1],
                    shaderColorBefore[2],
                    shaderColorBefore[3]
            );
            minecraft.level = previousLevel;
        }
    }

    private static int getPackedEntityLight(ClientLevel level, Entity entity, float partialTick) {
        BlockPos lightPos = BlockPos.containing(entity.getLightProbePosition(partialTick));
        if (!level.hasChunkAt(lightPos)) {
            return LightTexture.FULL_BRIGHT;
        }

        int blockLight = level.getBrightness(LightLayer.BLOCK, lightPos);
        int skyLight = level.getBrightness(LightLayer.SKY, lightPos);
        return LightTexture.pack(blockLight, skyLight);
    }

    private static void renderBlockEntity(
            Minecraft minecraft,
            BlockEntity blockEntity,
            Vec3 renderOffset,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            float partialTick
    ) {
        poseStack.pushPose();
        poseStack.translate(renderOffset.x(), renderOffset.y(), renderOffset.z());
        minecraft.getBlockEntityRenderDispatcher().render(
                blockEntity,
                partialTick,
                poseStack,
                bufferSource
        );
        poseStack.popPose();
    }

    private static Vec3 cameraRelativeOffset(BlockPos pos, Vec3 cameraPosition) {
        return new Vec3(
                pos.getX() - cameraPosition.x(),
                pos.getY() - cameraPosition.y(),
                pos.getZ() - cameraPosition.z()
        );
    }

    private static void flushBlockEntityBuffers(MultiBufferSource.BufferSource bufferSource) {
        bufferSource.endBatch();
        bufferSource.endBatch(RenderType.solid());
        bufferSource.endBatch(RenderType.endPortal());
        bufferSource.endBatch(RenderType.endGateway());
        bufferSource.endBatch(Sheets.solidBlockSheet());
        bufferSource.endBatch(Sheets.cutoutBlockSheet());
        bufferSource.endBatch(Sheets.bedSheet());
        bufferSource.endBatch(Sheets.shulkerBoxSheet());
        bufferSource.endBatch(Sheets.signSheet());
        bufferSource.endBatch(Sheets.hangingSignSheet());
        bufferSource.endBatch(Sheets.chestSheet());
        bufferSource.endBatch(Sheets.translucentCullBlockSheet());
        bufferSource.endBatch(Sheets.bannerSheet());
        bufferSource.endBatch(Sheets.shieldSheet());
    }

    private static String blockStateFacing(BlockState state) {
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            String name = property.getName();
            if ("facing".equals(name)
                    || "horizontal_facing".equals(name)
                    || "rotation".equals(name)) {
                return name + "=" + state.getValue(property);
            }
        }
        return "-";
    }

    private static void logBlockEntityDebug(
            ResourceLocation viewId,
            BlockEntity blockEntity,
            BlockPos blockEntityPos,
            BlockState blockState,
            ClientLevel previousLevel,
            ClientLevel level,
            int framebufferBefore,
            Camera camera,
            Vec3 cameraPos,
            Vec3 mainCameraPos,
            Vec3 renderOffset
    ) {
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_RENDER_DEPTH: viewId={} blockPos={} blockEntityType={} displayDimension={} cameraDimension={} renderTargetName=framebuffer:{} inPortalContentPass=yes depthTestEnabled={} depthMask={} depthFunc={} usingPortalCameraOrigin={} portalCameraPos={} mainCameraPos={} renderOffset={} bufferFlushedBeforeComposite=pending reasonIfSkipped=-",
                viewId == null ? "-" : viewId,
                blockEntityPos,
                blockEntity.getType(),
                previousLevel.dimension().location(),
                level.dimension().location(),
                framebufferBefore,
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? "yes" : "no",
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? "yes" : "no",
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                camera == Minecraft.getInstance().gameRenderer.getMainCamera() ? "no" : "yes",
                cameraPos,
                mainCameraPos,
                renderOffset
        );
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_RENDER_TRANSFORM: viewId={} blockEntityType={} blockPos={} blockState={} blockStateFacing={} displayDimension={} cameraDimension={} portalCameraPos={} mainCameraPos={} terrainCameraOrigin={} beRenderOffset={} chunkSectionOrigin={},{},{} visualWorldOrigin=0,0,0 appliedPortalModelViewInRenderSystem=yes appliedPortalModelViewInPoseStack=no addedHalfBlockOffset=no mappedThroughPortalFrame=no sameDimPathEquivalent=yes",
                viewId == null ? "-" : viewId,
                blockEntity.getType(),
                blockEntityPos,
                blockState,
                blockStateFacing(blockState),
                previousLevel.dimension().location(),
                level.dimension().location(),
                cameraPos,
                mainCameraPos,
                cameraPos,
                renderOffset,
                blockEntityPos.getX() >> 4 << 4,
                blockEntityPos.getY() >> 4 << 4,
                blockEntityPos.getZ() >> 4 << 4
        );
    }
}
