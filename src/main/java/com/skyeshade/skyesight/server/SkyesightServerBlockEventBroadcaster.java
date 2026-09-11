package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightBlockEventPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SkyesightServerBlockEventBroadcaster {
    private SkyesightServerBlockEventBroadcaster() {}

    public static void send(ServerLevel level, BlockPos pos, int type, int data) {
        ChunkPos chunkPos = new ChunkPos(pos);
        MinecraftServer server = level.getServer();

        for (SkyesightServerViewTracker.WatchedPlayerView watched :
                SkyesightServerViewTracker.viewsWatching(level.dimension(), chunkPos)) {
            ServerPlayer player = server.getPlayerList().getPlayer(watched.playerId());

            if (player == null) {
                continue;
            }

            PacketDistributor.sendToPlayer(
                    player,
                    new SkyesightBlockEventPayload(
                            watched.watch().viewId(),
                            watched.watch().generation(),
                            level.dimension(),
                            pos.immutable(),
                            type,
                            data
                    )
            );
        }
    }
}