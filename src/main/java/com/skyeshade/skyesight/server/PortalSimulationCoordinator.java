package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.mixin.server.lifecycle.ServerLevelTickNonPassengerInvoker;
import com.skyeshade.skyesight.server.portal.PortalChunkTicketController;
import com.skyeshade.skyesight.server.portal.PortalDespawnProtection;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Key;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Region;
import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;

public class PortalSimulationCoordinator {
    private static final boolean PORTAL_SIMULATION_ENABLED = true;
    private static final boolean PORTAL_SIMULATION_ENTITY_TICKING_ENABLED = true;
    private static final boolean PORTAL_NATURAL_SPAWNING_DEFAULT_ENABLED = true;
    private static final boolean PORTAL_NATURAL_SPAWNING_INCLUDE_NEAR_SAME_DIM_DEFAULT = false;
    private static final int PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE = 128;
    private static final boolean PORTAL_DESPAWN_PROTECTION_ENABLED = true;
    private static final boolean PORTAL_SIMULATION_SAME_DIM_ENABLED = true;
    private static final boolean PORTAL_SIMULATION_CROSS_DIM_ENABLED = true;
    private static final boolean PORTAL_FORCE_TICK_EXISTING_MOBS = true;
    private static final boolean PORTAL_SIMULATION_USE_VIRTUAL_PLAYER_COORDS_FOR_SPAWN_DISTANCE = false;
    private static final int PORTAL_SIMULATION_MAX_HOSTILE_MOBS_PER_PORTAL_REGION = 8;
    private static final int PORTAL_SIMULATION_PATHFINDING_CHUNK_MARGIN = 1;
    private static final int DEFAULT_PORTAL_SIMULATION_RADIUS_CHUNKS = 4;
    private static final int MAX_PORTAL_SIMULATION_CHUNKS_PER_PLAYER = 81;
    private static final int MAX_PORTAL_SIMULATION_REGIONS_PER_PLAYER = 8;
    private static final int PORTAL_NATURAL_SPAWNING_CHUNKS_PER_TICK = 4;
    private static final int PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_LEVEL = 4;
    private static final int PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_VIEW = PORTAL_NATURAL_SPAWNING_CHUNKS_PER_TICK;
    private static final boolean PORTAL_NATURAL_SPAWNING_FORCE_CENTER_CHUNK_DEFAULT = false;
    private static final int PORTAL_NATURAL_SPAWNING_MAX_LIVE_HOSTILE_PER_VIEW = 4;
    private static final int PORTAL_NATURAL_SPAWNING_EXISTING_LIVE_SOFT_CAP = 16;
    private static final int PORTAL_NATURAL_SPAWNING_MAX_TOTAL_PER_DIM = 8;
    private static final String PORTAL_SPAWNED_TAG = "skyesight_portal_spawned";
    private static final String PORTAL_SPAWNED_VIEW_TAG = "skyesight_portal_spawned_view";
    private static final String PORTAL_SPAWNED_OWNER_TAG = "skyesight_portal_spawned_owner";
    private static final String PORTAL_SPAWNED_TICK_TAG = "skyesight_portal_spawned_tick";
    private static final String PORTAL_LIFECYCLE_TEST_TAG = "skyesight_portal_lifecycle_test";
    private static final String PORTAL_TRANSIENT_TEST_ENTITY_TAG = "skyesight_transient_test_entity";
    private static final long EXPIRE_AFTER_TICKS = 120L;
    private static final Map<EntityTickKey, Integer> LAST_ENTITY_TICKS = new HashMap<>();
    private static final Map<EntityTickKey, Long> VANILLA_TICK_NON_PASSENGER_LAST_TICK = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongSet> PORTAL_ADDED_SPAWN_CHUNKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongSet> PORTAL_GATE_ADMITTED_CHUNKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, PortalSpawnLoopSelection> PORTAL_SPAWN_LOOP_SELECTIONS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> PORTAL_VANILLA_LOOP_MERGE_COUNTS = new HashMap<>();
    private static final Map<RegionCategoryKey, Integer> PORTAL_SPAWN_SELECTION_CURSORS = new HashMap<>();
    private static final Map<ResourceLocation, Long> PORTAL_SPAWN_PAUSED_UNTIL_TICK = new HashMap<>();
    private static final Map<ResourceLocation, Integer> PORTAL_DISCARD_WINDOW_COUNTS = new HashMap<>();
    private static final Map<ResourceLocation, Long> PORTAL_DISCARD_WINDOW_START_MILLIS = new HashMap<>();
    private static boolean portalNaturalSpawningBootDefaultsLogged;
    private static boolean portalNaturalSpawningRuntimeEnabled = PORTAL_NATURAL_SPAWNING_DEFAULT_ENABLED;
    private static boolean portalNaturalSpawningRuntimeMobSpawningEnabled = PORTAL_NATURAL_SPAWNING_DEFAULT_ENABLED;
    private static boolean portalNaturalSpawningRuntimeExperimentEnabled = PORTAL_NATURAL_SPAWNING_DEFAULT_ENABLED;
    private static int portalNaturalSpawningRuntimeLiveHostileCap = PORTAL_NATURAL_SPAWNING_MAX_LIVE_HOSTILE_PER_VIEW;
    private static int portalNaturalSpawningRuntimeDimCap = PORTAL_NATURAL_SPAWNING_MAX_TOTAL_PER_DIM;
    private static int portalNaturalSpawningRuntimeChunksPerView = PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_VIEW;
    private static boolean portalNaturalSpawningRuntimeForceCenterChunk = PORTAL_NATURAL_SPAWNING_FORCE_CENTER_CHUNK_DEFAULT;
    private static boolean portalNaturalSpawningRuntimeIncludeNearSameDim = PORTAL_NATURAL_SPAWNING_INCLUDE_NEAR_SAME_DIM_DEFAULT;
    private static final ThreadLocal<SpawnForChunkContext> CURRENT_SPAWN_FOR_CHUNK = new ThreadLocal<>();
    private static final ThreadLocal<SpawnCategoryContext> CURRENT_SPAWN_CATEGORY = new ThreadLocal<>();
    private static final ThreadLocal<PortalSpawnContext> CURRENT_PORTAL_SPAWN_CONTEXT = new ThreadLocal<>();

    protected PortalSimulationCoordinator() {}

    public static void update(
            ServerPlayer player,
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radiusChunks
    ) {
        if (!PORTAL_SIMULATION_ENABLED || player == null || viewId == null || dimension == null) {
            return;
        }

        boolean sameDim = player.serverLevel().dimension().equals(dimension);
        if (sameDim && !PORTAL_SIMULATION_SAME_DIM_ENABLED) {
            return;
        }
        if (!sameDim && !PORTAL_SIMULATION_CROSS_DIM_ENABLED) {
            return;
        }

        MinecraftServer server = player.server;
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }

        int radius = Math.max(0, Math.min(radiusChunks <= 0 ? DEFAULT_PORTAL_SIMULATION_RADIUS_CHUNKS : radiusChunks, DEFAULT_PORTAL_SIMULATION_RADIUS_CHUNKS));
        while (PortalRegionTracker.chunkCountForRadius(radius) > MAX_PORTAL_SIMULATION_CHUNKS_PER_PLAYER && radius > 0) {
            radius--;
        }

        Key key = new Key(player.getUUID(), viewId);
        Region previous = PortalRegionTracker.get(key);
        if (previous == null && PortalRegionTracker.countRegionsForPlayer(player.getUUID()) >= MAX_PORTAL_SIMULATION_REGIONS_PER_PLAYER) {
            return;
        }

        LongSet nextChunks = PortalRegionTracker.buildChunkSet(centerChunkX, centerChunkZ, radius);

        if (previous != null) {
            removeRegionTickets(server, previous);
        }

