package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightChunkDataPayload;
import com.skyeshade.skyesight.network.SkyesightChunkRequestPayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteCenterDiagnostics;
import com.skyeshade.skyesight.remote.SkyesightRemoteRadiusPolicy;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

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
                    SkyesightServerRemoteViewRegistry.resolve(player, payload.viewId()).orElse(null);
            String reason = registration == null ? "unregistered"
                    : registration.generation() != payload.viewGeneration() ? "generation_mismatch"
                    : !registration.targetDimension().equals(payload.dimension()) ? "dimension_mismatch" : "accepted";
            SkyesightRemoteCenterDiagnostics.trace("SERVER_REQUEST_RECEIVED",payload.viewId(),payload.viewGeneration(),payload.requestSequence(),
                    "player="+player.getUUID()+" payloadCenter="+payload.centerChunkX()+","+payload.centerChunkZ()
                    +" registered="+(registration!=null)+" accepted="+reason.equals("accepted")+" reason="+reason
                    +" currentGeneration="+(registration==null?0:registration.generation()));
            if (registration == null
                    || registration.generation() != payload.viewGeneration()
                    || !registration.targetDimension().equals(payload.dimension())) {
                if (SkyesightRemoteCenterDiagnostics.due("server-rejected-"+player.getUUID(),payload.viewId(),payload.viewGeneration()))
                    SkyesightRemoteCenterDiagnostics.log("server-rejected",payload.viewId(),payload.viewGeneration(),"player="+player.getUUID()+" current="+registration+" payloadCenter="+payload.centerChunkX()+","+payload.centerChunkZ());
                return;
            }

            ServerLevel level = player.server.getLevel(payload.dimension());
            // ChunkMap clamps normal server view distance to 2..32; mirror its upper bound.
            int serverMaximum = Math.max(0, Math.min(32, player.server.getPlayerList().getViewDistance()));
            int loadRadius = SkyesightRemoteRadiusPolicy.accepted(payload.radius(),serverMaximum);
            if (SkyesightRemoteCenterDiagnostics.due("radius-"+player.getUUID(),payload.viewId(),payload.viewGeneration()))
                Skyesight.LOGGER.info("REMOTE_RADIUS_ACCEPTED player={} view={} requested={} serverMax={} accepted={}",
                        player.getUUID(),payload.viewId(),payload.radius(),serverMaximum,loadRadius);
            var request = new SkyesightChunkRequestPayload(payload.viewId(),payload.viewGeneration(),payload.requestSequence(),
                    payload.dimension(),payload.centerChunkX(),payload.centerChunkZ(),loadRadius,payload.chunks());

            if (level == null) {
                Skyesight.LOGGER.warn(
                        "[Skyesight] Ignoring chunk request for missing dimension {}",
                        payload.dimension().location()
                );
                return;
            }

            try (var timing = SkyesightRemoteLoadDiagnostics.begin(player, level, request)) {
            long phase = System.nanoTime();
            SkyesightServerChunkLoader.updateLoadedView(
                    player,
                    request.viewId(),
                    level,
                    request.centerChunkX(),
                    request.centerChunkZ(),
                    loadRadius, request.viewGeneration(), request.requestSequence()
            );
            if (timing != null) timing.forceNanos += System.nanoTime() - phase;
            int sent = 0;

            for (ChunkPos pos : request.chunks()) {
                if (sent >= MAX_CHUNKS_PER_REQUEST) {
                    break;
                }

                if (!isWithinRequestRadius(
                        pos,
                        request.centerChunkX(),
                        request.centerChunkZ(),
                        request.radius()
                )) {
                    continue;
                }

                phase = System.nanoTime();
                LevelChunk chunk = level.getChunk(pos.x, pos.z);
                if (timing != null) timing.acquireNanos += System.nanoTime() - phase;
                phase = System.nanoTime();

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
                                request.viewId(),
                                request.viewGeneration(),
                                request.requestSequence(),
                                request.dimension(),
                                request.centerChunkX(),
                                request.centerChunkZ(),
                                request.radius(),
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
                            request.viewId(),
                            level.dimension().location(),
                            pos.x,
                            pos.z,
                            chunk.getBlockEntitiesPos().size()
                    );
                }

                if (timing != null) timing.serializeNanos += System.nanoTime() - phase;
                sent++;
            }

            if (timing != null) timing.sent = sent;
            phase = System.nanoTime();
            List<ChunkPos> watchedChunks = buildWatchedChunks(
                    request.centerChunkX(),
                    request.centerChunkZ(),
                    request.radius()
            );

            var oldWatch = SkyesightServerViewTracker.getWatch(player, request.viewId());
            SkyesightServerViewTracker.updateWatch(
                    player,
                    request.viewId(),
                    request.viewGeneration(),
                    request.dimension(),
                    request.centerChunkX(),
                    request.centerChunkZ(),
                    request.radius(),
                    watchedChunks
            );
            SkyesightServerViewTracker.ViewWatch watch =
                    SkyesightServerViewTracker.getWatch(player, request.viewId());

            if (SkyesightRemoteCenterDiagnostics.due("server-accepted-"+player.getUUID(),request.viewId(),request.viewGeneration()))
                SkyesightRemoteCenterDiagnostics.log("server-accepted",request.viewId(),request.viewGeneration(),"player="+player.getUUID()+" requestCenter="+request.centerChunkX()+","+request.centerChunkZ()+" serverWatchCenter="+(watch==null?"none":watch.centerChunkX()+","+watch.centerChunkZ())+" sent="+sent);
            SkyesightRemoteCenterDiagnostics.trace("SERVER_WATCH_UPDATE",request.viewId(),request.viewGeneration(),request.requestSequence(),
                    "player="+player.getUUID()+" oldCenter="+(oldWatch==null?"none":oldWatch.centerChunkX()+","+oldWatch.centerChunkZ())
                    +" newCenter="+(watch==null?"none":watch.centerChunkX()+","+watch.centerChunkZ())+" watchChunks="+watchedChunks.size()
                    +" sent="+sent+" registeredAfter="+SkyesightServerRemoteViewRegistry.get(player,request.viewId()));
            if (watch != null) {
                SkyesightServerEnvironmentSender.send(player, watch, level);
                SkyesightServerEntitySnapshotSender.sendSnapshot(player, watch, level);
            }
            if (SkyesightDebugConfig.WATCH_DEBUG
                    && level != null
                    && !player.serverLevel().dimension().equals(level.dimension())) {
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_WATCH_REGION: viewId={} displayDimension={} cameraDimension={} centerChunk={},{} radius={} chunksSent={}",
                        request.viewId(),
                        player.serverLevel().dimension().location(),
                        level.dimension().location(),
                        request.centerChunkX(),
                        request.centerChunkZ(),
                        request.radius(),
                        sent
                );
            }
            if (SkyesightDebugConfig.WATCH_DEBUG) {
                Skyesight.LOGGER.info(
                        "[Skyesight] Sent {} chunks for view {} and watching {} chunks around {}, {}",
                        sent,
                        request.viewId(),
                        watchedChunks.size(),
                        request.centerChunkX(),
                        request.centerChunkZ()
                );
            }
            if (timing != null) timing.watchNanos += System.nanoTime() - phase;
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
        return Math.abs((long) pos.x - centerChunkX) <= radius
                && Math.abs((long) pos.z - centerChunkZ) <= radius;
    }
}
