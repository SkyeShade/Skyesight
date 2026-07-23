package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.mixin.server.chunk.ChunkMapEntityTrackerAccessor;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PortalVirtualPlayerProximity {
    private static final double NATURAL_SPAWN_MIN_DISTANCE = 24.0D;
    private static final double NATURAL_SPAWN_MAX_DISTANCE = 128.0D;
    private static long lastSummaryMillis;
    private static int queries;
    private static int hits;
    private static int realPlayerExcluded;
    private static int crossDimHits;
    private static int farSameDimHits;
    private static int nearSameDimSuppressed;
    private static int ownerCoordLeakPrevented;

    private PortalVirtualPlayerProximity() {
    }

    public record PortalVirtualObserver(
            ResourceLocation viewId,
            UUID ownerUuid,
            ResourceKey<Level> ownerDimension,
            ResourceKey<Level> targetDimension,
            Vec3 virtualPosition,
            ChunkPos centerChunk,
            boolean crossDim,
            boolean farSameDim
    ) {
    }

    public record PortalProximityResult(
            boolean found,
            ResourceLocation viewId,
            Vec3 virtualPosition,
            double distanceSqr,
            boolean crossDim,
            boolean farSameDim,
            String reason
    ) {
    }

    public static Optional<PortalProximityResult> nearestPortalObserver(
            ServerLevel level,
            double x,
            double y,
            double z,
            double maxDistance
    ) {
        queries++;
        if (level == null || PortalRegionTracker.isEmpty()) {
            maybeLogSummary(level);
            return Optional.empty();
        }
        double maxDistanceSq = maxDistance < 0.0D ? Double.MAX_VALUE : maxDistance * maxDistance;
        PortalProximityResult best = null;
        for (Region region : PortalRegionTracker.values()) {
            PortalVirtualObserver observer = observerFor(level, region);
            if (observer == null) {
                continue;
            }
            if (!observer.crossDim() && isCoveredByRealPlayerVanilla(level, observer.centerChunk())) {
                realPlayerExcluded++;
                continue;
            }
            if (!observer.crossDim() && !observer.farSameDim()) {
                nearSameDimSuppressed++;
                continue;
            }
            Vec3 pos = observer.virtualPosition();
            double distanceSq = pos.distanceToSqr(x, y, z);
            double regionMax = Math.max(maxDistanceSq, square(region.entityTickRadiusChunks() * 16.0D + 16.0D));
            if (distanceSq > regionMax || (best != null && distanceSq >= best.distanceSqr())) {
                continue;
            }
            best = new PortalProximityResult(
                    true,
                    observer.viewId(),
                    observer.virtualPosition(),
                    distanceSq,
                    observer.crossDim(),
                    observer.farSameDim(),
                    observer.crossDim() ? "cross-dim-virtual-observer" : "far-same-dim-virtual-observer"
            );
        }
        if (best != null) {
            hits++;
            if (best.crossDim()) {
                crossDimHits++;
            } else if (best.farSameDim()) {
                farSameDimHits++;
            }
        }
        maybeLogSummary(level);
        return Optional.ofNullable(best);
    }

    public static boolean hasNearbyPortalObserver(ServerLevel level, double x, double y, double z, double maxDistance) {
        return nearestPortalObserver(level, x, y, z, maxDistance).isPresent();
    }

    public static boolean isPortalObservedChunk(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null) {
            return false;
        }
        for (Region region : PortalRegionTracker.values()) {
            PortalVirtualObserver observer = observerFor(level, region);
            if (observer == null || !withinChunkRadius(region, chunk, region.mobSpawnRadiusChunks())) {
                continue;
            }
            if (!observer.crossDim() && isCoveredByRealPlayerVanilla(level, chunk)) {
                realPlayerExcluded++;
                maybeLogSummary(level);
                return false;
            }
            if (!observer.crossDim() && !observer.farSameDim()) {
                nearSameDimSuppressed++;
                maybeLogSummary(level);
                return false;
            }
            maybeLogSummary(level);
            return true;
        }
        maybeLogSummary(level);
        return false;
    }

    public static boolean isPortalBypassAllowed(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null) {
            return false;
        }
        if (isCoveredByRealPlayerVanilla(level, chunk)) {
            realPlayerExcluded++;
            maybeLogSummary(level);
            return false;
        }
        return true;
    }

    public static boolean isCoveredByRealPlayerVanilla(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null || level.players().isEmpty()) {
            return false;
        }
        try {
            return ((ChunkMapEntityTrackerAccessor) level.getChunkSource().chunkMap)
                    .skyesight$anyPlayerCloseEnoughForSpawning(chunk);
        } catch (RuntimeException exception) {
            return fallbackPlayerChunkCoverage(level, chunk);
        }
    }

    public static boolean isSameDimRealPlayerSafetyBlocked(ServerLevel level, BlockPos pos, double minDistance) {
        if (level == null || pos == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(pos);
        if (isCoveredByRealPlayerVanilla(level, chunk)) {
            realPlayerExcluded++;
            maybeLogSummary(level);
            return true;
        }
        double minDistanceSq = minDistance * minDistance;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= minDistanceSq) {
                realPlayerExcluded++;
                maybeLogSummary(level);
                return true;
            }
        }
        return false;
    }

    public static double portalDistanceSqr(ServerLevel level, BlockPos pos, ResourceLocation viewId) {
        if (level == null || pos == null) {
            return -1.0D;
        }
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            if (viewId != null && !viewId.equals(region.viewId())) {
                continue;
            }
            if (!withinChunkRadius(region, new ChunkPos(pos), region.mobSpawnRadiusChunks())) {
                continue;
            }
            PortalVirtualObserver observer = observerFor(level, region);
            if (observer == null) {
                continue;
            }
            return observer.virtualPosition().distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        }
        return -1.0D;
    }

    public static Optional<PortalProximityResult> nearestPortalObserverForChunk(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null || !isPortalObservedChunk(level, chunk)) {
            return Optional.empty();
        }
        return nearestPortalObserver(level, chunk.getMiddleBlockX(), 80.0D, chunk.getMiddleBlockZ(), NATURAL_SPAWN_MAX_DISTANCE);
    }

    public static boolean portalNaturalSpawnDistanceAllows(ServerLevel level, BlockPos pos, ResourceLocation viewId) {
        double distanceSq = portalDistanceSqr(level, pos, viewId);
        if (distanceSq < 0.0D) {
            return false;
        }
        return distanceSq >= square(NATURAL_SPAWN_MIN_DISTANCE)
                && distanceSq <= square(NATURAL_SPAWN_MAX_DISTANCE);
    }

    public static double virtualCoordinateForNaturalSpawner(ServerLevel level, ResourceLocation viewId, String axis, double vanillaValue) {
        if (level == null) {
            return vanillaValue;
        }
        for (Region region : PortalRegionTracker.values()) {
            if (!region.dimension().equals(level.dimension())) {
                continue;
            }
            if (viewId != null && !viewId.equals(region.viewId())) {
                continue;
            }
            PortalVirtualObserver observer = observerFor(level, region);
            if (observer == null) {
                continue;
            }
            ownerCoordLeakPrevented++;
            maybeLogSummary(level);
            return switch (axis) {
                case "x" -> observer.virtualPosition().x;
                case "y" -> observer.virtualPosition().y;
                case "z" -> observer.virtualPosition().z;
                default -> vanillaValue;
            };
        }
        return vanillaValue;
    }

    private static PortalVirtualObserver observerFor(ServerLevel level, Region region) {
        if (level == null || region == null || !region.dimension().equals(level.dimension())) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(region.playerId());
        ResourceKey<Level> ownerDimension = owner == null ? null : owner.serverLevel().dimension();
        boolean crossDim = !region.sameDim();
        Vec3 virtualPosition = virtualPosition(region);
        boolean farSameDim = false;
        if (!crossDim) {
            if (owner == null || !owner.serverLevel().dimension().equals(region.dimension())) {
                farSameDim = true;
            } else {
                double dx = owner.getX() - virtualPosition.x;
                double dz = owner.getZ() - virtualPosition.z;
                farSameDim = dx * dx + dz * dz >= NATURAL_SPAWN_MAX_DISTANCE * NATURAL_SPAWN_MAX_DISTANCE;
            }
        }
        return new PortalVirtualObserver(
                region.viewId(),
                region.playerId(),
                ownerDimension,
                region.dimension(),
                virtualPosition,
                new ChunkPos(region.centerChunkX(), region.centerChunkZ()),
                crossDim,
                farSameDim
        );
    }

    private static Vec3 virtualPosition(Region region) {
        return new Vec3(region.centerChunkX() * 16.0D + 8.0D, 80.0D, region.centerChunkZ() * 16.0D + 8.0D);
    }

    private static boolean withinChunkRadius(Region region, ChunkPos chunk, int radius) {
        return Math.abs(chunk.x - region.centerChunkX()) <= radius
                && Math.abs(chunk.z - region.centerChunkZ()) <= radius;
    }

    private static boolean fallbackPlayerChunkCoverage(ServerLevel level, ChunkPos chunk) {
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dz * dz <= NATURAL_SPAWN_MAX_DISTANCE * NATURAL_SPAWN_MAX_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static double square(double value) {
        return value * value;
    }

    private static void maybeLogSummary(ServerLevel level) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSummaryMillis < 5000L) {
            return;
        }
        lastSummaryMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VIRTUAL_PLAYER_PROXIMITY_SUMMARY: dim={} views={} queries={} hits={} realPlayerExcluded={} crossDimHits={} farSameDimHits={} nearSameDimSuppressed={} ownerCoordLeakPrevented={}",
                level == null ? "-" : level.dimension().location(),
                PortalRegionTracker.size(),
                queries,
                hits,
                realPlayerExcluded,
                crossDimHits,
                farSameDimHits,
                nearSameDimSuppressed,
                ownerCoordLeakPrevented
        );
        queries = 0;
        hits = 0;
        realPlayerExcluded = 0;
        crossDimHits = 0;
        farSameDimHits = 0;
        nearSameDimSuppressed = 0;
        ownerCoordLeakPrevented = 0;
    }
}
