package com.skyeshade.skyesight.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

final class VanillaSecondaryViewState implements AutoCloseable {
    private static final int SECONDARY_RENDER_BUFFER_PACKS = 1;

    private final RenderBuffers renderBuffers;
    private final LevelRenderer levelRenderer;
    private ClientLevel boundLevel;
    private ChunkPos lastBootstrapCenter;
    private int lastBootstrapRadius = -1;

    private VanillaSecondaryViewState(Minecraft minecraft) {
        this.renderBuffers = new RenderBuffers(SECONDARY_RENDER_BUFFER_PACKS);
        this.levelRenderer = new LevelRenderer(
                minecraft,
                minecraft.getEntityRenderDispatcher(),
                minecraft.getBlockEntityRenderDispatcher(),
                this.renderBuffers
        );
    }

    static VanillaSecondaryViewState getOrCreate(SecondaryViewContext context, Minecraft minecraft) {
        Object state = context.vanillaState();
        if (state instanceof VanillaSecondaryViewState vanillaState) {
            return vanillaState;
        }

        VanillaSecondaryViewState vanillaState = new VanillaSecondaryViewState(minecraft);
        context.setVanillaState(vanillaState);
        return vanillaState;
    }

    static void close(SecondaryViewContext context) {
        Object state = context == null ? null : context.vanillaState();
        if (state instanceof VanillaSecondaryViewState vanillaState) {
            vanillaState.close();
        }
        if (context != null) {
            context.setVanillaState(null);
        }
    }

    LevelRenderer rendererFor(ClientLevel level) {
        if (this.boundLevel != level) {
            if (this.boundLevel != null) {
                this.levelRenderer.setLevel(null);
            }
            this.boundLevel = level;
            this.clearBootstrapState();
            if (level != null) {
                this.levelRenderer.setLevel(level);
            }
        }

        return this.levelRenderer;
    }

    BootstrapResult bootstrapLoadedChunks(
            ClientLevel level,
            Vec3 cameraPosition,
            int radius
    ) {
        if (level == null || cameraPosition == null) {
            return BootstrapResult.skipped();
        }

        int clampedRadius = Math.max(0, radius);
        ChunkPos center = new ChunkPos(
                ((int) Math.floor(cameraPosition.x())) >> 4,
                ((int) Math.floor(cameraPosition.z())) >> 4
        );
        if (center.equals(this.lastBootstrapCenter) && clampedRadius == this.lastBootstrapRadius) {
            return BootstrapResult.skipped();
        }

        int loadedChunksFound = 0;
        int chunksNotified = 0;
        int sectionsDirtied = 0;
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();

        for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
            for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                loadedChunksFound++;
                this.levelRenderer.onChunkLoaded(new ChunkPos(chunkX, chunkZ));
                chunksNotified++;
                for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                    this.levelRenderer.setSectionDirty(chunkX, sectionY, chunkZ);
                    sectionsDirtied++;
                }
            }
        }

        this.levelRenderer.needsUpdate();
        this.lastBootstrapCenter = center;
        this.lastBootstrapRadius = clampedRadius;
        return new BootstrapResult(true, center, clampedRadius, loadedChunksFound, chunksNotified, sectionsDirtied);
    }

    void invalidateBootstrap() {
        this.clearBootstrapState();
    }

    ClientLevel boundLevel() {
        return this.boundLevel;
    }

    @Override
    public void close() {
        this.levelRenderer.setLevel(null);
        this.levelRenderer.close();
        this.boundLevel = null;
        this.clearBootstrapState();
    }

    private void clearBootstrapState() {
        this.lastBootstrapCenter = null;
        this.lastBootstrapRadius = -1;
    }

    record BootstrapResult(
            boolean ran,
            ChunkPos center,
            int radius,
            int loadedChunksFound,
            int chunksNotified,
            int sectionsDirtied
    ) {
        private static BootstrapResult skipped() {
            return new BootstrapResult(false, new ChunkPos(0, 0), 0, 0, 0, 0);
        }
    }
}
