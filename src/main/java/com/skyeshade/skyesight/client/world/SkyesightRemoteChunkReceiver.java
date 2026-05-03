package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.BitSet;
import java.util.Iterator;

public final class SkyesightRemoteChunkReceiver {
    private final ClientLevel level;
    private final ChunkTracker tracker;
    private final LongSet loadedChunks = new LongOpenHashSet();

    public SkyesightRemoteChunkReceiver(ClientLevel level, ChunkTracker tracker) {
        this.level = level;
        this.tracker = tracker;
    }

    public void setViewCenter(int chunkX, int chunkZ, int radius) {
        ClientChunkCache cache = this.level.getChunkSource();

        cache.updateViewCenter(chunkX, chunkZ);
        cache.updateViewRadius(radius);
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.loadedChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public boolean receiveChunkWithLight(
            int chunkX,
            int chunkZ,
            ClientboundLevelChunkPacketData chunkData,
            ClientboundLightUpdatePacketData lightData,
            Runnable afterLightApplied
    ) {
        boolean inserted = receiveChunk(chunkX, chunkZ, chunkData);

        if (!inserted) {
            return false;
        }

        applyLightData(chunkX, chunkZ, lightData);

        LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);

        if (chunk != null) {
            enableChunkLight(chunkX, chunkZ);
        }

        afterLightApplied.run();

        return true;
    }

    private boolean receiveChunk(
            int chunkX,
            int chunkZ,
            ClientboundLevelChunkPacketData chunkData
    ) {
        ClientChunkCache cache = this.level.getChunkSource();

        LevelChunk loadedChunk = cache.replaceWithPacketData(
                chunkX,
                chunkZ,
                chunkData.getReadBuffer(),
                chunkData.getHeightmaps(),
                chunkData.getBlockEntitiesTagsConsumer(chunkX, chunkZ)
        );

        if (loadedChunk == null) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Failed to insert chunk {}, {} into visual level",
                    chunkX,
                    chunkZ
            );
            return false;
        }

        this.loadedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
        this.tracker.onChunkStatusAdded(chunkX, chunkZ, 3);
        return true;
    }
    public boolean applyLightUpdate(
            int chunkX,
            int chunkZ,
            ClientboundLightUpdatePacketData lightData
    ) {
        if (!hasChunk(chunkX, chunkZ)) {
            return false;
        }

        applyLightData(chunkX, chunkZ, lightData);
        enableChunkLight(chunkX, chunkZ);

        return true;
    }
    public boolean applyBlockUpdate(BlockPos pos, BlockState state) {
        ChunkPos chunkPos = new ChunkPos(pos);

        if (!hasChunk(chunkPos.x, chunkPos.z)) {
            return false;
        }

        this.level.setBlock(pos, state, 19);

        SectionPos sectionPos = SectionPos.of(pos);

        this.level.getChunkSource().onLightUpdate(LightLayer.BLOCK, sectionPos);

        if (this.level.dimensionType().hasSkyLight()) {
            this.level.getChunkSource().onLightUpdate(LightLayer.SKY, sectionPos);
        }

        return true;
    }
    private void applyLightData(
            int chunkX,
            int chunkZ,
            ClientboundLightUpdatePacketData data
    ) {
        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();

        readSectionList(
                chunkX,
                chunkZ,
                lightEngine,
                LightLayer.SKY,
                data.getSkyYMask(),
                data.getEmptySkyYMask(),
                data.getSkyUpdates().iterator()
        );

        readSectionList(
                chunkX,
                chunkZ,
                lightEngine,
                LightLayer.BLOCK,
                data.getBlockYMask(),
                data.getEmptyBlockYMask(),
                data.getBlockUpdates().iterator()
        );

        lightEngine.setLightEnabled(new ChunkPos(chunkX, chunkZ), true);
    }

    private void readSectionList(
            int chunkX,
            int chunkZ,
            LevelLightEngine lightEngine,
            LightLayer lightLayer,
            BitSet sectionMask,
            BitSet emptySectionMask,
            Iterator<byte[]> updates
    ) {
        for (int sectionIndex = 0; sectionIndex < lightEngine.getLightSectionCount(); sectionIndex++) {
            int sectionY = lightEngine.getMinLightSection() + sectionIndex;
            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);

            if (sectionMask.get(sectionIndex)) {
                if (!updates.hasNext()) {
                    return;
                }

                byte[] data = updates.next();
                lightEngine.queueSectionData(lightLayer, sectionPos, new DataLayer(data.clone()));
            } else if (emptySectionMask.get(sectionIndex)) {
                lightEngine.queueSectionData(lightLayer, sectionPos, new DataLayer());
            }
        }
    }
    public void forEachLoadedChunk(LongConsumer consumer) {
        for (long packed : this.loadedChunks) {

            consumer.accept(packed);
        }
    }
    public void tickBlockEntities() {
        for (long packed : this.loadedChunks) {
            ChunkPos chunkPos = new ChunkPos(packed);

            LevelChunk chunk = this.level.getChunkSource().getChunk(
                    chunkPos.x,
                    chunkPos.z,
                    false
            );

            if (chunk == null) {
                continue;
            }

            for (BlockPos blockEntityPos : chunk.getBlockEntitiesPos()) {
                BlockEntity blockEntity = this.level.getBlockEntity(blockEntityPos);

                if (blockEntity == null || blockEntity.isRemoved()) {
                    continue;
                }

                BlockState state = this.level.getBlockState(blockEntityPos);

                BlockEntityTicker<BlockEntity> ticker =
                        getTicker(this.level, state, blockEntity);

                if (ticker == null) {
                    continue;
                }

                ticker.tick(this.level, blockEntityPos, state, blockEntity);
            }
        }
    }
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            T blockEntity
    ) {
        Block block = state.getBlock();

        if (!(block instanceof EntityBlock entityBlock)) {
            return null;
        }

        return (BlockEntityTicker<T>) entityBlock.getTicker(
                level,
                state,
                blockEntity.getType()
        );
    }
    private void enableChunkLight(int chunkX, int chunkZ) {
        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();

        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        lightEngine.setLightEnabled(chunkPos, true);

        for (int sectionY = this.level.getMinSection(); sectionY < this.level.getMaxSection(); sectionY++) {
            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);

            this.level.getChunkSource().onLightUpdate(LightLayer.BLOCK, sectionPos);

            if (this.level.dimensionType().hasSkyLight()) {
                this.level.getChunkSource().onLightUpdate(LightLayer.SKY, sectionPos);
            }
        }
    }

    public void pruneOutside(int centerChunkX, int centerChunkZ, int radius) {
        LongSet toRemove = new LongOpenHashSet();

        for (long packed : this.loadedChunks) {
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);

            if (Math.abs(chunkX - centerChunkX) > radius || Math.abs(chunkZ - centerChunkZ) > radius) {
                toRemove.add(packed);
            }
        }

        for (long packed : toRemove) {
            ChunkPos pos = new ChunkPos(
                    ChunkPos.getX(packed),
                    ChunkPos.getZ(packed)
            );

            unloadChunk(pos);
        }
    }
    public void clear() {
        LongSet copy = new LongOpenHashSet(this.loadedChunks);

        for (long packed : copy) {
            unloadChunk(new ChunkPos(
                    ChunkPos.getX(packed),
                    ChunkPos.getZ(packed)
            ));
        }

        this.loadedChunks.clear();
    }
    public void unloadChunk(ChunkPos pos) {
        this.loadedChunks.remove(pos.toLong());
        this.level.getChunkSource().drop(pos);
        this.tracker.onChunkStatusRemoved(pos.x, pos.z, 3);
    }
}