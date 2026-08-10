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
    private static final int VANILLA_CAMERA_ANIMATE_TICK_SAMPLES = 667;
    private static final int CAMERA_GENERIC_DISPLAY_TICK_SAMPLES = 1536;
    private static final int CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS = 16;
    private static final int CAMERA_GENERIC_DISPLAY_TICK_VERTICAL_RADIUS = 16;
    private final SkyesightVisualClientLevel level;
    private final ChunkStatusListener chunkStatusListener;
    private final LongSet loadedChunks = new LongOpenHashSet();
    private final RandomSource visualParticleRandom = RandomSource.create();
    private int viewCenterChunkX;
    private int viewCenterChunkZ;
    private long visualParticleTickCounter;
    private long lastCameraVisualParticleTickMillis;
    private long lastVisualParticleSourceLogMillis;
    private long lastCameraVisualParticleSourceLogMillis;

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

    public int tickVisualParticles(ResourceLocation viewId, SkyesightVisualParticleManager particles) {
        if (particles == null) {
            return 0;
        }

        this.level.setSkyesightViewId(viewId);
        tickWatchedParticleBlock(viewId, particles);
        int chunksSampled = 0;
        int blocksSampled = 0;
        int capturedBefore = particles.size();
        int chunkBudget = Math.min(4, Math.max(1, this.loadedChunks.size()));
        int chunkOffset = this.loadedChunks.isEmpty()
                ? 0
                : (int) (this.visualParticleTickCounter++ % this.loadedChunks.size());
        int index = 0;

        for (long packed : this.loadedChunks) {
            if (chunksSampled >= chunkBudget) {
                break;
            }
            if (index++ < chunkOffset && this.loadedChunks.size() > chunkBudget) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(packed);

            LevelChunk chunk = this.level.getChunkSource().getChunk(
                    chunkPos.x,
                    chunkPos.z,
                    false
            );

            if (chunk == null) {
                continue;
            }

            blocksSampled += sampleVisualParticleChunk(chunkPos, chunk);
            chunksSampled++;
        }

        int captured = Math.max(0, particles.size() - capturedBefore);
        logVisualParticleTickIfDue(viewId, particles, chunksSampled, blocksSampled, captured);
        return captured;
    }

    public int tickVisualParticlesAround(
            ResourceLocation viewId,
            SkyesightVisualParticleManager particles,
            Vec3 center
    ) {
        if (particles == null || center == null) {
            return 0;
        }

        this.level.setSkyesightViewId(viewId);
        tickWatchedParticleBlock(viewId, particles);
        long now = System.currentTimeMillis();
        if (now - this.lastCameraVisualParticleTickMillis < 45L) {
            return 0;
        }

        this.lastCameraVisualParticleTickMillis = now;
        int centerX = Mth.floor(center.x());
        int centerY = Mth.floor(center.y());
        int centerZ = Mth.floor(center.z());

        particles.beginCaptureAudit();
        this.level.setSkyesightParticleCaptureSource("visual-animateTick");
        VisualParticleTickAudit audit;
        try {
            this.level.animateTick(centerX, centerY, centerZ);
            audit = runGenericVisualDisplayTicksAround(centerX, centerY, centerZ);
        } finally {
            this.level.setSkyesightParticleCaptureSource("visual-addParticle");
        }

        int captured = particles.captureAuditCount();
        logVisualAnimateTickSampleIfDue(viewId, particles, centerX, centerY, centerZ, captured, audit);
        return captured;
    }

    private void tickWatchedParticleBlock(ResourceLocation viewId, SkyesightVisualParticleManager particles) {
        if (!SkyesightVisualParticleWatch.matches(viewId) || particles == null) {
            return;
        }

        BlockPos pos = SkyesightVisualParticleWatch.pos();
        if (pos == null) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        boolean chunkLoaded = hasChunk(chunkPos.x, chunkPos.z)
                && this.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false) != null;
        BlockState state = this.level.getBlockState(pos);
        FluidState fluidState = this.level.getFluidState(pos);
        BlockEntity blockEntity = this.level.getBlockEntity(pos);

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_BLOCK_STATE: viewId={} targetDim={} pos={},{},{} visualLevel={} chunkLoaded={} block={} fluid={} blockEntity={} isInWatchedRegion={}",
                viewId,
                this.level.dimension().location(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                this.level.getClass().getName(),
                yesNo(chunkLoaded),
                blockId(state),
                fluidState.isEmpty() ? "-" : fluidId(fluidState),
                blockEntity == null ? "-" : blockEntity.getType().toString(),
                yesNo(hasChunk(chunkPos.x, chunkPos.z))
        );

        SkyesightVisualParticleWatch.recordWatchedState(viewId, this.level, chunkLoaded, state, fluidState, hasChunk(chunkPos.x, chunkPos.z));
    }

    private VisualParticleTickAudit runGenericVisualDisplayTicksAround(int centerX, int centerY, int centerZ) {
        VisualParticleTickAudit audit = new VisualParticleTickAudit();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < CAMERA_GENERIC_DISPLAY_TICK_SAMPLES; i++) {
            int x = centerX + this.visualParticleRandom.nextInt(CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS * 2 + 1)
                    - CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS;
            int y = centerY + this.visualParticleRandom.nextInt(CAMERA_GENERIC_DISPLAY_TICK_VERTICAL_RADIUS * 2 + 1)
                    - CAMERA_GENERIC_DISPLAY_TICK_VERTICAL_RADIUS;
            int z = centerZ + this.visualParticleRandom.nextInt(CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS * 2 + 1)
                    - CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS;

            if (y < this.level.getMinBuildHeight() || y >= this.level.getMaxBuildHeight()) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(mutablePos.set(x, y, z));
            if (!hasChunk(chunkPos.x, chunkPos.z)) {
                continue;
            }

            audit.sampledBlocksTotal++;
            BlockState state = this.level.getBlockState(mutablePos);
            FluidState fluidState = this.level.getFluidState(mutablePos);
            if (!state.isAir()) {
                audit.countSampledBlock(state);
            }
            if (!fluidState.isEmpty()) {
                audit.countSampledFluid(fluidState);
            }

            state.getBlock().animateTick(state, this.level, mutablePos, this.visualParticleRandom);
            audit.blockAnimateTickCalls++;
            if (!fluidState.isEmpty()) {
                fluidState.animateTick(this.level, mutablePos, this.visualParticleRandom);
                audit.fluidAnimateTickCalls++;
            }
        }

        audit.sampleRadius = CAMERA_GENERIC_DISPLAY_TICK_HORIZONTAL_RADIUS + "x" + CAMERA_GENERIC_DISPLAY_TICK_VERTICAL_RADIUS;
        return audit;
    }

    private int sampleVisualParticleChunk(ChunkPos chunkPos, LevelChunk chunk) {
        int sampled = 0;
        int samplesPerChunk = chunkPos.x == this.viewCenterChunkX && chunkPos.z == this.viewCenterChunkZ ? 96 : 48;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        this.level.setSkyesightParticleCaptureSource("visual-animateTick");
        try {
            for (int i = 0; i < samplesPerChunk; i++) {
                int x = chunkPos.getMinBlockX() + this.visualParticleRandom.nextInt(16);
                int z = chunkPos.getMinBlockZ() + this.visualParticleRandom.nextInt(16);
                int surfaceY = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int minY = Math.max(this.level.getMinBuildHeight(), surfaceY - 24);
                int maxY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 8);

                if (maxY < minY) {
                    minY = this.level.getMinBuildHeight();
                    maxY = this.level.getMaxBuildHeight() - 1;
                }

                int y = minY + this.visualParticleRandom.nextInt(Math.max(1, maxY - minY + 1));
                mutablePos.set(x, y, z);
                BlockState state = chunk.getBlockState(mutablePos);
                FluidState fluidState = chunk.getFluidState(mutablePos);

                state.getBlock().animateTick(state, this.level, mutablePos, this.visualParticleRandom);
                if (!fluidState.isEmpty()) {
                    fluidState.animateTick(this.level, mutablePos, this.visualParticleRandom);
                }
                sampled++;
            }
        } finally {
            this.level.setSkyesightParticleCaptureSource("visual-addParticle");
        }

        return sampled;
    }

    private static String blockId(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockId == null ? "unknown" : blockId.toString();
    }

    private static String fluidId(FluidState state) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(state.getType());
        return fluidId == null ? "unknown" : fluidId.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private void logVisualAnimateTickSampleIfDue(
            ResourceLocation viewId,
            SkyesightVisualParticleManager particles,
            int centerX,
            int centerY,
            int centerZ,
            int captured,
            VisualParticleTickAudit audit
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastCameraVisualParticleSourceLogMillis < 3000L) {
            return;
        }

        this.lastCameraVisualParticleSourceLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_DISPLAY_TICK: viewId={} targetDim={} method={} cameraPos={},{},{} positionsSampled={} blockAnimateTickCalls={} fluidAnimateTickCalls={} particleAddsCaptured={} types={}",
                viewId == null ? "-" : viewId,
                this.level.dimension().location(),
                "generic-vanilla-copy",
                centerX,
                centerY,
                centerZ,
                VANILLA_CAMERA_ANIMATE_TICK_SAMPLES + audit.sampledBlocksTotal,
                audit.blockAnimateTickCalls,
                audit.fluidAnimateTickCalls,
                captured,
                particles.captureAuditTypeSummary()
        );
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_PARTICLE_TICK_AUDIT: viewId={} targetDim={} sampleCenter={},{},{} sampleRadius={} loadedChunks={} sampledBlocksTotal={} blockAnimateTickCalls={} fluidAnimateTickCalls={} addParticleCallsCaptured={} sampledBlockTypes={} sampledFluidTypes={} capturedTypes={} reasonIfOnlyCampfire={}",
                viewId == null ? "-" : viewId,
                this.level.dimension().location(),
                centerX,
                centerY,
                centerZ,
                audit.sampleRadius,
                this.loadedChunks.size(),
                VANILLA_CAMERA_ANIMATE_TICK_SAMPLES + audit.sampledBlocksTotal,
                "vanilla ClientLevel.animateTick+" + audit.blockAnimateTickCalls,
                audit.fluidAnimateTickCalls,
                captured,
                audit.sampledBlockSummary(),
                audit.sampledFluidSummary(),
                particles.captureAuditTypeSummary(),
                captured > 0 && particles.captureAuditTypeSummary().contains("campfire") && !particles.captureAuditTypeSummary().contains(",")
                        ? "only campfire-type particles captured by generic display ticks"
                        : "-"
        );
    }

    private void logVisualParticleTickIfDue(
            ResourceLocation viewId,
            SkyesightVisualParticleManager particles,
            int chunksSampled,
            int blocksSampled,
            int captured
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastVisualParticleSourceLogMillis < 3000L) {
            return;
        }

        this.lastVisualParticleSourceLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_PARTICLE_TICK: viewId={} targetDim={} chunksSampled={} blocksSampled={} particleAddsCaptured={} reasonIfNone={}",
                viewId == null ? "-" : viewId,
                this.level.dimension().location(),
                chunksSampled,
                blocksSampled,
                captured,
                captured == 0 ? "no sampled block emitted particles" : "-"
        );
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_PARTICLE_SOURCE: viewId={} targetDim={} source={} captured={} types={} providerMissing={} rejectedOutOfRegion={}",
                viewId == null ? "-" : viewId,
                this.level.dimension().location(),
                particles.lastSource(),
                particles.lastSourceCaptured(),
                particles.debugTypeSummary(),
                "unknown-until-render",
                0
        );
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_PARTICLE_TYPES: viewId={} types={}",
                viewId == null ? "-" : viewId,
                particles.debugTypeSummary()
        );
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

    private static final class VisualParticleTickAudit {
        private String sampleRadius = "-";
        private int sampledBlocksTotal;
        private int blockAnimateTickCalls;
        private int fluidAnimateTickCalls;
        private final Map<String, Integer> sampledBlocks = new LinkedHashMap<>();
        private final Map<String, Integer> sampledFluids = new LinkedHashMap<>();

        private void countSampledBlock(BlockState state) {
            this.sampledBlocks.merge(blockId(state), 1, Integer::sum);
        }

        private void countSampledFluid(FluidState state) {
            this.sampledFluids.merge(fluidId(state), 1, Integer::sum);
        }

        private String sampledBlockSummary() {
            return mapSummary(this.sampledBlocks);
        }

        private String sampledFluidSummary() {
            return mapSummary(this.sampledFluids);
        }

        private static String mapSummary(Map<String, Integer> values) {
            if (values.isEmpty()) {
                return "-";
            }

            StringBuilder summary = new StringBuilder();
            int emitted = 0;
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                if (emitted++ >= 12) {
                    summary.append(",...");
                    break;
                }
                if (!summary.isEmpty()) {
                    summary.append(',');
                }
                summary.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return summary.toString();
        }
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
