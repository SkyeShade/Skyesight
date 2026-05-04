package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.network.SkyesightBlockUpdatesPayload;
import com.skyeshade.skyesight.network.SkyesightLightDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightServerBlockUpdateBroadcaster {
    private SkyesightServerBlockUpdateBroadcaster() {}
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SkyesightPendingLightUpdates.flush(event.getServer());
    }
    public static void send(ServerLevel level, BlockPos pos, BlockState state) {
        ChunkPos changedChunk = new ChunkPos(pos);
        MinecraftServer server = level.getServer();

        for (SkyesightServerViewTracker.WatchedPlayerView watched :
                SkyesightServerViewTracker.viewsWatching(level.dimension(), changedChunk)) {
            ServerPlayer player = server.getPlayerList().getPlayer(watched.playerId());

            if (player == null) {
                continue;
            }

            PacketDistributor.sendToPlayer(
                    player,
                    new SkyesightBlockUpdatesPayload(
                            watched.watch().viewId(),
                            level.dimension(),
                            List.of(new SkyesightBlockUpdatesPayload.Entry(pos.immutable(), state))
                    )
            );

            SkyesightPendingLightUpdates.queue(level, player.getUUID(), watched, changedChunk);
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
}