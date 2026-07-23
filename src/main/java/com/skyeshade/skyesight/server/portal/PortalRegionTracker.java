package com.skyeshade.skyesight.server.portal;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PortalRegionTracker {
    private static final Object LOCK = new Object();
    private static final Map<Key, Region> REGIONS = new LinkedHashMap<>();

    private PortalRegionTracker() {
    }

    public static Region get(Key key) {
        synchronized (LOCK) {
            return REGIONS.get(key);
        }
    }

    public static void put(Region region) {
        synchronized (LOCK) {
            REGIONS.put(new Key(region.playerId(), region.viewId()), region);
        }
    }

    public static void remove(Key key) {
        synchronized (LOCK) {
            REGIONS.remove(key);
        }
    }

    public static boolean contains(Key key) {
        synchronized (LOCK) {
            return REGIONS.containsKey(key);
        }
    }

    public static boolean isEmpty() {
        synchronized (LOCK) {
            return REGIONS.isEmpty();
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return REGIONS.size();
        }
    }

    public static Collection<Region> values() {
        return snapshotValues();
    }

    public static List<Region> snapshotValues() {
        synchronized (LOCK) {
            return List.copyOf(REGIONS.values());
        }
    }

    public static Set<Map.Entry<Key, Region>> entrySet() {
        synchronized (LOCK) {
            return Set.copyOf(new LinkedHashMap<>(REGIONS).entrySet());
        }
    }

    public static Set<Key> keySet() {
        synchronized (LOCK) {
            return Set.copyOf(REGIONS.keySet());
        }
    }

    public static int countRegionsForPlayer(UUID playerId) {
        synchronized (LOCK) {
            int count = 0;
            for (Key key : REGIONS.keySet()) {
                if (key.playerId().equals(playerId)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static int activeRegionCount(ResourceKey<Level> dimension) {
        synchronized (LOCK) {
            int count = 0;
            for (Region region : REGIONS.values()) {
                if (region.dimension().equals(dimension)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static int eligibleChunkCount(ResourceKey<Level> dimension) {
        synchronized (LOCK) {
            int count = 0;
            for (Region region : REGIONS.values()) {
                if (region.dimension().equals(dimension)) {
                    count += region.chunks().size();
                }
            }
            return count;
        }
    }

    public static LongSet buildChunkSet(int centerChunkX, int centerChunkZ, int radius) {
        LongSet chunks = new LongOpenHashSet();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                chunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        return chunks;
    }

    public static int chunkCountForRadius(int radius) {
        int diameter = radius * 2 + 1;
        return diameter * diameter;
    }

    public record Key(UUID playerId, ResourceLocation viewId) {
    }

    public record Region(
            UUID playerId,
            ResourceLocation viewId,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int loadRadiusChunks,
            int entityTickRadiusChunks,
            int mobSpawnRadiusChunks,
            LongSet chunks,
            long lastUpdateTick,
            boolean sameDim
    ) {
    }
}
