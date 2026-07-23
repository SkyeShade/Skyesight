package com.skyeshade.skyesight.client.portal;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class PortalRenderDebugStatus {
    private PortalRenderDebugStatus() {}

    public static int instancesRendered() {
        return PortalDirectStencilRenderer.instancesRendered();
    }

    public static String lastDirectRenderException() {
        return PortalDirectStencilRenderer.lastDirectRenderException();
    }

    public static String directPortalDepthMode() {
        return PortalDirectStencilRenderer.directPortalDepthMode();
    }

    public static boolean farPortalRenderBlockEntities() {
        return PortalDirectStencilRenderer.farPortalRenderBlockEntities();
    }

    public static int stencilBits() {
        return PortalDirectStencilRenderer.stencilBits();
    }

    public static List<PortalLookMarkerDebugData.PortalLookDebugMarker> portalLookDebugMarkers(
            Minecraft minecraft,
            Camera camera
    ) {
        return PortalDirectStencilRenderer.portalLookDebugMarkers(minecraft, camera);
    }
}
