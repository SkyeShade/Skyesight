package com.skyeshade.skyesight.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class SkyesightServerViewTracker {
    private static final Map<UUID, Map<ResourceLocation, ViewWatch>> WATCHES = new HashMap<>();

    private SkyesightServerViewTracker() {}

    public static void updateWatch(
            ServerPlayer player,
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            Collection<ChunkPos> chunks
    ) {
        Map<ResourceLocation, ViewWatch> playerWatches =
                WATCHES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());

        playerWatches.put(
                viewId,
                new ViewWatch(
                        viewId,
                        dimension,
                        centerChunkX,
                        centerChunkZ,
                        radius,
                        Set.copyOf(chunks)
                )
        );
        PortalSimulationCoordinator.update(
                player,
                viewId,
                dimension,
                centerChunkX,
                centerChunkZ,
                radius
        );
    }

    public static ViewWatch getWatch(ServerPlayer player, ResourceLocation viewId) {
        Map<ResourceLocation, ViewWatch> playerWatches = WATCHES.get(player.getUUID());

        if (playerWatches == null) {
            return null;
        }

        return playerWatches.get(viewId);
    }

    public static ViewWatch firstWatch(ResourceLocation viewId) {
        for (Map<ResourceLocation, ViewWatch> playerWatches : WATCHES.values()) {
            ViewWatch watch = playerWatches.get(viewId);

            if (watch != null) {
                return watch;
            }
        }

        return null;
    }

    public static void forEachWatch(BiConsumer<UUID, ViewWatch> consumer) {
        for (Map.Entry<UUID, Map<ResourceLocation, ViewWatch>> playerEntry : WATCHES.entrySet()) {
            UUID playerId = playerEntry.getKey();

            for (ViewWatch watch : playerEntry.getValue().values()) {
                consumer.accept(playerId, watch);
            }
        }
    }

    public static String activeWatchSummary() {
        Map<ResourceKey<Level>, Integer> byDimension = new HashMap<>();
        int total = 0;
        StringBuilder sample = new StringBuilder();

        for (Map<ResourceLocation, ViewWatch> playerWatches : WATCHES.values()) {
            for (ViewWatch watch : playerWatches.values()) {
                total++;
                byDimension.merge(watch.dimension(), 1, Integer::sum);
                if (sample.length() < 240) {
                    if (sample.length() > 0) {
                        sample.append(";");
                    }
                    sample.append(watch.viewId())
                            .append("@")
                            .append(watch.dimension().location())
                            .append(" c=")
                            .append(watch.centerChunkX())
                            .append(",")
                            .append(watch.centerChunkZ())
                            .append(" r=")
                            .append(watch.radius());
                }
            }
        }

        StringBuilder dims = new StringBuilder();
        byDimension.forEach((dimension, count) -> {
            if (dims.length() > 0) {
                dims.append(",");
            }
            dims.append(dimension.location()).append("=").append(count);
        });

        return "total=" + total
                + " byDimension=" + (dims.length() == 0 ? "-" : dims)
                + " sample=" + (sample.length() == 0 ? "-" : sample);
    }

    public static void removePlayer(ServerPlayer player) {
        WATCHES.remove(player.getUUID());
    }

    public static void removeView(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        WATCHES.values().forEach(playerWatches -> playerWatches.remove(viewId));
        WATCHES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static Collection<WatchedPlayerView> viewsWatching(
            ResourceKey<Level> dimension,
            ChunkPos chunkPos
    ) {
        List<WatchedPlayerView> result = new ArrayList<>();

        for (Map.Entry<UUID, Map<ResourceLocation, ViewWatch>> playerEntry : WATCHES.entrySet()) {
            UUID playerId = playerEntry.getKey();

            for (ViewWatch watch : playerEntry.getValue().values()) {
                if (!watch.dimension().equals(dimension)) {
                    continue;
                }

                if (!watch.chunks().contains(chunkPos)) {
                    continue;
                }

                result.add(new WatchedPlayerView(playerId, watch));
            }
        }

        return result;
    }

    public record WatchedPlayerView(UUID playerId, ViewWatch watch) {}

    public record ViewWatch(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            Set<ChunkPos> chunks
    ) {}
}
