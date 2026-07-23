package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.network.SkyesightBlockUpdatesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightServerBlockUpdateBroadcaster {
    private static final boolean DEBUG_VERBOSE_PORTAL_STREAMING_DIAGNOSTICS = false;

    private SkyesightServerBlockUpdateBroadcaster() {}
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SkyesightPendingLightUpdates.flush(event.getServer());
    }
    public static void send(ServerLevel level, BlockPos pos, BlockState state) {
        ChunkPos changedChunk = new ChunkPos(pos);
        int regionPlayers = 0;
        int blockPackets = 0;
        int blockEntityPackets = 0;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag blockEntityTag = blockEntity == null ? null : blockEntity.getUpdateTag(level.registryAccess());

        for (SkyesightServerViewTracker.WatchedPlayerView watched :
                SkyesightServerViewTracker.viewsWatching(level.dimension(), changedChunk)) {
            UUID playerId = watched.playerId();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

            if (player == null) {
                continue;
            }

            regionPlayers++;
            PacketDistributor.sendToPlayer(
                    player,
                    new SkyesightBlockUpdatesPayload(
                            watched.watch().viewId(),
                            level.dimension(),
                            List.of(new SkyesightBlockUpdatesPayload.Entry(
                                    pos.immutable(),
                                    state,
                                    blockEntityTag == null ? null : blockEntityTag.copy()
                            ))
                    )
            );
            blockPackets++;
            SkyesightSecondaryChunkWatchRegion.recordBlockUpdateForwarded(pos);
            SkyesightPendingLightUpdates.queue(level, playerId, watched, changedChunk);

            if (blockEntityTag != null) {
                blockEntityPackets++;
                SkyesightSecondaryChunkWatchRegion.recordBlockEntityUpdateForwarded(pos);
            }

            if (SkyesightDebugConfig.WATCH_DEBUG && !player.serverLevel().dimension().equals(level.dimension())) {
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_UPDATE: viewId={} displayDimension={} cameraDimension={} chunk={},{} blockPos={} blockEntityType={}",
                        watched.watch().viewId(),
                        player.serverLevel().dimension().location(),
                        level.dimension().location(),
                        changedChunk.x,
                        changedChunk.z,
                        pos,
                        blockEntity == null ? "-" : blockEntity.getType()
                );

                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_UPDATE_SEND: viewId={} displayDimension={} cameraDimension={} blockPos={} blockState={} blockEntityType={} hasTag={}",
                        watched.watch().viewId(),
                        player.serverLevel().dimension().location(),
                        level.dimension().location(),
                        pos,
                        state,
                        blockEntity == null ? "-" : blockEntity.getType(),
                        blockEntityTag == null ? "no" : "yes"
                );
            }
        }

        if (DEBUG_VERBOSE_PORTAL_STREAMING_DIAGNOSTICS) {
            Skyesight.LOGGER.info(
                    "[Skyesight] Remote block update hook pos={} chunk={},{} dim={} newState={} regionPlayers={} blockPackets={} blockEntityPackets={} activeRegions={} watchedChunks={}",
                    pos,
                    changedChunk.x,
                    changedChunk.z,
                    level.dimension().location(),
                    state,
                    regionPlayers,
                    blockPackets,
                    blockEntityPackets,
                    SkyesightSecondaryChunkWatchRegion.activeRegionCount(),
                    SkyesightSecondaryChunkWatchRegion.watchedChunkCount()
            );
        }
    }
}
