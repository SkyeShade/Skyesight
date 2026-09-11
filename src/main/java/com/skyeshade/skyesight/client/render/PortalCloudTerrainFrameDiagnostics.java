package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Temporary render-frame correlation diagnostics for portal clouds and terrain. */
public final class PortalCloudTerrainFrameDiagnostics {
    private static final int MAX_VIEWS_PER_FRAME = 64;
    private static final long LOG_INTERVAL_NANOS = 500_000_000L;
    private static final Map<ResourceLocation, CloudFrame> CLOUD_FRAMES = new LinkedHashMap<>();
    private static int retainedRenderTick = Integer.MIN_VALUE;
    private static long lastLogNanos;

    private PortalCloudTerrainFrameDiagnostics() {}

    public static void recordCloudFrame(
            ResourceLocation viewId,
            int renderTick,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            Matrix4f viewMatrix,
            Matrix4f projectionMatrix,
            int cloudTicks,
            Vec3 cloudCamArgs,
            int captureTargetId,
            int captureFrameId
    ) {
        if (!enabled() || viewId == null) {
            return;
        }
        advanceFrame(renderTick);
        if (!CLOUD_FRAMES.containsKey(viewId) && CLOUD_FRAMES.size() >= MAX_VIEWS_PER_FRAME) {
            ResourceLocation eldest = CLOUD_FRAMES.keySet().iterator().next();
            CLOUD_FRAMES.remove(eldest);
        }
        CLOUD_FRAMES.put(viewId, new CloudFrame(
                renderTick,
                copy(cameraPosition),
                new Quaternionf(cameraRotation),
                new Matrix4f(viewMatrix),
                new Matrix4f(projectionMatrix),
                cloudTicks,
                copy(cloudCamArgs),
                captureTargetId,
                captureFrameId
        ));
    }

    public static void compareTerrainFrame(
            ResourceLocation viewId,
            int renderTick,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            Matrix4f viewMatrix,
            Matrix4f projectionMatrix
    ) {
        if (!enabled() || viewId == null) {
            return;
        }
        advanceFrame(renderTick);
        CloudFrame cloud = CLOUD_FRAMES.remove(viewId);
        if (cloud == null) {
            logMissing(viewId, renderTick, "missing-cloud-capture", "-");
            return;
        }
        if (cloud.renderTick() != renderTick || cloud.captureFrameId() != renderTick) {
            logMissing(
                    viewId,
                    renderTick,
                    "capture-frame-mismatch",
                    "cloudFrame=" + cloud.renderTick() + ",captureFrame=" + cloud.captureFrameId()
            );
            return;
        }

        Vec3 positionDelta = cameraPosition.subtract(cloud.cameraPosition());
        double positionMaxDelta = maxAbs(positionDelta);
        double quaternionAngleDeltaDegrees = quaternionAngleDeltaDegrees(cloud.cameraRotation(), cameraRotation);
        float viewMatrixMaxDelta = matrixMaxDelta(cloud.viewMatrix(), viewMatrix);
        float projectionMatrixMaxDelta = matrixMaxDelta(cloud.projectionMatrix(), projectionMatrix);
        Vec3 cloudArgDelta = cloud.cloudCamArgs().subtract(cloud.cameraPosition());
        double cloudArgMaxDelta = maxAbs(cloudArgDelta);

        if (!shouldLog()) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] CLOUD_TERRAIN_FRAME view={} frame={} posDelta={} positionMaxDelta={} quatDeltaDeg={} viewMatrixDelta={} projectionDelta={} cloudArgDelta={} cloudArgMaxDelta={} cloudTicks={} captureTarget={} captureFrame={}",
                viewId,
                renderTick,
                format(positionDelta),
                format(positionMaxDelta),
                format(quaternionAngleDeltaDegrees),
                format(viewMatrixMaxDelta),
                format(projectionMatrixMaxDelta),
                format(cloudArgDelta),
                format(cloudArgMaxDelta),
                cloud.cloudTicks(),
                cloud.captureTargetId(),
                cloud.captureFrameId()
        );
    }

    private static boolean enabled() {
        if (SkyesightDebugConfig.SKY_CAPTURE_AUDIT) {
            return true;
        }
        CLOUD_FRAMES.clear();
        retainedRenderTick = Integer.MIN_VALUE;
        return false;
    }

    private static void advanceFrame(int renderTick) {
        if (retainedRenderTick == Integer.MIN_VALUE) {
            retainedRenderTick = renderTick;
            return;
        }
        if (retainedRenderTick == renderTick) {
            return;
        }
        if (!CLOUD_FRAMES.isEmpty()) {
            Map.Entry<ResourceLocation, CloudFrame> unmatched = CLOUD_FRAMES.entrySet().iterator().next();
            logMissing(
                    unmatched.getKey(),
                    retainedRenderTick,
                    "portal-not-rendered-after-capture",
                    "nextFrame=" + renderTick
            );
        }
        CLOUD_FRAMES.clear();
        retainedRenderTick = renderTick;
    }

    private static void logMissing(ResourceLocation viewId, int renderTick, String reason, String detail) {
        if (!shouldLog()) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] CLOUD_TERRAIN_FRAME view={} frame={} reason={} detail={}",
                viewId,
                renderTick,
                reason,
                detail
        );
    }

    private static boolean shouldLog() {
        long now = System.nanoTime();
        if (now - lastLogNanos < LOG_INTERVAL_NANOS) {
            return false;
        }
        lastLogNanos = now;
        return true;
    }

    private static Vec3 copy(Vec3 value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private static double maxAbs(Vec3 value) {
        return Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z())));
    }

    private static double quaternionAngleDeltaDegrees(Quaternionf first, Quaternionf second) {
        Quaternionf normalizedFirst = new Quaternionf(first).normalize();
        Quaternionf normalizedSecond = new Quaternionf(second).normalize();
        double dot = Math.abs(
                (double) normalizedFirst.x * normalizedSecond.x
                        + (double) normalizedFirst.y * normalizedSecond.y
                        + (double) normalizedFirst.z * normalizedSecond.z
                        + (double) normalizedFirst.w * normalizedSecond.w
        );
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        return Math.toDegrees(2.0D * Math.acos(dot));
    }

    private static float matrixMaxDelta(Matrix4f first, Matrix4f second) {
        float[] firstValues = first.get(new float[16]);
        float[] secondValues = second.get(new float[16]);
        float max = 0.0F;
        for (int index = 0; index < firstValues.length; index++) {
            max = Math.max(max, Math.abs(firstValues[index] - secondValues[index]));
        }
        return max;
    }

    private static String format(Vec3 value) {
        return String.format(Locale.ROOT, "(%.9g,%.9g,%.9g)", value.x(), value.y(), value.z());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9g", value);
    }

    private record CloudFrame(
            int renderTick,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            Matrix4f viewMatrix,
            Matrix4f projectionMatrix,
            int cloudTicks,
            Vec3 cloudCamArgs,
            int captureTargetId,
            int captureFrameId
    ) {}
}
