package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkyesightSecondaryChunkWatchRegion {
    private static final Map<UUID, Map<ResourceLocation, WatchRegion>> REGIONS = new HashMap<>();
    private static final Map<ResourceLocation, InitialChunkSendStats> INITIAL_SEND_STATS = new HashMap<>();

    private static volatile boolean active;
    private static volatile int activeRegionCount;
    private static volatile int watchedChunkCount;
    private static volatile int blockUpdatePacketsForwarded;
    private static volatile int blockEntityUpdatePacketsForwarded;
    private static volatile int lightUpdatePacketsForwarded;
    private static volatile String lastUpdateSummary = "n/a";
    private static volatile String lastRegionSummary = "n/a";
    private static volatile String lastInitialChunkSendSummary = "n/a";
    private static volatile long lastRegionLogMillis;

    private SkyesightSecondaryChunkWatchRegion() {}

    public static void setRegion(
            ServerPlayer player,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            ChunkPos center,
            int radius
    ) {
        if (player == null || regionId == null || dimension == null || center == null) {
            return;
        }

        REGIONS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(regionId, new WatchRegion(regionId, dimension, center, radius));
        PortalSimulationCoordinator.update(
                player,
                regionId,
                dimension,
                center.x,
                center.z,
                radius
        );
        lastRegionSummary = "region="
                + regionId
                + " player="
                + player.getGameProfile().getName()
                + " center="
                + center.x
                + ","
                + center.z
                + " radius="
                + radius
                + " requested="
                + ((radius * 2 + 1) * (radius * 2 + 1))
                + " dim="
                + dimension.location();
        updateSummaries();
        logRegionSummaryIfDue();
    }

    public static void removeRegion(ServerPlayer player, ResourceLocation regionId) {
        if (player == null || regionId == null) {
            return;
        }

        Map<ResourceLocation, WatchRegion> playerRegions = REGIONS.get(player.getUUID());

        if (playerRegions == null) {
            return;
        }

        playerRegions.remove(regionId);
        INITIAL_SEND_STATS.remove(regionId);

        if (playerRegions.isEmpty()) {
            REGIONS.remove(player.getUUID());
        }

        updateSummaries();
    }

    public static void removeRegion(ResourceLocation regionId) {
        if (regionId == null) {
            return;
        }
        REGIONS.values().forEach(playerRegions -> playerRegions.remove(regionId));
        REGIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        INITIAL_SEND_STATS.remove(regionId);
        updateSummaries();
    }

    public static void clearAllRegions(ServerPlayer player) {
        if (player != null) {
            REGIONS.remove(player.getUUID());
        }

        INITIAL_SEND_STATS.clear();

        updateSummaries();
    }

    public static List<ServerPlayer> playersWatching(
            Level level,
            ChunkPos chunkPos
    ) {
        List<ServerPlayer> players = new ArrayList<>();

        if (level == null || chunkPos == null || level.getServer() == null) {
            return players;
        }

        for (UUID playerId : REGIONS.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

            if (player == null || player.isRemoved() || !player.serverLevel().dimension().equals(level.dimension())) {
                continue;
            }

            if (playerWatchesChunk(player, level.dimension(), chunkPos)) {
                players.add(player);
            }
        }

        return players;
    }

    public static boolean playerWatchesChunk(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos
    ) {
        if (player == null || dimension == null || chunkPos == null) {
            return false;
        }

        Map<ResourceLocation, WatchRegion> playerRegions = REGIONS.get(player.getUUID());

        if (playerRegions == null || playerRegions.isEmpty()) {
            return false;
        }

        for (WatchRegion region : playerRegions.values()) {
            if (region.dimension().equals(dimension) && region.contains(chunkPos)) {
                return true;
            }
        }

        return false;
    }

    public static boolean anyRegionWatchesChunk(
            ResourceKey<Level> dimension,
            ChunkPos chunkPos
    ) {
        if (dimension == null || chunkPos == null) {
            return false;
        }

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            for (WatchRegion region : playerRegions.values()) {
                if (region.dimension().equals(dimension) && region.contains(chunkPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static List<ResourceLocation> regionIdsWatchingChunk(
            ResourceKey<Level> dimension,
            ChunkPos chunkPos
    ) {
        List<ResourceLocation> ids = new ArrayList<>();

        if (dimension == null || chunkPos == null) {
            return ids;
        }

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            for (WatchRegion region : playerRegions.values()) {
                if (region.dimension().equals(dimension) && region.contains(chunkPos)) {
                    ids.add(region.id());
                }
            }
        }

        return ids;
    }

    public static void recordInitialChunkSend(
            ServerPlayer player,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            ChunkPos center,
            int radius,
            int requested,
            int forceLoaded,
            int packetsSent,
            String firstChunks
    ) {
        lastInitialChunkSendSummary = "region="
                + regionId
                + " player="
                + (player == null ? "n/a" : player.getGameProfile().getName())
                + " center="
                + (center == null ? "n/a" : center.x + "," + center.z)
                + " radius="
                + radius
                + " requested="
                + requested
                + " forceLoaded="
                + forceLoaded
                + " packetsSent="
                + packetsSent
                + " first="
                + firstChunks
                + " dim="
                + (dimension == null ? "n/a" : dimension.location());
        INITIAL_SEND_STATS.put(
                regionId,
                new InitialChunkSendStats(radius, requested, forceLoaded, packetsSent, firstChunks, lastInitialChunkSendSummary)
        );
        if (SkyesightDebugConfig.WATCH_DEBUG) {
            Skyesight.LOGGER.info("[Skyesight] Secondary chunk watch initial send: {}", lastInitialChunkSendSummary);
        }
        logRegionSummaryIfDue();
    }

    public static void recordBlockUpdateForwarded(BlockPos pos) {
        blockUpdatePacketsForwarded++;
        lastUpdateSummary = "block " + formatPos(pos);
    }

    public static void recordBlockEntityUpdateForwarded(BlockPos pos) {
        blockEntityUpdatePacketsForwarded++;
        lastUpdateSummary = "blockEntity " + formatPos(pos);
    }

    public static void recordLightUpdateForwarded(ChunkPos pos) {
        lightUpdatePacketsForwarded++;
        lastUpdateSummary = "light " + pos.x + "," + pos.z;
    }

    public static boolean active() {
        return active;
    }

    public static int activeRegionCount() {
        return activeRegionCount;
    }

    public static int watchedChunkCount() {
        return watchedChunkCount;
    }

    public static int blockUpdatePacketsForwarded() {
        return blockUpdatePacketsForwarded;
    }

    public static int blockEntityUpdatePacketsForwarded() {
        return blockEntityUpdatePacketsForwarded;
    }

    public static int lightUpdatePacketsForwarded() {
        return lightUpdatePacketsForwarded;
    }

    public static String lastUpdateSummary() {
        return lastUpdateSummary;
    }

    public static String lastRegionSummary() {
        return lastRegionSummary;
    }

    public static String lastInitialChunkSendSummary() {
        return lastInitialChunkSendSummary;
    }

    public static String initialChunkSendSummary(ResourceLocation regionId) {
        InitialChunkSendStats stats = INITIAL_SEND_STATS.get(regionId);
        return stats == null ? "n/a" : stats.summary();
    }

    public static int regionRadius(ResourceLocation regionId) {
        if (regionId == null) {
            return -1;
        }

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            WatchRegion region = playerRegions.get(regionId);

            if (region != null) {
                return region.radius();
            }
        }

        return -1;
    }

    public static int initialChunkSendRadius(ResourceLocation regionId) {
        InitialChunkSendStats stats = INITIAL_SEND_STATS.get(regionId);
        return stats == null ? -1 : stats.radius();
    }

    public static int initialChunkSendRequested(ResourceLocation regionId) {
        InitialChunkSendStats stats = INITIAL_SEND_STATS.get(regionId);
        return stats == null ? 0 : stats.requested();
    }

    public static int initialChunkSendForceLoaded(ResourceLocation regionId) {
        InitialChunkSendStats stats = INITIAL_SEND_STATS.get(regionId);
        return stats == null ? 0 : stats.forceLoaded();
    }

    public static int initialChunkSendPacketsSent(ResourceLocation regionId) {
        InitialChunkSendStats stats = INITIAL_SEND_STATS.get(regionId);
        return stats == null ? 0 : stats.packetsSent();
    }

    private static void logRegionSummaryIfDue() {
        if (!SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();

        if (now - lastRegionLogMillis < 3000L) {
            return;
        }

        lastRegionLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] Secondary chunk watch: activeRegions={} watchedChunks={} region='{}' initialSend='{}'",
                activeRegionCount,
                watchedChunkCount,
                lastRegionSummary,
                lastInitialChunkSendSummary
        );
    }

    private static void updateSummaries() {
        activeRegionCount = 0;
        watchedChunkCount = 0;

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            activeRegionCount += playerRegions.size();

            for (WatchRegion region : playerRegions.values()) {
                watchedChunkCount += (region.radius() * 2 + 1) * (region.radius() * 2 + 1);
            }
        }

        active = activeRegionCount > 0;
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "n/a";
        }

        return String.format(Locale.ROOT, "%d,%d,%d", pos.getX(), pos.getY(), pos.getZ());
    }

    private record WatchRegion(
            ResourceLocation id,
            ResourceKey<Level> dimension,
            ChunkPos center,
            int radius
    ) {
        private boolean contains(ChunkPos pos) {
            return Math.abs(pos.x - this.center.x) <= this.radius
                    && Math.abs(pos.z - this.center.z) <= this.radius;
        }
    }

    private record InitialChunkSendStats(
            int radius,
            int requested,
            int forceLoaded,
            int packetsSent,
            String firstChunks,
            String summary
    ) {}
}
