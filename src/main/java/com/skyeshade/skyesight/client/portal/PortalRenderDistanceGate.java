package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PortalRenderDistanceGate {
    private static final Map<String, Boolean> LAST_INSIDE_BY_VIEW_STAGE = new HashMap<>();
    private static final Map<String, Long> LAST_LOG_MILLIS_BY_VIEW_STAGE = new HashMap<>();

    private PortalRenderDistanceGate() {}

    public static boolean shouldRenderPortalFromCamera(
            Minecraft minecraft,
            RegisteredPortalView view,
            Camera camera,
            double maxDistanceBlocks
    ) {
        if (minecraft == null || minecraft.level == null || view == null || view.source() == null || camera == null) {
            return false;
        }

        if (!minecraft.level.dimension().equals(view.source().dimension())) {
            return false;
        }

        if (maxDistanceBlocks < 0.0D) {
            return true;
        }

        double distanceSqr = distanceToPortalSourceSqr(camera, view);
        double max = Math.max(0.0D, maxDistanceBlocks);
        return distanceSqr <= max * max;
    }

    public static double distanceToPortalSource(Camera camera, RegisteredPortalView view) {
        if (camera == null || view == null || view.source() == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(distanceToPortalSourceSqr(camera, view));
    }

    public static void logDecisionIfDue(
            Minecraft minecraft,
            RegisteredPortalView view,
            Camera camera,
            double maxDistanceBlocks,
            boolean inside,
            String stage,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        if (view == null || view.id() == null) {
            return;
        }

        String key = view.id() + ":" + (stage == null || stage.isBlank() ? "unknown" : stage);
        long now = System.currentTimeMillis();
        Boolean previousInside = LAST_INSIDE_BY_VIEW_STAGE.get(key);
        Long previousLog = LAST_LOG_MILLIS_BY_VIEW_STAGE.get(key);
        if (previousInside != null
                && previousInside == inside
                && previousLog != null
                && now - previousLog < 3_000L) {
            return;
        }

        LAST_INSIDE_BY_VIEW_STAGE.put(key, inside);
        LAST_LOG_MILLIS_BY_VIEW_STAGE.put(key, now);

        Vec3 cameraPos = camera == null ? null : camera.getPosition();
        Vec3 portalCenter = view.source() == null ? null : view.source().center();
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_RENDER_DISTANCE_GATE: viewId={} sourceDim={} clientDim={} portalCenter={} cameraPos={} distance={} maxDistance={} inside={} stage={} reason={}",
                view.id(),
                view.source() == null ? "-" : view.source().dimension().location(),
                minecraft == null || minecraft.level == null ? "-" : minecraft.level.dimension().location(),
                format(portalCenter),
                format(cameraPos),
                String.format(Locale.ROOT, "%.2f", distanceToPortalSource(camera, view)),
                maxDistanceBlocks < 0.0D ? "unlimited" : String.format(Locale.ROOT, "%.2f", Math.max(0.0D, maxDistanceBlocks)),
                inside ? "yes" : "no",
                stage == null || stage.isBlank() ? "unknown" : stage,
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static double distanceToPortalSourceSqr(Camera camera, RegisteredPortalView view) {
        Vec3 cameraPosition = camera.getPosition();
        Vec3 sourceCenter = view.source().center();
        return cameraPosition.distanceToSqr(sourceCenter);
    }

    private static String format(Vec3 value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", value.x(), value.y(), value.z());
    }
}
