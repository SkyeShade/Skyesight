package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.network.SkyesightChunkRequestPayload;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkyesightClientChunkRequester {
    private static final Map<ResourceLocation, ViewRequestState> STATES = new HashMap<>();

    private SkyesightClientChunkRequester() {}

    public static void requestChunksFor(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            Camera camera,
            int radius
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }

        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getOrCreate(viewId, dimension);

        if (world == null) {
            return;
        }

        ViewRequestState state = STATES.computeIfAbsent(viewId, ignored -> new ViewRequestState());

        int centerChunkX = Mth.floor(camera.getPosition().x()) >> 4;
        int centerChunkZ = Mth.floor(camera.getPosition().z()) >> 4;

        int cacheRadius = radius + 3;

        world.chunkReceiver().setViewCenter(centerChunkX, centerChunkZ, cacheRadius);
        world.chunkReceiver().pruneOutside(centerChunkX, centerChunkZ, cacheRadius);
        prunePendingOutside(state, dimension, centerChunkX, centerChunkZ, cacheRadius);

        List<ChunkPos> missing = collectMissingChunks(
                state,
                world,
                dimension,
                centerChunkX,
                centerChunkZ,
                radius
        );

        ChunkPos center = new ChunkPos(centerChunkX, centerChunkZ);

        boolean centerChanged = state.lastRequestedCenter == null
                || !state.lastRequestedCenter.equals(center)
                || state.lastRequestedDimension == null
                || !state.lastRequestedDimension.equals(dimension);

        if (missing.isEmpty() && !centerChanged) {
            return;
        }

        state.lastRequestedCenter = center;
        state.lastRequestedDimension = dimension;

        missing.sort((a, b) -> {
            int da = Math.abs(a.x - centerChunkX) + Math.abs(a.z - centerChunkZ);
            int db = Math.abs(b.x - centerChunkX) + Math.abs(b.z - centerChunkZ);
            return Integer.compare(da, db);
        });

        for (ChunkPos pos : missing) {
            state.pendingChunks.add(packPending(dimension, pos.x, pos.z));
        }

        PacketDistributor.sendToServer(
                new SkyesightChunkRequestPayload(
                        viewId,
                        dimension,
                        centerChunkX,
                        centerChunkZ,
                        radius,
                        missing
                )
        );
    }

    public static void markChunkReceived(
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ
    ) {
        ViewRequestState state = STATES.get(viewId);

        if (state == null) {
            return;
        }

        state.pendingChunks.remove(packPending(dimension, chunkX, chunkZ));
    }

    public static void reset(ResourceLocation viewId) {
        STATES.remove(viewId);
    }

    public static void reset() {
        STATES.clear();
    }

    private static void prunePendingOutside(
            ViewRequestState state,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        ObjectOpenHashSet<PendingChunkKey> toRemove = new ObjectOpenHashSet<>();

        for (PendingChunkKey key : state.pendingChunks) {
            if (!key.dimension().equals(dimension)) {
                continue;
            }

            if (Math.abs(key.chunkX() - centerChunkX) > radius || Math.abs(key.chunkZ() - centerChunkZ) > radius) {
                toRemove.add(key);
            }
        }

        for (PendingChunkKey key : toRemove) {
            state.pendingChunks.remove(key);
        }
    }

    private static List<ChunkPos> collectMissingChunks(
            ViewRequestState state,
            SkyesightVisualWorld world,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        List<ChunkPos> missing = new ArrayList<>();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;

                PendingChunkKey key = packPending(dimension, chunkX, chunkZ);

                if (world.chunkReceiver().hasChunk(chunkX, chunkZ)) {
                    state.pendingChunks.remove(key);
                    continue;
                }

                if (state.pendingChunks.contains(key)) {
                    continue;
                }

                missing.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        return missing;
    }

    private static PendingChunkKey packPending(
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ
    ) {
        return new PendingChunkKey(dimension, chunkX, chunkZ);
    }

    private static final class ViewRequestState {
        private ChunkPos lastRequestedCenter;
        private ResourceKey<Level> lastRequestedDimension;
        private final ObjectOpenHashSet<PendingChunkKey> pendingChunks = new ObjectOpenHashSet<>();
    }

    private record PendingChunkKey(
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ
    ) {}
}