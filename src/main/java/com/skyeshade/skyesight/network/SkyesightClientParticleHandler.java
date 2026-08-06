package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SkyesightClientParticleHandler {
    private static long lastParticleLogMillis;
    private SkyesightClientParticleHandler() {}

    public static void handle(SkyesightParticlePayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());

        if (world == null || world.isClosed()) {
            world = SkyesightVisualWorldManager.getOrCreateIfCurrent(payload.viewId(), payload.dimension());
        }

        if (world == null || world.isClosed()) {
            return;
        }

        world.particles().addParticle(payload);
        logIfDue(payload, world);
    }

    private static void logIfDue(SkyesightParticlePayload payload, SkyesightVisualWorld world) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.PACKET_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastParticleLogMillis < 3000L) {
            return;
        }

        lastParticleLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] Cross-dim real particle mirror clientReceived=yes viewId={} targetDim={} eventParticleType={} visualWorldFound=yes appliedToVisualWorld=yes visualStoreSize={} diagnosticSpawnEnabled=false store='{}'",
                payload.viewId(),
                payload.dimension().location(),
                BuiltInRegistries.PARTICLE_TYPE.getKey(payload.particle().getType()),
                world.particles().size(),
                world.particles().debugSummary()
        );
    }
}
