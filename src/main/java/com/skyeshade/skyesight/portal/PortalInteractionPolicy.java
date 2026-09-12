package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.SkyesightPortalRaycast;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Validate a server-computed result against an intent, never against a claimed destination position.
 */
public final class PortalInteractionPolicy {
    private PortalInteractionPolicy() {
    }

    public static boolean matches(SkyesightPortalRaycast.Result ray, ResourceLocation portal, long revision,
                                  double blockReach, double entityReach) {
        if (ray.end() != SkyesightPortalRaycast.End.HIT || ray.hit() == null || ray.chain().size() != 1) return false;
        var step = ray.chain().getFirst();
        return step.portal().equals(portal) && step.revision() == revision && Double.isFinite(ray.distance())
                && ray.distance() >= 0 && ray.distance() <= (ray.hit() instanceof EntityHitResult ? entityReach : blockReach);
    }
}
