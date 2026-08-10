package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.client.compat.sodium.SkyesightSodiumCompat;
import com.skyeshade.skyesight.client.render.SkyesightVisualFeatureRenderer;
import com.skyeshade.skyesight.client.render.vanilla.SkyesightVisualVanillaTerrain;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

public final class SkyesightVisualWorld implements AutoCloseable {
    private final ResourceKey<Level> dimension;
    private final SkyesightVisualClientLevel level;
    private boolean closed;
    private final SkyesightVisualTerrainBackend terrainBackend;
    private final SkyesightRemoteChunkReceiver chunkReceiver;
    private final SkyesightVisualEntityStore entityStore;
    private final SkyesightVisualParticleManager particles;
    public static SkyesightVisualWorld create(ResourceKey<Level> dimension) {
        SkyesightVisualClientLevel level = SkyesightClientLevelFactory.create(dimension);
        return new SkyesightVisualWorld(dimension, level);
    }
    public SkyesightVisualWorld(
            ResourceKey<Level> dimension,
            SkyesightVisualClientLevel level
    ) {
        this.dimension = dimension;
        this.level = level;
        this.terrainBackend = SkyesightSodiumCompat.isLoaded()
                ? SodiumVisualMethods.create(level)
                : SkyesightVisualVanillaTerrain.create(level);
        this.chunkReceiver = new SkyesightRemoteChunkReceiver(
                level,
                new SkyesightRemoteChunkReceiver.ChunkStatusListener() {
                    @Override
                    public void onChunkStatusAdded(int chunkX, int chunkZ) {
                        SkyesightVisualWorld.this.onChunkStatusAdded(chunkX, chunkZ);
                    }

                    @Override
                    public void onChunkStatusRemoved(int chunkX, int chunkZ) {
                        SkyesightVisualWorld.this.onChunkStatusRemoved(chunkX, chunkZ);
                    }
                }
        );
        this.entityStore = new SkyesightVisualEntityStore(level);
        this.particles = new SkyesightVisualParticleManager(dimension);
        this.level.setSkyesightParticleManager(this.particles);

    }

    public SkyesightVisualParticleManager particles() {
        return this.particles;
    }
    public SkyesightRemoteChunkReceiver chunkReceiver() {
        return this.chunkReceiver;
    }
    public SkyesightVisualEntityStore entityStore() {
        return this.entityStore;
    }
    public TickStats tick(ResourceLocation viewId) {
        SkyesightRemoteChunkReceiver.TickStats blockEntityTicks = this.chunkReceiver.tickBlockEntities(viewId);
        SkyesightVisualEntityStore.TickStats entityTicks = this.entityStore.tickVisualEntities(
                viewId == null ? "-" : viewId.toString(),
                this.dimension.location().toString()
        );
        int visualParticlesSpawned = this.chunkReceiver.tickVisualParticles(viewId, this.particles);
        this.particles.tick();
        return new TickStats(
                entityTicks.ticked(),
                entityTicks.skipped(),
                blockEntityTicks.ticked(),
                blockEntityTicks.skipped(),
                visualParticlesSpawned,
                entityTicks.skippedReason(),
                blockEntityTicks.skippedReason()
        );
    }

    public void renderParticles(
            Camera camera,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        // Cross-dimension particles are rendered by SecondaryParticlePass so
        // they can share the direct portal stencil/depth slot with terrain and entities.
    }
    public void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            int chunkRadius
    ) {
        renderTerrain(camera, frustum, modelMatrix, projectionMatrix, chunkRadius, true);
    }

    public void renderTerrain(
            Camera camera,
            Frustum frustum,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            int chunkRadius,
            boolean renderTranslucent
    ) {
        if (this.terrainBackend == null) {
            return;
        }
        this.terrainBackend.renderTerrain(
                camera,
                frustum,
                modelMatrix,
                projectionMatrix,
                chunkRadius,
                renderTranslucent
        );
    }
    public void renderBlockEntities(
            Camera camera,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        renderBlockEntities(null, camera, modelMatrix, projectionMatrix, partialTick);
    }

    public void renderBlockEntities(
            ResourceLocation viewId,
            Camera camera,
            Matrix4f modelMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        SkyesightVisualFeatureRenderer.renderBlockEntities(
                this.level,
                viewId,
                this.chunkReceiver,
                camera,
                modelMatrix,
                projectionMatrix,
                partialTick
        );
    }
    public void renderEntities(
            Camera camera,
            Matrix4f modelMatrix,
            float partialTick
    ) {
        renderEntities(this.entityStore.entities(), camera, modelMatrix, partialTick);
    }

    public void renderEntities(
            Iterable<SkyesightVisualEntity> entities,
            Camera camera,
            Matrix4f modelMatrix,
            float partialTick
    ) {
        SkyesightVisualFeatureRenderer.renderEntities(
                this.level,
                entities,
                camera,
                modelMatrix,
                partialTick
        );
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public ClientLevel level() {
        return level;
    }

    public void scheduleTerrainUpdate() {
        if (this.terrainBackend != null) {
            this.terrainBackend.scheduleTerrainUpdate();
        }
    }

    public void scheduleBlockUpdate(BlockPos pos) {
        if (this.terrainBackend != null) {
            this.terrainBackend.scheduleBlockUpdate(pos);
        }
    }

    public void scheduleChunkRebuild(int chunkX, int chunkZ, boolean important) {
        if (this.terrainBackend != null) {
            this.terrainBackend.scheduleChunkRebuild(chunkX, chunkZ, important);
        }
    }

    public int visibleChunkCount() {
        if (this.terrainBackend == null) {
            return 0;
        }
        return this.terrainBackend.visibleChunkCount();
    }
    public boolean isClosed() {
        return this.closed;
    }

    public record TickStats(
            int visualEntitiesClientTicked,
            int visualEntitiesSkipped,
            int visualBlockEntitiesClientTicked,
            int visualBlockEntitiesSkipped,
            int visualParticlesSpawned,
            String entitySkippedReason,
            String blockEntitySkippedReason
    ) {
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;

        this.chunkReceiver.clear();
        this.entityStore.clear();
        this.particles.close();
        if (this.terrainBackend != null) {
            this.terrainBackend.close();
        }
    }

    private void onChunkStatusAdded(int chunkX, int chunkZ) {
        if (this.terrainBackend != null) {
            this.terrainBackend.onChunkStatusAdded(chunkX, chunkZ);
        }
    }

    private void onChunkStatusRemoved(int chunkX, int chunkZ) {
        if (this.terrainBackend != null) {
            this.terrainBackend.onChunkStatusRemoved(chunkX, chunkZ);
        }
    }

    private static final class SodiumVisualMethods {
        private SodiumVisualMethods() {}

        private static SkyesightVisualTerrainBackend create(SkyesightVisualClientLevel level) {
            return (SkyesightVisualTerrainBackend) com.skyeshade.skyesight.client.render.sodium.SkyesightVisualSodiumTerrain.create(level);
        }
    }
}
