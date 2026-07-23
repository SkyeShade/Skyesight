package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.server.portal.PortalPathProximity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class SkyesightVisualWorldManager {
    private static final Map<ResourceLocation, SkyesightVisualWorld> WORLDS = new HashMap<>();
    private static long lastAnimationSummaryMillis;

    private SkyesightVisualWorldManager() {}

    public static void tickAll() {
        int activeViews = 0;
        int visualEntities = 0;
        int visualEntitiesClientTicked = 0;
        int visualBlockEntities = 0;
        int visualBlockEntitiesClientTicked = 0;
        int visualParticlesSpawned = 0;
        String skippedReasons = "-";

        for (Map.Entry<ResourceLocation, SkyesightVisualWorld> entry : WORLDS.entrySet()) {
            SkyesightVisualWorld world = entry.getValue();
            if (!world.isClosed()) {
                activeViews++;
                visualEntities += world.entityStore().size();
                visualBlockEntities += world.chunkReceiver().countBlockEntities();
                SkyesightVisualWorld.TickStats tickStats = world.tick(entry.getKey());
                visualEntitiesClientTicked += tickStats.visualEntitiesClientTicked();
                visualBlockEntitiesClientTicked += tickStats.visualBlockEntitiesClientTicked();
                visualParticlesSpawned += tickStats.visualParticlesSpawned();
                if (!"-".equals(tickStats.entitySkippedReason())) {
                    skippedReasons = "entity=" + tickStats.entitySkippedReason();
                }
                if (!"-".equals(tickStats.blockEntitySkippedReason())) {
                    skippedReasons = ("-".equals(skippedReasons) ? "" : skippedReasons + ";")
                            + "blockEntity=" + tickStats.blockEntitySkippedReason();
                }
            }
        }
        maybeLogAnimationSummary(
                activeViews,
                visualEntities,
                visualEntitiesClientTicked,
                visualBlockEntities,
                visualBlockEntitiesClientTicked,
                visualParticlesSpawned,
                skippedReasons
        );
    }

    public static SkyesightVisualWorld get(ResourceLocation viewId) {
        return WORLDS.get(viewId);
    }

    public static void forEachWorld(BiConsumer<ResourceLocation, SkyesightVisualWorld> consumer) {
        WORLDS.forEach(consumer);
    }

    public static SkyesightVisualWorld getIfCurrent(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        if (!isCurrentTargetDimension(viewId, dimension)) {
            return null;
        }
        SkyesightVisualWorld world = get(viewId);
        if (world != null && !world.isClosed() && !world.dimension().equals(dimension)) {
            return null;
        }
        return world;
    }

    public static SkyesightVisualWorld getOrCreateIfCurrent(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        return isCurrentTargetDimension(viewId, dimension)
                ? getOrCreate(viewId, dimension)
                : null;
    }

    public static boolean isCurrentTargetDimension(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        if (viewId == null || dimension == null) {
            return false;
        }
        RegisteredPortalView view = SkyesightPortalApi.getPortal(viewId.toString());
        return view != null && view.target().dimension().equals(dimension);
    }

    public static SkyesightVisualWorld getOrCreate(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        SkyesightVisualWorld existing = WORLDS.get(viewId);

        if (existing != null && !existing.isClosed()) {
            if (existing.dimension().equals(dimension)) {
                return existing;
            }
            Skyesight.LOGGER.warn(
                    "[Skyesight] PORTAL_VIEW_CACHE_INVALIDATED: viewId={} oldTargetDim={} newTargetDim={} reason=visual_world_dim_mismatch cleared=visualWorld",
                    viewId,
                    existing.dimension().location(),
                    dimension.location()
            );
            close(viewId);
        }

        if (existing != null) {
            WORLDS.remove(viewId);
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.getConnection() == null) {
            return null;
        }

        SkyesightVisualClientLevel skyesightLevel = SkyesightClientLevelFactory.create(dimension);
        SkyesightVisualWorld world = new SkyesightVisualWorld(dimension, skyesightLevel);
        PortalPathProximity.registerVisualLevel(
                skyesightLevel,
                viewId,
                dimension,
                () -> Minecraft.getInstance().player
        );

        if (SkyesightDebugConfig.VERBOSE_RENDER) {
            Skyesight.LOGGER.info(
                    "[Skyesight] Created visual world view={} dimension={} sameObjectAsMain={}",
                    viewId,
                    dimension.location(),
                    skyesightLevel == minecraft.level
            );
        }

        WORLDS.put(viewId, world);

        return world;
    }

    public static void close(ResourceLocation viewId) {
        SkyesightVisualWorld world = WORLDS.remove(viewId);

        if (world != null) {
            SkyesightPortalEntityPool.clearView(viewId);
            PortalPathProximity.unregisterVisualLevel(world.level());
            world.close();
        }
    }

    public static void closeAll() {
        SkyesightPortalEntityPool.clearAll();
        for (SkyesightVisualWorld world : WORLDS.values()) {
            PortalPathProximity.unregisterVisualLevel(world.level());
            world.close();
        }

        WORLDS.clear();
    }

    private static void maybeLogAnimationSummary(
            int activeViews,
            int visualEntities,
            int visualEntitiesClientTicked,
            int visualBlockEntities,
            int visualBlockEntitiesClientTicked,
            int visualParticlesSpawned,
            String skippedReasons
    ) {
        if (!SkyesightDebugConfig.WATCH_DEBUG && !SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.VERBOSE_ENTITY) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAnimationSummaryMillis < 5000L) {
            return;
        }
        lastAnimationSummaryMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_CROSS_DIM_ANIMATION_SYNC_SUMMARY: activeViews={} visualEntities={} visualEntitiesClientTicked={} visualEntitiesCreated=see-snapshot-log visualEntitiesUpdated=see-snapshot-log visualEntitiesRecreated=0 visualBlockEntities={} visualBlockEntitiesClientTicked={} visualParticlesSpawned={} playerQueriesFromVisualBEs=see-proximity-log playerQueryHits=see-proximity-log playerQueryMisses=see-proximity-log serverEntitiesTicked=not-audited serverBlockEntitiesTicked=not-audited skippedReasons={}",
                activeViews,
                visualEntities,
                visualEntitiesClientTicked,
                visualBlockEntities,
                visualBlockEntitiesClientTicked,
                visualParticlesSpawned,
                skippedReasons
        );
    }
}
