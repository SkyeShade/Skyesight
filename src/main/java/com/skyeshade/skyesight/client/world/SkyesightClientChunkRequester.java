package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.network.SkyesightChunkRequestPayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import com.skyeshade.skyesight.remote.SkyesightRemoteCenterDiagnostics;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

public final class SkyesightClientChunkRequester {
    private static final Map<ResourceLocation, SkyesightChunkDemand> STATES = new HashMap<>();
    private SkyesightClientChunkRequester() {}

    public static void requestChunksFor(ResourceLocation viewId, ResourceKey<Level> dimension, Camera camera, int radius) {
        requestChunksFor(viewId, dimension,
                new ChunkPos(Mth.floor(camera.getPosition().x()) >> 4, Mth.floor(camera.getPosition().z()) >> 4), radius);
    }

    /** Portal callers already have a transformed destination chunk; share the same demand lifecycle. */
    public static void requestChunksFor(ResourceLocation viewId, ResourceKey<Level> dimension, ChunkPos center, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        var registration = SkyesightRemoteViewRegistry.get(viewId).orElse(null);
        if (registration == null || !registration.targets(dimension)) return;
        var world = SkyesightVisualWorldManager.getOrCreate(viewId, dimension);
        if (world == null) return;
        var state = stateFor(viewId,registration.generation(),dimension);
        ChunkPos previous = state.center;
        world.chunkReceiver().setViewCenter(center.x,center.z,radius+3);
        world.chunkReceiver().pruneOutside(center.x,center.z,radius+3);
        var missing = state.update(center,radius,System.nanoTime(),world.chunkReceiver()::hasChunk);
        long sequence = missing == null ? 0L : SkyesightRemoteCenterDiagnostics.nextSequence();
        if (SkyesightRemoteCenterDiagnostics.enabled() && (missing != null
                || SkyesightRemoteCenterDiagnostics.due("client",viewId,registration.generation()))) {
            SkyesightRemoteCenterDiagnostics.trace("CLIENT_CAMERA_CENTER",viewId,registration.generation(),sequence,
                    "cameraChunk="+center+" desiredCenter="+state.center+" previousCenter="+previous
                    +" missingCount="+(missing==null?0:missing.size())+" pendingCount="+state.pending.size());
            SkyesightRemoteCenterDiagnostics.trace("VISUAL_CENTER",viewId,registration.generation(),sequence,
                    "receiverCenter="+world.chunkReceiver().viewCenter());
        }
        if (missing == null) return;
        PacketDistributor.sendToServer(new SkyesightChunkRequestPayload(viewId,registration.generation(),sequence,dimension,
                center.x,center.z,radius,missing));
        SkyesightRemoteCenterDiagnostics.trace("CLIENT_REQUEST_SENT",viewId,registration.generation(),sequence,
                "payloadCenter="+center+" requestedCount="+missing.size());
    }

    static SkyesightChunkDemand stateFor(ResourceLocation id,long generation,ResourceKey<Level> dimension) {
        var state = STATES.get(id);
        if (state == null || !state.matches(generation,dimension)) {
            state = new SkyesightChunkDemand(generation,dimension);
            STATES.put(id,state);
        }
        return state;
    }

    /** Apply response data inside the current camera region, never recenter on an old response. */
    public static boolean prepareResponse(ResourceLocation id,long generation,ResourceKey<Level> dimension,
            SkyesightRemoteChunkReceiver receiver,int packetCenterX,int packetCenterZ,int packetRadius,int chunkX,int chunkZ) {
        var state = STATES.get(id);
        if (state == null) {
            // Portal-only callers publish their center in the packet and have no camera requester.
            receiver.setViewCenter(packetCenterX,packetCenterZ,packetRadius+3);
            receiver.pruneOutside(packetCenterX,packetCenterZ,packetRadius+3);
            return true;
        }
        if (!state.matches(generation,dimension)) return false;
        state.acknowledge(generation,dimension,chunkX,chunkZ);
        receiver.setViewCenter(state.center.x,state.center.z,state.radius+3);
        receiver.pruneOutside(state.center.x,state.center.z,state.radius+3);
        if (SkyesightRemoteCenterDiagnostics.due("response",id,generation)) {
            SkyesightRemoteCenterDiagnostics.log("response",id,generation,
                    "payloadCenter="+new ChunkPos(packetCenterX,packetCenterZ)+" receiverCenter="+receiver.viewCenter()
                    +" chunk="+new ChunkPos(chunkX,chunkZ));
        }
        return state.contains(new ChunkPos(chunkX,chunkZ),state.radius+3);
    }

    public static void markChunkReceived(ResourceLocation id, long generation, ResourceKey<Level> dimension, int x,int z) {
        var state = STATES.get(id);
        if (state != null) state.acknowledge(generation,dimension,x,z);
    }
    public static void reset(ResourceLocation id,long generation) {
        var state = STATES.get(id);
        if (state != null && state.generation==generation) STATES.remove(id);
    }
    public static void reset(ResourceLocation id) { STATES.remove(id); }
    public static void reset() { STATES.clear(); }
    public static ViewRequestDiagnostics diagnostics(ResourceLocation id) {
        var state = STATES.get(id);
        return state==null ? new ViewRequestDiagnostics(null,0,0)
                : new ViewRequestDiagnostics(state.center,state.lastRequestedCount,state.pending.size());
    }
    public record ViewRequestDiagnostics(ChunkPos center,int lastRequestedChunks,int pendingChunks) {}
}
