package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Server adapter. Call on the server thread with current, permission-filtered portal links. */
public final class SkyesightServerPortalRays implements SkyesightPortalRaycast.WorldAccess {
    private final MinecraftServer server;
    private final Entity actor;
    private final Function<ResourceKey<Level>, Iterable<SkyesightPortalRaycast.Link>> links;
    private final Predicate<Entity> entities;
    public SkyesightServerPortalRays(MinecraftServer server, Entity actor,
            Function<ResourceKey<Level>, Iterable<SkyesightPortalRaycast.Link>> links, Predicate<Entity> entities) {
        this.server = server; this.actor = actor; this.links = links; this.entities = entities;
    }
    @Override public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> dimension) { return links.apply(dimension); }
    @Override public boolean available(ResourceKey<Level> dimension, Vec3 from, Vec3 to) {
        var level = server.getLevel(dimension);
        if (level == null) return false;
        // Bounded interaction rays only; never force-load a destination for a preview/query.
        if (from.distanceToSqr(to) > 2048 * 2048) return false;
        int x = net.minecraft.util.Mth.floor(from.x) >> 4, z = net.minecraft.util.Mth.floor(from.z) >> 4;
        int endX = net.minecraft.util.Mth.floor(to.x) >> 4, endZ = net.minecraft.util.Mth.floor(to.z) >> 4;
        double dx = to.x - from.x, dz = to.z - from.z;
        int sx = Double.compare(dx, 0), sz = Double.compare(dz, 0);
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((x + (sx > 0 ? 1 : 0)) * 16.0 - from.x) / dx;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : ((z + (sz > 0 ? 1 : 0)) * 16.0 - from.z) / dz;
        for (int steps = 0; steps < 4096; steps++) {
            if (!level.hasChunk(x, z)) return false;
            if (x == endX && z == endZ) return true;
            if (tx < tz) { x += sx; tx += 16 / Math.abs(dx); }
            else { z += sz; tz += 16 / Math.abs(dz); }
        }
        return false;
    }
    @Override public HitResult nearestHit(ResourceKey<Level> dimension, Vec3 from, Vec3 to) {
        var level = server.getLevel(dimension);
        if (level == null) return null;
        HitResult nearest = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, actor));
        double distance = nearest.getType() == HitResult.Type.MISS ? from.distanceToSqr(to) : from.distanceToSqr(nearest.getLocation());
        for (Entity entity : level.getEntities(actor, new AABB(from, to).inflate(1),
                e -> !e.isRemoved() && e.isPickable() && !e.isSpectator() && entities.test(e))) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < distance) {
                distance = from.distanceToSqr(hit.get()); nearest = new EntityHitResult(entity, hit.get());
            }
        }
        return nearest;
    }
}
