package com.skyeshade.skyesight.client.render.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;

public final class SkyesightSodiumWorldRenderer implements AutoCloseable {
    private static final boolean BUILD_IMMEDIATELY = false;
    private static Field sodiumWorldRendererSectionManagerField;
    private static boolean sodiumReadinessReflectionFailed;

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
        renderTerrain(camera, frustum, modelMatrix, projectionMatrix, true);
    }

    public void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            boolean renderTranslucent
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
            drawTerrainLayer(RenderType.cutoutMipped(), matrices, cameraPos);
            drawTerrainLayer(RenderType.cutout(), matrices, cameraPos);
            if (renderTranslucent) {
                drawTerrainLayer(RenderType.translucent(), matrices, cameraPos);
            }
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
            ResourceLocation viewId,
            SkyesightRemoteChunkReceiver chunkReceiver,
            Camera camera,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        if (this.level == null || this.minecraft.player == null) {
            return;
        }

        PoseStack poseStack = new PoseStack();

        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();

        ClientLevel previousLevel = this.minecraft.level;
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting vertexSortingBefore = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();
        Vec3 mainCameraPos = this.minecraft.gameRenderer.getMainCamera().getPosition();
        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean crossDimension = previousLevel != null && !previousLevel.dimension().equals(this.level.dimension());

        modelViewStack.pushMatrix();

        try {
            this.minecraft.level = this.level;
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

                    Vec3 renderOffset = cameraRelativeOffset(blockEntityPos, cameraPos);
                    BlockState blockState = this.level.getBlockState(blockEntityPos);
                    renderBlockEntity(
                            blockEntity,
                            renderOffset,
                            poseStack,
                            bufferSource,
                            partialTick
                    );
                    if (SkyesightDebugConfig.WATCH_DEBUG && crossDimension) {
                        Skyesight.LOGGER.info(
                                "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_RENDER_DEPTH: viewId={} blockPos={} blockEntityType={} displayDimension={} cameraDimension={} renderTargetName=framebuffer:{} inPortalContentPass=yes depthTestEnabled={} depthMask={} depthFunc={} usingPortalCameraOrigin={} portalCameraPos={} mainCameraPos={} renderOffset={} bufferFlushedBeforeComposite=pending reasonIfSkipped=-",
                                viewId == null ? "-" : viewId,
                                blockEntityPos,
                                blockEntity.getType(),
                                previousLevel.dimension().location(),
                                this.level.dimension().location(),
                                framebufferBefore,
                                GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? "yes" : "no",
                                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? "yes" : "no",
                                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                                camera == this.minecraft.gameRenderer.getMainCamera() ? "no" : "yes",
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
                                this.level.dimension().location(),
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
            this.minecraft.level = previousLevel;
        }
    }


    private void renderBlockEntity(
            BlockEntity blockEntity,
            Vec3 renderOffset,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            float partialTick
    ) {
        poseStack.pushPose();
        poseStack.translate(renderOffset.x(), renderOffset.y(), renderOffset.z());
        this.minecraft.getBlockEntityRenderDispatcher().render(
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

    public void scheduleBlockUpdate(BlockPos pos) {
        if (this.level == null || !this.isRendererReady()) {
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
            if (SkyesightDebugConfig.WATCH_DEBUG
                    && this.minecraft.level != null
                    && !this.minecraft.level.dimension().equals(this.level.dimension())) {
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_UPDATE: displayDimension={} cameraDimension={} blockPos={} dirtySection={},{},{}",
                        this.minecraft.level.dimension().location(),
                        this.level.dimension().location(),
                        pos,
                        sectionX,
                        sectionY,
                        sectionZ
                );
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
    }
    public void scheduleTerrainUpdate() {
        if (!this.isRendererReady()) {
            return;
        }
        this.renderer.scheduleTerrainUpdate();
    }

    private void drawTerrainLayer(RenderType renderType, ChunkRenderMatrices matrices, Vec3 cameraPos) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
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
        if (this.level == null || !this.isRendererReady()) {
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

    private boolean isRendererReady() {
        if (this.renderer == null || sodiumReadinessReflectionFailed) {
            return false;
        }
        try {
            initializeReadinessReflection();
            return sodiumWorldRendererSectionManagerField.get(this.renderer) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            sodiumReadinessReflectionFailed = true;
            return false;
        }
    }

    private static void initializeReadinessReflection() throws NoSuchFieldException {
        if (sodiumWorldRendererSectionManagerField != null) {
            return;
        }
        sodiumWorldRendererSectionManagerField = SodiumWorldRenderer.class.getDeclaredField("renderSectionManager");
        sodiumWorldRendererSectionManagerField.setAccessible(true);
    }
}
