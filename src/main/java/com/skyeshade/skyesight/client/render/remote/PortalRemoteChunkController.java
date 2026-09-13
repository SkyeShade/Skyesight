package com.skyeshade.skyesight.client.render.remote;

import com.skyeshade.skyesight.client.render.config.PortalRemoteChunkConfig;
import com.skyeshade.skyesight.client.render.state.PortalRemoteChunkRuntimeState;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.network.SkyesightChunkDataPayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import com.skyeshade.skyesight.server.SkyesightSecondaryChunkWatchRegion;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class PortalRemoteChunkController {
    private PortalRemoteChunkController() {}

    public static void requestCrossDimensionPortalChunks(
            Minecraft minecraft,
            String portalName,
            ResourceLocation regionId,
            ResourceKey<Level> sourceDimension,
            ResourceKey<Level> targetDimension,
            BlockPos cameraBlock,
            ChunkPos center,
            int radius
    ) {
        if (minecraft == null || minecraft.player == null || regionId == null || targetDimension == null || center == null) return;
        // Integrated and remote servers share one demand owner. The old direct sender
        // resent every chunk at a center change alongside the standby network stream.
        if (PortalNetworkStreaming.ensure(minecraft, regionId, targetDimension)) {
            SkyesightClientChunkRequester.requestChunksFor(regionId, targetDimension, center,
                    com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.radius(regionId, radius));
        }
    }
    public static void sendSecondaryWatchChunksToLocalClient(
            MinecraftServer server,
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation regionId,
            ChunkPos center,
            int radius
    ) {
        int requested = 0;
        int forceLoaded = 0;
        int packetsSent = 0;
        StringBuilder firstChunks = new StringBuilder();

        if (server == null || level == null || player == null || player.connection == null || center == null) {
            SkyesightSecondaryChunkWatchRegion.recordInitialChunkSend(
                    player,
                    regionId,
                    level == null ? null : level.dimension(),
                    center,
                    radius,
                    requested,
                    forceLoaded,
                    packetsSent,
                    "unavailable"
            );
            return;
        }

        if (player.serverLevel() != level) {
            SkyesightSecondaryChunkWatchRegion.recordInitialChunkSend(
                    player,
                    regionId,
                    level.dimension(),
                    center,
                    radius,
                    requested,
                    forceLoaded,
                    packetsSent,
                    "dimension mismatch"
            );
            return;
        }

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                requested++;
                level.setChunkForced(chunkX, chunkZ, true);

                ChunkAccess access = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                LevelChunk chunk = access instanceof LevelChunk levelChunk
                        ? levelChunk
                        : getServerLevelChunk(level, chunkX, chunkZ);

                if (chunk == null) {
                    continue;
                }

                forceLoaded++;

                if (firstChunks.length() < 80) {
                    if (!firstChunks.isEmpty()) {
                        firstChunks.append(' ');
                    }

                    firstChunks.append(chunkX).append(',').append(chunkZ);
                }

                player.connection.send(chunk.getAuxLightManager(chunk.getPos()).sendLightDataTo(
                        new ClientboundLevelChunkWithLightPacket(
                                chunk,
                                level.getLightEngine(),
                                null,
                                null
                        )
                ));
                packetsSent++;
            }
        }

        SkyesightSecondaryChunkWatchRegion.recordInitialChunkSend(
                player,
                regionId,
                level.dimension(),
                center,
                radius,
                requested,
                forceLoaded,
                packetsSent,
                firstChunks.isEmpty() ? "-" : firstChunks.toString()
        );
    }

    public static void updateRemoteChunkForceLoading(
            Minecraft minecraft,
            ChunkPos remoteChunkPos
    ) {
        if (!PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNKS) {
            resetRemoteChunkForceLoadRuntimeState();
            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null) {
            return;
        }

        ServerLevel serverLevel = server.getLevel(minecraft.level.dimension());
        if (serverLevel == null) {
            return;
        }

        if (!remoteChunkPos.equals(PortalSecondaryRenderState.forceLoadTicketCenter)) {
            PortalSecondaryRenderState.forceLoadTicketCenter = remoteChunkPos;
            PortalSecondaryRenderState.forceLoadTicketSubmitted = false;
            PortalSecondaryRenderState.forceLoadSynchronousLoadQueued = false;
            PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing = 0;
            PortalRemoteChunkRuntimeState.forceLoadRequestedChunks = 0;
            PortalRemoteChunkRuntimeState.loadedAfterWait = false;
            PortalRemoteChunkRuntimeState.requiredLoadedChunksInRadius = 0;
            PortalRemoteChunkRuntimeState.clientChunkNonAirSamples = 0;
            PortalRemoteChunkRuntimeState.clientCenterSectionNonAirCount = 0;
            PortalSecondaryRenderState.SECONDARY_VIEW.setSodiumRebuildCenter(null);
            PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames = 0;
        }

        if (!PortalSecondaryRenderState.forceLoadTicketSubmitted) {
            PortalSecondaryRenderState.forceLoadTicketSubmitted = true;
            UUID playerId = minecraft.player.getUUID();
            server.execute(() -> ticketRemoteChunksOnServer(server, serverLevel, playerId, remoteChunkPos));
        } else {
            PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing++;

            if (PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing % 10 == 0) {
                server.execute(() -> checkRemoteChunkOnServer(serverLevel, remoteChunkPos, false));
            }

            if (PortalRemoteChunkConfig.SEND_REMOTE_CHUNKS_TO_LOCAL_CLIENT
                    && PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing % PortalRemoteChunkConfig.REMOTE_CHUNK_RESEND_FRAMES == 0
                    && PortalRemoteChunkRuntimeState.requiredLoadedChunksInRadius < PortalRemoteChunkRuntimeState.forceLoadRequestedChunks) {
                UUID playerId = minecraft.player.getUUID();
                server.execute(() -> sendRemoteChunksToLocalClient(server, serverLevel, playerId, remoteChunkPos));
            }

            if (PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing >= PortalRemoteChunkConfig.FORCE_LOAD_WAIT_FRAMES) {
                server.execute(() -> checkRemoteChunkOnServer(serverLevel, remoteChunkPos, true));

                if (!PortalSecondaryRenderState.forceLoadSynchronousLoadQueued && !PortalRemoteChunkRuntimeState.loadedAfterWait) {
                    PortalSecondaryRenderState.forceLoadSynchronousLoadQueued = true;
                    server.execute(() -> synchronouslyLoadRemoteChunkOnServer(serverLevel, remoteChunkPos));
                }
            }
        }
    }

    public static int countClientLoadedChunksInRadius(
            Minecraft minecraft,
            ChunkPos center,
            int radius
    ) {
        if (minecraft.level == null) {
            return 0;
        }

        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;

                if (minecraft.level.getChunkSource().getChunk(
                        chunkX,
                        chunkZ,
                        false
                ) != null) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int countClientLoadedChunksInRequiredRadius(
            Minecraft minecraft,
            ChunkPos center,
            int radius
    ) {
        if (minecraft.level == null) {
            return 0;
        }

        int count = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;

                if (minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, false) != null) {
                    count++;
                }
            }
        }

        return count;
    }

    public static void updateRemoteClientChunkReadiness(
            Minecraft minecraft,
            ChunkPos center
    ) {
        if (minecraft.level == null) {
            PortalRemoteChunkRuntimeState.clientChunkNonAirSamples = 0;
            PortalRemoteChunkRuntimeState.clientCenterSectionNonAirCount = 0;
            return;
        }

        int[] ySamples = {60, 80, 90, 100, 120, 140, 146};
        int[] localSamples = {0, 5, 10, 15};
        int sampleNonAir = 0;

        for (int dz = -PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS; dz <= PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS; dz++) {
            for (int dx = -PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS; dx <= PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                LevelChunk chunk = minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, false);

                if (chunk == null) {
                    continue;
                }

                for (int y : ySamples) {
                    for (int localZ : localSamples) {
                        for (int localX : localSamples) {
                            BlockPos pos = new BlockPos(
                                    chunkX * 16 + localX,
                                    y,
                                    chunkZ * 16 + localZ
                            );
                            BlockState state = minecraft.level.getBlockState(pos);

                            if (!state.isAir()) {
                                sampleNonAir++;
                            }
                        }
                    }
                }
            }
        }

        LevelChunk centerChunk = minecraft.level.getChunkSource().getChunk(center.x, center.z, false);
        int centerNonAirTotal = 0;

        if (centerChunk != null) {
            LevelChunkSection[] sections = centerChunk.getSections();

            for (LevelChunkSection section : sections) {
                centerNonAirTotal += countNonAirInSection(section);
            }
        }

        PortalRemoteChunkRuntimeState.clientChunkNonAirSamples = sampleNonAir;
        PortalRemoteChunkRuntimeState.clientCenterSectionNonAirCount = centerNonAirTotal;
    }

    private static void ticketRemoteChunksOnServer(
            MinecraftServer server,
            ServerLevel level,
            UUID playerId,
            ChunkPos center
    ) {
        PortalRemoteChunkRuntimeState.forceLoadRequestedChunks = 0;

        for (int dz = -PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dz <= PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dz++) {
            for (int dx = -PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dx <= PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dx++) {
                level.setChunkForced(center.x + dx, center.z + dz, true);
                PortalRemoteChunkRuntimeState.forceLoadRequestedChunks++;
            }
        }

        checkRemoteChunkOnServer(level, center, false);

        if (PortalRemoteChunkConfig.SEND_REMOTE_CHUNKS_TO_LOCAL_CLIENT) {
            sendRemoteChunksToLocalClient(server, level, playerId, center);
        }
    }

    private static void sendRemoteChunksToLocalClient(
            MinecraftServer server,
            ServerLevel level,
            UUID playerId,
            ChunkPos center
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);

        if (player == null || player.connection == null) {
            return;
        }

        if (player.serverLevel() != level) {
            return;
        }

        for (int dz = -PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dz <= PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dz++) {
            for (int dx = -PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dx <= PortalRemoteChunkConfig.REMOTE_CHUNK_CLIENT_LOAD_RADIUS; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                LevelChunk chunk = getServerLevelChunk(level, chunkX, chunkZ);

                if (chunk == null) {
                    continue;
                }

                player.connection.send(chunk.getAuxLightManager(chunk.getPos()).sendLightDataTo(
                        new ClientboundLevelChunkWithLightPacket(
                                chunk,
                                level.getLightEngine(),
                                null,
                                null
                        )
                ));
            }
        }
    }

    private static void checkRemoteChunkOnServer(
            ServerLevel level,
            ChunkPos center,
            boolean delayed
    ) {
        boolean serverHasChunk = level.getChunkSource().hasChunk(center.x, center.z);
        boolean getChunkNowPresent = level.getChunkSource().getChunkNow(center.x, center.z) != null;
        ChunkAccess chunk = level.getChunkSource().getChunk(
                center.x,
                center.z,
                ChunkStatus.FULL,
                false
        );
        boolean getChunkFalsePresent = chunk instanceof LevelChunk;

        if (delayed) {
            PortalRemoteChunkRuntimeState.loadedAfterWait = serverHasChunk
                    || getChunkFalsePresent
                    || getChunkNowPresent;
        }

    }

    private static void synchronouslyLoadRemoteChunkOnServer(ServerLevel level, ChunkPos center) {
        level.getChunkSource().getChunk(
                center.x,
                center.z,
                ChunkStatus.FULL,
                true
        );

        checkRemoteChunkOnServer(level, center, true);
    }

    public static void resetRemoteChunkForceLoadRuntimeState() {
        PortalRemoteChunkRuntimeState.loadedAfterWait = false;
        PortalRemoteChunkRuntimeState.clientChunkNonAirSamples = 0;
        PortalRemoteChunkRuntimeState.clientCenterSectionNonAirCount = 0;
        PortalSecondaryRenderState.SECONDARY_VIEW.setSodiumRebuildCenter(null);
        PortalSecondaryRenderState.sodiumDelayedRebuildStableFrames = 0;
        PortalRemoteChunkRuntimeState.forceLoadFramesSinceTicketing = 0;
        PortalRemoteChunkRuntimeState.forceLoadRequestedChunks = 0;
        PortalRemoteChunkRuntimeState.loadedChunksInRadius = 0;
        PortalRemoteChunkRuntimeState.requiredLoadedChunksInRadius = 0;
        PortalSecondaryRenderState.forceLoadTicketCenter = null;
        PortalSecondaryRenderState.forceLoadTicketSubmitted = false;
        PortalSecondaryRenderState.forceLoadSynchronousLoadQueued = false;
    }

    private static int countNonAirInSection(LevelChunkSection section) {
        if (section.hasOnlyAir()) {
            return 0;
        }

        int count = 0;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!section.getBlockState(x, y, z).isAir()) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private static LevelChunk getServerLevelChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return null;
        }

        ChunkAccess access = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

        return access instanceof LevelChunk levelChunk ? levelChunk : null;
    }
}
