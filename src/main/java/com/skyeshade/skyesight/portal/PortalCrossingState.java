package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.world.phys.Vec3;

/** One player's history for one fixed aperture. Contact is not a change of stable side. */
public final class PortalCrossingState {
    public static final double ENTER_PLANE = .001;
    public static final double EXIT_PLANE = .01;
    private Vec3 previous, pending;
    private int stableSide;
    public int stableSide() { return stableSide; }
    public boolean hasPendingCrossing() { return pending != null; }

    public Vec3 update(PortalEndpoint endpoint, Vec3 feet, double width, double height) {
        double d = PortalTraversalMath.local(endpoint, feet).z;
        if (previous == null || previous.distanceToSqr(feet) > 64) {
            previous = feet; pending = null;
            stableSide = Math.abs(d) >= EXIT_PLANE ? (d > 0 ? 1 : -1) : 0;
            return null;
        }
        double before = PortalTraversalMath.local(endpoint, previous).z;
        if (stableSide == 0) {
            if (Math.abs(d) >= EXIT_PLANE) stableSide = d > 0 ? 1 : -1;
        } else {
            if (before * stableSide >= 0 && d * stableSide <= 0 && before != d) {
                var hit = previous.lerp(feet, before / (before - d));
                pending = fits(endpoint, hit, width, height) ? hit : null;
            }
            // Do not retain a crossing after leaving the opening while straddling it.
            if (Math.abs(d) < EXIT_PLANE && !fits(endpoint, feet, width, height)
                    || d * stableSide > ENTER_PLANE) pending = null;
            if (d * stableSide <= -EXIT_PLANE) {
                var hit = pending;
                stableSide = -stableSide; pending = null; previous = feet;
                return hit;
            }
        }
        previous = feet;
        return null;
    }
    private static boolean fits(PortalEndpoint endpoint, Vec3 feet, double width, double height) {
        var local = PortalTraversalMath.local(endpoint, feet);
        return Math.abs(local.x) + width / 2 <= endpoint.width() / 2
                && local.y >= -endpoint.height() / 2 - .001
                && local.y + height <= endpoint.height() / 2 + .001;
    }
}
