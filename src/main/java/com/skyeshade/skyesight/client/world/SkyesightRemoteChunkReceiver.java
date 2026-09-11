package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkyesightRemoteChunkReceiver {
    private final SkyesightVisualClientLevel level;
    private final ChunkStatusListener chunkStatusListener;
    private final LongSet loadedChunks = new LongOpenHashSet();
    private int viewCenterChunkX;
    private int viewCenterChunkZ;

    public SkyesightRemoteChunkReceiver(SkyesightVisualClientLevel level, ChunkStatusListener chunkStatusListener) {
        this.level = level;
        this.chunkStatusListener = chunkStatusListener;
    }

    public void setViewCenter(int chunkX, int chunkZ, int radius) {
        ClientChunkCache cache = this.level.getChunkSource();

        this.viewCenterChunkX = chunkX;
        this.viewCenterChunkZ = chunkZ;
        cache.updateViewCenter(chunkX, chunkZ);
        cache.updateViewRadius(radius);
    }

    public ChunkPos viewCenter() { return new ChunkPos(viewCenterChunkX, viewCenterChunkZ); }

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

        updateChunkLightSectionStatus(chunkX, chunkZ);

        applyLightData(chunkX, chunkZ, lightData);

        enableChunkLight(chunkX, chunkZ);

        afterLightApplied.run();

        return true;
    }

    private boolean receiveChunk(
            int chunkX,
            int chunkZ,
            ClientboundLevelChunkPacketData chunkData
    ) {
        ClientChunkCache cache = this.level.getChunkSource();

        LevelChunk loadedChunk;
        try {
            loadedChunk = cache.replaceWithPacketData(
                    chunkX,
                    chunkZ,
                    chunkData.getReadBuffer(),
                    chunkData.getHeightmaps(),
                    chunkData.getBlockEntitiesTagsConsumer(chunkX, chunkZ)
            );
        } catch (RuntimeException exception) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Failed to decode portal visual chunk dim={} chunk={},{} reason={}: {}",
                    this.level.dimension().location(),
                    chunkX,
                    chunkZ,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            clear();
            return false;
        }

        if (loadedChunk == null) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Failed to insert chunk {}, {} into visual level",
                    chunkX,
                    chunkZ
            );
            return false;
        }

        this.loadedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
        this.chunkStatusListener.onChunkStatusAdded(chunkX, chunkZ);
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

        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();

        BlockState oldState = this.level.getBlockState(pos);

        int oldEmission = oldState.getLightEmission();
        int newEmission = state.getLightEmission();

        lightEngine.checkBlock(pos);

        this.level.setBlock(pos, state, 19);

        updateChunkLightSectionStatus(chunkPos.x, chunkPos.z);

        lightEngine.checkBlock(pos);

        if (oldEmission != newEmission) {
            for (BlockPos neighbor : BlockPos.betweenClosed(
                    pos.offset(-1, -1, -1),
                    pos.offset(1, 1, 1)
            )) {
                lightEngine.checkBlock(neighbor);
            }
        }

        lightEngine.runLightUpdates();

        SectionPos centerSection = SectionPos.of(pos);

        for (int sectionY = centerSection.y() - 1; sectionY <= centerSection.y() + 1; sectionY++) {
            SectionPos sectionPos = SectionPos.of(
                    centerSection.x(),
                    sectionY,
                    centerSection.z()
            );

            this.level.getChunkSource().onLightUpdate(LightLayer.BLOCK, sectionPos);

            if (this.level.dimensionType().hasSkyLight()) {
                this.level.getChunkSource().onLightUpdate(LightLayer.SKY, sectionPos);
            }
        }

        return true;
    }

    public boolean applyBlockEntityUpdate(BlockPos pos, CompoundTag tag) {
        if (pos == null || tag == null) {
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(pos);

        if (!hasChunk(chunkPos.x, chunkPos.z)) {
            return false;
        }

        BlockState state = this.level.getBlockState(pos);
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            this.level.removeBlockEntity(pos);
            return false;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(pos);

        if (blockEntity == null || blockEntity.isRemoved()) {
            blockEntity = entityBlock.newBlockEntity(pos, state);

            if (blockEntity == null) {
                return false;
            }

            this.level.setBlockEntity(blockEntity);
        }

        blockEntity.loadWithComponents(tag, this.level.registryAccess());
        blockEntity.setChanged();
        return true;
    }

    public int countBlockEntities() {
        int count = 0;
        for (long packed : this.loadedChunks) {
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = this.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);

            if (chunk != null) {
                count += chunk.getBlockEntitiesPos().size();
            }
        }
        return count;
    }

    public String firstBlockEntities(int limit) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (long packed : this.loadedChunks) {
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = this.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);

            if (chunk == null) {
                continue;
            }

            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                BlockEntity blockEntity = this.level.getBlockEntity(pos);

                if (blockEntity == null) {
                    continue;
                }

                if (builder.length() > 0) {
                    builder.append(";");
                }
                builder.append(blockEntity.getType()).append("@").append(pos.toShortString());
                count++;

                if (count >= limit) {
                    return builder.toString();
                }
            }
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }
    private void applyLightData(
            int chunkX,
            int chunkZ,
            ClientboundLightUpdatePacketData data
    ) {
        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        lightEngine.setLightEnabled(chunkPos, true);

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

        lightEngine.runLightUpdates();
    }
    private void updateChunkLightSectionStatus(int chunkX, int chunkZ) {
        LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);

        if (chunk == null) {
            return;
        }

        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();

        for (int sectionY = lightEngine.getMinLightSection(); sectionY < lightEngine.getMaxLightSection(); sectionY++) {
            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);

            boolean empty = true;

            if (sectionY >= this.level.getMinSection() && sectionY < this.level.getMaxSection()) {
                int sectionIndex = this.level.getSectionIndexFromSectionY(sectionY);
                LevelChunkSection section = chunk.getSection(sectionIndex);
                empty = section.hasOnlyAir();
            }

            lightEngine.updateSectionStatus(sectionPos, empty);
        }
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
    public TickStats tickBlockEntities(ResourceLocation viewId) {
        int ticked = 0;
        int skipped = 0;
        String skippedReason = "-";

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
                    skipped++;
                    skippedReason = "no-client-ticker";
                    continue;
                }

                ticker.tick(this.level, blockEntityPos, state, blockEntity);
                ticked++;
                if (SkyesightDebugConfig.WATCH_DEBUG) {
                    Skyesight.LOGGER.info(
                            "[Skyesight] SKYESIGHT_CROSS_DIM_VISUAL_BE_CLIENT_TICK: viewId={} cameraDimension={} blockPos={} blockEntityType={} blockState={} ticked=yes reasonIfSkipped=-",
                            viewId == null ? "-" : viewId,
                            this.level.dimension().location(),
                            blockEntityPos,
                            blockEntity.getType(),
                            state
                    );
                }
            }
        }
        return new TickStats(ticked, skipped, skippedReason);
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

    public record TickStats(int ticked, int skipped, String skippedReason) {
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
        this.chunkStatusListener.onChunkStatusRemoved(pos.x, pos.z);
    }

    public interface ChunkStatusListener {
        void onChunkStatusAdded(int chunkX, int chunkZ);

        void onChunkStatusRemoved(int chunkX, int chunkZ);
    }
}
