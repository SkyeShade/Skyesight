package com.skyeshade.skyesight.client.render.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.client.render.entity.SkyesightNameTagSuppressor;
import com.skyeshade.skyesight.client.world.SkyesightRemoteChunkReceiver;
import com.skyeshade.skyesight.client.world.SkyesightVisualEntity;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SkyesightSodiumWorldRenderer implements AutoCloseable {
    private static final boolean BUILD_IMMEDIATELY = false;

    private final Minecraft minecraft;
    private final SodiumWorldRenderer renderer;
    private final ChunkTracker tracker;

    private ClientLevel level;

    public SkyesightSodiumWorldRenderer(Minecraft minecraft, ChunkTracker tracker) {
        this.minecraft = minecraft;
        this.tracker = tracker;
        this.renderer = new SodiumWorldRenderer(minecraft);
    }

    public SodiumWorldRenderer renderer() {
        return renderer;
    }

    public void setLevel(ClientLevel level) {
        if (this.level == level) {
            return;
        }

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setLevel(level);
            this.level = level;

            if (level != null) {
                this.renderer.scheduleTerrainUpdate();
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
    public void renderEntities(
            Iterable<SkyesightVisualEntity> entities,
            Camera camera,
            Matrix4f modelMatrix,
            float partialTick
    ) {
        if (this.level == null || this.minecraft.player == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelMatrix);

        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();

        ClientLevel previousLevel = this.minecraft.level;

        try {
            this.minecraft.level = this.level;

            this.minecraft.getEntityRenderDispatcher().prepare(
                    this.level,
                    camera,
                    this.minecraft.crosshairPickEntity
            );

            try (SkyesightNameTagSuppressor.Scope ignored =
                         SkyesightNameTagSuppressor.suppressOwner(this.minecraft.player.getUUID())) {
                for (SkyesightVisualEntity visualEntity : entities) {
                    visualEntity.applyInterpolated();

                    Entity entity = visualEntity.entity();

                    int packedLight = getPackedEntityLight(entity, partialTick);
                    this.minecraft.getEntityRenderDispatcher().render(
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
            this.minecraft.level = previousLevel;

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private int getPackedEntityLight(Entity entity, float partialTick) {
        BlockPos lightPos = BlockPos.containing(entity.getLightProbePosition(partialTick));

        if (!this.level.hasChunkAt(lightPos)) {
            return LightTexture.FULL_BRIGHT;
        }

        int blockLight = this.level.getBrightness(LightLayer.BLOCK, lightPos);
        int skyLight = this.level.getBrightness(LightLayer.SKY, lightPos);

        return LightTexture.pack(blockLight, skyLight);
    }
    public void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix
    ) {
        if (level == null || minecraft.player == null) {
            return;
        }

        Viewport viewport = ((ViewportProvider) frustum).sodium$createViewport();
        Vec3 cameraPos = camera.getPosition();

        ChunkRenderMatrices matrices = new ChunkRenderMatrices(
                projectionMatrix,
                modelMatrix
        );

        boolean spectator = minecraft.player.isSpectator();

        RenderDevice.enterManagedCode();

        try (SkyesightSodiumRenderContext.Scope ignored =
                     SkyesightSodiumRenderContext.push(this.tracker)) {

            renderer.setupTerrain(camera, viewport, spectator, BUILD_IMMEDIATELY);

            drawTerrainLayer(RenderType.solid(), matrices, cameraPos);
            drawTerrainLayer(RenderType.translucent(), matrices, cameraPos);
        } finally {
            RenderDevice.exitManagedCode();

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void renderBlockEntitiesManual(
            SkyesightRemoteChunkReceiver chunkReceiver,
            Camera camera,
            Matrix4f modelMatrix,
            float partialTick
    ) {
        if (this.level == null || this.minecraft.player == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelMatrix);

        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();

        ClientLevel previousLevel = this.minecraft.level;

        try {
            this.minecraft.level = this.level;

            this.minecraft.getBlockEntityRenderDispatcher().prepare(
                    this.level,
                    camera,
                    this.minecraft.hitResult
            );

            chunkReceiver.forEachLoadedChunk(packed -> {
                ChunkPos chunkPos = new ChunkPos(packed);

                LevelChunk chunk = this.level.getChunkSource().getChunk(
                        chunkPos.x,
                        chunkPos.z,
                        false
                );

                if (chunk == null) {
                    return;
                }

                for (BlockPos blockEntityPos : chunk.getBlockEntitiesPos()) {
                    BlockEntity blockEntity = this.level.getBlockEntity(blockEntityPos);

                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }

                    renderBlockEntity(
                            blockEntity,
                            cameraPos,
                            poseStack,
                            bufferSource,
                            partialTick
                    );
                }
            });

            bufferSource.endBatch();
        } finally {
            this.minecraft.level = previousLevel;

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }


    private void renderBlockEntity(
            BlockEntity blockEntity,
            Vec3 cameraPos,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            float partialTick
    ) {
        BlockPos pos = blockEntity.getBlockPos();

        poseStack.pushPose();

        poseStack.translate(
                pos.getX() - cameraPos.x(),
                pos.getY() - cameraPos.y(),
                pos.getZ() - cameraPos.z()
        );

        this.minecraft.getBlockEntityRenderDispatcher().render(
                blockEntity,
                partialTick,
                poseStack,
                bufferSource
        );

        poseStack.popPose();
    }

    public void scheduleBlockUpdate(BlockPos pos) {
        if (this.level == null) {
            return;
        }

        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;

        RenderDevice.enterManagedCode();

        try {
            this.renderer.scheduleRebuildForChunks(
                    sectionX - 1,
                    sectionY - 1,
                    sectionZ - 1,
                    sectionX + 1,
                    sectionY + 1,
                    sectionZ + 1,
                    true
            );

            this.renderer.scheduleTerrainUpdate();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
    public void scheduleTerrainUpdate() {
        this.renderer.scheduleTerrainUpdate();
    }

    private void drawTerrainLayer(RenderType renderType, ChunkRenderMatrices matrices, Vec3 cameraPos) {
        renderType.setupRenderState();

        try {
            renderer.drawChunkLayer(
                    renderType,
                    matrices,
                    cameraPos.x(),
                    cameraPos.y(),
                    cameraPos.z()
            );
        } finally {
            renderType.clearRenderState();
        }
    }

    public ClientLevel level() {
        return level;
    }


    @Override
    public void close() {
        setLevel(null);
    }


    public void scheduleChunkRebuild(int chunkX, int chunkZ, boolean important) {
        if (this.level == null) {
            return;
        }

        RenderDevice.enterManagedCode();

        try {
            this.renderer.scheduleRebuildForChunks(
                    chunkX,
                    this.level.getMinSection(),
                    chunkZ,
                    chunkX,
                    this.level.getMaxSection() - 1,
                    chunkZ,
                    important
            );

            this.renderer.scheduleTerrainUpdate();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
}