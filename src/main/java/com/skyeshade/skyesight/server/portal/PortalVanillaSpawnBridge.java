package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.server.PortalSimulationCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class PortalVanillaSpawnBridge extends PortalSimulationCoordinator {
    private PortalVanillaSpawnBridge() {
    }

    public static boolean isChunkCoveredByRealPlayerVanilla(ServerLevel level, ChunkPos chunkPos) {
        return PortalVirtualPlayerProximity.isCoveredByRealPlayerVanilla(level, chunkPos);
    }

    public static boolean isPortalBypassAllowed(ServerLevel level, ChunkPos chunkPos, ResourceLocation viewId) {
        return PortalVirtualPlayerProximity.isPortalBypassAllowed(level, chunkPos);
    }

    public static boolean allowSpawnChunk(ChunkMap chunkMap, ServerLevel level, ChunkPos chunkPos) {
        boolean vanilla = PortalVirtualPlayerProximity.isCoveredByRealPlayerVanilla(level, chunkPos);
        boolean portal = false;
        if (!vanilla && PortalVirtualPlayerProximity.isPortalObservedChunk(level, chunkPos)) {
            portal = PortalSimulationCoordinator.hasPortalSpawnObserverNearChunk(level, chunkPos);
        }
        return vanilla || portal;
    }

    public static boolean allowForceTickGate(DistanceManager distanceManager, ServerLevel level, long packedChunkPos) {
        boolean vanilla = distanceManager.shouldForceTicks(packedChunkPos);
        ChunkPos pos = new ChunkPos(ChunkPos.getX(packedChunkPos), ChunkPos.getZ(packedChunkPos));
        boolean result = vanilla;
        if (vanilla && PortalVirtualPlayerProximity.isPortalObservedChunk(level, pos)) {
            result = PortalSimulationCoordinator.shouldForceTickChunkForNaturalSpawnGate(level, pos, true);
        }
        return result;
    }

    public static boolean shouldAllowLocalMobCap(ServerLevel level, ChunkPos pos, MobCategory category, boolean vanillaAllows) {
        if (vanillaAllows || !PortalVirtualPlayerProximity.isPortalObservedChunk(level, pos)) {
            return false;
        }
        return PortalSimulationCoordinator.shouldPortalObserverAllowLocalMobCap(level, pos, category);
    }

    public static boolean shouldForcePortalCategory(MobCategory category, ChunkPos pos, boolean globalCapAllows, boolean vanillaAllows) {
        return PortalSimulationCoordinator.shouldForcePortalCategory(category, pos, globalCapAllows, vanillaAllows);
    }

    public static boolean hasPortalSpawnObserverNearChunk(ServerLevel level, ChunkPos pos) {
        return PortalVirtualPlayerProximity.isPortalObservedChunk(level, pos)
                && PortalSimulationCoordinator.hasPortalSpawnObserverNearChunk(level, pos);
    }

    public static boolean hasPortalObserverNear(ServerLevel level, double x, double y, double z, double maxDistance) {
        return PortalVirtualPlayerProximity.hasNearbyPortalObserver(level, x, y, z, maxDistance);
    }

    public static double getNearestPortalObserverDistanceSq(ServerLevel level, double x, double y, double z, double maxDistance) {
        return PortalVirtualPlayerProximity.nearestPortalObserver(level, x, y, z, maxDistance)
                .map(PortalVirtualPlayerProximity.PortalProximityResult::distanceSqr)
                .orElse(-1.0D);
    }

    public static Boolean portalDistanceResultForSpawn(ServerLevel level, ChunkAccess chunk, BlockPos pos, double vanillaDistanceSq) {
        if (level == null || chunk == null || pos == null || !PortalVirtualPlayerProximity.isPortalObservedChunk(level, chunk.getPos())) {
            return null;
        }
        return PortalVirtualPlayerProximity.nearestPortalObserverForChunk(level, chunk.getPos())
                .map(result -> PortalVirtualPlayerProximity.portalNaturalSpawnDistanceAllows(level, pos, result.viewId()))
                .orElse(null);
    }

    public static double portalDistanceToSqrForPlayerCoordinateRead(Entity receiver, double x, double y, double z, double vanillaDistanceSq) {
        if (receiver != null && receiver.level() instanceof ServerLevel level) {
            PortalVirtualPlayerProximity.nearestPortalObserver(level, x, y, z, 128.0D);
        }
        return PortalSimulationCoordinator.portalDistanceToSqrForPlayerCoordinateRead(receiver, x, y, z, vanillaDistanceSq);
    }

    public static double portalPlayerCoordinateForNaturalSpawner(Entity receiver, String axis, double vanillaValue) {
        if (receiver != null && receiver.level() instanceof ServerLevel level) {
            PortalVirtualPlayerProximity.virtualCoordinateForNaturalSpawner(level, null, axis, vanillaValue);
        }
        return PortalSimulationCoordinator.portalPlayerCoordinateForNaturalSpawner(receiver, axis, vanillaValue);
    }

    public static boolean globalCapAllows(int spawnableChunkCount, int currentCategoryCount, MobCategory category) {
        if (category == null || spawnableChunkCount <= 0) {
            return false;
        }
        int limit = category.getMaxInstancesPerChunk() * spawnableChunkCount / 17;
        return currentCategoryCount < limit;
    }

    public static void beginServerChunkCacheTick(ServerLevel level) {
        PortalSimulationCoordinator.beginServerChunkCacheTick(level);
    }

    public static void recordPortalSpawnedMob(ServerLevel level, net.minecraft.world.entity.Mob mob) {
        PortalSimulationCoordinator.recordPortalSpawnedMob(level, mob);
    }

}
