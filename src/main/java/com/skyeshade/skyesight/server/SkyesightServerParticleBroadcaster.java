package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightParticlePayload;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SkyesightServerParticleBroadcaster {
    private SkyesightServerParticleBroadcaster() {}
    public static void send(ServerLevel level, ParticleOptions type, boolean force,
                            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        send(level, null, type, force, x, y, z, count, dx, dy, dz, speed);
    }
    public static void send(ServerLevel level, ServerPlayer recipient, ParticleOptions type, boolean force,
                            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        SkyesightParticleWatches.forEvent(level, recipient, x, y, z, (player, watch) -> PacketDistributor.sendToPlayer(player,
                new SkyesightParticlePayload(watch.viewId(), watch.generation(), level.dimension(), type, force,
                        x, y, z, dx, dy, dz, speed, count)));
    }
}