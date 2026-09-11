package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.mixin.client.LevelRendererAccessor;
import com.skyeshade.skyesight.mixin.client.LevelRendererCloudInvoker;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Experimental isolation of the vanilla cloud cache for secondary views.
 *
 * <p>The actual cloud render remains {@link LevelRenderer#renderClouds}; only
 * the persistent cache fields normally owned by the physical renderer are
 * exchanged around the call.</p>
 */
public final class SkyesightIsolatedCloudRenderer implements AutoCloseable {
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 2_000_000_000L;

    private final Map<String, CloudState> states = new HashMap<>();

    public RenderResult render(
            String cacheKey,
            LevelRenderer levelRenderer,
            PoseStack poseStack,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            int renderFrame,
            int expectedTarget
    ) {
        CloudState portalState = this.states.computeIfAbsent(cacheKey, ignored -> new CloudState());
        LevelRendererAccessor accessor = (LevelRendererAccessor) levelRenderer;
        CloudState physicalState = CloudState.capture(accessor);
        VertexBuffer bufferBefore = portalState.cloudBuffer;

        try (CloudVertexNdcDiagnostics.Scope ignored = CloudVertexNdcDiagnostics.portalCall(
                cacheKey,
                renderFrame,
                expectedTarget,
                levelRenderer
        )) {
            portalState.install(accessor);
            ((LevelRendererCloudInvoker) levelRenderer).skyesight$renderClouds(
                    poseStack,
                    frustumMatrix,
                    projectionMatrix,
                    partialTick,
                    camX,
                    camY,
                    camZ
            );
        } finally {
            portalState.captureFrom(accessor);
            physicalState.install(accessor);
        }

        boolean regenerated = bufferBefore != portalState.cloudBuffer;
        int ticks = levelRenderer.getTicks();
        double cloudX = (camX + ((ticks + partialTick) * 0.03F)) / 12.0D;
        double cloudZ = camZ / 12.0D + 0.33D;
        cloudX -= Mth.floor(cloudX / 2048.0D) * 2048.0D;
        cloudZ -= Mth.floor(cloudZ / 2048.0D) * 2048.0D;
        int cellX = Mth.floor(cloudX);
        int cellZ = Mth.floor(cloudZ);
        logDiagnostic(cacheKey, portalState, cellX, cellZ, regenerated);
        return new RenderResult(ticks, cellX, cellZ, regenerated);
    }

    public void remove(String cacheKey) {
        CloudState state = this.states.remove(cacheKey);
        if (state != null) {
            state.close();
        }
        CloudVertexNdcDiagnostics.remove(cacheKey);
    }

    @Override
    public void close() {
        for (CloudState state : this.states.values()) {
            state.close();
        }
        this.states.clear();
        CloudVertexNdcDiagnostics.clear();
    }

    private static void logDiagnostic(
            String cacheKey,
            CloudState state,
            int cellX,
            int cellZ,
            boolean regenerated
    ) {
        if (!SkyesightDebugConfig.SKY_CAPTURE_AUDIT) {
            return;
        }
        long now = System.nanoTime();
        if (now - state.lastDiagnosticNanos < DIAGNOSTIC_INTERVAL_NANOS) {
            return;
        }
        state.lastDiagnosticNanos = now;
        Skyesight.LOGGER.info(
                "[Skyesight] CLOUD_RENDER_SOURCE view={} backend=isolated-vanilla cache={}:{} cell={},{} regenerated={}",
                cacheKey,
                cacheKey,
                Integer.toHexString(System.identityHashCode(state)),
                cellX,
                cellZ,
                regenerated
        );
    }

    public record RenderResult(int ticks, int cellX, int cellZ, boolean regenerated) {}

    private static final class CloudState implements AutoCloseable {
        private boolean generateClouds = true;
        private VertexBuffer cloudBuffer;
        private int prevCloudX = Integer.MIN_VALUE;
        private int prevCloudY = Integer.MIN_VALUE;
        private int prevCloudZ = Integer.MIN_VALUE;
        private Vec3 prevCloudColor = Vec3.ZERO;
        private CloudStatus prevCloudsType;
        private long lastDiagnosticNanos;

        private static CloudState capture(LevelRendererAccessor accessor) {
            CloudState state = new CloudState();
            state.captureFrom(accessor);
            return state;
        }

        private void captureFrom(LevelRendererAccessor accessor) {
            this.generateClouds = accessor.skyesight$getGenerateClouds();
            this.cloudBuffer = accessor.skyesight$getCloudBuffer();
            this.prevCloudX = accessor.skyesight$getPrevCloudX();
            this.prevCloudY = accessor.skyesight$getPrevCloudY();
            this.prevCloudZ = accessor.skyesight$getPrevCloudZ();
            this.prevCloudColor = accessor.skyesight$getPrevCloudColor();
            this.prevCloudsType = accessor.skyesight$getPrevCloudsType();
        }

        private void install(LevelRendererAccessor accessor) {
            accessor.skyesight$setGenerateClouds(this.generateClouds);
            accessor.skyesight$setCloudBuffer(this.cloudBuffer);
            accessor.skyesight$setPrevCloudX(this.prevCloudX);
            accessor.skyesight$setPrevCloudY(this.prevCloudY);
            accessor.skyesight$setPrevCloudZ(this.prevCloudZ);
            accessor.skyesight$setPrevCloudColor(this.prevCloudColor);
            accessor.skyesight$setPrevCloudsType(this.prevCloudsType);
        }

        @Override
        public void close() {
            if (this.cloudBuffer != null) {
                this.cloudBuffer.close();
                this.cloudBuffer = null;
            }
        }
    }
}
