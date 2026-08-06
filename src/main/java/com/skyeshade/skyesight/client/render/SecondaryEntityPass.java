package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.SkyesightClientConfig;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.client.render.entity.PortalEntityRenderContextScope;
import com.skyeshade.skyesight.client.render.entity.PortalMultipartPartEligibility;
import com.skyeshade.skyesight.client.render.entity.PortalRenderableEntity;
import com.skyeshade.skyesight.mixin.client.EntityRenderDispatcherAccessor;
import com.skyeshade.skyesight.client.world.SkyesightVisualEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SecondaryEntityPass {
    private static final double ENTITY_FRUSTUM_CULL_PADDING_BLOCKS = 0.75D;
    private static final double PLAYER_ENTITY_FRUSTUM_CULL_PADDING_BLOCKS = 1.25D;
    private static final int DEFAULT_PORTAL_ENTITY_RENDER_CHUNK_RADIUS = 6;
    private static final double PORTAL_ENTITY_RENDER_MARGIN_BLOCKS = 16.0D;

    private SecondaryEntityPass() {}

    public static Result render(
            SecondaryViewFrame frame,
            SecondaryRemoteEntityTracker tracker,
            Minecraft minecraft,
            Vec3 aabbCenter,
            double radius,
            float partialTick
    ) {
        if (minecraft.level == null || minecraft.player == null) {
            return Result.empty();
        }

        ClientLevel level = minecraft.level;
        Vec3 cameraPosition = frame.camera().getPosition();
        BlockPos cameraBlockPos = BlockPos.containing(cameraPosition);
        ChunkPos cameraChunkPos = new ChunkPos(cameraBlockPos);
        int configuredEntityChunkRadius = frame.diagnostics().entityChunkRadius();
        int portalEntityChunkRadius = Math.max(
                0,
                configuredEntityChunkRadius > 0
                        ? configuredEntityChunkRadius
                        : DEFAULT_PORTAL_ENTITY_RENDER_CHUNK_RADIUS
        );
        double portalEntityBlockRadius = portalEntityChunkRadius * 16.0D + PORTAL_ENTITY_RENDER_MARGIN_BLOCKS;
        double minEntityY = Math.max(level.getMinBuildHeight(), cameraPosition.y() - portalEntityBlockRadius);
        double maxEntityY = Math.min(level.getMaxBuildHeight(), cameraPosition.y() + portalEntityBlockRadius);
        AABB bounds = new AABB(
                cameraPosition.x() - portalEntityBlockRadius,
                minEntityY,
                cameraPosition.z() - portalEntityBlockRadius,
                cameraPosition.x() + portalEntityBlockRadius,
                maxEntityY,
                cameraPosition.z() + portalEntityBlockRadius
        );
        Collection<Entity> portalEntityCandidates = level.getEntitiesOfClass(Entity.class, bounds, entity -> true);
        int duplicateRenderAttempts = 0;
        PoseStack poseStack = createEntityPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderDispatcherAccessor dispatcherAccessor = (EntityRenderDispatcherAccessor) dispatcher;
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting vertexSortingBefore = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        ShaderInstance shaderBefore = RenderSystem.getShader();
        int texture0Before = RenderSystem.getShaderTexture(0);
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();
        Level dispatcherLevelBefore = dispatcherAccessor.skyesight$getLevel();
        Camera dispatcherCameraBefore = dispatcher.camera;
        Entity dispatcherCrosshairBefore = dispatcher.crosshairPickEntity;
        Quaternionf dispatcherOrientationBefore = dispatcherAccessor.skyesight$getCameraOrientation() == null
                ? null
                : new Quaternionf(dispatcherAccessor.skyesight$getCameraOrientation());
        int clientConsidered = 0;
        int skippedOutsideAabb = 0;
        int skippedChunkRange = 0;
        int skippedDistance = 0;
        int skippedFrustum = 0;
        int rendered = 0;
        boolean entityFrustumCullingEnabled = SkyesightClientConfig.enablePortalEntityFrustumCulling();
        boolean entityFrustumAvailable = entityFrustumCullingEnabled && frame.frustum() != null;
        int framebufferBeforePass = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        modelViewStack.pushMatrix();

        try {
            if (!frame.diagnostics().renderToCurrentTarget()) {
                frame.colorTarget().bindWrite(true);
                RenderSystem.viewport(0, 0, frame.viewportWidth(), frame.viewportHeight());
            }
            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(frame.modelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            RenderSystem.enableDepthTest();

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            dispatcher.prepare(level, frame.camera(), minecraft.crosshairPickEntity);

            for (Entity entity : portalEntityCandidates) {

                if (entity.isRemoved()) {
                    continue;
                }

                ChunkPos entityChunkPos = entity.chunkPosition();
                int chunkDistanceX = Math.abs(entityChunkPos.x - cameraChunkPos.x);
                int chunkDistanceZ = Math.abs(entityChunkPos.z - cameraChunkPos.z);

                if (chunkDistanceX > portalEntityChunkRadius || chunkDistanceZ > portalEntityChunkRadius) {
                    skippedChunkRange++;
                    continue;
                }

                if (!bounds.intersects(entity.getBoundingBoxForCulling())) {
                    skippedOutsideAabb++;
                    continue;
                }

                double horizontalDistanceSquared = horizontalDistanceSquared(entity.position(), cameraPosition);

                if (horizontalDistanceSquared > portalEntityBlockRadius * portalEntityBlockRadius) {
                    skippedDistance++;
                    continue;
                }

                clientConsidered++;

                if (entityFrustumAvailable
                        && !frame.frustum().isVisible(entityFrustumCullBox(entity))) {
                    skippedFrustum++;
                    continue;
                }

                Vec3 renderPosition = lerpedPosition(entity, partialTick);
                Vec3 renderCoordinates = renderCoordinates(renderPosition, cameraPosition);
                renderEntityWithDispatcher(
                        entity,
                        renderCoordinates,
                        partialTick,
                        poseStack,
                        bufferSource,
                        dispatcher,
                        false,
                        true,
                        -1
                );
                rendered++;
            }

            bufferSource.endBatch();

            dispatcher.prepare(dispatcherLevelBefore, dispatcherCameraBefore, dispatcherCrosshairBefore);
            if (dispatcherOrientationBefore != null) {
                dispatcher.overrideCameraOrientation(dispatcherOrientationBefore);
            }

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionBefore, vertexSortingBefore);
            restoreFramebuffer(minecraft, framebufferBeforePass);
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);
            return Result.success(
                    clientConsidered,
                    rendered,
                    skippedOutsideAabb + skippedChunkRange,
                    skippedDistance,
                    skippedFrustum,
                    duplicateRenderAttempts
            );
        } catch (RuntimeException exception) {
            bufferSource.endBatch();
            try {
                dispatcher.prepare(dispatcherLevelBefore, dispatcherCameraBefore, dispatcherCrosshairBefore);
                if (dispatcherOrientationBefore != null) {
                    dispatcher.overrideCameraOrientation(dispatcherOrientationBefore);
                }
            } catch (RuntimeException ignored) {

            }
            try {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            } catch (RuntimeException ignored) {
                // Preserve the original exception
            }
            RenderSystem.setProjectionMatrix(projectionBefore, vertexSortingBefore);
            restoreFramebuffer(minecraft, framebufferBeforePass);
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);
            return Result.failed(
                    clientConsidered,
                    rendered,
                    skippedOutsideAabb + skippedChunkRange,
                    skippedDistance,
                    skippedFrustum,
                    duplicateRenderAttempts,
                    exception
            );
        } finally {
            bufferSource.endBatch();
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);
        }
    }



    public static Result renderPortalEntities(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            ClientLevel renderLevel,
            Iterable<PortalRenderableEntity> renderableEntities,
            int portalEntityChunkRadius,
            float partialTick,
            boolean renderSlotMarkers,
            boolean renderDepthOffSlotMarker,
            boolean renderProofBox,
            int expectedFramebufferId
    ) {
        boolean stencilEnabled = false;

        try {
            stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        } catch (RuntimeException ignored) {
            // Treat stencil query failures as inactive.
        }

        String earlyReturnReason = "";

        if (frame == null) {
            earlyReturnReason = "frame null";
        } else if (minecraft == null) {
            earlyReturnReason = "minecraft null";
        } else if (minecraft.player == null) {
            earlyReturnReason = "minecraft player null";
        } else if (minecraft.getEntityRenderDispatcher() == null) {
            earlyReturnReason = "dispatcher null";
        } else if (renderLevel == null) {
            earlyReturnReason = "level null";
        } else if (renderableEntities == null) {
            earlyReturnReason = "source null";
        } else if (!stencilEnabled) {
            earlyReturnReason = "stencil inactive";
        }

        if (!earlyReturnReason.isBlank()) {
            return Result.skipped(earlyReturnReason);
        }

        Vec3 cameraPosition = frame.camera().getPosition();
        ChunkPos cameraChunkPos = new ChunkPos(BlockPos.containing(cameraPosition));
        double portalEntityBlockRadius = Math.max(0, portalEntityChunkRadius) * 16.0D + PORTAL_ENTITY_RENDER_MARGIN_BLOCKS;
        AABB renderBounds = new AABB(
                cameraPosition.x() - portalEntityBlockRadius,
                renderLevel.getMinBuildHeight(),
                cameraPosition.z() - portalEntityBlockRadius,
                cameraPosition.x() + portalEntityBlockRadius,
                renderLevel.getMaxBuildHeight(),
                cameraPosition.z() + portalEntityBlockRadius
        );
        PoseStack poseStack = createEntityPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderDispatcherAccessor dispatcherAccessor = (EntityRenderDispatcherAccessor) dispatcher;
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting vertexSortingBefore = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        ShaderInstance shaderBefore = RenderSystem.getShader();
        int texture0Before = RenderSystem.getShaderTexture(0);
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();
        ClientLevel minecraftLevelBefore = minecraft.level;
        Level dispatcherLevelBefore = dispatcherAccessor.skyesight$getLevel();
        Camera dispatcherCameraBefore = dispatcher.camera;
        Entity dispatcherCrosshairBefore = dispatcher.crosshairPickEntity;
        Quaternionf dispatcherOrientationBefore = dispatcherAccessor.skyesight$getCameraOrientation() == null
                ? null
                : new Quaternionf(dispatcherAccessor.skyesight$getCameraOrientation());
        int total = 0;
        int rendered = 0;
        int skippedDistance = 0;
        int skippedFrustum = 0;
        int duplicateSuppressed = 0;
        Set<Entity> renderedParentObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> renderedParentIds = new HashSet<>();
        Set<UUID> renderedParentUuids = new HashSet<>();
        Set<String> renderedParentSignatures = new HashSet<>();
        int framebufferBeforePass = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        List<PortalRenderableEntity> renderableList = materializeRenderables(renderableEntities);
        if (renderableList.isEmpty()) {
            return Result.skipped("source empty");
        }

        modelViewStack.pushMatrix();

        try {
            if (!frame.diagnostics().renderToCurrentTarget() && expectedFramebufferId <= 0) {
                frame.colorTarget().bindWrite(true);
                RenderSystem.viewport(0, 0, frame.viewportWidth(), frame.viewportHeight());
            }
            if (expectedFramebufferId > 0
                    && GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != expectedFramebufferId) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, expectedFramebufferId);
            }

            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(frame.modelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            RenderSystem.enableDepthTest();

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.level = renderLevel;
            dispatcher.prepare(renderLevel, frame.camera(), minecraft.crosshairPickEntity);

            String scopeSource = sourceSummary(renderableList);
            try (PortalEntityRenderContextScope ignored = PortalEntityRenderContextScope.enter(
                    frame.diagnostics().portalInstanceId() == null
                            ? null
                            : ResourceLocation.tryParse(frame.diagnostics().portalInstanceId()),
                    renderLevel.dimension(),
                    scopeSource
            )) {
                for (PortalRenderableEntity renderableEntity : renderableList) {
                    total++;
                    if (!renderableEntity.standalonePart()
                            && suppressDuplicateVisualParent(
                            renderableEntity,
                            renderedParentObjects,
                            renderedParentIds,
                            renderedParentUuids,
                            renderedParentSignatures,
                            dispatcher
                    )) {
                        duplicateSuppressed++;
                        continue;
                    }
                    EntityRenderOutcome outcome = renderPortalRenderableEntity(
                            renderableEntity,
                            frame,
                            cameraPosition,
                            renderBounds,
                            portalEntityBlockRadius,
                            partialTick
                    );
                    if (!outcome.rendered()) {
                        skippedDistance += outcome.skippedDistance();
                        skippedFrustum += outcome.skippedFrustum();
                        duplicateSuppressed += outcome.duplicateSuppressed();
                        continue;
                    }

                    Entity entity = renderableEntity.entity();
                    PortalEntityRenderContextScope.register(
                            entity,
                            renderableEntity.standalonePart(),
                            renderableEntity.parentEntityId()
                    );
                    renderEntityWithDispatcher(
                            entity,
                            outcome.renderCoordinates(),
                            partialTick,
                            poseStack,
                            bufferSource,
                            dispatcher,
                            renderableEntity.standalonePart(),
                            renderableEntity.mainLevelBacked(),
                            renderableEntity.parentEntityId()
                    );
                    renderableEntity.finishRender();
                    rendered++;

                    if (!renderableEntity.mainLevelBacked()
                            && !renderableEntity.standalonePart()
                            && PortalMultipartEntityUtil.isMultipartParent(entity)) {
                        PassLocalPartExpansion expansion = passLocalMultipartParts(
                                renderableEntity,
                                renderBounds,
                                frame
                        );
                        duplicateSuppressed += expansion.duplicateSuppressed();

                        for (PortalRenderableEntity partRenderable : expansion.parts()) {
                            total++;
                            EntityRenderOutcome partOutcome = renderPortalRenderableEntity(
                                    partRenderable,
                                    frame,
                                    cameraPosition,
                                    renderBounds,
                                    portalEntityBlockRadius,
                                    partialTick
                            );
                            if (!partOutcome.rendered()) {
                                skippedDistance += partOutcome.skippedDistance();
                                skippedFrustum += partOutcome.skippedFrustum();
                                duplicateSuppressed += partOutcome.duplicateSuppressed();
                                continue;
                            }
                            Entity part = partRenderable.entity();
                            PortalEntityRenderContextScope.register(
                                    part,
                                    true,
                                    partRenderable.parentEntityId()
                            );
                            renderEntityWithDispatcher(
                                    part,
                                    partOutcome.renderCoordinates(),
                                    partialTick,
                                    poseStack,
                                    bufferSource,
                                    dispatcher,
                                    true,
                                    partRenderable.mainLevelBacked(),
                                    partRenderable.parentEntityId()
                            );
                            partRenderable.finishRender();
                            rendered++;
                        }
                    }
                }
            }

            bufferSource.endBatch();
            PortalProxyMarkerRenderer.renderMarkers(renderLevel, frame.camera(), poseStack, true);
            PortalLookMarkerRenderer.renderMarkers(renderLevel, frame.camera(), poseStack, true);

            minecraft.level = minecraftLevelBefore;
            dispatcher.prepare(dispatcherLevelBefore, dispatcherCameraBefore, dispatcherCrosshairBefore);
            if (dispatcherOrientationBefore != null) {
                dispatcher.overrideCameraOrientation(dispatcherOrientationBefore);
            }

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionBefore, vertexSortingBefore);
            restoreFramebuffer(minecraft, framebufferBeforePass);
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);

            return Result.success(
                    total,
                    rendered,
                    0,
                    skippedDistance,
                    skippedFrustum,
                    duplicateSuppressed
            );
        } catch (RuntimeException exception) {
            bufferSource.endBatch();
            try {
                minecraft.level = minecraftLevelBefore;
                dispatcher.prepare(dispatcherLevelBefore, dispatcherCameraBefore, dispatcherCrosshairBefore);
                if (dispatcherOrientationBefore != null) {
                    dispatcher.overrideCameraOrientation(dispatcherOrientationBefore);
                }
            } catch (RuntimeException ignored) {

            }
            try {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            } catch (RuntimeException ignored) {
                // Preserve the original exception.
            }
            RenderSystem.setProjectionMatrix(projectionBefore, vertexSortingBefore);
            restoreFramebuffer(minecraft, framebufferBeforePass);
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);

            return Result.failed(
                    total,
                    rendered,
                    0,
                    skippedDistance,
                    skippedFrustum,
                    duplicateSuppressed,
                    exception
            );
        } finally {
            minecraft.level = minecraftLevelBefore;
            bufferSource.endBatch();
            restoreCommonRenderState(shaderColorBefore, shaderBefore, texture0Before);
        }
    }

    private static void restoreCommonRenderState(float[] shaderColor, ShaderInstance shader, int texture0) {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        if (shader != null) {
            RenderSystem.setShader(() -> shader);
        }
        RenderSystem.setShaderTexture(0, texture0);
    }

    private static Vec3 lerpedPosition(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ())
        );
    }

    private static PoseStack createEntityPoseStack() {
        return new PoseStack();
    }

    private static Vec3 renderCoordinates(Vec3 worldPosition, Vec3 cameraPosition) {
        return worldPosition.subtract(cameraPosition);
    }

    private static double horizontalDistanceSquared(Vec3 entityPosition, Vec3 cameraPosition) {
        double dx = entityPosition.x() - cameraPosition.x();
        double dz = entityPosition.z() - cameraPosition.z();
        return dx * dx + dz * dz;
    }

    private static void restoreFramebuffer(Minecraft minecraft, int framebufferId) {
        if (minecraft != null
                && minecraft.getMainRenderTarget() != null
                && framebufferId == minecraft.getMainRenderTarget().frameBufferId) {
            minecraft.getMainRenderTarget().bindWrite(false);
            return;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
    }



    private static EntityRenderOutcome renderPortalRenderableEntity(
            PortalRenderableEntity renderableEntity,
            SecondaryViewFrame frame,
            Vec3 cameraPosition,
            AABB renderBounds,
            double portalEntityBlockRadius,
            float partialTick
    ) {
        if (renderableEntity == null || renderableEntity.entity() == null) {
            return EntityRenderOutcome.removedSkip();
        }

        renderableEntity.prepareForRender();
        Entity entity = renderableEntity.entity();

        if (entity.isRemoved()) {
            return EntityRenderOutcome.removedSkip();
        }

        if (renderableEntity.standalonePart()
                && shouldSkipDormantPassLocalPart(entity, renderableEntity.parentEntityId())) {
            return EntityRenderOutcome.dormantPartSkip();
        }

        AABB cullingBox = entity.getBoundingBoxForCulling();
        if (renderBounds != null && !renderBounds.intersects(cullingBox)) {
            return EntityRenderOutcome.distanceSkip();
        }

        if (frame != null && frame.frustum() != null && !frame.frustum().isVisible(cullingBox)) {
            return EntityRenderOutcome.frustumSkip();
        }

        Vec3 renderPosition = lerpedPosition(entity, partialTick);
        if (renderPosition.distanceToSqr(cameraPosition) > portalEntityBlockRadius * portalEntityBlockRadius * 4.0D) {
            return EntityRenderOutcome.distanceSkip();
        }
        return EntityRenderOutcome.rendered(renderPosition, renderCoordinates(renderPosition, cameraPosition));
    }

    private static void renderEntityWithDispatcher(
            Entity entity,
            Vec3 renderCoordinates,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            EntityRenderDispatcher dispatcher,
            boolean standalonePart,
            boolean mainLevelBacked,
            int parentEntityId
    ) {
        if (standalonePart && !mainLevelBacked) {
            clampVisualPartOldPosition(entity, parentEntityId);
        }
        dispatcher.render(
                entity,
                renderCoordinates.x(),
                renderCoordinates.y(),
                renderCoordinates.z(),
                entity.getYRot(),
                partialTick,
                poseStack,
                bufferSource,
                dispatcher.getPackedLightCoords(entity, partialTick)
        );
    }

    private static void clampVisualPartOldPosition(Entity entity, int parentEntityId) {
        if (!(entity instanceof PartEntity<?> part)) {
            return;
        }
        Entity parent = part.getParent();
        if (parent == null || parent.getId() != parentEntityId) {
            return;
        }
        Vec3 current = entity.position();
        Vec3 old = new Vec3(entity.xo, entity.yo, entity.zo);
        boolean oldNearOrigin = Math.abs(old.x()) < 0.5D && Math.abs(old.y()) < 0.5D && Math.abs(old.z()) < 0.5D;
        boolean currentValid = current.length() > 0.5D;
        boolean oldFarFromCurrent = old.distanceToSqr(current) > 16.0D * 16.0D;
        if ((oldNearOrigin && currentValid) || oldFarFromCurrent) {
            entity.xo = current.x();
            entity.yo = current.y();
            entity.zo = current.z();
            entity.xOld = current.x();
            entity.yOld = current.y();
            entity.zOld = current.z();
        }
    }

    private static String sourceSummary(Iterable<PortalRenderableEntity> renderableEntities) {
        if (renderableEntities == null) {
            return "unknown";
        }
        for (PortalRenderableEntity renderableEntity : renderableEntities) {
            if (renderableEntity != null && renderableEntity.source() != null) {
                return renderableEntity.source();
            }
        }
        return "empty";
    }

    private static List<PortalRenderableEntity> materializeRenderables(
            Iterable<PortalRenderableEntity> renderableEntities
    ) {
        if (renderableEntities instanceof List<PortalRenderableEntity> list) {
            return list;
        }
        List<PortalRenderableEntity> list = new ArrayList<>();
        if (renderableEntities == null) {
            return list;
        }
        for (PortalRenderableEntity renderableEntity : renderableEntities) {
            list.add(renderableEntity);
        }
        return list;
    }

    private static boolean suppressDuplicateVisualParent(
            PortalRenderableEntity renderableEntity,
            Set<Entity> renderedParentObjects,
            Set<Integer> renderedParentIds,
            Set<UUID> renderedParentUuids,
            Set<String> renderedParentSignatures,
            EntityRenderDispatcher dispatcher
    ) {
        Entity entity = renderableEntity.entity();
        if (entity == null) {
            return false;
        }

        boolean duplicateObject = !renderedParentObjects.add(entity);
        boolean duplicateId = !renderedParentIds.add(entity.getId());
        boolean duplicateUuid = entity.getUUID() != null && !renderedParentUuids.add(entity.getUUID());
        String signature = visualParentSignature(entity, dispatcher);
        boolean duplicateSignature = !renderedParentSignatures.add(signature);
        return duplicateObject || duplicateId || duplicateUuid || duplicateSignature;
    }

    private static String visualParentSignature(Entity entity, EntityRenderDispatcher dispatcher) {
        return entity.getType()
                + "|"
                + entity.getClass().getName()
                + "|"
                + rendererClass(entity, dispatcher)
                + "|"
                + Math.round(entity.getX() * 8.0D)
                + ","
                + Math.round(entity.getY() * 8.0D)
                + ","
                + Math.round(entity.getZ() * 8.0D);
    }

    private static String rendererClass(Entity entity, EntityRenderDispatcher dispatcher) {
        try {
            return dispatcher.getRenderer(entity).getClass().getName();
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }

    private static PassLocalPartExpansion passLocalMultipartParts(
            PortalRenderableEntity parentRenderable,
            AABB renderBounds,
            SecondaryViewFrame frame
    ) {
        List<PortalRenderableEntity> parts = new ArrayList<>();
        Entity parent = parentRenderable.entity();
        PartEntity<?>[] parentParts = PortalMultipartEntityUtil.parts(parent);
        if (parentParts == null || parentParts.length == 0) {
            return new PassLocalPartExpansion(parts, 0, 0);
        }

        int skippedDormant = 0;
        int duplicateSuppressed = 0;
        Set<PartEntity<?>> seenObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> seenIds = new HashSet<>();
        Set<String> seenSignatures = new HashSet<>();
        for (PartEntity<?> part : parentParts) {
            if (part == null || part.isRemoved()) {
                skippedDormant++;
                continue;
            }
            if (isBodyLikePassLocalPart(parent, part)) {
                duplicateSuppressed++;
                continue;
            }
            if (!seenObjects.add(part) || !seenIds.add(part.getId())) {
                duplicateSuppressed++;
                continue;
            }
            String signature = passLocalPartSignature(part);
            if (!seenSignatures.add(signature)) {
                duplicateSuppressed++;
                continue;
            }
            if (part.getParent() != parent) {
                duplicateSuppressed++;
                continue;
            }
            if (shouldSkipDormantPassLocalPart(part, parent.getId())) {
                skippedDormant++;
                continue;
            }
            PortalMultipartPartEligibility.Result eligibility =
                    PortalMultipartPartEligibility.evaluate(parent, part);
            if (!eligibility.render()) {
                skippedDormant++;
                continue;
            }
            AABB cullingBox = part.getBoundingBoxForCulling();
            if (renderBounds != null && !renderBounds.intersects(cullingBox)) {
                skippedDormant++;
                continue;
            }
            if (frame != null && frame.frustum() != null && !frame.frustum().isVisible(cullingBox)) {
                skippedDormant++;
                continue;
            }

            parts.add(new PortalRenderableEntity(
                    part,
                    parentRenderable.dimension(),
                    parentRenderable.source() + "_part",
                    () -> {},
                    () -> {},
                    false,
                    true,
                    parent.getId()
            ));
        }
        return new PassLocalPartExpansion(parts, skippedDormant, duplicateSuppressed);
    }

    private static boolean isBodyLikePassLocalPart(Entity parent, PartEntity<?> part) {
        if (parent == null || part == null) {
            return true;
        }
        if (part == parent || part.getParent() != parent) {
            return true;
        }
        if (part.getUUID() != null && part.getUUID().equals(parent.getUUID())) {
            return true;
        }
        if (part.getType() == parent.getType()) {
            return true;
        }
        if (part.getClass() == parent.getClass()) {
            return true;
        }
        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            return rendererClass(part, dispatcher).equals(rendererClass(parent, dispatcher));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String passLocalPartSignature(PartEntity<?> part) {
        AABB box = part.getBoundingBoxForCulling();
        return part.getClass().getName()
                + "@"
                + Math.round(part.getX() * 16.0D)
                + ","
                + Math.round(part.getY() * 16.0D)
                + ","
                + Math.round(part.getZ() * 16.0D)
                + ":"
                + Math.round(box.getXsize() * 16.0D)
                + "x"
                + Math.round(box.getYsize() * 16.0D)
                + "x"
                + Math.round(box.getZsize() * 16.0D);
    }

    private static boolean shouldSkipDormantPassLocalPart(Entity part, int parentEntityId) {
        if (part == null) {
            return true;
        }
        Entity parent = PortalMultipartEntityUtil.parentOfPart(part);
        if (parent != null && parentEntityId >= 0 && parent.getId() != parentEntityId) {
            return true;
        }
        double distanceFromParent = parent == null ? 0.0D : part.position().distanceTo(parent.position());
        boolean nearOrigin = Math.abs(part.getX()) < 0.5D
                && Math.abs(part.getY()) < 0.5D
                && Math.abs(part.getZ()) < 0.5D;
        AABB box = part.getBoundingBoxForCulling();
        boolean invalidBox = box.getXsize() <= 0.0D || box.getYsize() <= 0.0D || box.getZsize() <= 0.0D;
        return invalidBox || (nearOrigin && distanceFromParent > 32.0D);
    }

    private static AABB entityFrustumCullBox(Entity entity) {
        double padding = entity instanceof Player
                ? PLAYER_ENTITY_FRUSTUM_CULL_PADDING_BLOCKS
                : ENTITY_FRUSTUM_CULL_PADDING_BLOCKS;
        return entity.getBoundingBoxForCulling().inflate(padding);
    }

    public record Result(
            boolean attempted,
            int considered,
            int rendered,
            int skippedFrustum,
            int skippedDistance,
            int skippedOutsideBounds,
            int duplicateSuppressed,
            String skippedReason,
            String exception
    ) {
        public static Result empty() {
            return skipped("not attempted");
        }

        public static Result skipped(String reason) {
            return new Result(false, 0, 0, 0, 0, 0, 0, reason, "");
        }

        public static Result success(
                int considered,
                int rendered,
                int skippedOutsideBounds,
                int skippedDistance,
                int skippedFrustum,
                int duplicateSuppressed
        ) {
            return new Result(
                    true,
                    considered,
                    rendered,
                    skippedFrustum,
                    skippedDistance,
                    skippedOutsideBounds,
                    duplicateSuppressed,
                    "",
                    ""
            );
        }

        public static Result failed(
                int considered,
                int rendered,
                int skippedOutsideBounds,
                int skippedDistance,
                int skippedFrustum,
                int duplicateSuppressed,
                RuntimeException exception
        ) {
            String message = exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
            return new Result(
                    true,
                    considered,
                    rendered,
                    skippedFrustum,
                    skippedDistance,
                    skippedOutsideBounds,
                    duplicateSuppressed,
                    "",
                    message
            );
        }
    }

    private record PassLocalPartExpansion(
            List<PortalRenderableEntity> parts,
            int skippedDormant,
            int duplicateSuppressed
    ) {}

    private record EntityRenderOutcome(
            boolean rendered,
            Vec3 renderPosition,
            Vec3 renderCoordinates,
            int skippedDistance,
            int skippedFrustum,
            int duplicateSuppressed
    ) {
        private static EntityRenderOutcome rendered(Vec3 renderPosition, Vec3 renderCoordinates) {
            return new EntityRenderOutcome(
                    true,
                    renderPosition,
                    renderCoordinates,
                    0,
                    0,
                    0
            );
        }

        private static EntityRenderOutcome removedSkip() {
            return skipped(0, 0, 0);
        }

        private static EntityRenderOutcome distanceSkip() {
            return skipped(1, 0, 0);
        }

        private static EntityRenderOutcome frustumSkip() {
            return skipped(0, 1, 0);
        }

        private static EntityRenderOutcome dormantPartSkip() {
            return skipped(0, 0, 0);
        }

        private static EntityRenderOutcome skipped(
                int skippedDistance,
                int skippedFrustum,
                int duplicateSuppressed
        ) {
            return new EntityRenderOutcome(
                    false,
                    null,
                    null,
                    skippedDistance,
                    skippedFrustum,
                    duplicateSuppressed
            );
        }
    }

}
