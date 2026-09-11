package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.resources.ResourceLocation;

/** Client/render-thread visual handoff only. The server must perform the actual teleport. */
public final class SkyesightTransitionApi {
    private SkyesightTransitionApi() {}

    /** Select an existing portal. Render it normally until the returned transition is READY. */
    public static SkyesightTransition preparePortalTransition(ResourceLocation portalId) {
        var selected = SkyesightPortalApi.getPortal(portalId.toString());
        return SecondaryTransition.prepare(portalId, () -> {
            var portal = SkyesightPortalApi.getPortal(portalId.toString());
            return selected != null && portal != null && portal.active() && portal.generation() == selected.generation();
        });
    }

    /** Select an existing camera. Its next successful render prepares the destination image. */
    public static SkyesightTransition prepareViewTransition(SkyesightCameraView view) {
        var dimension = view.dimension();
        return SecondaryTransition.prepare(view.id(), () -> !view.isClosed() && view.dimension().equals(dimension));
    }
}
