package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SkyesightServerChunkLoader {
    private static final Map<ViewKey, LoadedView> LOADED_VIEWS = new HashMap<>();

    private SkyesightServerChunkLoader() {}

    public static void updateLoadedView(
            ServerPlayer player,
            ResourceLocation viewId,
            ServerLevel level,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        ViewKey key = new ViewKey(player.getUUID(), viewId);

        LoadedView previous = LOADED_VIEWS.get(key);
        LongSet nextChunks = buildChunkSet(centerChunkX, centerChunkZ, radius);

        if (previous != null) {
            unloadRemovedChunks(level, previous.chunks(), nextChunks);
        }

        loadNewChunks(level, previous == null ? LongSet.of() : previous.chunks(), nextChunks);

        LOADED_VIEWS.put(
                key,
                new LoadedView(
                        level.dimension(),
                        centerChunkX,
                        centerChunkZ,
                        radius,
                        nextChunks
                )
        );
    }

    public static void removeView(
            ServerPlayer player,
            ResourceLocation viewId,
            ServerLevel level
    ) {
        ViewKey key = new ViewKey(player.getUUID(), viewId);
        LoadedView view = LOADED_VIEWS.remove(key);

        if (view == null) {
            return;
        }

        for (long packed : view.chunks()) {
            removeTicket(level, new ChunkPos(packed));
        }
    }

    public static void removeView(MinecraftServer server, ResourceLocation viewId) {
        if (server == null || viewId == null) {
            return;
        }
        LOADED_VIEWS.entrySet().removeIf(entry -> {
            if (!entry.getKey().viewId().equals(viewId)) {
                return false;
            }
            LoadedView view = entry.getValue();
            ServerLevel level = server.getLevel(view.dimension());
            if (level != null) {
                for (long packed : view.chunks()) {
                    removeTicket(level, new ChunkPos(packed));
                }
            }
            return true;
        });
    }

    public static void removeAllForPlayer(ServerPlayer player, Map<ResourceKey<Level>, ServerLevel> levels) {
        UUID playerId = player.getUUID();

        LOADED_VIEWS.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId().equals(playerId)) {
                return false;
            }

            LoadedView view = entry.getValue();
            ServerLevel level = levels.get(view.dimension());

            if (level != null) {
                for (long packed : view.chunks()) {
                    removeTicket(level, new ChunkPos(packed));
                }
            }

            return true;
        });
    }

    private static LongSet buildChunkSet(int centerChunkX, int centerChunkZ, int radius) {
        LongSet chunks = new LongOpenHashSet();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                chunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        return chunks;
    }

    private static void loadNewChunks(ServerLevel level, LongSet previous, LongSet next) {
        for (long packed : next) {
            if (!previous.contains(packed)) {
                addTicket(level, new ChunkPos(packed));
            }
        }
    }

    private static void unloadRemovedChunks(ServerLevel level, LongSet previous, LongSet next) {
        for (long packed : previous) {
            if (!next.contains(packed)) {
                removeTicket(level, new ChunkPos(packed));
            }
        }
    }

    private static void addTicket(ServerLevel level, ChunkPos pos) {
        Skyesight.LOGGER.debug(
                "[Skyesight] Adding chunk ticket dim={} chunk={}",
                level.dimension().location(),
                pos
        );

        level.setChunkForced(pos.x, pos.z, true);
    }

    private static void removeTicket(ServerLevel level, ChunkPos pos) {
        Skyesight.LOGGER.debug(
                "[Skyesight] Removing chunk ticket dim={} chunk={}",
                level.dimension().location(),
                pos
        );

        level.setChunkForced(pos.x, pos.z, false);
    }

    private record ViewKey(UUID playerId, ResourceLocation viewId) {}

    private record LoadedView(
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius,
            LongSet chunks
    ) {}
}
