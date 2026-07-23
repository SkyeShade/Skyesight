package com.skyeshade.skyesight.client.chunk;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.world.SkyesightClientLevelFactory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SkyesightPortalChunkStorage {
    private static final boolean DEBUG_VERBOSE_PORTAL_STREAMING_DIAGNOSTICS = false;
    private static final Map<Key, SkyesightPortalChunkStoreEntry> CHUNKS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, ClientLevel> DECODE_LEVELS = new ConcurrentHashMap<>();
    private static volatile long lastSummaryLogMillis;

    private SkyesightPortalChunkStorage() {}

    public static SkyesightPortalChunkStoreEntry store(
            ResourceKey<Level> dimension,
            ResourceLocation viewId,
            int chunkX,
            int chunkZ,
            ClientboundLevelChunkPacketData chunkData,
            ClientboundLightUpdatePacketData lightData
    ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        DecodedChunk decoded = decodeChunk(dimension, pos, chunkData);
        SkyesightPortalChunkStoreEntry entry = new SkyesightPortalChunkStoreEntry(
                dimension,
                pos,
                viewId,
                chunkData,
                lightData,
                decoded.chunk(),
                decoded.exception(),
                decoded.nonEmptySectionCount(),
                decoded.blockEntityTagCount(),
                System.currentTimeMillis(),
                true
        );
        CHUNKS.put(new Key(dimension, pos.toLong()), entry);
        logSummaryIfDue();
        return entry;
    }

    public static int countChunks(ResourceKey<Level> dimension) {
        int count = 0;

        for (Key key : CHUNKS.keySet()) {
            if (key.dimension().equals(dimension)) {
                count++;
            }
        }

        return count;
    }

    public static boolean hasChunk(ResourceKey<Level> dimension, ChunkPos pos) {
        return pos != null && CHUNKS.containsKey(new Key(dimension, pos.toLong()));
    }

    public static boolean packetDataPresent(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry != null && entry.chunkData() != null;
    }

    public static boolean lightDataPresent(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry != null && entry.lightData() != null;
    }

    public static boolean decoded(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry != null && entry.decodedChunk() != null;
    }

    public static String decodeException(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry == null ? "missing" : emptyDash(entry.decodeException());
    }

    public static int blockEntityTagCount(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry == null ? -1 : entry.blockEntityTagCount();
    }

    public static int nonEmptySectionCount(ResourceKey<Level> dimension, ChunkPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        return entry == null ? -1 : entry.nonEmptySectionCount();
    }

    public static Optional<BlockState> getBlockState(ResourceKey<Level> dimension, BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }

        SkyesightPortalChunkStoreEntry entry = entry(dimension, new ChunkPos(pos));
        if (entry == null || entry.decodedChunk() == null) {
            return Optional.empty();
        }

        return Optional.of(entry.decodedChunk().getBlockState(pos));
    }

    public static Optional<Integer> minBuildHeight(ResourceKey<Level> dimension) {
        return firstDecodedChunk(dimension).map(LevelChunk::getMinBuildHeight);
    }

    public static Optional<Integer> maxBuildHeight(ResourceKey<Level> dimension) {
        return firstDecodedChunk(dimension).map(LevelChunk::getMaxBuildHeight);
    }

    public static String sampleBlockStateSummary(ResourceKey<Level> dimension, BlockPos pos) {
        if (pos == null) {
            return "pos=n/a packetDataPresent=no blockStateDecodeImplemented=no";
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        return "pos="
                + pos.toShortString()
                + " dim="
                + dimension.location()
                + " chunk="
                + chunkPos.x
                + ","
                + chunkPos.z
                + " chunkPresent="
                + yesNo(hasChunk(dimension, chunkPos))
                + " packetDataPresent="
                + yesNo(packetDataPresent(dimension, chunkPos))
                + decodedBlockStateSummary(dimension, pos);
    }

    public static String sampleHeightOrFirstNonAirSummary(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        if (entry != null && entry.decodedChunk() != null) {
            return firstNonAirSummary(dimension, chunkX, chunkZ);
        }

        return "chunk="
                + chunkX
                + ","
                + chunkZ
                + " dim="
                + dimension.location()
                + " chunkPresent="
                + yesNo(hasChunk(dimension, pos))
                + " packetDataPresent="
                + yesNo(packetDataPresent(dimension, pos))
                + " heightDecodeImplemented=no firstNonAirDecodeImplemented=no decodeException="
                + emptyDash(entry == null ? "missing" : entry.decodeException());
    }

    public static String firstNonAirSummary(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        SkyesightPortalChunkStoreEntry entry = entry(dimension, pos);
        if (entry == null) {
            return "chunk=" + chunkX + "," + chunkZ + " decoded=no reason=missing";
        }
        if (entry.decodedChunk() == null) {
            return "chunk="
                    + chunkX
                    + ","
                    + chunkZ
                    + " decoded=no decodeException="
                    + emptyDash(entry.decodeException());
        }

        LevelChunk chunk = entry.decodedChunk();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos blockPos = new BlockPos(baseX + x, y, baseZ + z);
                    BlockState state = chunk.getBlockState(blockPos);
                    if (!state.isAir()) {
                        return "chunk="
                                + chunkX
                                + ","
                                + chunkZ
                                + " decoded=yes firstNonAir="
                                + blockPos.toShortString()
                                + ":"
                                + state.getBlock().builtInRegistryHolder().key().location();
                    }
                }
            }
        }

        return "chunk=" + chunkX + "," + chunkZ + " decoded=yes firstNonAir=none";
    }

    public static int totalChunkCount() {
        return CHUNKS.size();
    }

    public static String summaryForDimension(ResourceKey<Level> dimension) {
        return "dim="
                + dimension.location()
                + " count="
                + countChunks(dimension)
                + " first="
                + firstNChunks(dimension, 5);
    }

    public static String firstNChunks(ResourceKey<Level> dimension, int limit) {
        return CHUNKS.values()
                .stream()
                .filter(entry -> entry.dimension().equals(dimension))
                .sorted(Comparator.comparingInt((SkyesightPortalChunkStoreEntry entry) -> entry.pos().x)
                        .thenComparingInt(entry -> entry.pos().z))
                .limit(Math.max(0, limit))
                .map(entry -> entry.pos().x + "," + entry.pos().z + "@" + entry.viewId())
                .collect(Collectors.joining(" ", "[", "]"));
    }

    public static String globalSummary() {
        Map<ResourceKey<Level>, Integer> byDimension = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> byView = new LinkedHashMap<>();

        for (SkyesightPortalChunkStoreEntry entry : CHUNKS.values()) {
            byDimension.merge(entry.dimension(), 1, Integer::sum);
            byView.merge(entry.viewId(), 1, Integer::sum);
        }

        return "total="
                + totalChunkCount()
                + " byDimension="
                + summarizeDimensions(byDimension)
                + " byView="
                + summarizeViews(byView)
                + " collisions="
                + coordinateCollisionsSummary(5);
    }

    public static void clear() {
        CHUNKS.clear();
        DECODE_LEVELS.clear();
    }

    public static void clearView(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        CHUNKS.entrySet().removeIf(entry -> viewId.equals(entry.getValue().viewId()));
    }

    public static String coordinateCollisionsSummary(int limit) {
        Map<Long, List<ResourceKey<Level>>> dimensionsByChunk = new HashMap<>();

        for (Key key : CHUNKS.keySet()) {
            dimensionsByChunk.computeIfAbsent(key.chunkPos(), ignored -> new ArrayList<>())
                    .add(key.dimension());
        }

        StringBuilder summary = new StringBuilder("[");
        int written = 0;

        for (Map.Entry<Long, List<ResourceKey<Level>>> entry : dimensionsByChunk.entrySet()) {
            List<ResourceKey<Level>> dimensions = entry.getValue()
                    .stream()
                    .distinct()
                    .toList();

            if (dimensions.size() < 2) {
                continue;
            }

            if (written++ >= limit) {
                break;
            }

            if (summary.length() > 1) {
                summary.append(' ');
            }

            summary.append(ChunkPos.getX(entry.getKey()))
                    .append(',')
                    .append(ChunkPos.getZ(entry.getKey()))
                    .append('=')
                    .append(dimensions.stream()
                            .map(dimension -> dimension.location().toString())
                            .collect(Collectors.joining("|")));
        }

        return summary.append(']').toString();
    }

    public static String coordinateCollisionSummary(int chunkX, int chunkZ) {
        long packed = ChunkPos.asLong(chunkX, chunkZ);
        List<ResourceKey<Level>> dimensions = CHUNKS.keySet()
                .stream()
                .filter(key -> key.chunkPos() == packed)
                .map(Key::dimension)
                .distinct()
                .toList();

        return "coordinateCollision x="
                + chunkX
                + ",z="
                + chunkZ
                + " dims="
                + dimensions.stream()
                .map(dimension -> dimension.location().toString())
                .collect(Collectors.joining(",", "[", "]"))
                + " separated="
                + yesNo(dimensions.size() > 1);
    }

    private static SkyesightPortalChunkStoreEntry entry(ResourceKey<Level> dimension, ChunkPos pos) {
        if (dimension == null || pos == null) {
            return null;
        }

        return CHUNKS.get(new Key(dimension, pos.toLong()));
    }

    private static Optional<LevelChunk> firstDecodedChunk(ResourceKey<Level> dimension) {
        return CHUNKS.values()
                .stream()
                .filter(entry -> entry.dimension().equals(dimension))
                .map(SkyesightPortalChunkStoreEntry::decodedChunk)
                .filter(chunk -> chunk != null)
                .findFirst();
    }

    private static DecodedChunk decodeChunk(
            ResourceKey<Level> dimension,
            ChunkPos pos,
            ClientboundLevelChunkPacketData chunkData
    ) {
        if (dimension == null || pos == null || chunkData == null) {
            return new DecodedChunk(null, "missing dimension/pos/packetData", -1, -1);
        }

        try {
            ClientLevel level = DECODE_LEVELS.computeIfAbsent(dimension, SkyesightClientLevelFactory::create);
            LevelChunk chunk = new LevelChunk(level, pos);
            chunk.replaceWithPacketData(
                    chunkData.getReadBuffer(),
                    chunkData.getHeightmaps(),
                    chunkData.getBlockEntitiesTagsConsumer(pos.x, pos.z)
            );
            return new DecodedChunk(
                    chunk,
                    "",
                    countNonEmptySections(chunk),
                    chunk.getBlockEntitiesPos().size()
            );
        } catch (RuntimeException exception) {
            return new DecodedChunk(
                    null,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    -1,
                    -1
            );
        }
    }

    private static int countNonEmptySections(LevelChunk chunk) {
        int count = 0;

        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) {
                count++;
            }
        }

        return count;
    }

    private static String decodedBlockStateSummary(ResourceKey<Level> dimension, BlockPos pos) {
        SkyesightPortalChunkStoreEntry entry = entry(dimension, new ChunkPos(pos));
        if (entry == null) {
            return " blockStateDecodeImplemented=yes decoded=no reason=missing";
        }
        if (entry.decodedChunk() == null) {
            return " blockStateDecodeImplemented=yes decoded=no decodeException="
                    + emptyDash(entry.decodeException());
        }

        BlockState state = entry.decodedChunk().getBlockState(pos);
        return " blockStateDecodeImplemented=yes decoded=yes state="
                + state.getBlock().builtInRegistryHolder().key().location()
                + " air="
                + yesNo(state.isAir());
    }

    private static void logSummaryIfDue() {
        if (!DEBUG_VERBOSE_PORTAL_STREAMING_DIAGNOSTICS) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSummaryLogMillis < 3000L) {
            return;
        }

        lastSummaryLogMillis = now;
        Skyesight.LOGGER.info("[Skyesight] Portal chunk storage summary {}", globalSummary());
    }

    private static String summarizeDimensions(Map<ResourceKey<Level>, Integer> counts) {
        return counts.entrySet()
                .stream()
                .map(entry -> entry.getKey().location() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String summarizeViews(Map<ResourceLocation, Integer> counts) {
        return counts.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record Key(ResourceKey<Level> dimension, long chunkPos) {}

    private record DecodedChunk(
            LevelChunk chunk,
            String exception,
            int nonEmptySectionCount,
            int blockEntityTagCount
    ) {}
}
