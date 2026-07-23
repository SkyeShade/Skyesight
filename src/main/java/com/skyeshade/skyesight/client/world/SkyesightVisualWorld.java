package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
    private final ChunkTracker chunkTracker;
    private boolean closed;
    private final SkyesightSodiumWorldRenderer renderer;
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
        this.chunkTracker = new ChunkTracker();
        this.chunkReceiver = new SkyesightRemoteChunkReceiver(level, this.chunkTracker);
        this.entityStore = new SkyesightVisualEntityStore(level);
        this.particles = new SkyesightVisualParticleManager(dimension);
        this.level.setSkyesightParticleManager(this.particles);
        this.renderer = new SkyesightSodiumWorldRenderer(Minecraft.getInstance(), this.chunkTracker);
        this.renderer.setLevel(level);

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
    public SkyesightSodiumWorldRenderer renderer() {
        return this.renderer;
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
        this.renderer.renderTerrain(
                camera,
                frustum,
                modelMatrix,
                projectionMatrix,
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
        this.renderer.renderBlockEntitiesManual(
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
        this.renderer.renderEntities(
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

    public ChunkTracker chunkTracker() {
        return chunkTracker;
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
        this.renderer.close();
    }
}
