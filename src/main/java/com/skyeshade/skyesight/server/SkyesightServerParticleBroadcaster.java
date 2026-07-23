package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightParticlePayload;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SkyesightServerParticleBroadcaster {
    private static long lastMirrorLogMillis;
    private SkyesightServerParticleBroadcaster() {}

    public static void send(
            ServerLevel level,
            ParticleOptions particle,
            boolean overrideLimiter,
            double x,
            double y,
            double z,
            int count,
            double xDist,
            double yDist,
            double zDist,
            double maxSpeed
    ) {
        ChunkPos chunkPos = new ChunkPos(
                Mth.floor(x) >> 4,
                Mth.floor(z) >> 4
        );

        MinecraftServer server = level.getServer();
        int matchedRegions = 0;
        int payloadsSent = 0;
        StringBuilder matched = new StringBuilder();

        for (SkyesightServerViewTracker.WatchedPlayerView watched :
                SkyesightServerViewTracker.viewsWatching(level.dimension(), chunkPos)) {
            ServerPlayer player = server.getPlayerList().getPlayer(watched.playerId());

            if (player == null) {
                continue;
            }
            if (player.serverLevel().dimension().equals(level.dimension())) {
                continue;
            }

            matchedRegions++;
            PacketDistributor.sendToPlayer(
                    player,
                    new SkyesightParticlePayload(
                            watched.watch().viewId(),
                            level.dimension(),
                            particle,
                            overrideLimiter,
                            x,
                            y,
                            z,
                            xDist,
                            yDist,
                            zDist,
                            maxSpeed,
                            count
                    )
            );
            payloadsSent++;
            if (matched.length() < 200) {
                if (matched.length() > 0) {
                    matched.append(';');
                }
                matched.append(watched.watch().viewId());
            }
        }
        logMirrorIfDue(level, particle, x, y, z, matchedRegions, payloadsSent, matched.toString());
    }

    private static void logMirrorIfDue(
            ServerLevel level,
            ParticleOptions particle,
            double x,
            double y,
            double z,
            int matchedRegions,
            int payloadsSent,
            String matched
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMirrorLogMillis < 3000L) {
            return;
        }

        lastMirrorLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] Cross-dim real particle mirror dimension={} eventParticleType={} pos={},{},{} matchedRegions={} matchedViews={} payloadSent={} clientReceived=see-client-handler visualStoreSize=see-client-handler proofSpawnEnabled=false",
                level.dimension().location(),
                BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType()),
                format(x),
                format(y),
                format(z),
                matchedRegions,
                matched.isBlank() ? "-" : matched,
                payloadsSent
        );
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
