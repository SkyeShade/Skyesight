package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalAperture;
import com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link;
import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import com.skyeshade.skyesight.server.portal.TraversalPortalManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.Vec3;

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

    private record Side(ResourceKey<Level> dimension,
                        ResourceLocation portal, long revision, int sign) {
    }

    private static final ThreadLocal<WeakHashMap<Entity, Side>> SIDES = ThreadLocal.withInitial(WeakHashMap::new);

    public static List<VoxelShape> collect(Level source, Entity entity, AABB query, Iterable<VoxelShape> original) {
        return collect(source, entity, query, original, entity == null ? Vec3.ZERO : entity.position());
    }

    public static List<VoxelShape> collect(Level source, Entity entity, AABB query, Iterable<VoxelShape> original, Vec3 reference) {
        List<VoxelShape> result = new ArrayList<>();
        original.forEach(result::add);
        if (entity == null || entity.isRemoved() || entity.isSpectator() || !entity.canUsePortal(false)
                || entity instanceof Marker || entity.isPassenger() || !entity.getPassengers().isEmpty()
                || query.getXsize() > 16 || query.getYsize() > 16 || query.getZsize() > 16) return result;
        Iterable<Link> links = source instanceof ServerLevel ? TraversalPortalManager.links(source.dimension())
                : client == null ? List.of() : client.links(source);
        for (Link link : links) {
            if (!PortalCollisionMath.supported(link.source()) || !PortalCollisionMath.supported(link.target()))
                continue;
            AABB local = PortalCollisionMath.map(query, p -> PortalTraversalMath.local(link.source(), p));
            if (local.minZ > 1e-6 || local.maxZ < -1e-6 || !PortalAperture.fits(link, entity.getBoundingBox()))
                continue;
            // Only server-defined gameplay apertures enter this path.
            Level destination = source instanceof ServerLevel server ? server.getServer().getLevel(link.target().dimension()) : client.destination(link);
            AABB mapped = PortalCollisionMath.transform(query, link.source(), link.target());
            if (destination == null || !loaded(destination, mapped)) continue;
            if (source instanceof ServerLevel from && destination instanceof ServerLevel to && !entity.canChangeDimensions(from, to))
                continue;
                        var reverseLinks = destination instanceof ServerLevel ? TraversalPortalManager.links(destination.dimension()) : client.links(destination);
            Link reverse = null;
            for(var candidate : reverseLinks) if(candidate.id().equals(SkyesightTraversalPayload.pairedPortalId(link.id()))) { reverse=candidate; break; }
            if (reverse == null || !PortalAperture.fits(reverse, PortalCollisionMath.transform(entity.getBoundingBox(), link.source(), link.target())))
                continue;
            double z = PortalTraversalMath.local(link.source(), reference).z;
            Side previous = SIDES.get().get(entity);
            boolean same = previous != null && previous.dimension.equals(source.dimension()) && previous.portal.equals(link.id()) && previous.revision == link.revision();
            int sign = same && Math.abs(z) < entity.getBbWidth() / 2 + .02 ? previous.sign : z >= 0 ? 1 : -1;
            if (!link.sidedness().allows(sign)) continue;
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
        for (int x = Mth.floor(box.minX) >> 4; x <= Mth.floor(box.maxX) >> 4; x++)
            for (int z = Mth.floor(box.minZ) >> 4; z <= Mth.floor(box.maxZ) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
}



