package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.server.portal.PortalRegionTracker;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkyesightServerChunkLoader {
    private static final Map<ViewKey, LoadedView> LOADED_VIEWS = new HashMap<>();
    private static final String PURPOSE = "remote_camera";
    private SkyesightServerChunkLoader() {}

    public static void updateLoadedView(ServerPlayer player, ResourceLocation viewId, ServerLevel level,
                                        int centerChunkX, int centerChunkZ, int radius) {
        updateLoadedView(player, viewId, level, centerChunkX, centerChunkZ, radius, 0L, 0L);
    }

    public static void updateLoadedView(ServerPlayer player, ResourceLocation viewId, ServerLevel level,
            int centerChunkX, int centerChunkZ, int radius, long generation, long sequence) {
        ViewKey key = new ViewKey(player.getUUID(), viewId);
        LoadedView previous = LOADED_VIEWS.get(key);
        LongSet next = PortalRegionTracker.buildChunkSet(centerChunkX, centerChunkZ, radius);
        boolean sameDimension = previous != null && previous.dimension().equals(level.dimension());
        int newlyOwned = 0;
        int released = 0;
        for (long chunk : next) {
            if (!sameDimension || !previous.chunks().contains(chunk)) {
                newlyOwned++;
                SkyesightForcedChunkTickets.acquire(level, chunk, PURPOSE, key.playerId(), key.viewId());
            }
        }
        if (previous != null) {
            for (long chunk : previous.chunks()) {
                if (!sameDimension || !next.contains(chunk)) {
                    released++;
                    release(player.server, key, previous.dimension(), chunk);
                }
            }
        }
        LOADED_VIEWS.put(key, new LoadedView(level.dimension(), next));
        com.skyeshade.skyesight.remote.SkyesightRemoteCenterDiagnostics.trace("SERVER_LOAD_UPDATE",viewId,generation,sequence,
                "player="+player.getUUID()+" center="+centerChunkX+","+centerChunkZ
                +" newlyOwned="+newlyOwned+" released="+released+" ownedChunks="+next.size());
    }

    public static void removeView(ServerPlayer player, ResourceLocation viewId, ServerLevel ignored) {
        ViewKey key = new ViewKey(player.getUUID(), viewId);
        LoadedView previous = LOADED_VIEWS.remove(key);
        if (previous != null) release(player.server, key, previous);
    }

    public static void removeView(MinecraftServer server, ResourceLocation viewId) {
        if (server == null || viewId == null) return;
        LOADED_VIEWS.entrySet().removeIf(entry -> {
            if (!entry.getKey().viewId().equals(viewId)) return false;
            release(server, entry.getKey(), entry.getValue());
            return true;
        });
    }

    public static void removeAllForPlayer(ServerPlayer player, Map<ResourceKey<Level>, ServerLevel> ignored) {
        LOADED_VIEWS.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId().equals(player.getUUID())) return false;
            release(player.server, entry.getKey(), entry.getValue());
            return true;
        });
    }

    public static void clear(MinecraftServer server) {
        LOADED_VIEWS.forEach((key, value) -> release(server, key, value));
        LOADED_VIEWS.clear();
    }

    private static void release(MinecraftServer server, ViewKey key, LoadedView view) {
        for (long chunk : view.chunks()) release(server, key, view.dimension(), chunk);
    }

    private static void release(MinecraftServer server, ViewKey key, ResourceKey<Level> dimension, long chunk) {
        SkyesightForcedChunkTickets.release(server, dimension, chunk, PURPOSE, key.playerId(), key.viewId());
    }

    private record ViewKey(UUID playerId, ResourceLocation viewId) {}
    private record LoadedView(ResourceKey<Level> dimension, LongSet chunks) {}
}
