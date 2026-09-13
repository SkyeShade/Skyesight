package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Read-only, common-side ray traversal. Does not authorize or perform interactions. */
public final class SkyesightPortalRaycast {
    private SkyesightPortalRaycast() {}
    /** Aperture predicate receives source-local coordinates; masked links must provide their actual shape. */
    public record Link(ResourceLocation id, long revision, PortalEndpoint source, PortalEndpoint target,
                       boolean backface, Predicate<Vec3> aperture, PortalSidedness sidedness) {
        public Link(ResourceLocation id, long revision, PortalEndpoint source, PortalEndpoint target,
                    boolean backface, Predicate<Vec3> aperture) {
            this(id, revision, source, target, backface, aperture, backface ? PortalSidedness.BOTH : PortalSidedness.FRONT_ONLY);
        }
        public Link {
            Objects.requireNonNull(id); Objects.requireNonNull(source); Objects.requireNonNull(target);
            Objects.requireNonNull(aperture);
            Objects.requireNonNull(sidedness);
        }
        public static Link rectangle(ResourceLocation id, long revision, PortalEndpoint source,
                                     PortalEndpoint target, boolean backface) {
            return new Link(id, revision, source, target, backface,
                    p -> Math.abs(p.x) < source.width() / 2 && Math.abs(p.y) < source.height() / 2);
        }
    }
    /** Supply current authorized links and nearest block/entity hits, without loading missing worlds. */
    public interface WorldAccess {
        boolean available(ResourceKey<Level> dimension, Vec3 from, Vec3 to);
        Iterable<Link> portals(ResourceKey<Level> dimension);
        /** Null or MISS means no hit. Implementations must return the nearest hit on this segment. */
        HitResult nearestHit(ResourceKey<Level> dimension, Vec3 from, Vec3 to);
    }
    public enum End { HIT, MISS, UNAVAILABLE, HOP_LIMIT }
    public record Step(ResourceLocation portal, long revision, ResourceKey<Level> source,
                       ResourceKey<Level> target, Vec3 intersection, Vec3 exit, Vec3 direction,
                       double distanceFromStart) {}
    public record Result(End end, ResourceKey<Level> dimension, Vec3 position, Vec3 direction,
                         double distance, double remainingDistance, HitResult hit, List<Step> chain) {
        public Result { chain = List.copyOf(chain); }
    }
    public static Result trace(WorldAccess worlds, ResourceKey<Level> dimension, Vec3 origin,
                               Vec3 direction, double reach, int maxHops) {
        Objects.requireNonNull(worlds); Objects.requireNonNull(dimension);
        if (!finite(origin) || !finite(direction) || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-12
                || !Double.isFinite(reach) || reach < 0 || maxHops < 0 || maxHops > 16)
            throw new IllegalArgumentException("Finite ray, nonnegative reach and 0..16 portal hops required");
        direction = direction.normalize();
        double used = 0;
        List<Step> chain = new ArrayList<>();
        while (true) {
            Vec3 end = origin.add(direction.scale(reach - used));
            // Find the closest portal first, so unavailable source chunks BEHIND it cannot block tracing.
            Link closest = null; Vec3 intersection = null; double portalDistance = reach - used + 1;
            for (Link link : worlds.portals(dimension)) {
                if (!link.source.dimension().equals(dimension)) continue;
                Vec3 a = PortalTraversalMath.local(link.source, origin);
                Vec3 b = PortalTraversalMath.local(link.source, end);
                if (a.z == b.z || !link.sidedness.allows(a.z)) continue;
                double t = a.z / (a.z - b.z);
                if (t < 0 || t > 1) continue;
                double distance = t * (reach - used);
                // Ignore the exit surface at precisely zero distance, without extending reach.
                if (distance <= 1e-7 && !chain.isEmpty() || distance >= portalDistance || !link.aperture.test(a.lerp(b, t))) continue;
                closest = link; intersection = origin.lerp(end, t); portalDistance = distance;
            }
            Vec3 segmentEnd = closest == null ? end : intersection;
            if (!worlds.available(dimension, origin, segmentEnd))
                return new Result(End.UNAVAILABLE, dimension, origin, direction, used, reach - used, null, chain);
            HitResult hit = worlds.nearestHit(dimension, origin, segmentEnd);
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                double distance = origin.distanceTo(hit.getLocation());
                if (distance <= origin.distanceTo(segmentEnd) + 1e-7)
                    return new Result(End.HIT, dimension, hit.getLocation(), direction, used + distance,
                            Math.max(0, reach - used - distance), hit, chain);
            }
            if (closest == null) return new Result(End.MISS, dimension, end, direction, reach, 0, null, chain);
            used += portalDistance;
            if (chain.size() >= maxHops)
                return new Result(End.HOP_LIMIT, dimension, intersection, direction, used, reach - used, null, chain);
            origin = PortalTraversalMath.position(closest.source, closest.target, intersection);
            direction = PortalTraversalMath.rotate(direction,
                    PortalTraversalMath.rotation(closest.source.rotation(), closest.target.rotation())).normalize();
            chain.add(new Step(closest.id, closest.revision, dimension, closest.target.dimension(),
                    intersection, origin, direction, used));
            dimension = closest.target.dimension();
        }
    }
    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
