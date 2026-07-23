package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.client.render.SameDimPortalChunkRenderPolicy;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class SameLevelSkyesightChunkSource implements SkyesightClientChunkSource {
    private final LongSet trackedChunks = new LongOpenHashSet();
    private long trackedChunkSignature;
    private int lastReadyChunkCount;
    private boolean lastCenterChunkReady;
    private int lastPortalOwnedChunksRendered;
    private int lastPlayerLoadedChunksReused;
    private int lastMissingChunksSkipped;
    private int lastCandidateChunkCount;
    private int lastAddedChunkCount;
    private int lastRemovedChunkCount;
    private int lastBudgetSkippedChunkCount;
    private int lastScannedChunkCount;
    private boolean lastScanCompletedCycle;
    private int scanCursor;
    private boolean firstPopulation = true;
    private boolean lastUpdateWasFirstPopulation;

    @Override
    public void updateReadyChunks(
            ClientLevel level,
            ChunkTracker tracker,
            Vec3 cameraPosition,
            int radius
    ) {
        updateReadyChunks(level, tracker, cameraPosition, radius, radius, false);
    }

    public void updateReadyChunks(
            ClientLevel level,
            ChunkTracker tracker,
            Vec3 cameraPosition,
            int portalOwnedRenderRadiusChunks,
            int sameDimPlayerLoadedReuseRadiusChunks,
            boolean reusePlayerLoadedChunksForSameDim
    ) {
        updateReadyChunks(
                level,
                tracker,
                cameraPosition,
                portalOwnedRenderRadiusChunks,
                sameDimPlayerLoadedReuseRadiusChunks,
                reusePlayerLoadedChunksForSameDim,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
    }

    public void updateReadyChunks(
            ClientLevel level,
            ChunkTracker tracker,
            Vec3 cameraPosition,
            int portalOwnedRenderRadiusChunks,
            int sameDimPlayerLoadedReuseRadiusChunks,
            boolean reusePlayerLoadedChunksForSameDim,
            int maxReadyChunksAddedPerFrame,
            int maxChunkCandidatesScannedPerFrame
    ) {
        int centerChunkX = ((int) Math.floor(cameraPosition.x())) >> 4;
        int centerChunkZ = ((int) Math.floor(cameraPosition.z())) >> 4;
        int ownedRadius = Math.max(0, portalOwnedRenderRadiusChunks);
        int reuseRadius = reusePlayerLoadedChunksForSameDim
                ? Math.max(ownedRadius, sameDimPlayerLoadedReuseRadiusChunks)
                : ownedRadius;

        LongSet wanted = new LongOpenHashSet();
        long centerPacked = ChunkPos.asLong(centerChunkX, centerChunkZ);
        int owned = 0;
        int reused = 0;
        int missing = 0;
        int candidates = 0;
        int scanned = 0;
        int maxScanned = Math.max(0, maxChunkCandidatesScannedPerFrame);
        int side = reuseRadius * 2 + 3;
        int totalCandidateChunks = side * side;
        int start = totalCandidateChunks <= 0 ? 0 : Math.floorMod(this.scanCursor, totalCandidateChunks);

        for (int offset = 0; offset < totalCandidateChunks && scanned < maxScanned; offset++) {
            int index = (start + offset) % totalCandidateChunks;
            int dx = (index % side) - reuseRadius - 1;
            int dz = (index / side) - reuseRadius - 1;
            scanned++;
            candidates++;
            int chunkX = centerChunkX + dx;
            int chunkZ = centerChunkZ + dz;
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);

            if (SameDimPortalChunkRenderPolicy.shouldRenderChunkForSameDimPortal(
                    level,
                    chunk,
                    cameraPosition,
                    ownedRadius,
                    sameDimPlayerLoadedReuseRadiusChunks,
                    reusePlayerLoadedChunksForSameDim
            )) {
                wanted.add(ChunkPos.asLong(chunkX, chunkZ));
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= ownedRadius) {
                    owned++;
                } else {
                    reused++;
                }
            } else if (Math.max(Math.abs(dx), Math.abs(dz)) <= reuseRadius
                    && !SameDimPortalChunkRenderPolicy.isLoaded(level, chunk)) {
                missing++;
            }
        }
        if (totalCandidateChunks > 0) {
            this.scanCursor = (start + scanned) % totalCandidateChunks;
        }
        this.lastScanCompletedCycle = totalCandidateChunks <= 0
                || scanned >= totalCandidateChunks
                || start + scanned >= totalCandidateChunks;

        this.lastReadyChunkCount = wanted.size();
        this.lastCenterChunkReady = wanted.contains(centerPacked);
        this.lastPortalOwnedChunksRendered = owned;
        this.lastPlayerLoadedChunksReused = reused;
        this.lastMissingChunksSkipped = missing;
        this.lastCandidateChunkCount = candidates;
        this.lastScannedChunkCount = scanned;
        this.lastAddedChunkCount = 0;
        this.lastRemovedChunkCount = 0;
        this.lastBudgetSkippedChunkCount = 0;
        this.lastUpdateWasFirstPopulation = this.firstPopulation;
        int maxAdds = Math.max(0, maxReadyChunksAddedPerFrame);

        for (long packed : wanted) {
            if (this.trackedChunks.contains(packed)) {
                continue;
            }
            if (this.lastAddedChunkCount >= maxAdds) {
                this.lastBudgetSkippedChunkCount++;
                continue;
            }
            if (this.trackedChunks.add(packed)) {
                this.lastAddedChunkCount++;
                tracker.onChunkStatusAdded(
                        ChunkPos.getX(packed),
                        ChunkPos.getZ(packed),
                        3
                );
            }
        }

        if (scanned >= totalCandidateChunks) {
            LongSet removed = new LongOpenHashSet();

            for (long packed : this.trackedChunks) {
                if (!wanted.contains(packed)) {
                    removed.add(packed);
                }
            }

            for (long packed : removed) {
                this.trackedChunks.remove(packed);
                this.lastRemovedChunkCount++;

                tracker.onChunkStatusRemoved(
                        ChunkPos.getX(packed),
                        ChunkPos.getZ(packed),
                        3
                );
            }
        }
        this.lastReadyChunkCount = this.trackedChunks.size();
        recomputeTrackedChunkSignature();
        this.firstPopulation = false;
    }

    public boolean isTracked(ChunkPos chunk) {
        return chunk != null && this.trackedChunks.contains(ChunkPos.asLong(chunk.x, chunk.z));
    }

    public boolean isTracked(long packedChunk) {
        return this.trackedChunks.contains(packedChunk);
    }

    public boolean primeTrackedChunk(ChunkTracker tracker, ChunkPos chunk) {
        if (tracker == null || chunk == null) {
            return false;
        }

        long packed = ChunkPos.asLong(chunk.x, chunk.z);
        if (!this.trackedChunks.add(packed)) {
            return false;
        }

        tracker.onChunkStatusAdded(chunk.x, chunk.z, 3);
        this.lastAddedChunkCount++;
        this.lastReadyChunkCount = this.trackedChunks.size();
        recomputeTrackedChunkSignature();
        return true;
    }

    public int trackedChunkCount() {
        return this.trackedChunks.size();
    }

    public long trackedChunkSignature() {
        return this.trackedChunkSignature;
    }

    public int lastReadyChunkCount() {
        return this.lastReadyChunkCount;
    }

    public boolean lastCenterChunkReady() {
        return this.lastCenterChunkReady;
    }

    public int lastPortalOwnedChunksRendered() {
        return this.lastPortalOwnedChunksRendered;
    }

    public int lastPlayerLoadedChunksReused() {
        return this.lastPlayerLoadedChunksReused;
    }

    public int lastMissingChunksSkipped() {
        return this.lastMissingChunksSkipped;
    }

    public int lastCandidateChunkCount() {
        return this.lastCandidateChunkCount;
    }

    public int lastAddedChunkCount() {
        return this.lastAddedChunkCount;
    }

    public int lastRemovedChunkCount() {
        return this.lastRemovedChunkCount;
    }

    public int lastBudgetSkippedChunkCount() {
        return this.lastBudgetSkippedChunkCount;
    }

    public int lastScannedChunkCount() {
        return this.lastScannedChunkCount;
    }

    public boolean lastScanCompletedCycle() {
        return this.lastScanCompletedCycle;
    }

    public boolean lastUpdateWasFirstPopulation() {
        return this.lastUpdateWasFirstPopulation;
    }

    private void recomputeTrackedChunkSignature() {
        long signature = 0L;
        for (long packed : this.trackedChunks) {
            signature += mixChunk(packed);
        }
        this.trackedChunkSignature = signature;
    }

    private static long mixChunk(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
