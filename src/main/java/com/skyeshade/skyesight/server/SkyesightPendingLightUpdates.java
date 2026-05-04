package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightLightDataPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SkyesightPendingLightUpdates {
    private static final List<Entry> QUEUED = new ArrayList<>();

    private SkyesightPendingLightUpdates() {}

    public static void queue(
            ServerLevel level,
            UUID playerId,
            SkyesightServerViewTracker.WatchedPlayerView watched,
            ChunkPos center
    ) {
        QUEUED.add(new Entry(
                level.dimension(),
                playerId,
                watched,
                center
        ));
    }

    public static void flush(MinecraftServer server) {
        if (QUEUED.isEmpty()) {
            return;
        }

        List<Entry> entries = new ArrayList<>(QUEUED);
        QUEUED.clear();

        for (Entry entry : entries) {
            ServerLevel level = server.getLevel(entry.dimension());

            if (level == null) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());

            if (player == null) {
                continue;
            }

            sendLightForNeighborChunks(level, player, entry.watched(), entry.center());
        }
    }

    private static void sendLightForNeighborChunks(
            ServerLevel level,
            ServerPlayer player,
            SkyesightServerViewTracker.WatchedPlayerView watched,
            ChunkPos center
    ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);

                if (!watched.watch().chunks().contains(pos)) {
                    continue;
                }

                ClientboundLightUpdatePacketData lightData =
                        new ClientboundLightUpdatePacketData(
                                pos,
                                level.getLightEngine(),
                                null,
                                null
                        );

                PacketDistributor.sendToPlayer(
                        player,
                        new SkyesightLightDataPayload(
                                watched.watch().viewId(),
                                level.dimension(),
                                pos.x,
                                pos.z,
                                lightData
                        )
                );
            }
        }
    }

    private record Entry(
            ResourceKey<Level> dimension,
            UUID playerId,
            SkyesightServerViewTracker.WatchedPlayerView watched,
            ChunkPos center
    ) {}
}