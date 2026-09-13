package com.skyeshade.skyesight.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;

/** Redirects only synchronous particle creation belonging to one secondary simulation. */
public final class SecondaryParticleCapture implements AutoCloseable {
    private static final ThreadLocal<SecondaryParticleCapture> CURRENT = new ThreadLocal<>();
    private final SecondaryParticleCapture previous;
    private final ClientLevel level;
    private final SkyesightVisualParticleManager particles;

    private SecondaryParticleCapture(ClientLevel level, SkyesightVisualParticleManager particles) {
        this.previous = CURRENT.get();
        this.level = level;
        this.particles = particles;
        CURRENT.set(this);
    }

    public static SecondaryParticleCapture push(ClientLevel level, SkyesightVisualParticleManager particles) {
        return new SecondaryParticleCapture(level, particles);
    }

    public static boolean capture(ClientLevel level, ParticleOptions type, double x, double y, double z,
                                  double vx, double vy, double vz) {
        var current = CURRENT.get();
        if (current == null || current.level != level) return false;
        current.particles.addVisualParticle(type, x, y, z, vx, vy, vz, false, "secondary-simulation");
        return true;
    }

    public static ClientLevel creationLevel(ClientLevel fallback) {
        var current = CURRENT.get();
        return current == null ? fallback : current.level;
    }
    public static boolean captureInstance(Particle particle) {
        var current = CURRENT.get();
        if (current == null) return false;
        current.particles.addClientParticle(particle);
        return true;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
