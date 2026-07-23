package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class SecondaryBlockEntityPass {
    /*
     * Portal block-entity frustum culling remains off because the current
     * secondary camera/frustum can reject visible portal content incorrectly.
     */
    private static final boolean ENABLE_BLOCK_ENTITY_FRUSTUM_CULLING = false;
    private static final boolean ENABLE_BLOCK_ENTITY_DEPTH_TEST = true;
    private static final boolean ENABLE_BLOCK_ENTITY_DEPTH_WRITE = true;

    private SecondaryBlockEntityPass() {}

    public static Result render(SecondaryViewFrame frame, Minecraft minecraft, ChunkPos center, int radius, float partialTick) {
        if (frame == null) {
            return Result.skipped("frame null");
        }
        if (minecraft == null) {
            return Result.skipped("minecraft null");
        }
        if (frame.camera() == null) {
            return Result.skipped("camera null");
        }
        if (center == null) {
            return Result.skipped("scan center null");
        }
        if (radius <= 0) {
            return Result.skipped("scan radius <= 0");
        }
        if (minecraft.level == null) {
            return Result.skipped("client level null");
        }

        ClientLevel level = minecraft.level;
        if (level.getChunkSource() == null) {
            return Result.skipped("client chunk source null");
        }

        PoseStack poseStack = new PoseStack();
        Vec3 cameraPosition = frame.camera().getPosition();
        int minChunkX = center.x - radius;
        int maxChunkX = center.x + radius;
        int minChunkZ = center.z - radius;
        int maxChunkZ = center.z + radius;
        double aabbRadius = Math.max(16.0D, radius * 16.0D);
        AABB portalBounds = new AABB(
                cameraPosition.x() - aabbRadius,
                cameraPosition.y() - aabbRadius,
                cameraPosition.z() - aabbRadius,
                cameraPosition.x() + aabbRadius,
                cameraPosition.y() + aabbRadius,
                cameraPosition.z() + aabbRadius
        );
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting vertexSortingBefore = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();

        int chunksScanned = 0;
        int blockEntitiesConsidered = 0;
        int blockEntitiesRendered = 0;
        int skippedFrustum = 0;

        modelViewStack.pushMatrix();

        try {
            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(frame.modelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            if (ENABLE_BLOCK_ENTITY_DEPTH_TEST) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(ENABLE_BLOCK_ENTITY_DEPTH_WRITE);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            minecraft.getBlockEntityRenderDispatcher().prepare(level, frame.camera(), minecraft.hitResult);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue;
                    }
                    chunksScanned++;

                    for (BlockPos blockEntityPos : chunk.getBlockEntitiesPos()) {
                        BlockEntity blockEntity = level.getBlockEntity(blockEntityPos);
                        if (blockEntity == null || blockEntity.isRemoved()) {
                            continue;
                        }
                        if (!portalBounds.intersects(blockBox(blockEntityPos))) {
                            continue;
                        }

                        blockEntitiesConsidered++;
                        if (ENABLE_BLOCK_ENTITY_FRUSTUM_CULLING
                                && !ClientHooks.isBlockEntityRendererVisible(
                                minecraft.getBlockEntityRenderDispatcher(),
                                blockEntity,
                                frame.frustum()
                        )) {
                            skippedFrustum++;
                            continue;
                        }

                        renderBlockEntity(
                                blockEntity,
                                renderTranslation(blockEntityPos, cameraPosition),
                                poseStack,
                                bufferSource,
                                partialTick,
                                minecraft
                        );
                        blockEntitiesRendered++;
                    }
                }
            }

            flushBlockEntityBuffers(bufferSource);
            return Result.rendered(
                    chunksScanned,
                    blockEntitiesConsidered,
                    blockEntitiesRendered,
                    skippedFrustum
            );
        } catch (RuntimeException exception) {
            flushBlockEntityBuffers(bufferSource);
            return Result.failed(
                    chunksScanned,
                    blockEntitiesConsidered,
                    blockEntitiesRendered,
                    skippedFrustum,
                    exception
            );
        } finally {
            try {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            } catch (RuntimeException ignored) {
                // Keep the original render result if restoration itself fails.
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
        }
    }

    private static void renderBlockEntity(
            BlockEntity blockEntity,
            Vec3 translation,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            float partialTick,
            Minecraft minecraft
    ) {
        poseStack.pushPose();
        poseStack.translate(translation.x(), translation.y(), translation.z());
        minecraft.getBlockEntityRenderDispatcher().render(blockEntity, partialTick, poseStack, bufferSource);
        poseStack.popPose();
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

    private static Vec3 renderTranslation(BlockPos pos, Vec3 cameraPosition) {
        return new Vec3(
                pos.getX() - cameraPosition.x(),
                pos.getY() - cameraPosition.y(),
                pos.getZ() - cameraPosition.z()
        );
    }

    private static AABB blockBox(BlockPos pos) {
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + 1.0D,
                pos.getY() + 1.0D,
                pos.getZ() + 1.0D
        );
    }

    public static void markSkipped(String reason) {
        // Compatibility hook for call sites that used to update debug-only skip state.
    }

    public static boolean depthWriteEnabled() {
        return ENABLE_BLOCK_ENTITY_DEPTH_WRITE;
    }

    public record Result(
            int chunksScanned,
            int blockEntitiesConsidered,
            int blockEntitiesRendered,
            int skippedFrustum,
            String skippedReason,
            String exception
    ) {
        public static Result skipped(String reason) {
            return new Result(0, 0, 0, 0, reason, "");
        }

        public static Result rendered(
                int chunksScanned,
                int blockEntitiesConsidered,
                int blockEntitiesRendered,
                int skippedFrustum
        ) {
            return new Result(chunksScanned, blockEntitiesConsidered, blockEntitiesRendered, skippedFrustum, "", "");
        }

        public static Result failed(
                int chunksScanned,
                int blockEntitiesConsidered,
                int blockEntitiesRendered,
                int skippedFrustum,
                RuntimeException exception
        ) {
            String message = exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
            return new Result(chunksScanned, blockEntitiesConsidered, blockEntitiesRendered, skippedFrustum, "", message);
        }
    }
}
