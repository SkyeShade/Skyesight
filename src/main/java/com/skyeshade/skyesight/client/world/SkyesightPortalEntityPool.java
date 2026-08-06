package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.client.render.entity.PortalRenderableEntity;
import com.skyeshade.skyesight.client.render.entity.PortalVisualEntityAnimationUpdater;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkyesightPortalEntityPool {
    /*
     * Experimental isolated pool for future cross-dim entity rendering.
     * Stable production rendering still uses SkyesightVisualEntityStore
     * snapshots; this pool is dormant unless both experimental flags are set.
     */
    private static final Map<PortalEntityKey, Entity> ENTITIES = new LinkedHashMap<>();
    private static final Map<PortalEntityKey, PoseState> POSE_STATES = new LinkedHashMap<>();
    private static boolean leakedMainLevelEntityWarningLogged;
    private static final Set<String> POSE_WARNINGS = new HashSet<>();

    private SkyesightPortalEntityPool() {}

    public static Entity get(ResourceLocation viewId, ResourceKey<Level> dimension, int entityId) {
        return ENTITIES.get(new PortalEntityKey(viewId, dimension, entityId));
    }

    public static boolean containsEntityId(int entityId) {
        for (PortalEntityKey key : ENTITIES.keySet()) {
            if (key.entityId() == entityId) {
                return true;
            }
        }
        return false;
    }

    public static void put(ResourceLocation viewId, ResourceKey<Level> dimension, int entityId, Entity entity) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            return;
        }
        if (viewId == null || dimension == null || entity == null) {
            return;
        }
        if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
            PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "portal_entity_pool");
            return;
        }
        if (removeIfLeakedToMainLevel(entity)) {
            return;
        }
        ((SkyesightEntityDimensionContext) entity).skyesight$setExplicitDimension(dimension);
        PortalEntityKey key = new PortalEntityKey(viewId, dimension, entityId);
        ENTITIES.put(key, entity);
        POSE_STATES.computeIfAbsent(key, ignored -> new PoseState()).lastPacketKind = "PUT";
    }

    public static void markAuthoritativePosition(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int entityId,
            String packetKind
    ) {
        PortalEntityKey key = new PortalEntityKey(viewId, dimension, entityId);
        PoseState state = POSE_STATES.computeIfAbsent(key, ignored -> new PoseState());
        state.hasAuthoritativePosition = true;
        state.lastAuthoritativePositionMillis = System.currentTimeMillis();
        state.lastPacketKind = packetKind;
    }

    public static void markPacket(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int entityId,
            String packetKind
    ) {
        PoseState state = POSE_STATES.computeIfAbsent(
                new PortalEntityKey(viewId, dimension, entityId),
                ignored -> new PoseState()
        );
        state.lastPacketKind = packetKind;
    }

    public static void remove(ResourceLocation viewId, ResourceKey<Level> dimension, int entityId) {
        Entity removed = ENTITIES.remove(new PortalEntityKey(viewId, dimension, entityId));
        POSE_STATES.remove(new PortalEntityKey(viewId, dimension, entityId));
        if (removed != null) {
            removed.remove(Entity.RemovalReason.DISCARDED);
        }
    }

    public static void clearView(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        ENTITIES.entrySet().removeIf(entry -> {
            if (!viewId.equals(entry.getKey().viewId())) {
                return false;
            }
            entry.getValue().remove(Entity.RemovalReason.DISCARDED);
            POSE_STATES.remove(entry.getKey());
            return true;
        });
    }

    public static void clearAll() {
        for (Entity entity : ENTITIES.values()) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        ENTITIES.clear();
        POSE_STATES.clear();
    }

    private static boolean removeIfLeakedToMainLevel(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        for (Entity mainLevelEntity : minecraft.level.entitiesForRendering()) {
            if (mainLevelEntity != entity) {
                continue;
            }
            minecraft.level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
            entity.remove(Entity.RemovalReason.DISCARDED);
            if (!leakedMainLevelEntityWarningLogged) {
                leakedMainLevelEntityWarningLogged = true;
                Skyesight.LOGGER.warn(
                        "[Skyesight] PORTAL_ENTITY_POOL_LEAK_GUARD: removed pooled entity from main client level entityId={} type={}",
                        entity.getId(),
                        entity.getType()
                );
            }
            return true;
        }
        return false;
    }

    public static Iterable<Entity> entities(ResourceLocation viewId, ResourceKey<Level> dimension) {
        List<Entity> result = new ArrayList<>();
        for (Map.Entry<PortalEntityKey, Entity> entry : ENTITIES.entrySet()) {
            PortalEntityKey key = entry.getKey();
            Entity entity = entry.getValue();
            if (key.viewId().equals(viewId)
                    && key.dimension().equals(dimension)
                    && entity != null
                    && !entity.isRemoved()) {
                result.add(entity);
            }
        }
        return result;
    }

    public static int count(ResourceLocation viewId, ResourceKey<Level> dimension) {
        int count = 0;
        for (Entity ignored : entities(viewId, dimension)) {
            count++;
        }
        return count;
    }

    public static List<PortalRenderableEntity> renderablesFor(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            AABB bounds,
            @Nullable Frustum frustum,
            @Nullable SkyesightVisualWorld visualWorld
    ) {
        List<PortalRenderableEntity> result = new ArrayList<>();
        if (viewId == null || dimension == null || bounds == null) {
            return result;
        }
        String source = "portal_entity_pool:" + viewId;
        for (Entity entity : entities(viewId, dimension)) {
            if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "portal_entity_pool_render_source");
                continue;
            }
            ResourceKey<Level> effectiveDimension =
                    ((SkyesightEntityDimensionContext) entity).skyesight$getEffectiveDimension(dimension);
            if (!dimension.equals(effectiveDimension)) {
                continue;
            }
            AABB cullingBox = entity.getBoundingBoxForCulling();
            PoseState poseState = POSE_STATES.get(new PortalEntityKey(viewId, dimension, entity.getId()));
            if (!isRenderEligible(viewId, dimension, entity, poseState, bounds)) {
                continue;
            }
            if (!bounds.intersects(cullingBox)) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(cullingBox)) {
                continue;
            }
            SkyesightVisualEntity snapshotState = visualWorld == null
                    ? null
                    : visualWorld.entityStore().getByEntityId(entity.getId());
            if (PortalMultipartEntityUtil.isMultipartParent(entity) && snapshotState == null) {
                logPoseSkip(viewId, entity, poseState, "poolSkippedMultipartStateIncomplete");
                continue;
            }
            result.add(new PortalRenderableEntity(
                    entity,
                    effectiveDimension,
                    source,
                    () -> {
                        if (snapshotState != null) {
                            snapshotState.applyRenderStateTo(
                                    entity,
                                    0.0F,
                                    "portal_entity_pool_snapshot_state"
                            );
                            return;
                        }
                        PortalVisualEntityAnimationUpdater.updateForRender(
                                entity,
                                0.0F,
                                "portal_entity_pool_prepare_render"
                        );
                    }
            ));
        }
        return result;
    }

    public static List<PortalRenderableEntity> renderablesFor(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        return renderablesFor(viewId, dimension, bounds, frustum, null);
    }

    private static boolean isRenderEligible(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            Entity entity,
            PoseState poseState,
            AABB bounds
    ) {
        if (poseState == null || !poseState.hasAuthoritativePosition) {
            logPoseSkip(viewId, entity, poseState, "no_authoritative_position");
            return false;
        }
        if (!Double.isFinite(entity.getX()) || !Double.isFinite(entity.getY()) || !Double.isFinite(entity.getZ())) {
            logPoseSkip(viewId, entity, poseState, "non_finite_position");
            return false;
        }
        if (!bounds.inflate(16.0D).intersects(entity.getBoundingBoxForCulling())) {
            logPoseSkip(viewId, entity, poseState, "outside_watch_bounds");
            return false;
        }
        clampBadOldPosition(viewId, dimension, entity, poseState);
        return true;
    }

    private static void clampBadOldPosition(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            Entity entity,
            PoseState poseState
    ) {
        double dx = entity.xo - entity.getX();
        double dy = entity.yo - entity.getY();
        double dz = entity.zo - entity.getZ();
        boolean oldNearOrigin = Math.abs(entity.xo) < 0.5D
                && Math.abs(entity.yo) < 0.5D
                && Math.abs(entity.zo) < 0.5D;
        boolean currentValid = entity.position().length() > 0.5D;
        boolean oldFar = dx * dx + dy * dy + dz * dz > 16.0D * 16.0D;
        if (!((oldNearOrigin && currentValid) || oldFar)) {
            return;
        }
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        logPoseSkip(viewId, entity, poseState, "clamped_old_position");
    }

    private static void logPoseSkip(
            ResourceLocation viewId,
            Entity entity,
            PoseState poseState,
            String reason
    ) {
        if (!SkyesightNativeVisualEntityRoutingDebug.enabled()) {
            return;
        }
        String key = viewId + ":" + entity.getType() + ":" + reason;
        if (!POSE_WARNINGS.add(key) || POSE_WARNINGS.size() > 32) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENTITY_POOL_POSE view={} entityId={} type={} reason={} pos={} old={},{},{} delta={} lastPacket={} hasAuthoritativePosition={} ageMs={}",
                viewId,
                entity.getId(),
                entity.getType(),
                reason,
                entity.position(),
                entity.xo,
                entity.yo,
                entity.zo,
                entity.getDeltaMovement(),
                poseState == null ? "-" : poseState.lastPacketKind,
                poseState != null && poseState.hasAuthoritativePosition,
                poseState == null || poseState.lastAuthoritativePositionMillis <= 0
                        ? -1
                        : System.currentTimeMillis() - poseState.lastAuthoritativePositionMillis
        );
    }

    private record PortalEntityKey(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int entityId
    ) {}

    private static final class PoseState {
        private boolean hasAuthoritativePosition;
        private long lastAuthoritativePositionMillis;
        private String lastPacketKind = "-";
    }
}