        Region region = new Region(
                player.getUUID(),
                viewId,
                dimension,
                centerChunkX,
                centerChunkZ,
                radius,
                radius,
                radius,
                nextChunks,
                server.getTickCount(),
                sameDim
        );
        addRegionTickets(level, region);
        PortalRegionTracker.put(region);
    }


    public static void tick(MinecraftServer server) {
        if (!PORTAL_SIMULATION_ENABLED || server == null) {
            return;
        }
        initializePortalNaturalSpawningState();

        int regions = 0;
        int chunksLoaded = 0;
        int chunksTicking = 0;
        int chunksEntityTicking = 0;
        int candidateEntities = 0;
        int entitiesTicked = 0;
        int naturalSpawnEligibleChunks = 0;
        int forceTickExistingMobs = 0;
        String reasonIfNoSpawns = "-";
        String firstNoEntityTickReason = "-";
        StringBuilder byDimension = new StringBuilder();
        StringBuilder samples = new StringBuilder();
        Map<ResourceKey<Level>, Integer> dimensionCounts = new HashMap<>();
        List<Map.Entry<Key, Region>> regionSnapshot = new ArrayList<>(PortalRegionTracker.entrySet());
        List<Key> removeRegionKeys = new ArrayList<>();
        int backingSizeBeforeRemoval = PortalRegionTracker.size();

        for (Map.Entry<Key, Region> entry : regionSnapshot) {
            Region region = entry.getValue();
            ServerLevel level = server.getLevel(region.dimension());

            if (level == null || server.getTickCount() - region.lastUpdateTick() > EXPIRE_AFTER_TICKS) {
                removeRegionTickets(server, region);
                removeRegionKeys.add(entry.getKey());
                continue;
            }

            regions++;
            dimensionCounts.merge(region.dimension(), 1, Integer::sum);
            addRegionTickets(level, region);

            AABB area = regionBounds(level, region, region.entityTickRadiusChunks());
            int beforeEntityCount = level.getEntities((Entity) null, area, entity -> true).size();
            candidateEntities += beforeEntityCount;
            entitiesTicked += countEntityTickDeltas(level, area);
            if (PORTAL_FORCE_TICK_EXISTING_MOBS) {
                forceTickExistingMobs += forceTickExistingEntities(level, area);
            }
            for (long packed : region.chunks()) {
                int chunkX = ChunkPos.getX(packed);
                int chunkZ = ChunkPos.getZ(packed);
                level.setChunkForced(chunkX, chunkZ, true);

                if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    chunksLoaded++;
                    chunksTicking++;
                    chunksEntityTicking++;
                    if (shouldRunNaturalSpawning(level) && withinChunkRadius(region, chunkX, chunkZ, region.mobSpawnRadiusChunks())) {
                        naturalSpawnEligibleChunks++;
                    }
                } else {
                    level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                }
            }

            if (samples.length() < 320) {
                if (samples.length() > 0) {
                    samples.append(';');
                }
                samples.append(region.viewId())
                        .append('@')
                        .append(region.dimension().location())
                        .append(" center=")
                        .append(region.centerChunkX())
                        .append(',')
                        .append(region.centerChunkZ())
                        .append(" loadRadius=")
                        .append(region.loadRadiusChunks())
                        .append(" entityTickRadius=")
                        .append(region.entityTickRadiusChunks())
                        .append(" mobSpawnRadius=")
                        .append(region.mobSpawnRadiusChunks())
                        .append(" biome=")
                        .append(centerBiome(level, region))
                        .append(" realPlayersNearby=")
                        .append(realPlayersNear(level, region));
            }
        }
        for (Key key : removeRegionKeys) {
            PortalRegionTracker.remove(key);
        }
        reasonIfNoSpawns = "vanilla-driven";
        if (candidateEntities == 0) {
            firstNoEntityTickReason = "no entities currently inside portal simulation regions";
        } else if (entitiesTicked == 0) {
            firstNoEntityTickReason = "entities present but tickCount did not advance between simulation samples";
        }
        for (Map.Entry<ResourceKey<Level>, Integer> entry : dimensionCounts.entrySet()) {
            if (byDimension.length() > 0) {
                byDimension.append(',');
            }
            byDimension.append(entry.getKey().location()).append('=').append(entry.getValue());
        }

    }

    private static void initializePortalNaturalSpawningState() {
        if (portalNaturalSpawningBootDefaultsLogged) {
            return;
        }
        portalNaturalSpawningBootDefaultsLogged = true;
        clearPortalSpawnPausesInternal("snapshot-discard-bug-fixed");
    }

    public static String summary() {
        return compactStatus();
    }

    public static String compactStatus() {
        return "enabled=" + yesNo(PORTAL_SIMULATION_ENABLED)
                + " entityTicking=" + yesNo(PORTAL_SIMULATION_ENTITY_TICKING_ENABLED)
                + " sameDim=" + yesNo(PORTAL_SIMULATION_SAME_DIM_ENABLED)
                + " crossDim=" + yesNo(PORTAL_SIMULATION_CROSS_DIM_ENABLED)
                + " despawnProtection=" + yesNo(PORTAL_DESPAWN_PROTECTION_ENABLED)
                + " regions=" + PortalRegionTracker.size();
    }



    public static int activeRegionCount(ResourceKey<Level> dimension) {
        int count = 0;
        for (Region region : PortalRegionTracker.values()) {
            if (region.dimension().equals(dimension)) {
                count++;
            }
        }
        return count;
    }

    public static boolean portalNaturalSpawningExperimentEnabled() {
        return PORTAL_SIMULATION_ENABLED
                && effectivePortalMobSpawningEnabled()
                && effectivePortalNaturalSpawningExperimentFlag()
                && effectivePortalNaturalSpawningEnabled();
    }

    private static boolean effectivePortalMobSpawningEnabled() {
        return portalNaturalSpawningRuntimeMobSpawningEnabled;
    }

    private static boolean effectivePortalNaturalSpawningExperimentFlag() {
        return portalNaturalSpawningRuntimeExperimentEnabled;
    }

    private static boolean effectivePortalNaturalSpawningEnabled() {
        return portalNaturalSpawningRuntimeEnabled;
    }



    public static String setPortalNaturalSpawningRuntimeEnabled(boolean enabled) {
        portalNaturalSpawningRuntimeEnabled = enabled;
        portalNaturalSpawningRuntimeMobSpawningEnabled = enabled;
        portalNaturalSpawningRuntimeExperimentEnabled = enabled;
        PORTAL_VANILLA_LOOP_MERGE_COUNTS.clear();
        return portalNaturalSpawningStatus();
    }

    public static String setPortalNaturalSpawningLiveCapOverride(int value) {
        portalNaturalSpawningRuntimeLiveHostileCap = Math.max(0, value);
        return "maxLiveHostilePerView=" + portalNaturalSpawningRuntimeLiveHostileCap;
    }

    public static String setPortalNaturalSpawningDimCapOverride(int value) {
        portalNaturalSpawningRuntimeDimCap = Math.max(0, value);
        return "maxTotalPortalSpawnedPerDim=" + portalNaturalSpawningRuntimeDimCap;
    }

    public static String setPortalNaturalSpawningChunksPerViewOverride(int value) {
        portalNaturalSpawningRuntimeChunksPerView = Math.max(0, value);
        return "chunksPerView=" + portalNaturalSpawningRuntimeChunksPerView;
    }

    private static int effectivePortalNaturalSpawningChunksPerLevel() {
        return Math.max(PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_LEVEL, effectivePortalNaturalSpawningChunksPerView());
    }

    public static String setPortalNaturalSpawningForceCenterChunk(boolean enabled) {
        portalNaturalSpawningRuntimeForceCenterChunk = enabled;
        return "forceCenterChunk=" + yesNo(portalNaturalSpawningRuntimeForceCenterChunk);
    }

    public static String setPortalNaturalSpawningIncludeNearSameDim(boolean enabled) {
        portalNaturalSpawningRuntimeIncludeNearSameDim = enabled;
        return "includeNearSameDim=" + yesNo(portalNaturalSpawningRuntimeIncludeNearSameDim);
    }

    public static String clearPortalSpawnPauses() {
        return clearPortalSpawnPausesInternal("command");
    }

    private static String clearPortalSpawnPausesInternal(String reason) {
        String oldPaused = PORTAL_SPAWN_PAUSED_UNTIL_TICK.isEmpty() ? "-" : PORTAL_SPAWN_PAUSED_UNTIL_TICK.keySet().toString();
        String oldCounts = PORTAL_DISCARD_WINDOW_COUNTS.isEmpty() ? "-" : PORTAL_DISCARD_WINDOW_COUNTS.toString();
        for (Map.Entry<ResourceLocation, Integer> entry : PORTAL_DISCARD_WINDOW_COUNTS.entrySet()) {
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_DISCARD_COUNTER_RESET: viewId={} oldCount={} reason={}",
                    entry.getKey(),
                    entry.getValue(),
                    reason
            );
        }
        PORTAL_SPAWN_PAUSED_UNTIL_TICK.clear();
        PORTAL_DISCARD_WINDOW_COUNTS.clear();
        PORTAL_DISCARD_WINDOW_START_MILLIS.clear();
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_SPAWN_PAUSES_CLEARED: views=all oldPausedViews={} oldDiscardCounts={} reason={}",
                oldPaused,
                oldCounts,
                reason
        );
        return "views=all oldPausedViews=" + oldPaused + " oldDiscardCounts=" + oldCounts;
    }

    public static String portalNaturalSpawningStatus() {
        String reason = portalNaturalSpawningDisabledReason(null);
        return "PORTAL_NATURAL_SPAWNING_ENABLED=" + yesNo(effectivePortalNaturalSpawningEnabled())
                + " PORTAL_SIMULATION_MOB_SPAWNING_ENABLED=" + yesNo(effectivePortalMobSpawningEnabled())
                + " PORTAL_SIMULATION_VANILLA_NATURAL_SPAWNING_EXPERIMENT_ENABLED=" + yesNo(effectivePortalNaturalSpawningExperimentFlag())
                + " portalNaturalSpawningExperimentEnabled=" + yesNo(portalNaturalSpawningExperimentEnabled())
                + " canMergePortalChunksIntoVanillaSpawnLoop=unknown"
                + " disabledReason=" + reason
                + " caps=maxChunksPerLevel:" + effectivePortalNaturalSpawningChunksPerLevel()
                + ",chunksPerView:" + effectivePortalNaturalSpawningChunksPerView()
                + ",maxLiveHostilePerView:" + effectivePortalNaturalSpawningLiveHostileCap()
                + ",maxTotalPerDim:" + effectivePortalNaturalSpawningDimCap()
                + ",forceCenterChunk:" + yesNo(portalNaturalSpawningRuntimeForceCenterChunk)
                + ",includeNearSameDim:" + yesNo(portalNaturalSpawningRuntimeIncludeNearSameDim)
                + ",crossDimPriority:yes"
                + ",farSameDimMinDistance:" + PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE
                + ",pausedViews:" + pausedViewsSummary()
                + ",discardCounts:" + discardCountsSummary();
    }

    public static String portalNaturalSpawningStatus(MinecraftServer server) {
        String reason = portalNaturalSpawningDisabledReason(server);
        boolean canMerge = canMergePortalChunksIntoVanillaSpawnLoop(server);
        return "PORTAL_NATURAL_SPAWNING_ENABLED=" + yesNo(effectivePortalNaturalSpawningEnabled())
                + " PORTAL_SIMULATION_MOB_SPAWNING_ENABLED=" + yesNo(effectivePortalMobSpawningEnabled())
                + " PORTAL_SIMULATION_VANILLA_NATURAL_SPAWNING_EXPERIMENT_ENABLED=" + yesNo(effectivePortalNaturalSpawningExperimentFlag())
                + " portalNaturalSpawningExperimentEnabled=" + yesNo(portalNaturalSpawningExperimentEnabled())
                + " canMergePortalChunksIntoVanillaSpawnLoop=" + yesNo(canMerge)
                + " disabledReason=" + reason
                + " caps=maxChunksPerLevel:" + effectivePortalNaturalSpawningChunksPerLevel()
                + ",chunksPerView:" + effectivePortalNaturalSpawningChunksPerView()
                + ",maxLiveHostilePerView:" + effectivePortalNaturalSpawningLiveHostileCap()
                + ",maxTotalPerDim:" + effectivePortalNaturalSpawningDimCap()
                + ",forceCenterChunk:" + yesNo(portalNaturalSpawningRuntimeForceCenterChunk)
                + ",includeNearSameDim:" + yesNo(portalNaturalSpawningRuntimeIncludeNearSameDim)
                + ",crossDimPriority:yes"
                + ",farSameDimMinDistance:" + PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE
                + ",chunksPerView:" + effectivePortalNaturalSpawningChunksPerView()
                + ",liveCap:" + effectivePortalNaturalSpawningLiveHostileCap()
                + ",dimCap:" + effectivePortalNaturalSpawningDimCap()
                + ",pausedViews:" + pausedViewsSummary()
                + ",portalOwnedByViewLastTick:" + portalOwnedByViewLastTick()
                + ",discardCounts:" + discardCountsSummary()
                + " activeViewCaps=" + portalNaturalSpawningCapStatus(server);
    }

    private static String pausedViewsSummary() {
        return PORTAL_SPAWN_PAUSED_UNTIL_TICK.isEmpty() ? "-" : PORTAL_SPAWN_PAUSED_UNTIL_TICK.keySet().toString();
    }

    private static String discardCountsSummary() {
        return PORTAL_DISCARD_WINDOW_COUNTS.isEmpty() ? "-" : PORTAL_DISCARD_WINDOW_COUNTS.toString();
    }

    private static String portalOwnedByViewLastTick() {
        StringBuilder builder = new StringBuilder();
        for (PortalSpawnLoopSelection selection : PORTAL_SPAWN_LOOP_SELECTIONS.values()) {
            String value = selection.portalOwnedByView();
            if (!"-".equals(value)) {
                appendUnique(builder, selection.dimension.location() + "=" + value, 260);
            }
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private static int effectivePortalNaturalSpawningLiveHostileCap() {
        return portalNaturalSpawningRuntimeLiveHostileCap;
    }

    private static int effectivePortalNaturalSpawningDimCap() {
        return portalNaturalSpawningRuntimeDimCap;
    }

    private static int effectivePortalNaturalSpawningChunksPerView() {
        return portalNaturalSpawningRuntimeChunksPerView;
    }

    public static boolean canMergePortalChunksIntoVanillaSpawnLoop(MinecraftServer server) {
        if (!portalNaturalSpawningExperimentEnabled() || server == null) {
            return false;
        }
        for (ServerLevel level : server.getAllLevels()) {
            PortalNaturalSpawnDryRun dryRun = dryRunPortalNaturalSpawnMerge(level);
            if ("-".equals(dryRun.disabledReason()) && dryRun.wouldMergeChunks() > 0) {
                return true;
            }
        }
        return false;
    }

    private static String portalNaturalSpawningCapStatus(MinecraftServer server) {
        if (server == null) {
            return "server-unavailable";
        }
        StringBuilder builder = new StringBuilder();
        for (Region region : PortalRegionTracker.values()) {
            ServerLevel level = server.getLevel(region.dimension());
            if (level == null) {
                continue;
            }
            AABB bounds = regionBounds(level, region, region.mobSpawnRadiusChunks());
            ServerPlayer owner = server.getPlayerList().getPlayer(region.playerId());
            Vec3 virtualObserver = virtualObserverPosForRegion(region);
            double distanceOwnerToVirtual = owner == null ? -1.0D : horizontalDistance(owner.getX(), owner.getZ(), virtualObserver.x, virtualObserver.z);
            List<Entity> hostiles = level.getEntities(
                    (Entity) null,
                    bounds,
                    entity -> !entity.isRemoved() && entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER
            );
            int dimPortalSpawned = countTrackedPortalSpawnedInActiveRegions(level);
            StringBuilder samples = new StringBuilder();
            for (Entity entity : hostiles) {
                appendUnique(samples, entity.getId() + ":" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), 160);
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append("viewId=").append(region.viewId())
                    .append(" dim=").append(region.dimension().location())
                    .append(" portalKind=").append(region.sameDim() ? "same-dim" : "cross-dim")
                    .append(" virtualObserverChunk=").append(region.centerChunkX()).append(',').append(region.centerChunkZ())
                    .append(" distanceOwnerToVirtual=").append(owner == null ? "-" : formatDouble(distanceOwnerToVirtual))
                    .append(" liveHostileCount=").append(hostiles.size())
                    .append(" existingLiveSoftCap=").append(PORTAL_NATURAL_SPAWNING_EXISTING_LIVE_SOFT_CAP)
                    .append(" portalSpawnedViewCap=").append(effectivePortalNaturalSpawningLiveHostileCap())
                    .append(" dimPortalSpawnedCount=").append(dimPortalSpawned)
                    .append(" maxTotalPortalSpawnedPerDim=").append(effectivePortalNaturalSpawningDimCap())
                    .append(" blockedByLiveHostileCap=").append(yesNo(hostiles.size() >= PORTAL_NATURAL_SPAWNING_EXISTING_LIVE_SOFT_CAP))
                    .append(" blockedByDimCap=").append(yesNo(dimPortalSpawned >= effectivePortalNaturalSpawningDimCap()))
                    .append(" samples=").append(samples.length() == 0 ? "-" : samples);
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }


    private static String portalNaturalSpawningDisabledReason(MinecraftServer server) {
        if (!effectivePortalNaturalSpawningEnabled()) {
            return "portal-natural-spawning-command-disabled";
        }
        if (!effectivePortalMobSpawningEnabled()) {
            return "debug-mob-spawning-disabled";
        }
        if (!effectivePortalNaturalSpawningExperimentFlag()) {
            return "vanilla-natural-spawning-experiment-disabled";
        }
        if (!PORTAL_SIMULATION_ENABLED) {
            return "portal-simulation-disabled";
        }
        if (server == null) {
            return "server-unavailable";
        }

        boolean sawRegion = false;
        String firstBlockedReason = "no-active-portal-region";
        for (ServerLevel level : server.getAllLevels()) {
            PortalNaturalSpawnDryRun dryRun = dryRunPortalNaturalSpawnMerge(level);
            if (dryRun.activePortalObservers() <= 0) {
                continue;
            }
            sawRegion = true;
            if ("-".equals(dryRun.disabledReason()) && dryRun.wouldMergeChunks() > 0) {
                return "-";
            }
            if ("no-eligible-entity-ticking-chunks".equals(firstBlockedReason) && dryRun.wouldCandidateChunks() > 0) {
                firstBlockedReason = dryRun.disabledReason();
            } else if (!"no-active-portal-region".equals(dryRun.disabledReason())) {
                firstBlockedReason = dryRun.disabledReason();
            }
        }
        return sawRegion ? firstBlockedReason : "no-active-portal-region";
    }



    private static int countTrackedPortalSpawnedInActiveRegions(ServerLevel level) {
        if (level == null) {
            return 0;
        }
        LongSet ids = new LongOpenHashSet();
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            for (Entity entity : level.getEntities((Entity) null, regionBounds(level, region, region.mobSpawnRadiusChunks()), PortalSimulationCoordinator::isPortalSpawned)) {
                ids.add(entity.getId());
            }
        }
        return ids.size();
    }

    private static int countPortalSpawnedForView(ServerLevel level, ResourceLocation viewId) {
        if (level == null || viewId == null) {
            return 0;
        }
        int count = 0;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension()) || !region.viewId().equals(viewId)) {
                continue;
            }
            for (Entity entity : level.getEntities((Entity) null, regionBounds(level, region, region.mobSpawnRadiusChunks()), PortalSimulationCoordinator::isPortalSpawned)) {
                if (viewId.toString().equals(entity.getPersistentData().getString(PORTAL_SPAWNED_VIEW_TAG))) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int eligibleChunkCount(ResourceKey<Level> dimension) {
        int count = 0;
        for (Region region : PortalRegionTracker.values()) {
            if (region.dimension().equals(dimension)) {
                count += region.chunks().size();
            }
        }
        return count;
    }

    public static int effectiveNaturalSpawnChunkCount(ServerLevel level, int vanillaCount) {
        if (!portalNaturalSpawningExperimentEnabled()) {
            return vanillaCount;
        }
        PortalSpawnLoopSelection selection = preparePortalChunksForVanillaSpawnLoop(level);
        int portalAdded = selection.selectedChunks.size();
        int effective = vanillaCount + portalAdded;
        selection.vanillaSpawningChunkCount = vanillaCount;
        return effective;
    }


    private static PortalSpawnLoopSelection preparePortalChunksForVanillaSpawnLoop(ServerLevel level) {
        PORTAL_VANILLA_LOOP_MERGE_COUNTS.put(level.dimension(), 0);
        PORTAL_ADDED_SPAWN_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet()).clear();
        PORTAL_SPAWN_LOOP_SELECTIONS.remove(level.dimension());
        PortalSpawnLoopSelection selection = new PortalSpawnLoopSelection(level.dimension(), level.getGameTime());
        if (!portalNaturalSpawningExperimentEnabled() || !shouldRunNaturalSpawning(level)) {
            selection.reason = portalNaturalSpawningDisabledReasonForLevel(level);
            PORTAL_SPAWN_LOOP_SELECTIONS.put(level.dimension(), selection);
            return selection;
        }

        List<PortalSpawnViewPlan> plans = new ArrayList<>();
        LongSet existing = new LongOpenHashSet();
        LongSet vanillaChunks = vanillaPlayerSpawnChunkSet(level);
        for (long packed : vanillaChunks) {
            selection.spawnSources.put(packed, SpawnSource.realPlayer());
        }
        selection.realPlayerOwnedChunks = vanillaChunks.size();
        for (PortalNaturalSpawnObserver observer : portalNaturalSpawnObservers(level)) {
            selection.regionsForLevel++;
            selection.addRegionIdentity(observer);
            List<LevelChunk> validForRegion = new ArrayList<>();
            int regionAlreadyVanillaRejected = 0;
            int regionPositionsConsidered = 0;
            int liveHostiles = countEntitiesByCategory(level, regionBounds(level, observer.region(), observer.radiusChunks()), MobCategory.MONSTER);
            int viewPortalSpawned = countPortalSpawnedForView(level, observer.viewId());
            int dimPortalSpawned = countTrackedPortalSpawnedInActiveRegions(level);
            boolean liveCapReached = liveHostiles >= effectivePortalNaturalSpawningLiveHostileCap();
            boolean viewCapReached = viewPortalSpawned >= effectivePortalNaturalSpawningLiveHostileCap();
            boolean dimCapReached = dimPortalSpawned >= effectivePortalNaturalSpawningDimCap();
            for (int chunkX = observer.centerChunk().x - observer.radiusChunks(); chunkX <= observer.centerChunk().x + observer.radiusChunks(); chunkX++) {
                for (int chunkZ = observer.centerChunk().z - observer.radiusChunks(); chunkZ <= observer.centerChunk().z + observer.radiusChunks(); chunkZ++) {
                    long packed = ChunkPos.asLong(chunkX, chunkZ);
                    selection.positionsConsidered++;
                    regionPositionsConsidered++;
                    if (!existing.add(packed)) {
                        selection.rejectedAlreadyVanilla++;
                        regionAlreadyVanillaRejected++;
                        selection.recordFirstReject(chunkX, chunkZ, "duplicate-selected-region");
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        selection.rejectedNotLoaded++;
                        selection.recordFirstReject(chunkX, chunkZ, "not-loaded");
                        continue;
                    }
                    selection.loaded++;
                    appendUnique(selection.sample, chunkX + "," + chunkZ, 220);
                    boolean entityTicking = level.shouldTickBlocksAt(packed)
                            && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(packed);
                    if (!entityTicking) {
                        selection.rejectedNotEntityTicking++;
                        selection.recordFirstReject(chunkX, chunkZ, "not-entity-ticking");
                        continue;
                    }
                    selection.entityTicking++;
                    if (selection.spawnSources.get(packed) != null && selection.spawnSources.get(packed).kind() == SpawnSourceKind.REAL_PLAYER
                            || (observer.sameDim() && !PortalVanillaSpawnBridge.isPortalBypassAllowed(level, new ChunkPos(chunkX, chunkZ), observer.viewId()))) {
                        selection.rejectedAlreadyVanilla++;
                        regionAlreadyVanillaRejected++;
                        selection.recordFirstReject(chunkX, chunkZ, "already-covered-by-real-player-vanilla-spawn-list");
                        continue;
                    }
                    if (isChunkEntirelyTooCloseToVirtualObserver(observer.pos(), chunk.getPos(), 24.0D)) {
                        selection.rejectedTooCloseToVirtualObserver++;
                        selection.firstTooCloseChunk = chunkX + "," + chunkZ;
                        selection.recordFirstReject(chunkX, chunkZ, "too-close-to-virtual-observer");
                        continue;
                    }
                    selection.candidateBeforeCaps++;
                    validForRegion.add(chunk);
                }
            }
            String blockedReason = "-";
            String priority = portalSpawnPriority(level, observer, validForRegion);
            selection.recordObservedPriority(priority);
            boolean fullyVanillaCoveredSameDim = observer.sameDim()
                    && validForRegion.isEmpty()
                    && regionPositionsConsidered > 0
                    && regionAlreadyVanillaRejected >= regionPositionsConsidered;
            boolean liveCapBlocksSelection = observer.sameDim() && !fullyVanillaCoveredSameDim && liveCapReached;
            if (fullyVanillaCoveredSameDim) {
                blockedReason = "already-covered-by-real-player-vanilla-spawn-list";
            } else if (liveCapBlocksSelection) {
                selection.rejectedLiveHostileCap++;
                selection.recordFirstReject(observer.centerChunk().x, observer.centerChunk().z, "live-hostile-cap-reached");
                blockedReason = "live-hostile-cap-reached";
            } else if (viewCapReached) {
                selection.rejectedLiveHostileCap++;
                selection.recordFirstReject(observer.centerChunk().x, observer.centerChunk().z, "view-portal-spawned-cap-reached");
                blockedReason = "view-portal-spawned-cap-reached";
            } else if (dimCapReached) {
                selection.rejectedDimCap++;
                selection.recordFirstReject(observer.centerChunk().x, observer.centerChunk().z, "total-dim-cap-reached");
                blockedReason = "total-dim-cap-reached";
            } else if (isPortalSpawnPaused(level, observer.viewId())) {
                selection.recordFirstReject(observer.centerChunk().x, observer.centerChunk().z, "hostile-discard-pause-active");
                blockedReason = "hostile-discard-pause-active";
            }
            boolean skipNearSameDim = observer.sameDim()
                    && "near-same-dim".equals(priority)
                    && !portalNaturalSpawningRuntimeIncludeNearSameDim;
            if ("-".equals(blockedReason) && skipNearSameDim) {
                blockedReason = "near-same-dim-disabled";
            }
            if (!"-".equals(blockedReason)) {
                selection.recordBlockedPriority(priority, blockedReason);
                selection.recordSkippedView(blockedReason);
                continue;
            }
            plans.add(new PortalSpawnViewPlan(observer, validForRegion, priority, liveHostiles, viewPortalSpawned, dimPortalSpawned));
        }
        selection.viewsConsidered = plans.size() + selection.viewsSkippedVanillaCovered + selection.viewsSkippedCap;
        plans.sort(Comparator.comparingInt(PortalSpawnViewPlan::priorityRank));
        int perLevelRemaining = effectivePortalNaturalSpawningChunksPerLevel();
        int viewsSelected = 0;
        for (PortalSpawnViewPlan plan : plans) {
            if (perLevelRemaining <= 0) {
                break;
            }
            int before = selection.selectedChunks.size();
            int perViewRemaining = Math.min(effectivePortalNaturalSpawningChunksPerView(), perLevelRemaining);
            if (portalNaturalSpawningRuntimeForceCenterChunk) {
                LevelChunk centerChunk = removeChunkByPos(plan.chunks(), plan.observer().centerChunk());
                if (centerChunk != null && perViewRemaining > 0 && perLevelRemaining > 0) {
                    selectPortalSpawnChunk(level, selection, plan.observer(), centerChunk);
                    selection.centerChunkIncluded = true;
                    perViewRemaining--;
                    perLevelRemaining--;
                }
            }
            int selectedFromRing = selectPortalSpawnChunksFromRing(level, selection, plan.observer(), plan.chunks(), perViewRemaining, perLevelRemaining);
            perLevelRemaining -= selectedFromRing;
            if (selection.selectedChunks.size() > before) {
                viewsSelected++;
                selection.recordSelectedPriority(plan.priority(), selection.selectedChunks.size() - before);
            }
        }
        selection.viewsSelected = viewsSelected;
        selection.reasonIfNoCrossDimSelection = selection.crossDimSelected > 0 ? "-" : firstMissingPriorityReason(selection, plans, "cross-dim");
        selection.reasonIfNoFarSameDimSelection = selection.farSameDimSelected > 0 ? "-" : firstMissingPriorityReason(selection, plans, "far-same-dim");
        selection.portalOwnedChunks = selection.selectedChunks.size();
        selection.candidateAfterCaps = selection.selectedChunks.size();
        if (selection.candidateAfterCaps > selection.candidateBeforeCaps) {
            Skyesight.LOGGER.error(
                    "[Skyesight] Portal spawn selection invariant violation: dim={} positionsConsidered={} candidateBeforeCaps={} candidateAfterCaps={} selected={} reason={}",
                    level.dimension().location(),
                    selection.positionsConsidered,
                    selection.candidateBeforeCaps,
                    selection.candidateAfterCaps,
                    selection.selectedChunks.size(),
                    selection.reason
            );
        }
        selection.reason = selection.selectedChunks.isEmpty()
                ? selection.zeroSelectionReason()
                : "-";
        PORTAL_SPAWN_LOOP_SELECTIONS.put(level.dimension(), selection);
        PORTAL_VANILLA_LOOP_MERGE_COUNTS.put(level.dimension(), selection.selectedChunks.size());
        return selection;
    }

    private static int selectPortalSpawnChunksFromRing(
            ServerLevel level,
            PortalSpawnLoopSelection selection,
            PortalNaturalSpawnObserver observer,
            List<LevelChunk> validForRegion,
            int perViewRemaining,
            int perLevelRemaining
    ) {
        if (level == null || selection == null || observer == null || validForRegion == null || validForRegion.isEmpty()) {
            return 0;
        }
        int limit = Math.min(perViewRemaining, perLevelRemaining);
        if (limit <= 0) {
            return 0;
        }
        RegionCategoryKey key = new RegionCategoryKey(observer.owner(), observer.viewId(), observer.targetDim(), MobCategory.MONSTER);
        validForRegion.sort(Comparator.comparingLong(chunk -> portalSpawnRingOrder(observer, chunk.getPos())));
        int size = validForRegion.size();
        int cursor = Math.floorMod(PORTAL_SPAWN_SELECTION_CURSORS.getOrDefault(key, 0), size);
        selection.selectionCursor = cursor;
        int selected = 0;
        for (int offset = 0; offset < size && selected < limit; offset++) {
            LevelChunk chunk = validForRegion.get((cursor + offset) % size);
            if (chunk == null || selection.selectedPositions.contains(chunk.getPos().toLong())) {
                continue;
            }
            selectPortalSpawnChunk(level, selection, observer, chunk);
            selected++;
        }
        PORTAL_SPAWN_SELECTION_CURSORS.put(key, Math.floorMod(cursor + Math.max(1, selected), size));
        return selected;
    }

    private static String portalSpawnPriority(ServerLevel level, PortalNaturalSpawnObserver observer, List<LevelChunk> candidates) {
        if (!observer.sameDim()) {
            return "cross-dim";
        }
        double distance = ownerDistanceToObserver(level, observer);
        if (distance >= PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE) {
            return "far-same-dim";
        }
        if (candidates != null && !candidates.isEmpty()) {
            return "partial-same-dim";
        }
        return "near-same-dim";
    }




    private static double ownerDistanceToObserver(ServerLevel level, PortalNaturalSpawnObserver observer) {
        if (level == null || observer == null) {
            return -1.0D;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(observer.owner());
        return owner == null ? -1.0D : horizontalDistance(owner.getX(), owner.getZ(), observer.pos().x, observer.pos().z);
    }



    private static long portalSpawnRingOrder(PortalNaturalSpawnObserver observer, ChunkPos pos) {
        long value = pos.toLong()
                ^ (((long) observer.viewId().hashCode()) << 32)
                ^ observer.targetDim().location().hashCode();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static boolean isChunkEntirelyTooCloseToVirtualObserver(Vec3 observer, ChunkPos pos, double minDistance) {
        if (observer == null || pos == null) {
            return false;
        }
        double minX = pos.getMinBlockX();
        double maxX = pos.getMaxBlockX() + 1.0D;
        double minZ = pos.getMinBlockZ();
        double maxZ = pos.getMaxBlockZ() + 1.0D;
        double maxDistSq = 0.0D;
        double[] xs = {minX, maxX};
        double[] zs = {minZ, maxZ};
        for (double x : xs) {
            for (double z : zs) {
                double dx = x - observer.x;
                double dz = z - observer.z;
                maxDistSq = Math.max(maxDistSq, dx * dx + dz * dz);
            }
        }
        return maxDistSq < minDistance * minDistance;
    }

    private static boolean isLikelyVanillaSpawningChunk(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player == null || player.isRemoved() || player.isSpectator()) {
                continue;
            }
            ChunkPos playerChunk = player.chunkPosition();
            if (Math.abs(pos.x - playerChunk.x) <= 8 && Math.abs(pos.z - playerChunk.z) <= 8) {
                return true;
            }
        }
        return false;
    }



    private static LongSet vanillaPlayerSpawnChunkSet(ServerLevel level) {
        LongSet chunks = new LongOpenHashSet();
        if (level == null) {
            return chunks;
        }
        for (ServerPlayer player : level.players()) {
            if (player == null || player.isRemoved() || player.isSpectator()) {
                continue;
            }
            ChunkPos center = player.chunkPosition();
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    chunks.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                }
            }
        }
        return chunks;
    }

    private static void selectPortalSpawnChunk(
            ServerLevel level,
            PortalSpawnLoopSelection selection,
            PortalNaturalSpawnObserver observer,
            LevelChunk chunk
    ) {
        if (level == null || selection == null || chunk == null) {
            return;
        }
        long packed = chunk.getPos().toLong();
        SpawnSource existing = selection.spawnSources.get(packed);
        if (existing != null && existing.kind() == SpawnSourceKind.REAL_PLAYER) {
            selection.rejectedAlreadyVanilla++;
            return;
        }
        if (existing != null && existing.kind() == SpawnSourceKind.PORTAL_OBSERVER) {
            return;
        }
        selection.spawnSources.put(packed, SpawnSource.portal(observer));
        selection.selectedChunks.add(chunk);
        selection.selectedPositions.add(packed);
        appendUnique(selection.selectedChunkSamples, chunk.getPos().x + "," + chunk.getPos().z, 220);
        appendUnique(selection.selectedChunkDistances, chunk.getPos().x + "," + chunk.getPos().z + ":" + formatDouble(chunkDistanceToObserverBlocks(observer, chunk.getPos())), 260);
        markPortalAddedSpawnChunk(level, chunk.getPos());
    }

    private static double chunkDistanceToObserverBlocks(PortalNaturalSpawnObserver observer, ChunkPos pos) {
        if (observer == null || pos == null) {
            return -1.0D;
        }
        double centerX = pos.getMinBlockX() + 8.0D;
        double centerZ = pos.getMinBlockZ() + 8.0D;
        double dx = centerX - observer.pos().x;
        double dz = centerZ - observer.pos().z;
        return Math.sqrt(dx * dx + dz * dz);
    }


    private static LevelChunk removeChunkByPos(List<LevelChunk> chunks, ChunkPos pos) {
        if (chunks == null || pos == null) {
            return null;
        }
        for (int i = 0; i < chunks.size(); i++) {
            LevelChunk chunk = chunks.get(i);
            if (chunk != null && chunk.getPos().equals(pos)) {
                return chunks.remove(i);
            }
        }
        return null;
    }





    private static PortalNaturalSpawnDryRun dryRunPortalNaturalSpawnMerge(ServerLevel level) {
        if (level == null) {
            return new PortalNaturalSpawnDryRun(0, 0, 0, false, 0, "server-unavailable");
        }
        List<PortalNaturalSpawnObserver> observers = portalNaturalSpawnObservers(level);
        if (observers.isEmpty()) {
            return new PortalNaturalSpawnDryRun(0, 0, 0, false, 0, "no-active-portal-region");
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return new PortalNaturalSpawnDryRun(observers.size(), 0, 0, false, 0, "gamerule-doMobSpawning-false");
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return new PortalNaturalSpawnDryRun(observers.size(), 0, 0, false, 0, "peaceful-difficulty");
        }
        if (countTrackedPortalSpawnedInActiveRegions(level) >= effectivePortalNaturalSpawningDimCap()) {
            return new PortalNaturalSpawnDryRun(observers.size(), 0, 0, true, 0, "total-dim-cap-reached");
        }

        int candidateChunks = 0;
        int entityTickingChunks = 0;
        int wouldMerge = 0;
        boolean liveCapReached = false;
        int perLevelRemaining = PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_LEVEL;
        LongSet seen = new LongOpenHashSet();
        for (PortalNaturalSpawnObserver observer : observers) {
            int liveHostiles = countEntitiesByCategory(level, regionBounds(level, observer.region(), observer.radiusChunks()), MobCategory.MONSTER);
            boolean observerLiveCapReached = observer.sameDim() && liveHostiles >= effectivePortalNaturalSpawningLiveHostileCap();
            int perViewRemaining = PORTAL_NATURAL_SPAWNING_MAX_CHUNKS_PER_VIEW;
            int observerCandidates = 0;
        for (int chunkX = observer.centerChunk().x - observer.radiusChunks(); chunkX <= observer.centerChunk().x + observer.radiusChunks(); chunkX++) {
            for (int chunkZ = observer.centerChunk().z - observer.radiusChunks(); chunkZ <= observer.centerChunk().z + observer.radiusChunks(); chunkZ++) {
                long packed = ChunkPos.asLong(chunkX, chunkZ);
                if (!withinChunkRadius(observer.region(), chunkX, chunkZ, observer.radiusChunks()) || !seen.add(packed)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                candidateChunks++;
                boolean entityTicking = level.shouldTickBlocksAt(packed)
                        && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(packed);
                if (!entityTicking) {
                    continue;
                }
                entityTickingChunks++;
                if (observer.sameDim() && isLikelyVanillaSpawningChunk(level, new ChunkPos(chunkX, chunkZ))) {
                    continue;
                }
                if (isChunkEntirelyTooCloseToVirtualObserver(observer.pos(), new ChunkPos(chunkX, chunkZ), 24.0D)) {
                    continue;
                }
                observerCandidates++;
                if (observerLiveCapReached) {
                    liveCapReached = true;
                    continue;
                }
                String priority = portalSpawnPriority(level, observer, List.of(chunk));
                if (observer.sameDim()
                        && "near-same-dim".equals(priority)
                        && !portalNaturalSpawningRuntimeIncludeNearSameDim) {
                    continue;
                }
                if (perLevelRemaining > 0 && perViewRemaining > 0) {
                    wouldMerge++;
                    perLevelRemaining--;
                    perViewRemaining--;
                }
            }
        }
            if (observer.sameDim() && observerCandidates == 0 && liveHostiles >= effectivePortalNaturalSpawningLiveHostileCap()) {
                liveCapReached = true;
            }
        }

        String reason = "-";
        if (wouldMerge <= 0) {
            reason = liveCapReached ? "live-hostile-cap-reached" : "no-eligible-entity-ticking-chunks";
        }
        return new PortalNaturalSpawnDryRun(observers.size(), candidateChunks, entityTickingChunks, liveCapReached, wouldMerge, reason);
    }

    private static String portalNaturalSpawningDisabledReasonForLevel(ServerLevel level) {
        if (!effectivePortalNaturalSpawningEnabled()) {
            return "portal-natural-spawning-command-disabled";
        }
        if (!effectivePortalMobSpawningEnabled()) {
            return "debug-mob-spawning-disabled";
        }
        if (!effectivePortalNaturalSpawningExperimentFlag()) {
            return "vanilla-natural-spawning-experiment-disabled";
        }
        if (level == null) {
            return "server-unavailable";
        }
        PortalNaturalSpawnDryRun dryRun = dryRunPortalNaturalSpawnMerge(level);
        return dryRun.disabledReason();
    }


    private static List<PortalNaturalSpawnObserver> portalNaturalSpawnObservers(ServerLevel level) {
        List<PortalNaturalSpawnObserver> observers = new ArrayList<>();
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            observers.add(new PortalNaturalSpawnObserver(
                    region.viewId(),
                    region.dimension(),
                    virtualObserverPosForRegion(region),
                    new ChunkPos(region.centerChunkX(), region.centerChunkZ()),
                    region.mobSpawnRadiusChunks(),
                    region.playerId(),
                    region,
                    region.sameDim()
            ));
        }
        return observers;
    }


    public static boolean isPortalAddedSpawnChunk(ServerLevel level, ChunkPos pos) {
        LongSet chunks = PORTAL_ADDED_SPAWN_CHUNKS.get(level.dimension());
        return chunks != null && chunks.contains(pos.toLong());
    }

    private static boolean isPortalSpawnLoopChunk(ServerLevel level, ChunkPos pos) {
        PortalSpawnLoopSelection selection = PORTAL_SPAWN_LOOP_SELECTIONS.get(level.dimension());
        LongSet admitted = PORTAL_GATE_ADMITTED_CHUNKS.get(level.dimension());
        SpawnSource source = selection == null ? null : selection.spawnSources.get(pos.toLong());
        return portalNaturalSpawningExperimentEnabled()
                && selection != null
                && source != null
                && source.kind() == SpawnSourceKind.PORTAL_OBSERVER
                && PortalVanillaSpawnBridge.isPortalBypassAllowed(level, pos, source.viewId())
                && isPortalAddedSpawnChunk(level, pos)
                && admitted != null
                && admitted.contains(pos.toLong());
    }

    public static boolean hasPortalSpawnObserverNearChunk(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null || !portalNaturalSpawningExperimentEnabled() || !shouldRunNaturalSpawning(level)) {
            return false;
        }
        PortalSpawnLoopSelection selection = PORTAL_SPAWN_LOOP_SELECTIONS.get(level.dimension());
        SpawnSource source = selection == null ? null : selection.spawnSources.get(pos.toLong());
        if (selection == null
                || source == null
                || source.kind() != SpawnSourceKind.PORTAL_OBSERVER
                || !isPortalAddedSpawnChunk(level, pos)
                || !PortalVanillaSpawnBridge.isPortalBypassAllowed(level, pos, source.viewId())) {
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        long packed = pos.toLong();
        return level.shouldTickBlocksAt(packed)
                && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(packed);
    }

    public static boolean shouldForceTickChunkForNaturalSpawnGate(ServerLevel level, ChunkPos pos, boolean vanillaShouldForceTicks) {
        if (!vanillaShouldForceTicks || level == null || pos == null) {
            return vanillaShouldForceTicks;
        }
        if (!portalNaturalSpawningExperimentEnabled() || !isChunkInPortalMobSpawnRegion(level, pos)) {
            return vanillaShouldForceTicks;
        }
        PortalSpawnLoopSelection selection = PORTAL_SPAWN_LOOP_SELECTIONS.get(level.dimension());
        if (selection == null) {
            return false;
        }
        SpawnSource source = selection.spawnSources.get(pos.toLong());
        return source != null
                && source.kind() == SpawnSourceKind.PORTAL_OBSERVER
                && PortalVanillaSpawnBridge.isPortalBypassAllowed(level, pos, source.viewId())
                && isPortalAddedSpawnChunk(level, pos);
    }







    public static boolean shouldPortalObserverAllowLocalMobCap(ServerLevel level, ChunkPos pos, MobCategory category) {
        return portalCategoryDecision(level, pos, category, true, false).portalLocalAllows();
    }



    public static boolean isMobSpawnObserverNear(ServerLevel level, BlockPos pos) {
        if (!shouldRunNaturalSpawning(level)) {
            return false;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            if (!withinChunkRadius(region, chunkX, chunkZ, region.mobSpawnRadiusChunks())) {
                continue;
            }
            double centerX = region.centerChunkX() * 16.0D + 8.0D;
            double centerZ = region.centerChunkZ() * 16.0D + 8.0D;
            double dx = pos.getX() + 0.5D - centerX;
            double dz = pos.getZ() + 0.5D - centerZ;
            double radiusBlocks = region.mobSpawnRadiusChunks() * 16.0D + 16.0D;
            return dx * dx + dz * dz <= radiusBlocks * radiusBlocks;
        }
        return false;
    }

    public static boolean hasPortalObserverNear(ServerLevel level, double x, double y, double z, double maxDistance) {
        return getNearestPortalObserverDistanceSq(level, x, y, z, maxDistance) >= 0.0D;
    }

    public static boolean shouldPortalObserverSatisfyNearbyAlivePlayer(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    ) {
        if (!portalNaturalSpawningExperimentEnabled()) {
            return false;
        }
        SpawnForChunkContext context = CURRENT_SPAWN_FOR_CHUNK.get();
        if (context == null
                || !context.portalChunk()
                || !context.level().dimension().equals(level.dimension())
                || !isPortalSpawnLoopChunk(level, context.pos())) {
            return false;
        }
        return hasPortalObserverNear(level, x, y, z, maxDistance);
    }

    public static double getNearestPortalObserverDistanceSq(ServerLevel level, double x, double y, double z, double maxDistance) {
        double maxDistanceSq = maxDistance < 0.0D ? Double.MAX_VALUE : maxDistance * maxDistance;
        double best = Double.MAX_VALUE;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            double centerX = region.centerChunkX() * 16.0D + 8.0D;
            double centerZ = region.centerChunkZ() * 16.0D + 8.0D;
            double dx = x - centerX;
            double dz = z - centerZ;
            double dy = y - 80.0D;
            double distSq = dx * dx + dy * dy + dz * dz;
            double regionMax = Math.max(maxDistanceSq, square(region.entityTickRadiusChunks() * 16.0D + 16.0D));
            if (distSq <= regionMax && distSq < best) {
                best = distSq;
            }
        }
        return best == Double.MAX_VALUE ? -1.0D : best;
    }


    public static boolean isChunkInPortalMobSpawnRegion(ServerLevel level, ChunkPos pos) {
        for (Region region : PortalRegionTracker.values()) {
            if (region.dimension().equals(level.dimension())
                    && withinChunkRadius(region, pos.x, pos.z, region.mobSpawnRadiusChunks())) {
                return true;
            }
        }
        return false;
    }


    public static boolean isEntityInPortalSimulationRegion(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        Region region = firstRegionForChunk(level, entity.chunkPosition(), entity.getType().getCategory());
        if (region == null || !withinChunkRadius(region, entity.chunkPosition().x, entity.chunkPosition().z, region.entityTickRadiusChunks())) {
            return false;
        }
        int live = countEntitiesByCategory(level, regionBounds(level, region, region.mobSpawnRadiusChunks()), entity.getType().getCategory());
        return live <= Math.max(portalRegionCapFor(entity.getType().getCategory()) * 2, portalRegionCapFor(entity.getType().getCategory()) + 2);
    }


    public static void beginNaturalSpawnerChunk(
            ServerLevel level,
            LevelChunk chunk,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean spawnAnimals,
            NaturalSpawner.SpawnState spawnState
    ) {
        boolean portalChunk = isPortalSpawnLoopChunk(level, chunk.getPos());
        PortalSpawnLoopSelection selection = PORTAL_SPAWN_LOOP_SELECTIONS.get(level.dimension());
        if (selection != null && spawnState != null) {
            selection.spawnStateSpawnableChunks = spawnState.getSpawnableChunkCount();
        }
        CURRENT_SPAWN_FOR_CHUNK.set(new SpawnForChunkContext(level, chunk.getPos(), spawnFriendlies, spawnEnemies, spawnAnimals, spawnState, portalChunk));
        if (portalChunk) {
            Region region = firstRegionForChunk(level, chunk.getPos(), MobCategory.MONSTER);
            if (region != null) {
                Vec3 virtualObserverPos = virtualObserverPosForRegion(region);
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(region.playerId());
                CURRENT_PORTAL_SPAWN_CONTEXT.set(new PortalSpawnContext(
                        true,
                        level.dimension(),
                        region.viewId(),
                        chunk.getPos(),
                        null,
                        virtualObserverPos,
                        owner,
                        region.mobSpawnRadiusChunks()
                ));
            } else {
                CURRENT_PORTAL_SPAWN_CONTEXT.remove();
            }
        } else {
            CURRENT_PORTAL_SPAWN_CONTEXT.remove();
        }
    }

    public static void endNaturalSpawnerChunk(ServerLevel level, LevelChunk chunk) {
        CURRENT_SPAWN_FOR_CHUNK.remove();
        CURRENT_PORTAL_SPAWN_CONTEXT.remove();
    }

    public static void beginNaturalSpawnerCategory(ServerLevel level, LevelChunk chunk, MobCategory category) {
        if (level == null || chunk == null || category == null || !portalNaturalSpawningExperimentEnabled() || !isChunkInPortalMobSpawnRegion(level, chunk.getPos())) {
            CURRENT_SPAWN_CATEGORY.remove();
            return;
        }
        CURRENT_SPAWN_CATEGORY.set(new SpawnCategoryContext(level, chunk.getPos(), category));
        PortalSpawnContext portalContext = CURRENT_PORTAL_SPAWN_CONTEXT.get();
        if (portalContext != null && portalContext.active() && portalContext.targetDim().equals(level.dimension()) && portalContext.processedChunk().equals(chunk.getPos())) {
            CURRENT_PORTAL_SPAWN_CONTEXT.set(new PortalSpawnContext(
                    true,
                    portalContext.targetDim(),
                    portalContext.viewId(),
                    portalContext.processedChunk(),
                    category,
                    portalContext.virtualObserverPos(),
                    portalContext.owner(),
                    portalContext.mobSpawnRadiusChunks()
            ));
        }
    }

    public static void endNaturalSpawnerCategory(ServerLevel level, LevelChunk chunk, MobCategory category) {
        CURRENT_SPAWN_CATEGORY.remove();
    }

    public static void beginServerChunkCacheTick(ServerLevel level) {
        if (level == null) {
            return;
        }
        PORTAL_ADDED_SPAWN_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet()).clear();
        PORTAL_GATE_ADMITTED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet()).clear();
        PORTAL_SPAWN_LOOP_SELECTIONS.remove(level.dimension());
        PORTAL_VANILLA_LOOP_MERGE_COUNTS.put(level.dimension(), 0);
    }


    public static BlockPos maybeForceValidNaturalSpawnerPosition(Level level, LevelChunk chunk, BlockPos original) {
        return original;
    }

    public static BlockPos overrideNaturalSpawnerRandomPositionAtActualCallSite(
            ServerLevel level,
            LevelChunk chunk,
            MobCategory category,
            BlockPos original
    ) {
        return original;
    }

    public static BlockPos overrideNaturalSpawnerRandomPositionRedirect(
            ServerLevel level,
            LevelChunk chunk,
            MobCategory category,
            BlockPos original
    ) {
        return overrideNaturalSpawnerRandomPositionAtActualCallSite(level, chunk, category, original);
    }

    public static boolean hasPendingForcedNaturalSpawnerPosition(Level level, LevelChunk chunk, BlockPos original, BlockPos replacement) {
        return false;
    }


    public static Player portalNearestPlayerOverrideForSpawnListGate(
            ServerLevel level,
            ChunkAccess chunk,
            MobCategory category,
            double x,
            double y,
            double z,
            double maxDistance,
            Player vanillaPlayer
    ) {
        if (!portalNaturalSpawningExperimentEnabled()) {
            return vanillaPlayer;
        }
        if (vanillaPlayer != null) {
            return vanillaPlayer;
        }
        if (level == null || chunk == null || category == null) {
            return null;
        }
        SpawnForChunkContext context = CURRENT_SPAWN_FOR_CHUNK.get();
        boolean portalContext = context != null
                && context.portalChunk()
                && context.level().dimension().equals(level.dimension())
                && context.pos().equals(chunk.getPos());
        boolean portalObserverNear = hasPortalObserverNear(level, x, y, z, 128.0D);
        if (!portalContext || !isChunkInPortalMobSpawnRegion(level, chunk.getPos()) || !portalObserverNear) {
            return null;
        }

        ServerPlayer owner = ownerPlayerForChunk(level, chunk.getPos());
        boolean validOwner = owner != null && owner.isAlive() && !owner.isSpectator();
        return validOwner ? owner : null;
    }


    private static double horizontalDistance(double x0, double z0, double x1, double z1) {
        double dx = x0 - x1;
        double dz = z0 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isPortalSpawned(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(PORTAL_SPAWNED_TAG);
    }

    public static void recordVanillaEntityTick(ServerLevel level, Entity entity) {
        if (level == null || entity == null || !isEntityInPortalSimulationRegion(entity)) {
            return;
        }
        VANILLA_TICK_NON_PASSENGER_LAST_TICK.put(
                new EntityTickKey(level.dimension(), entity.getId()),
                (long) level.getServer().getTickCount()
        );
    }

    public static void recordPortalSpawnedMob(ServerLevel level, Mob mob) {
        if (!portalNaturalSpawningExperimentEnabled() || level == null || mob == null) {
            return;
        }
        SpawnForChunkContext context = CURRENT_SPAWN_FOR_CHUNK.get();
        if (context == null || !context.portalChunk()) {
            return;
        }
        Region region = firstRegionForChunk(level, mob.chunkPosition(), mob.getType().getCategory());
        if (region == null) {
            return;
        }
        mob.getPersistentData().putBoolean(PORTAL_SPAWNED_TAG, true);
        mob.getPersistentData().putString(PORTAL_SPAWNED_VIEW_TAG, region.viewId().toString());
        mob.getPersistentData().putString(PORTAL_SPAWNED_OWNER_TAG, region.playerId().toString());
        mob.getPersistentData().putLong(PORTAL_SPAWNED_TICK_TAG, level.getServer().getTickCount());
        PortalSpawnLoopSelection selection = PORTAL_SPAWN_LOOP_SELECTIONS.get(level.dimension());
        if (selection != null) {
            appendUnique(selection.successChunkSamples, mob.chunkPosition().x + "," + mob.chunkPosition().z, 220);
        }
    }

    public static boolean shouldProtectPortalMobFromDespawn(Mob mob) {
        if (!PORTAL_DESPAWN_PROTECTION_ENABLED || mob == null || mob.isRemoved()) {
            return false;
        }
        return portalRemovalProtectionContext(mob, "checkDespawn") != null;
    }

    public static boolean shouldCancelPortalEntityRemoval(Entity entity, Entity.RemovalReason reason) {
        if (PortalDespawnProtection.isSkyesightIntentionalDiscard() || entity.getPersistentData().getBoolean(PORTAL_TRANSIENT_TEST_ENTITY_TAG)) {
            return false;
        }
        if (reason != Entity.RemovalReason.DISCARDED || !(entity instanceof Mob mob)) {
            return false;
        }
        PortalRemovalProtectionContext context = portalRemovalProtectionContext(mob, "remove-" + reason);
        if (context == null) {
            return false;
        }
        return true;
    }

    public static void onPortalEntityRemoved(Entity entity, Entity.RemovalReason reason, boolean cancelled) {
        if (!(entity.level() instanceof ServerLevel level) || bestMobRegionMatch(level, entity) == null) {
            return;
        }
        PortalMobRegionMatch match = bestMobRegionMatch(level, entity);
        Region region = match == null ? null : match.region();
        if (region == null) {
            return;
        }
        if (!cancelled
                && reason == Entity.RemovalReason.DISCARDED
                && MobCategory.MONSTER.equals(entity.getType().getCategory())
                && !entity.getPersistentData().getBoolean(PORTAL_TRANSIENT_TEST_ENTITY_TAG)
                && !PortalDespawnProtection.isSkyesightIntentionalDiscard()) {
            recordPortalDiscardForPause(level, region.viewId());
        }
        if (cancelled) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Portal entity discard blocked: entity={} type={} dim={} viewId={}",
                    entity.getId(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    level.dimension().location(),
                    region.viewId()
            );
        }
    }

    public static boolean shouldProtectHostileInPortalRegion(Mob mob, ServerLevel level, String reason) {
        return mob != null && level != null && portalRemovalProtectionContext(mob, reason) != null;
    }

    private static PortalRemovalProtectionContext portalRemovalProtectionContext(Mob mob, String reason) {
        if (!PORTAL_DESPAWN_PROTECTION_ENABLED || mob == null || mob.isRemoved()) {
            return null;
        }
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return null;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        PortalMobRegionMatch match = bestPortalMobRegionMatch(level, mob);
        if (match == null || match.region() == null) {
            return null;
        }
        if (!"-".equals(match.reasonIfNotProtected())) {
            return null;
        }
        return new PortalRemovalProtectionContext(
                level,
                match.region(),
                match.portalObserverDistance(),
                match.farSameDim(),
                match.crossDim(),
                reason == null || reason.isBlank() ? "portal-observer-active" : reason,
                match.matchingViews(),
                match.skippedViews(),
                match.reasonIfNotProtected()
        );
    }

    private static PortalMobRegionMatch bestPortalMobRegionMatch(ServerLevel level, Mob mob) {
        if (level == null || mob == null) {
            return null;
        }
        ChunkPos chunkPos = mob.chunkPosition();
        long packed = chunkPos.toLong();
        boolean chunkLoaded = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null;
        boolean blockTicking = chunkLoaded && level.shouldTickBlocksAt(packed);
        boolean entityTicking = chunkLoaded && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(packed);
        StringBuilder matchingViews = new StringBuilder();
        StringBuilder skippedViews = new StringBuilder();
        PortalMobRegionMatch best = null;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            AABB bounds = regionBounds(level, region, region.entityTickRadiusChunks());
            if (!bounds.contains(mob.position())) {
                continue;
            }
            Vec3 observerPos = virtualObserverPosForRegion(region);
            double observerDistance = Math.sqrt(observerPos.distanceToSqr(mob.position()));
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(region.playerId());
            double realPlayerDistance = owner == null ? -1.0D : Math.sqrt(owner.distanceToSqr(mob));
            double ownerObserverDistance = owner == null ? -1.0D : horizontalDistance(owner.getX(), owner.getZ(), observerPos.x, observerPos.z);
            boolean crossDim = !region.sameDim();
            boolean farSameDim = region.sameDim() && ownerObserverDistance >= PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE;
            boolean realPlayerCoversChunk = region.sameDim()
                    && PortalVanillaSpawnBridge.isChunkCoveredByRealPlayerVanilla(level, chunkPos);
            String reasonIfNotProtected = "-";
            if (!chunkLoaded) {
                reasonIfNotProtected = "chunk-not-loaded";
            } else if (!blockTicking) {
                reasonIfNotProtected = "chunk-not-block-ticking";
            } else if (!entityTicking) {
                reasonIfNotProtected = "chunk-not-entity-ticking";
            } else if (observerDistance > 128.0D) {
                reasonIfNotProtected = "portal-observer-outside-despawn-protection-range";
            } else if (realPlayerCoversChunk) {
                reasonIfNotProtected = "same-dim-real-player-owned-or-near-portal";
            } else if (!crossDim && !farSameDim && !portalNaturalSpawningRuntimeIncludeNearSameDim) {
                reasonIfNotProtected = "near-same-dim-vanilla-covered";
            }
            appendToken(matchingViews, region.viewId() + ":" + reasonIfNotProtected);
            if (!"-".equals(reasonIfNotProtected)) {
                appendToken(skippedViews, region.viewId() + ":" + reasonIfNotProtected);
            }
            PortalMobRegionMatch candidate = new PortalMobRegionMatch(
                    region,
                    observerDistance,
                    realPlayerDistance,
                    true,
                    farSameDim,
                    crossDim,
                    chunkLoaded,
                    blockTicking,
                    entityTicking,
                    matchingViews.toString(),
                    skippedViews.toString(),
                    reasonIfNotProtected
            );
            if (best == null || portalProtectionCandidateScore(candidate) > portalProtectionCandidateScore(best)
                    || (portalProtectionCandidateScore(candidate) == portalProtectionCandidateScore(best)
                    && candidate.portalObserverDistance() < best.portalObserverDistance())) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        return new PortalMobRegionMatch(
                best.region(),
                best.portalObserverDistance(),
                best.nearestRealPlayerDistance(),
                best.insideRegion(),
                best.farSameDim(),
                best.crossDim(),
                best.chunkLoaded(),
                best.blockTicking(),
                best.entityTicking(),
                matchingViews.length() == 0 ? "-" : matchingViews.toString(),
                skippedViews.length() == 0 ? "-" : skippedViews.toString(),
                best.reasonIfNotProtected()
        );
    }

    private static int portalProtectionCandidateScore(PortalMobRegionMatch match) {
        int score = "-".equals(match.reasonIfNotProtected()) ? 1000 : 0;
        if (match.crossDim()) {
            score += 100;
        }
        if (match.farSameDim()) {
            score += 80;
        }
        if (match.chunkLoaded()) {
            score += 4;
        }
        if (match.blockTicking()) {
            score += 2;
        }
        if (match.entityTicking()) {
            score += 1;
        }
        return score;
    }

    private static void appendToken(StringBuilder builder, Object value) {
        if (builder.length() > 0) {
            builder.append(",");
        }
        builder.append(value);
    }

    private static PortalMobRegionMatch bestMobRegionMatch(ServerLevel level, Entity entity) {
        if (level == null || entity == null) {
            return null;
        }
        if (entity instanceof Mob mob) {
            return bestPortalMobRegionMatch(level, mob);
        }
        Region region = firstRegionForChunk(level, entity.chunkPosition(), entity.getType().getCategory());
        if (region == null) {
            return null;
        }
        Vec3 observerPos = virtualObserverPosForRegion(region);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(region.playerId());
        double realPlayerDistance = owner == null ? -1.0D : Math.sqrt(owner.distanceToSqr(entity));
        double ownerObserverDistance = owner == null ? -1.0D : horizontalDistance(owner.getX(), owner.getZ(), observerPos.x, observerPos.z);
        long packed = entity.chunkPosition().toLong();
        boolean loaded = level.getChunkSource().getChunkNow(entity.chunkPosition().x, entity.chunkPosition().z) != null;
        boolean blockTicking = loaded && level.shouldTickBlocksAt(packed);
        boolean entityTicking = loaded && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(packed);
        boolean farSameDim = region.sameDim() && ownerObserverDistance >= PORTAL_NATURAL_SPAWNING_FAR_SAME_DIM_MIN_DISTANCE;
        boolean crossDim = !region.sameDim();
        return new PortalMobRegionMatch(
                region,
                Math.sqrt(observerPos.distanceToSqr(entity.position())),
                realPlayerDistance,
                regionBounds(level, region, region.entityTickRadiusChunks()).contains(entity.position()),
                farSameDim,
                crossDim,
                loaded,
                blockTicking,
                entityTicking,
                region.viewId().toString(),
                "-",
                "-"
        );
    }


    public static boolean shouldForcePortalCategory(MobCategory category, ChunkPos pos, boolean globalCapAllows, boolean vanillaAllows) {
        SpawnForChunkContext context = CURRENT_SPAWN_FOR_CHUNK.get();
        if (context == null) {
            return false;
        }
        PortalCategoryDecision decision = portalCategoryDecision(context.level(), pos, category, globalCapAllows, vanillaAllows);
        return !vanillaAllows && decision.finalAllows();
    }



    private static PortalCategoryDecision portalCategoryDecision(
            ServerLevel level,
            ChunkPos pos,
            MobCategory category,
            boolean vanillaGlobalAllows,
            boolean vanillaLocalAllows
    ) {
        SpawnForChunkContext context = CURRENT_SPAWN_FOR_CHUNK.get();
        boolean portalOwned = context != null
                && level != null
                && pos != null
                && context.level().dimension().equals(level.dimension())
                && context.portalChunk()
                && context.pos().equals(pos)
                && isPortalSpawnLoopChunk(level, pos);
        boolean monster = MobCategory.MONSTER.equals(category);
        boolean flagsAllow = context != null && context.spawnEnemies();
        PortalCapDecision capDecision = portalCapDecision(level, pos, category);
        boolean portalCapAllows = capDecision.allowsCategory();
        boolean portalLocalAllows = portalOwned && monster && flagsAllow && portalCapAllows;
        boolean globalAllows = vanillaGlobalAllows;
        boolean finalAllows = portalOwned
                ? monster && flagsAllow && globalAllows && (vanillaLocalAllows || portalLocalAllows) && portalCapAllows
                : vanillaGlobalAllows && vanillaLocalAllows;
        String reason;
        if (!portalOwned) {
            reason = "not-portal-owned";
        } else if (!monster) {
            reason = "portal-monster-only";
        } else if (!flagsAllow) {
            reason = "spawn-enemies-flag-disabled";
        } else if (!portalCapAllows) {
            reason = capDecision.reason();
        } else if (!globalAllows) {
            reason = "global-mob-cap";
        } else if (!vanillaLocalAllows && portalLocalAllows) {
            reason = "portal-owned-local-cap-override";
        } else if (finalAllows) {
            reason = "vanilla-or-portal-category-allowed";
        } else {
            reason = "local-mob-cap-or-flags";
        }
        return new PortalCategoryDecision(
                portalOwned,
                category,
                vanillaGlobalAllows,
                vanillaLocalAllows,
                portalLocalAllows,
                portalCapAllows,
                finalAllows,
                reason,
                capDecision.region(),
                capDecision.liveHostileInRegion(),
                capDecision.portalSpawnedAlive(),
                capDecision.liveCap()
        );
    }

    private static PortalCapDecision portalCapDecision(ServerLevel level, ChunkPos pos, MobCategory category) {
        Region region = firstRegionForChunk(level, pos, category);
        if (region == null) {
            return new PortalCapDecision(null, 0, 0, 0, effectivePortalNaturalSpawningLiveHostileCap(), effectivePortalNaturalSpawningLiveHostileCap(), effectivePortalNaturalSpawningDimCap(), false, "no-region");
        }
        int live = countEntitiesByCategory(level, regionBounds(level, region, region.mobSpawnRadiusChunks()), category);
        int portalSpawnedForView = countPortalSpawnedForView(level, region.viewId());
        int dimPortalSpawned = countTrackedPortalSpawnedInActiveRegions(level);
        int liveCap = PORTAL_NATURAL_SPAWNING_EXISTING_LIVE_SOFT_CAP;
        int spawnedCap = effectivePortalNaturalSpawningLiveHostileCap();
        int dimCap = effectivePortalNaturalSpawningDimCap();
        boolean liveBlocks = live >= liveCap;
        boolean spawnedBlocks = portalSpawnedForView >= spawnedCap;
        boolean dimBlocks = dimPortalSpawned >= dimCap;
        String reason = liveBlocks
                ? "live-hostile-cap-reached"
                : (spawnedBlocks ? "view-portal-spawned-cap-reached" : (dimBlocks ? "total-dim-cap-reached" : "-"));
        return new PortalCapDecision(region, live, portalSpawnedForView, dimPortalSpawned, liveCap, spawnedCap, dimCap, !liveBlocks && !spawnedBlocks && !dimBlocks, reason);
    }


    private static boolean isPortalSpawnPaused(ServerLevel level, ResourceLocation viewId) {
        long until = PORTAL_SPAWN_PAUSED_UNTIL_TICK.getOrDefault(viewId, 0L);
        if (until <= level.getGameTime()) {
            PORTAL_SPAWN_PAUSED_UNTIL_TICK.remove(viewId);
            return false;
        }
        return true;
    }

    private static void recordPortalDiscardForPause(ServerLevel level, ResourceLocation viewId) {
        long now = System.currentTimeMillis();
        long windowStart = PORTAL_DISCARD_WINDOW_START_MILLIS.getOrDefault(viewId, now);
        if (now - windowStart > 5000L) {
            PORTAL_DISCARD_WINDOW_START_MILLIS.put(viewId, now);
            PORTAL_DISCARD_WINDOW_COUNTS.put(viewId, 0);
        }
        int count = PORTAL_DISCARD_WINDOW_COUNTS.merge(viewId, 1, Integer::sum);
        if (count >= 5) {
            long pauseUntil = level.getGameTime() + 200L;
            long previous = PORTAL_SPAWN_PAUSED_UNTIL_TICK.getOrDefault(viewId, 0L);
            if (pauseUntil > previous) {
                PORTAL_SPAWN_PAUSED_UNTIL_TICK.put(viewId, pauseUntil);
            }
        }
    }

    public static String cleanupPortalSpawned(MinecraftServer server) {
        if (server == null) {
            return "server-unavailable";
        }
        Map<String, Integer> removedByType = new LinkedHashMap<>();
        LongSet removedIds = new LongOpenHashSet();
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Region region : PortalRegionTracker.values()) {
                if (!region.dimension().equals(level.dimension())) {
                    continue;
                }
                AABB area = regionBounds(level, region, region.mobSpawnRadiusChunks());
                for (Entity entity : level.getEntities((Entity) null, area, PortalSimulationCoordinator::isPortalSpawned)) {
                    if (!removedIds.add((((long) level.dimension().location().hashCode()) << 32) ^ entity.getId())) {
                        continue;
                    }
                    String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                    discardSkyesightOwnedEntity(entity);
                    removedByType.merge(level.dimension().location() + "/" + type, 1, Integer::sum);
                    removed++;
                }
            }
        }
        return "removedPortalSpawned=" + removed
                + " remainingPortalSpawnedByDim=" + remainingPortalSpawnedByDim(server)
                + " remainingPortalSpawnedByView=" + remainingPortalSpawnedByView(server)
                + " byType=" + removedByType;
    }

    private static String remainingPortalSpawnedByDim(MinecraftServer server) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            int count = countTrackedPortalSpawnedInActiveRegions(level);
            if (count > 0) {
                counts.put(level.dimension().location().toString(), count);
            }
        }
        return counts.isEmpty() ? "-" : counts.toString();
    }

    private static String remainingPortalSpawnedByView(MinecraftServer server) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Region region : PortalRegionTracker.values()) {
            ServerLevel level = server.getLevel(region.dimension());
            if (level == null) {
                continue;
            }
            int count = countPortalSpawnedForView(level, region.viewId());
            if (count > 0) {
                counts.put(region.viewId().toString(), count);
            }
        }
        return counts.isEmpty() ? "-" : counts.toString();
    }

    public static String cleanupActiveRegionHostiles(MinecraftServer server, ResourceLocation viewId) {
        if (server == null || viewId == null) {
            return "server-or-view-unavailable";
        }
        Map<String, Integer> removedByType = new LinkedHashMap<>();
        StringBuilder sampleRemoved = new StringBuilder();
        StringBuilder boundsSummary = new StringBuilder();
        int removed = 0;
        int remaining = 0;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.viewId().equals(viewId)) {
                continue;
            }
            ServerLevel level = server.getLevel(region.dimension());
            if (level == null) {
                continue;
            }
            AABB area = regionBounds(level, region, region.mobSpawnRadiusChunks());
            appendUnique(
                    boundsSummary,
                    level.dimension().location() + "["
                            + formatDouble(area.minX) + "," + formatDouble(area.minY) + "," + formatDouble(area.minZ)
                            + " -> "
                            + formatDouble(area.maxX) + "," + formatDouble(area.maxY) + "," + formatDouble(area.maxZ)
                            + "]",
                    240
            );
            for (Entity entity : level.getEntities(
                    (Entity) null,
                    area,
                    entity -> !entity.isRemoved() && entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER
            )) {
                String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                appendUnique(sampleRemoved, entity.getId() + ":" + type, 180);
                discardSkyesightOwnedEntity(entity);
                removedByType.merge(level.dimension().location() + "/" + type, 1, Integer::sum);
                removed++;
            }
            remaining += level.getEntities(
                    (Entity) null,
                    area,
                    entity -> !entity.isRemoved() && entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER
            ).size();
        }
        return "viewId=" + viewId
                + " removedHostile=" + removed
                + " remainingHostile=" + remaining
                + " regionBounds=" + (boundsSummary.length() == 0 ? "-" : boundsSummary)
                + " sampleRemoved=" + (sampleRemoved.length() == 0 ? "-" : sampleRemoved)
                + " byType=" + removedByType;
    }

    public static String cleanupActiveRegionItems(MinecraftServer server, ResourceLocation viewId) {
        if (server == null || viewId == null) {
            return "server-or-view-unavailable";
        }
        Map<String, Integer> removedByType = new LinkedHashMap<>();
        int removed = 0;
        for (Region region : PortalRegionTracker.values()) {
            if (!region.viewId().equals(viewId)) {
                continue;
            }
            ServerLevel level = server.getLevel(region.dimension());
            if (level == null) {
                continue;
            }
            AABB area = regionBounds(level, region, region.mobSpawnRadiusChunks());
            for (Entity entity : level.getEntities((Entity) null, area, entity -> entity instanceof ItemEntity)) {
                String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                discardSkyesightOwnedEntity(entity);
                removedByType.merge(level.dimension().location() + "/" + type, 1, Integer::sum);
                removed++;
            }
        }
        return "viewId=" + viewId + " removed=" + removed + " byType=" + removedByType;
    }

    private static void discardSkyesightOwnedEntity(Entity entity) {
        discardSkyesightOwnedEntityInternal(entity, "skyesight-owned-cleanup", false);
    }

    private static boolean isExplicitlySkyesightOwnedEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentData().getBoolean(PORTAL_TRANSIENT_TEST_ENTITY_TAG)
                || entity.getPersistentData().getBoolean(PORTAL_LIFECYCLE_TEST_TAG)
                || entity.getPersistentData().getBoolean(PORTAL_SPAWNED_TAG);
    }

    private static void discardSkyesightOwnedEntityInternal(Entity entity, String reason, boolean allowScratchEntity) {
        if (entity == null || entity.isRemoved()) {
            return;
        }
        if (!allowScratchEntity && !isExplicitlySkyesightOwnedEntity(entity)) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Refusing to discard non-Skyesight-owned entity: entity={} reason={}",
                    entity,
                    reason
            );
            return;
        }
        boolean previous = PortalDespawnProtection.setSkyesightIntentionalDiscard(true);
        try {
            entity.getPersistentData().putBoolean(PORTAL_TRANSIENT_TEST_ENTITY_TAG, true);
            entity.getPersistentData().putBoolean("skyesight_intentional_discard", true);
            entity.getPersistentData().putString("skyesight_intentional_discard_reason", reason);
            entity.discard();
        } finally {
            PortalDespawnProtection.setSkyesightIntentionalDiscard(previous);
        }
    }


    private static void addRegionTickets(ServerLevel level, Region region) {
        PortalChunkTicketController.refreshRegionTickets(
                level,
                region,
                PORTAL_SIMULATION_ENTITY_TICKING_ENABLED,
                PORTAL_SIMULATION_PATHFINDING_CHUNK_MARGIN
        );
    }

    private static void removeRegionTickets(MinecraftServer server, Region region) {
        PortalChunkTicketController.removeRegionTickets(server, region, PORTAL_SIMULATION_PATHFINDING_CHUNK_MARGIN);
    }

    private static boolean shouldRunNaturalSpawning(ServerLevel level) {
        return portalNaturalSpawningExperimentEnabled()
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                && level.getDifficulty() != Difficulty.PEACEFUL;
    }



    private static int countEntitiesByCategory(ServerLevel level, AABB bounds, MobCategory category) {
        int count = 0;
        for (Entity entity : level.getEntities(
                (Entity) null,
                bounds,
                entity -> !entity.isRemoved() && entity.getType().getCategory() == category
        )) {
            count++;
        }
        return count;
    }

    private static int portalRegionCapFor(MobCategory category) {
        if (MobCategory.MONSTER.equals(category)) {
            return PORTAL_SIMULATION_MAX_HOSTILE_MOBS_PER_PORTAL_REGION;
        }
        return Math.max(1, category == null ? 1 : category.getMaxInstancesPerChunk());
    }

    private static Region firstRegionForChunk(ServerLevel level, ChunkPos pos, MobCategory category) {
        for (Region region : PortalRegionTracker.values()) {
            if (region.dimension().equals(level.dimension())
                    && withinChunkRadius(region, pos.x, pos.z, region.mobSpawnRadiusChunks())) {
                return region;
            }
        }
        return null;
    }





    private static List<MobSpawnSettings.SpawnerData> monsterSpawnerData(ServerLevel level, BlockPos pos) {
        List<MobSpawnSettings.SpawnerData> result = new ArrayList<>();
        try {
            MobSpawnSettings settings = level.getBiome(pos).value().getMobSettings();
            for (Object weighted : settings.getMobs(MobCategory.MONSTER).unwrap()) {
                Object value = unwrapWeightedValue(weighted);
                if (value instanceof MobSpawnSettings.SpawnerData spawnerData) {
                    result.add(spawnerData);
                }
            }
        } catch (RuntimeException exception) {
            return result;
        }
        return result;
    }

    private static Object unwrapWeightedValue(Object weighted) {
        if (weighted == null) {
            return null;
        }
        if (weighted instanceof MobSpawnSettings.SpawnerData) {
            return weighted;
        }
        try {
            return weighted.getClass().getMethod("value").invoke(weighted);
        } catch (ReflectiveOperationException ignored) {
            return weighted;
        }
    }

    private static int forceTickExistingEntities(ServerLevel level, AABB area) {
        int count = 0;
        for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
            if (entity instanceof Player || !isEntityInPortalSimulationRegion(entity) || entity.isRemoved()) {
                continue;
            }
            EntityTickKey key = new EntityTickKey(level.dimension(), entity.getId());
            long serverTick = level.getServer().getTickCount();
            if (VANILLA_TICK_NON_PASSENGER_LAST_TICK.getOrDefault(key, -1L) == serverTick) {
                continue;
            }
            try {
                ((ServerLevelTickNonPassengerInvoker) level).skyesight$invokeTickNonPassenger(entity);
                count++;
            } catch (RuntimeException exception) {
            }
        }
        return count;
    }



    private static int countEntityTickDeltas(ServerLevel level, AABB area) {
        int ticked = 0;
        for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
            EntityTickKey key = new EntityTickKey(level.dimension(), entity.getId());
            Integer previous = LAST_ENTITY_TICKS.put(key, entity.tickCount);
            if (previous != null && entity.tickCount > previous) {
                ticked++;
            }
        }
        return ticked;
    }

    private static AABB regionBounds(ServerLevel level, Region region, int radiusChunks) {
        double centerX = region.centerChunkX() * 16.0D + 8.0D;
        double centerZ = region.centerChunkZ() * 16.0D + 8.0D;
        double radiusBlocks = radiusChunks * 16.0D + 16.0D;
        return new AABB(
                centerX - radiusBlocks,
                level.getMinBuildHeight(),
                centerZ - radiusBlocks,
                centerX + radiusBlocks,
                level.getMaxBuildHeight(),
                centerZ + radiusBlocks
        );
    }

    private static boolean withinChunkRadius(Region region, int chunkX, int chunkZ, int radius) {
        return Math.abs(chunkX - region.centerChunkX()) <= radius
                && Math.abs(chunkZ - region.centerChunkZ()) <= radius;
    }

    private static String centerBiome(ServerLevel level, Region region) {
        BlockPos guess = new BlockPos(region.centerChunkX() * 16 + 8, level.getMinBuildHeight(), region.centerChunkZ() * 16 + 8);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, guess);
        return level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }

    private static int realPlayersNear(ServerLevel level, Region region) {
        double centerX = region.centerChunkX() * 16.0D + 8.0D;
        double centerZ = region.centerChunkZ() * 16.0D + 8.0D;
        double radiusBlocks = region.mobSpawnRadiusChunks() * 16.0D + 16.0D;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dz * dz <= radiusBlocks * radiusBlocks) {
                count++;
            }
        }
        return count;
    }

    private static ServerPlayer ownerPlayerForChunk(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())
                    || !withinChunkRadius(region, pos.x, pos.z, region.mobSpawnRadiusChunks())) {
                continue;
            }
            return level.getServer().getPlayerList().getPlayer(region.playerId());
        }
        return null;
    }


    public static double portalDistanceToSqrForPlayerCoordinateRead(Entity receiver, double x, double y, double z, double vanillaDistanceSq) {
        if (!portalNaturalSpawningExperimentEnabled()) {
            return vanillaDistanceSq;
        }
        PortalSpawnContext context = CURRENT_PORTAL_SPAWN_CONTEXT.get();
        if (context == null || !context.active() || !(receiver instanceof Player)) {
            return vanillaDistanceSq;
        }
        double portalDistanceSq = context.virtualObserverPos().distanceToSqr(x, y, z);
        boolean finalDecision = portalDistanceAllows(context, new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        return PORTAL_SIMULATION_USE_VIRTUAL_PLAYER_COORDS_FOR_SPAWN_DISTANCE
                ? portalDistanceSq
                : vanillaDistanceSq;
    }

    public static double portalPlayerCoordinateForNaturalSpawner(Entity receiver, String axis, double vanillaValue) {
        if (!portalNaturalSpawningExperimentEnabled()) {
            return vanillaValue;
        }
        PortalSpawnContext context = CURRENT_PORTAL_SPAWN_CONTEXT.get();
        if (context == null || !context.active() || !(receiver instanceof Player)) {
            return vanillaValue;
        }
        double value = switch (axis) {
            case "x" -> context.virtualObserverPos().x;
            case "y" -> context.virtualObserverPos().y;
            case "z" -> context.virtualObserverPos().z;
            default -> vanillaValue;
        };
        return PORTAL_SIMULATION_USE_VIRTUAL_PLAYER_COORDS_FOR_SPAWN_DISTANCE ? value : vanillaValue;
    }

    private static boolean portalDistanceAllows(PortalSpawnContext context, BlockPos pos) {
        if (context == null || pos == null) {
            return false;
        }
        double distanceSq = context.virtualObserverPos().distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        double minSq = 24.0D * 24.0D;
        double maxRadius = context.mobSpawnRadiusChunks() * 16.0D + 16.0D;
        double maxSq = Math.min(128.0D * 128.0D, maxRadius * maxRadius);
        if (distanceSq < minSq || distanceSq > maxSq) {
            return false;
        }
        ServerPlayer owner = context.owner();
        double nearestRealPlayerDistance = -1.0D;
        if (owner != null && owner.serverLevel().dimension().equals(context.targetDim())) {
            double candidateX = pos.getX() + 0.5D;
            double candidateY = pos.getY();
            double candidateZ = pos.getZ() + 0.5D;
            for (ServerPlayer player : owner.serverLevel().players()) {
                double realPlayerDistanceSq = player.distanceToSqr(candidateX, candidateY, candidateZ);
                double realPlayerDistance = Math.sqrt(Math.max(0.0D, realPlayerDistanceSq));
                if (nearestRealPlayerDistance < 0.0D || realPlayerDistance < nearestRealPlayerDistance) {
                    nearestRealPlayerDistance = realPlayerDistance;
                }
                if (realPlayerDistanceSq < minSq) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vec3 virtualObserverPosForRegion(Region region) {
        return new Vec3(region.centerChunkX() * 16.0D + 8.0D, 80.0D, region.centerChunkZ() * 16.0D + 8.0D);
    }


    private static double square(double value) {
        return value * value;
    }


    private static void markPortalAddedSpawnChunk(ServerLevel level, ChunkPos pos) {
        PORTAL_ADDED_SPAWN_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet()).add(pos.toLong());
    }




    private static String firstMissingPriorityReason(PortalSpawnLoopSelection selection, List<PortalSpawnViewPlan> plans, String priority) {
        for (PortalSpawnViewPlan plan : plans) {
            if (priority.equals(plan.priority())) {
                return plan.chunks().isEmpty() ? "no-eligible-chunks" : "global-chunk-budget-exhausted";
            }
        }
        String blockedReason = selection == null ? "-" : selection.blockedReasonForPriority(priority);
        if (!"-".equals(blockedReason)) {
            return blockedReason;
        }
        return "no-" + priority + "-view";
    }


    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }



    private static void appendUnique(StringBuilder builder, String value, int maxLength) {
        if (builder.length() >= maxLength || value == null || value.isEmpty()) {
            return;
        }
        String existing = builder.toString();
        if (existing.contains(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record EntityTickKey(ResourceKey<Level> dimension, int entityId) {}

    private record RegionCategoryKey(
            UUID playerId,
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            MobCategory category
    ) {}

    private record PortalSpawnViewPlan(
            PortalNaturalSpawnObserver observer,
            List<LevelChunk> chunks,
            String priority,
            int liveHostiles,
            int viewPortalSpawned,
            int dimPortalSpawned
    ) {
        int priorityRank() {
            if ("cross-dim".equals(priority)) {
                return 0;
            }
            if ("far-same-dim".equals(priority)) {
                return 1;
            }
            if ("partial-same-dim".equals(priority)) {
                return 2;
            }
            return 3;
        }
    }

    private record PortalSpawnContext(
            boolean active,
            ResourceKey<Level> targetDim,
            ResourceLocation viewId,
            ChunkPos processedChunk,
            MobCategory category,
            Vec3 virtualObserverPos,
            ServerPlayer owner,
            int mobSpawnRadiusChunks
    ) {}

    private record PortalCapDecision(
            Region region,
            int liveHostileInRegion,
            int portalSpawnedAlive,
            int portalSpawnedAliveInDim,
            int liveCap,
            int spawnedCap,
            int dimCap,
            boolean allowsCategory,
            String reason
    ) {}

    private record PortalCategoryDecision(
            boolean portalOwnedChunk,
            MobCategory category,
            boolean vanillaGlobalAllows,
            boolean vanillaLocalAllows,
            boolean portalLocalAllows,
            boolean portalCapAllows,
            boolean finalAllows,
            String reason,
            Region region,
            int liveHostileInRegion,
            int portalSpawnedAlive,
            int liveCap
    ) {}

    private record PortalNaturalSpawnObserver(
            ResourceLocation viewId,
            ResourceKey<Level> targetDim,
            Vec3 pos,
            ChunkPos centerChunk,
            int radiusChunks,
            UUID owner,
            Region region,
            boolean sameDim
    ) {}

    private record PortalNaturalSpawnDryRun(
            int activePortalObservers,
            int wouldCandidateChunks,
            int wouldPassEntityTicking,
            boolean wouldBeBlockedByLiveCap,
            int wouldMergeChunks,
            String disabledReason
    ) {}

    private enum SpawnSourceKind {
        REAL_PLAYER,
        PORTAL_OBSERVER
    }

    private record SpawnSource(
            SpawnSourceKind kind,
            UUID ownerPlayer,
            ResourceLocation viewId,
            Vec3 observerPos,
            int priority,
            String reason
    ) {
        private static SpawnSource realPlayer() {
            return new SpawnSource(SpawnSourceKind.REAL_PLAYER, null, null, null, 0, "vanilla-real-player");
        }

        private static SpawnSource portal(PortalNaturalSpawnObserver observer) {
            return new SpawnSource(
                    SpawnSourceKind.PORTAL_OBSERVER,
                    observer.owner(),
                    observer.viewId(),
                    observer.pos(),
                    observer.sameDim() ? 2 : 1,
                    observer.sameDim() ? "portal-observer-same-dim" : "portal-observer-cross-dim"
            );
        }
    }

    private record PortalRemovalProtectionContext(
            ServerLevel level,
            Region region,
            double portalObserverDistance,
            boolean farSameDim,
            boolean crossDim,
            String reason,
            String matchingViews,
            String skippedViews,
            String reasonIfNotProtected
    ) {}

    private record PortalMobRegionMatch(
            Region region,
            double portalObserverDistance,
            double nearestRealPlayerDistance,
            boolean insideRegion,
            boolean farSameDim,
            boolean crossDim,
            boolean chunkLoaded,
            boolean blockTicking,
            boolean entityTicking,
            String matchingViews,
            String skippedViews,
            String reasonIfNotProtected
    ) {}

    private static final class PortalSpawnLoopSelection {
        private final ResourceKey<Level> dimension;
        private final long gameTime;
        private final List<LevelChunk> selectedChunks = new ArrayList<>();
        private final LongSet selectedPositions = new LongOpenHashSet();
        private final StringBuilder sample = new StringBuilder();
        private final StringBuilder viewIds = new StringBuilder();
        private final StringBuilder portalKinds = new StringBuilder();
        private final StringBuilder centers = new StringBuilder();
        private final StringBuilder radiusSummary = new StringBuilder();
        private final StringBuilder selectedChunkSamples = new StringBuilder();
        private final StringBuilder selectedChunkDistances = new StringBuilder();
        private final StringBuilder successChunkSamples = new StringBuilder();
        private final Map<Long, SpawnSource> spawnSources = new HashMap<>();
        private int regionsForLevel;
        private int realPlayerOwnedChunks;
        private int portalOwnedChunks;
        private int positionsConsidered;
        private int loaded;
        private int entityTicking;
        private int candidateBeforeCaps;
        private int candidateAfterCaps;
        private int rejectedTooCloseToVirtualObserver;
        private int rejectedWrongDim;
        private int rejectedNotLoaded;
        private int rejectedNotEntityTicking;
        private int rejectedAlreadyVanilla;
        private int rejectedLiveHostileCap;
        private int rejectedDimCap;
        private int spawnStateSpawnableChunks = -1;
        private int vanillaSpawningChunkCount = -1;
        private int selectionCursor;
        private int viewsConsidered;
        private int viewsSelected;
        private int viewsSkippedVanillaCovered;
        private int viewsSkippedCap;
        private int crossDimSelected;
        private int farSameDimSelected;
        private int nearSameDimSelected;
        private boolean crossDimObserved;
        private boolean farSameDimObserved;
        private boolean nearSameDimObserved;
        private String crossDimBlockedReason = "-";
        private String farSameDimBlockedReason = "-";
        private String nearSameDimBlockedReason = "-";
        private boolean centerChunkIncluded;
        private String firstTooCloseChunk = "-";
        private String firstRejectedChunk = "-";
        private String firstRejectedReason = "-";
        private String reasonIfNoCrossDimSelection = "-";
        private String reasonIfNoFarSameDimSelection = "-";
        private String reason = "-";

        private PortalSpawnLoopSelection(ResourceKey<Level> dimension, long gameTime) {
            this.dimension = dimension;
            this.gameTime = gameTime;
        }

        private void addRegionIdentity(PortalNaturalSpawnObserver observer) {
            appendUnique(viewIds, observer.viewId().toString(), 180);
            appendUnique(portalKinds, observer.sameDim() ? "same-dim" : "cross-dim", 80);
            appendUnique(centers, observer.centerChunk().x + "," + observer.centerChunk().z, 180);
            appendUnique(radiusSummary, String.valueOf(observer.radiusChunks()), 80);
        }

        private void recordFirstReject(int chunkX, int chunkZ, String reason) {
            if (!"-".equals(firstRejectedReason)) {
                return;
            }
            firstRejectedChunk = chunkX + "," + chunkZ;
            firstRejectedReason = reason == null ? "unknown" : reason;
        }

        private void recordSkippedView(String reason) {
            if ("already-covered-by-real-player-vanilla-spawn-list".equals(reason)) {
                viewsSkippedVanillaCovered++;
            } else if (reason != null && reason.contains("cap")) {
                viewsSkippedCap++;
            }
        }

        private void recordObservedPriority(String priority) {
            if ("cross-dim".equals(priority)) {
                crossDimObserved = true;
            } else if ("far-same-dim".equals(priority)) {
                farSameDimObserved = true;
            } else if ("partial-same-dim".equals(priority) || "near-same-dim".equals(priority)) {
                nearSameDimObserved = true;
            }
        }

        private void recordBlockedPriority(String priority, String reason) {
            String safeReason = reason == null || reason.isBlank() ? "unknown" : reason;
            if ("cross-dim".equals(priority) && "-".equals(crossDimBlockedReason)) {
                crossDimBlockedReason = safeReason;
            } else if ("far-same-dim".equals(priority) && "-".equals(farSameDimBlockedReason)) {
                farSameDimBlockedReason = safeReason;
            } else if (("partial-same-dim".equals(priority) || "near-same-dim".equals(priority)) && "-".equals(nearSameDimBlockedReason)) {
                nearSameDimBlockedReason = safeReason;
            }
        }

        private String blockedReasonForPriority(String priority) {
            if ("cross-dim".equals(priority)) {
                return crossDimObserved ? crossDimBlockedReason : "-";
            }
            if ("far-same-dim".equals(priority)) {
                return farSameDimObserved ? farSameDimBlockedReason : "-";
            }
            if ("partial-same-dim".equals(priority) || "near-same-dim".equals(priority)) {
                return nearSameDimObserved ? nearSameDimBlockedReason : "-";
            }
            return "-";
        }

        private void recordSelectedPriority(String priority, int chunks) {
            if ("cross-dim".equals(priority)) {
                crossDimSelected += chunks;
            } else if ("far-same-dim".equals(priority)) {
                farSameDimSelected += chunks;
            } else if ("partial-same-dim".equals(priority) || "near-same-dim".equals(priority)) {
                nearSameDimSelected += chunks;
            }
        }

        private String portalOwnedByView() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (SpawnSource source : spawnSources.values()) {
                if (source.kind() == SpawnSourceKind.PORTAL_OBSERVER && source.viewId() != null) {
                    counts.merge(source.viewId().toString(), 1, Integer::sum);
                }
            }
            return counts.isEmpty() ? "-" : counts.toString();
        }

        private String zeroSelectionReason() {
            if (candidateBeforeCaps > 0) {
                if (viewsSkippedCap > 0) {
                    return "portal-cap-reached";
                }
                if (viewsSkippedVanillaCovered > 0 && viewsSkippedVanillaCovered >= viewsConsidered) {
                    return "already-covered-by-real-player-vanilla-spawn-list";
                }
                return "candidate-chunks-available-but-not-selected";
            }
            if (rejectedTooCloseToVirtualObserver > 0 && positionsConsidered == rejectedTooCloseToVirtualObserver) {
                return "too-close-to-virtual-observer";
            }
            return "-".equals(firstRejectedReason) ? "no-candidate-chunks-selected" : firstRejectedReason;
        }
    }

    private record SpawnForChunkContext(
            ServerLevel level,
            ChunkPos pos,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean spawnAnimals,
            NaturalSpawner.SpawnState spawnState,
            boolean portalChunk
    ) {}

    private record SpawnCategoryContext(ServerLevel level, ChunkPos pos, MobCategory category) {}

}
