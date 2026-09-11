package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.client.render.SecondarySceneFrame;
import com.skyeshade.skyesight.client.render.PortalVisualDisplayTickDriver;
import com.skyeshade.skyesight.mixin.client.GameRendererStateAccessor;
import com.skyeshade.skyesight.network.SkyesightParticlePayload;
import com.skyeshade.skyesight.network.SkyesightParticleWatchPayload;
import com.skyeshade.skyesight.remote.SecondaryParticlePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Active, view-owned particle simulations. Rendering touches a lease; only client ticks advance it. */
public final class SecondaryParticleViews {
    private static final Map<ResourceLocation, View> VIEWS = new HashMap<>();
    private static long tick, nextGeneration;
    private SecondaryParticleViews() {}

    public static final class View {
        final ResourceLocation id;
        final long generation = ++nextGeneration;
        final ClientLevel level;
        final SkyesightVisualParticleManager particles;
        Vec3 center;
        int radius;
        boolean main;
        long touched, sent = Long.MIN_VALUE / 2;
        public long sampleNanos, tickNanos;
        public int spawnedLastTick, samplesLastTick;
        View(ResourceLocation id, ClientLevel level, SkyesightVisualParticleManager particles) {
            this.id = id; this.level = level; this.particles = particles;
            particles.bindLevel(level);
            if (level instanceof SkyesightVisualClientLevel visual) visual.setSkyesightViewId(id);
        }
        public boolean usesMainParticles() { return main; }
        public SkyesightVisualParticleManager particles() { return particles; }
    }

    public static View touch(SecondarySceneFrame scene) {
        var view = VIEWS.get(scene.viewId());
        if (view != null && view.level != scene.level()) { close(scene.viewId()); view = null; }
        if (view == null) {
            if (VIEWS.size() >= 64) return null;
            view = new View(scene.viewId(), scene.level(), scene.visualWorld() == null
                    ? new SkyesightVisualParticleManager(scene.level().dimension()) : scene.visualWorld().particles());
            VIEWS.put(scene.viewId(), view);
        }
        var minecraft = Minecraft.getInstance();
        Vec3 physicalEye = ((GameRendererStateAccessor) minecraft.gameRenderer).skyesight$getMainCameraField().getPosition();
        view.center = scene.view().camera().getPosition();
        view.radius = Math.min(SecondaryParticlePolicy.MAX_RADIUS, Math.max(32, scene.options().terrainRadius() * 16));
        boolean main = SecondaryParticlePolicy.usesMainParticles(scene.level() == minecraft.level, physicalEye, view.center);
        boolean changedSource = view.main != main;
        if (changedSource) view.particles.close();
        view.main = main; view.touched = tick;
        view.particles.setActive(!main, view.center, view.radius);
        if (tick - view.sent >= 10 || changedSource) send(view, !main);
        return view;
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) { clear(); return; }
        if (minecraft.isPaused()) return;
        tick++;
        for (var view : List.copyOf(VIEWS.values())) {
            if (tick - view.touched > SecondaryParticlePolicy.ACTIVITY_TICKS) { close(view.id); continue; }
            if (view.main) continue;
            int before = view.particles.totalCaptureCount();
            long started = System.nanoTime();
            var result = PortalVisualDisplayTickDriver.tick(view.id, "secondary-view", view.level, view.particles, view.center);
            view.sampleNanos = System.nanoTime() - started;
            view.samplesLastTick = result.positionsSampled();
            started = System.nanoTime();
            view.particles.tick();
            view.tickNanos = System.nanoTime() - started;
            view.spawnedLastTick = view.particles.totalCaptureCount() - before;
        }
    }

    public static boolean accept(SkyesightParticlePayload payload) {
        var view = VIEWS.get(payload.viewId());
        if (view == null || view.main || view.generation != payload.generation()
                || !view.level.dimension().equals(payload.dimension())) return false;
        view.particles.addParticle(payload);
        return true;
    }

    /** Physical entity ticks still run once; copy their spawn descriptions, never mutable particles. */
    public static void capturePhysical(ClientLevel level, ParticleOptions type, double x, double y, double z,
                                       double vx, double vy, double vz) {
        for (var view : VIEWS.values()) if (view.level == level && !view.main
                && SecondaryParticlePolicy.contains(view.center, view.radius, x, y, z)) {
            view.particles.addVisualParticle(type, x, y, z, vx, vy, vz, false, "physical-level-event");
        }
    }

    public static View get(ResourceLocation id) { return VIEWS.get(id); }
    public static void close(ResourceLocation id) {
        var view = VIEWS.remove(id);
        if (view == null) return;
        send(view, false);
        view.particles.setActive(false, view.center, view.radius);
        view.particles.close();
    }
    public static void clear() { for (var id : List.copyOf(VIEWS.keySet())) close(id); }
    private static void send(View view, boolean active) {
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(
                new SkyesightParticleWatchPayload(view.id, view.generation, view.level.dimension(), view.center, view.radius, active));
        view.sent = tick;
    }
}
