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

    /** An authoritative arrival establishes a side even inside the contact band. */
    public void arrive(PortalEndpoint endpoint, Vec3 feet) {
        previous = feet;
        pending = null;
        stableSide = PortalTraversalMath.local(endpoint, feet).z >= 0 ? 1 : -1;
    }

    public Vec3 update(PortalEndpoint endpoint, Vec3 feet, double width, double height) {
        return update(endpoint, feet, point -> fits(endpoint, point, width, height));
    }
    /** Shared sweep/side history; callers choose body containment or player overlap policy. */
    public Vec3 update(PortalEndpoint endpoint, Vec3 feet, java.util.function.Predicate<Vec3> aperture) {
        double d = PortalTraversalMath.local(endpoint, feet).z;
        if (previous == null) {
            previous = feet; pending = null;
            stableSide = Math.abs(d) >= EXIT_PLANE ? (d > 0 ? 1 : -1) : 0;
            return null;
        }
        double before = PortalTraversalMath.local(endpoint, previous).z;
        // A first accepted sweep can cross from inside the contact band. Its segment
        // supplies the entry side even if neither earlier tick established a stable side.
        if (stableSide == 0 && Math.abs(before) > ENTER_PLANE && before * d < 0)
            stableSide = before > 0 ? 1 : -1;
        if (stableSide == 0) {
            if (Math.abs(d) >= EXIT_PLANE) stableSide = d > 0 ? 1 : -1;
        } else {
            if (before * stableSide >= 0 && d * stableSide <= 0 && before != d) {
                var hit = previous.lerp(feet, before / (before - d));
                pending = aperture.test(hit) ? hit : null;
            }
            // Do not retain a crossing after leaving the opening while straddling it.
            if (Math.abs(d) < EXIT_PLANE && !aperture.test(feet)
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
        // Require the centerline inside the opening and vertical body overlap. Full-body
        // containment rejects jumping through a frameless opening near its upper edge.
        // A body merely brushing the outside of the horizontal aperture does not traverse.
        return Math.abs(local.x) < endpoint.width() / 2
                && local.y < endpoint.height() / 2
                && local.y + height > -endpoint.height() / 2;
    }
}
