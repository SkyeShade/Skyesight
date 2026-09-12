package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link;
import com.skyeshade.skyesight.server.portal.TraversalPortalManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

import java.util.*;

/**
 * Bounded collision composition; no level/chunk ownership swaps and no global collision disable.
 */
public final class PortalCollisions {
    public interface ClientAccess {
        Iterable<Link> links(Level source);

        Level destination(Link link);

        boolean loaded(Level level, AABB box);
    }

    private static ClientAccess client;

    public static void clientAccess(ClientAccess access) {
        client = access;
    }

    private record Side(net.minecraft.resources.ResourceKey<Level> dimension,
                        net.minecraft.resources.ResourceLocation portal, long revision, int sign) {
    }

    private static final ThreadLocal<WeakHashMap<Entity, Side>> SIDES = ThreadLocal.withInitial(WeakHashMap::new);

    public static List<VoxelShape> collect(Level source, Entity entity, AABB query, Iterable<VoxelShape> original) {
        return collect(source, entity, query, original, entity == null ? net.minecraft.world.phys.Vec3.ZERO : entity.position());
    }

    public static List<VoxelShape> collect(Level source, Entity entity, AABB query, Iterable<VoxelShape> original, net.minecraft.world.phys.Vec3 reference) {
        List<VoxelShape> result = new ArrayList<>();
        original.forEach(result::add);
        if (entity == null || entity.isRemoved() || entity.isSpectator() || !entity.canUsePortal(false)
                || entity instanceof net.minecraft.world.entity.Marker || entity.isPassenger() || !entity.getPassengers().isEmpty()
                || query.getXsize() > 16 || query.getYsize() > 16 || query.getZsize() > 16) return result;
        Iterable<Link> links = source instanceof ServerLevel ? TraversalPortalManager.links(source.dimension())
                : client == null ? List.of() : client.links(source);
        for (Link link : links) {
            if (!PortalCollisionMath.supported(link.source()) || !PortalCollisionMath.supported(link.target()))
                continue;
            AABB local = PortalCollisionMath.map(query, p -> PortalTraversalMath.local(link.source(), p));
            if (local.minZ > 1e-6 || local.maxZ < -1e-6 || !com.skyeshade.skyesight.api.PortalAperture.fits(link, entity.getBoundingBox()))
                continue;
            // Only server-defined gameplay apertures enter this path.
            Level destination = source instanceof ServerLevel server ? server.getServer().getLevel(link.target().dimension()) : client.destination(link);
            AABB mapped = PortalCollisionMath.transform(query, link.source(), link.target());
            if (destination == null || !loaded(destination, mapped)) continue;
            if (source instanceof ServerLevel from && destination instanceof ServerLevel to && !entity.canChangeDimensions(from, to))
                continue;
                        var reverseLinks = destination instanceof ServerLevel ? TraversalPortalManager.links(destination.dimension()) : client.links(destination);
            Link reverse = null;
            for(var candidate : reverseLinks) if(candidate.id().equals(com.skyeshade.skyesight.network.SkyesightTraversalPayload.pairedPortalId(link.id()))) { reverse=candidate; break; }
            if (reverse == null || !com.skyeshade.skyesight.api.PortalAperture.fits(reverse, PortalCollisionMath.transform(entity.getBoundingBox(), link.source(), link.target())))
                continue;
            double z = PortalTraversalMath.local(link.source(), reference).z;
            Side previous = SIDES.get().get(entity);
            boolean same = previous != null && previous.dimension.equals(source.dimension()) && previous.portal.equals(link.id()) && previous.revision == link.revision();
            int sign = same && Math.abs(z) < entity.getBbWidth() / 2 + .02 ? previous.sign : z >= 0 ? 1 : -1;
            if (!link.backface() && sign < 0) continue;
            SIDES.get().put(entity, new Side(source.dimension(), link.id(), link.revision(), sign));
            var half = PortalCollisionMath.apertureHalf(link, sign, 32);
            List<VoxelShape> combined = new ArrayList<>();
            for (VoxelShape shape : result) {
                var clipped = Shapes.joinUnoptimized(shape, half, BooleanOp.ONLY_FIRST);
                if (!clipped.isEmpty()) combined.add(clipped);
            }
            for (VoxelShape shape : destination.getCollisions(entity, mapped)) {
                var projected = PortalCollisionMath.transform(shape, link.target(), link.source());
                var clipped = Shapes.joinUnoptimized(projected, half, BooleanOp.AND);
                if (!clipped.isEmpty()) combined.add(clipped);
            }
            // A world border is authoritative even though it isn't a block shape.
            if (!destination.getWorldBorder().isWithinBounds(mapped)) return result;
            return combined; // One intersected portal; recursive collision is deliberately unsupported.
        }
        SIDES.get().remove(entity);
        return result;
    }

    private static boolean loaded(Level level, AABB box) {
        if (!(level instanceof ServerLevel)) return client != null && client.loaded(level, box);
        for (int x = net.minecraft.util.Mth.floor(box.minX) >> 4; x <= net.minecraft.util.Mth.floor(box.maxX) >> 4; x++)
            for (int z = net.minecraft.util.Mth.floor(box.minZ) >> 4; z <= net.minecraft.util.Mth.floor(box.maxZ) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
}



