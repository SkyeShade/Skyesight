package com.skyeshade.skyesight.remote;

import net.minecraft.world.phys.Vec3;

/** Shared, bounded particle interest policy. Distances are in blocks, not terrain chunks. */
public final class SecondaryParticlePolicy {
    public static final int MAX_RADIUS = 96;
    public static final int ACTIVITY_TICKS = 10;
    public static final int SERVER_LEASE_TICKS = 60;
    private SecondaryParticlePolicy() {}

    public static boolean usesMainParticles(boolean sameLevel, Vec3 physicalEye, Vec3 secondaryEye) {
        return sameLevel && physicalEye.distanceToSqr(secondaryEye) <= 16.0 * 16.0;
    }

    public static boolean contains(Vec3 center, int radius, double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && center.distanceToSqr(x, y, z) <= (double) radius * radius;
    }
}
