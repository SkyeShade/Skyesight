package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.network.SkyesightChunkDataPayload;
import com.skyeshade.skyesight.network.SkyesightChunkRequestPayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public final class SkyesightServerChunkSender {
    private static final int MAX_CHUNKS_PER_REQUEST = 256;

    private SkyesightServerChunkSender() {}

    public static void handleChunkRequest(
            SkyesightChunkRequestPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            SkyesightRemoteViewRegistration registration =
                    SkyesightRemoteViewRegistry.get(payload.viewId()).orElse(null);
            if (registration == null
                    || registration.generation() != payload.viewGeneration()
                    || !registration.targetDimension().equals(payload.dimension())) {
                return;
            }

            ServerLevel level = player.server.getLevel(payload.dimension());
            int loadRadius = payload.radius();

            SkyesightServerChunkLoader.updateLoadedView(
                    player,
                    payload.viewId(),
                    level,
                    payload.centerChunkX(),
                    payload.centerChunkZ(),
                    loadRadius
            );
            if (level == null) {
                Skyesight.LOGGER.warn(
                        "[Skyesight] Ignoring chunk request for missing dimension {}",
                        payload.dimension().location()
                );
                return;
            }

            int sent = 0;

            for (ChunkPos pos : payload.chunks()) {
                if (sent >= MAX_CHUNKS_PER_REQUEST) {
                    break;
                }

                if (!isWithinRequestRadius(
                        pos,
                        payload.centerChunkX(),
                        payload.centerChunkZ(),
                        payload.radius()
                )) {
                    continue;
                }

                LevelChunk chunk = level.getChunk(pos.x, pos.z);

                ClientboundLevelChunkPacketData chunkData =
                        new ClientboundLevelChunkPacketData(chunk);

                ClientboundLightUpdatePacketData lightData =
                        new ClientboundLightUpdatePacketData(
                                chunk.getPos(),
                                level.getLightEngine(),
                                null,
                                null
                        );

                PacketDistributor.sendToPlayer(
                        player,
                        new SkyesightChunkDataPayload(
                                payload.viewId(),
                                payload.viewGeneration(),
                                payload.dimension(),
                                payload.centerChunkX(),
                                payload.centerChunkZ(),
                                payload.radius(),
                                pos.x,
                                pos.z,
                                chunkData,
                                lightData
                        )
                );
                if (SkyesightDebugConfig.WATCH_DEBUG
                        && !player.serverLevel().dimension().equals(level.dimension())) {
                    Skyesight.LOGGER.info(
                            "[Skyesight] SKYESIGHT_CROSS_DIM_INITIAL_BLOCK_ENTITY_SEND: viewId={} cameraDimension={} chunkPos={},{} blockEntityCount={}",
                            payload.viewId(),
                            level.dimension().location(),
                            pos.x,
                            pos.z,
                            chunk.getBlockEntitiesPos().size()
                    );
                }

                sent++;
            }

            List<ChunkPos> watchedChunks = buildWatchedChunks(
                    payload.centerChunkX(),
                    payload.centerChunkZ(),
                    payload.radius()
            );

            SkyesightServerViewTracker.updateWatch(
                    player,
                    payload.viewId(),
                    payload.dimension(),
                    payload.centerChunkX(),
                    payload.centerChunkZ(),
                    payload.radius(),
                    watchedChunks
            );
            SkyesightServerViewTracker.ViewWatch watch =
                    SkyesightServerViewTracker.getWatch(player, payload.viewId());

            if (watch != null) {
                SkyesightServerEntitySnapshotSender.sendSnapshot(player, watch, level);
            }
            if (SkyesightDebugConfig.WATCH_DEBUG
                    && level != null
                    && !player.serverLevel().dimension().equals(level.dimension())) {
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_WATCH_REGION: viewId={} displayDimension={} cameraDimension={} centerChunk={},{} radius={} chunksSent={}",
                        payload.viewId(),
                        player.serverLevel().dimension().location(),
                        level.dimension().location(),
                        payload.centerChunkX(),
                        payload.centerChunkZ(),
                        payload.radius(),
                        sent
                );
            }
            if (SkyesightDebugConfig.WATCH_DEBUG) {
                Skyesight.LOGGER.info(
                        "[Skyesight] Sent {} chunks for view {} and watching {} chunks around {}, {}",
                        sent,
                        payload.viewId(),
                        watchedChunks.size(),
                        payload.centerChunkX(),
                        payload.centerChunkZ()
                );
            }
        });
    }

    private static List<ChunkPos> buildWatchedChunks(
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        List<ChunkPos> chunks = new ArrayList<>();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                chunks.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        return chunks;
    }

    private static boolean isWithinRequestRadius(
            ChunkPos pos,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        return Math.abs(pos.x - centerChunkX) <= radius
                && Math.abs(pos.z - centerChunkZ) <= radius;
    }
}
