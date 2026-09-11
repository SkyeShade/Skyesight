package com.skyeshade.skyesight.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shared by remote camera loading and simulation. Accessed on the server thread. */
public final class SkyesightForcedChunkTickets {
    private record ChunkKey(ResourceKey<Level> dimension, long position) {}
    private record Requester(String purpose, UUID player, ResourceLocation view) {}
    private static final ChunkTicketOwners<ChunkKey, Requester> OWNERS = new ChunkTicketOwners<>();
    private static final Set<ChunkKey> PREEXISTING = new HashSet<>();

    private SkyesightForcedChunkTickets() {}

    public static void acquire(ServerLevel level, long chunk, String purpose, UUID player, ResourceLocation view) {
        ChunkKey key = new ChunkKey(level.dimension(), chunk);
        if (OWNERS.acquire(key, new Requester(purpose, player, view))) {
            if (level.getForcedChunks().contains(chunk)) PREEXISTING.add(key);
            else level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
        }
    }

    public static void release(MinecraftServer server, ResourceKey<Level> dimension, long chunk,
                               String purpose, UUID player, ResourceLocation view) {
        ChunkKey key = new ChunkKey(dimension, chunk);
        if (OWNERS.release(key, new Requester(purpose, player, view))) {
            ServerLevel level = server.getLevel(dimension);
            if (!PREEXISTING.remove(key) && level != null) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
        }
    }

    public static void clear(MinecraftServer server) {
        for (ChunkKey key : OWNERS.chunks()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (!PREEXISTING.contains(key) && level != null) {
                level.setChunkForced(ChunkPos.getX(key.position()), ChunkPos.getZ(key.position()), false);
            }
        }
        OWNERS.clear();
        PREEXISTING.clear();
    }
}
