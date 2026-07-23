package com.skyeshade.skyesight.client.portal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PortalLookMarkerDebugData {
    private PortalLookMarkerDebugData() {}

    public record PortalLookDebugMarker(
            ResourceLocation viewId,
            String portalName,
            String kind,
            ResourceKey<Level> dimension,
            Vec3 position,
            @Nullable Vec3 lineStart,
            Vec3 right,
            Vec3 up,
            Vec3 forward,
            String source
    ) {
    }

    public record ViewConfig(
            String portalName,
            ResourceLocation viewId,
            PortalRenderView renderView,
            ResourceKey<Level> displayDimension,
            ResourceKey<Level> targetDimension
    ) {
    }

    public static List<PortalLookDebugMarker> buildMarkers(
            List<ViewConfig> viewConfigs,
            net.minecraft.client.Camera camera
    ) {
        if (camera == null || viewConfigs == null || viewConfigs.isEmpty()) {
            return List.of();
        }

        List<PortalLookDebugMarker> markers = new ArrayList<>();
        for (ViewConfig viewConfig : viewConfigs) {
            addPortalLookDebugMarkers(markers, viewConfig, camera);
        }
        return markers;
    }

    private static void addPortalLookDebugMarkers(
            List<PortalLookDebugMarker> markers,
            ViewConfig viewConfig,
            net.minecraft.client.Camera camera
    ) {
        PortalRenderView renderView = viewConfig.renderView();
        if (renderView == null || !renderView.renderConfig().enabled() || !renderView.renderConfig().rendersView()) {
            return;
        }
        PortalFrame entrance = renderView.entrancePortal();
        PortalFrame exit = renderView.exitPortal();
        DirectStencilPortalMath.PortalCameraPose pose = DirectStencilPortalMath.transformCamera(camera, entrance, exit);
        markers.add(marker(
                viewConfig.viewId(),
                viewConfig.portalName(),
                "display-center",
                viewConfig.displayDimension(),
                entrance.position(),
                null,
                entrance,
                "PortalRenderView.entrancePortal"
        ));
        markers.add(marker(
                viewConfig.viewId(),
                viewConfig.portalName(),
                "target-plane-center",
                viewConfig.targetDimension(),
                exit.position(),
                null,
                exit,
                "PortalRenderView.exitPortal"
        ));
        Vec3 lineStart = viewConfig.displayDimension().equals(viewConfig.targetDimension()) ? entrance.position() : null;
        markers.add(new PortalLookDebugMarker(
                viewConfig.viewId(),
                viewConfig.portalName(),
                "render-camera-look-center",
                viewConfig.targetDimension(),
                pose.position(),
                lineStart,
                rotate(pose.rotation(), 1.0F, 0.0F, 0.0F),
                rotate(pose.rotation(), 0.0F, 1.0F, 0.0F),
                rotate(pose.rotation(), 0.0F, 0.0F, 1.0F),
                "DirectStencilPortalMath.transformCamera"
        ));
    }

    private static PortalLookDebugMarker marker(
            ResourceLocation viewId,
            String portalName,
            String kind,
            ResourceKey<Level> dimension,
            Vec3 position,
            @Nullable Vec3 lineStart,
            PortalFrame frame,
            String source
    ) {
        return new PortalLookDebugMarker(
                viewId,
                portalName,
                kind,
                dimension,
                position,
                lineStart,
                DirectStencilPortalMath.right(frame),
                DirectStencilPortalMath.up(frame),
                DirectStencilPortalMath.normal(frame),
                source
        );
    }

    private static Vec3 rotate(Quaternionf rotation, float x, float y, float z) {
        Vector3f vector = new Vector3f(x, y, z);
        new Quaternionf(rotation).transform(vector).normalize();
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
