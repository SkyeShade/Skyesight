package com.skyeshade.skyesight.api;

import net.minecraft.world.phys.Vec3;

/** Immutable world-space plane. Positive signed distance is in the normal's direction. */
public record SkyesightClipPlane(Vec3 point, Vec3 normal) {
    public SkyesightClipPlane {
        java.util.Objects.requireNonNull(point, "point");
        java.util.Objects.requireNonNull(normal, "normal");
        double length = normal.length();
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || !Double.isFinite(length) || length < 1e-12)
            throw new IllegalArgumentException("Plane requires a finite point and nonzero finite normal");
        normal = normal.scale(1 / length);
    }

    public double signedDistance(Vec3 position) { return position.subtract(point).dot(normal); }
    public boolean retains(Vec3 position, ClipSide side) {
        return side == ClipSide.NONE || (side == ClipSide.POSITIVE ? signedDistance(position) >= 0 : signedDistance(position) <= 0);
    }
    public SkyesightClipPlane flipped() { return new SkyesightClipPlane(point, normal.scale(-1)); }
    public SkyesightClipPlane translated(Vec3 offset) { return new SkyesightClipPlane(point.add(offset), normal); }

    /** Affine transform; normals use the inverse transpose, including nonuniform scale. */
    public SkyesightClipPlane transformed(org.joml.Matrix4fc transform) {
        if (!transform.isAffine() || !Float.isFinite(transform.determinant()) || Math.abs(transform.determinant()) < 1e-12)
            throw new IllegalArgumentException("Expected an invertible affine transform");
        var p = transform.transformPosition(point.toVector3f());
        var n = new org.joml.Matrix3f(transform).invert().transpose().transform(normal.toVector3f());
        return new SkyesightClipPlane(new Vec3(p.x, p.y, p.z), new Vec3(n.x, n.y, n.z));
    }
}
