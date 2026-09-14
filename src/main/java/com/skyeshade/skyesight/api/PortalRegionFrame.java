package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Oriented surface coordinates: local X/Y are U/V and local Z is the normal. */
public record PortalRegionFrame(ResourceKey<Level> dimension, Vec3 origin, Quaternionf rotation) {
    public PortalRegionFrame {
        if (dimension == null || origin == null || !Double.isFinite(origin.lengthSqr())
                || rotation == null || !Float.isFinite(rotation.lengthSquared()) || rotation.lengthSquared() < 1e-12)
            throw new IllegalArgumentException("Finite region frame required");
        rotation = new Quaternionf(rotation).normalize();
    }
    @Override public Quaternionf rotation() { return new Quaternionf(rotation); }
    public Vec3 local(Vec3 point) { return PortalTraversalMath.rotate(point.subtract(origin), rotation().conjugate()); }
    public Vec3 world(double u, double v, double n) { return origin.add(PortalTraversalMath.rotate(new Vec3(u,v,n), rotation)); }
    public Vec3 tangentU() { return PortalTraversalMath.rotate(new Vec3(1,0,0), rotation); }
    public Vec3 tangentV() { return PortalTraversalMath.rotate(new Vec3(0,1,0), rotation); }
    public Vec3 normal() { return PortalTraversalMath.rotate(new Vec3(0,0,1), rotation); }
    public PortalEndpoint endpoint(String id, Vec3 center, float size) {
        return new PortalEndpoint(id, dimension, center, Direction.NORTH, rotation, size, size);
    }
}
