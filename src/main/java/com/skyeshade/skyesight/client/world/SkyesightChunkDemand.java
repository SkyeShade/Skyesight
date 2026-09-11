package com.skyeshade.skyesight.client.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.function.BiPredicate;

/** One generation's camera-owned demand. Response packet centers are never authoritative. */
final class SkyesightChunkDemand {
    static final long RETRY_NANOS = 5_000_000_000L;
    final long generation;
    final ResourceKey<Level> dimension;
    ChunkPos center;
    int radius = -1, lastRequestedCount;
    final Map<ChunkPos, Long> pending = new HashMap<>();

    SkyesightChunkDemand(long generation, ResourceKey<Level> dimension) {
        this.generation = generation; this.dimension = dimension;
    }
    boolean matches(long generation, ResourceKey<Level> dimension) {
        return this.generation == generation && this.dimension.equals(dimension);
    }
    List<ChunkPos> update(ChunkPos nextCenter, int nextRadius, long now, BiPredicate<Integer,Integer> loaded) {
        boolean changed = !nextCenter.equals(center) || radius != nextRadius;
        center = nextCenter; radius = nextRadius;
        pending.entrySet().removeIf(e -> !contains(e.getKey(), radius + 3) || now-e.getValue() >= RETRY_NANOS);
        List<ChunkPos> missing = new ArrayList<>();
        for (int z=-radius; z<=radius; z++) for (int x=-radius; x<=radius; x++) {
            ChunkPos pos = new ChunkPos(center.x+x,center.z+z);
            if (loaded.test(pos.x,pos.z)) pending.remove(pos);
            else if (!pending.containsKey(pos)) missing.add(pos);
        }
        missing.sort(Comparator.comparingInt(p -> Math.abs(p.x-center.x)+Math.abs(p.z-center.z)));
        if (missing.size()>256) missing = new ArrayList<>(missing.subList(0,256));
        if (missing.isEmpty() && !changed) return null;
        for (ChunkPos pos : missing) pending.put(pos,now);
        lastRequestedCount = missing.size();
        return missing;
    }
    boolean contains(ChunkPos pos, int distance) {
        return center != null && Math.abs(pos.x-center.x)<=distance && Math.abs(pos.z-center.z)<=distance;
    }
    void acknowledge(long generation, ResourceKey<Level> dimension, int x,int z) {
        if (matches(generation,dimension)) pending.remove(new ChunkPos(x,z));
    }
}
