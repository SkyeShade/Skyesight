package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightRemoteViewLifecyclePayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteCenterDiagnostics;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;

public final class SkyesightRemoteViewLifecycleHandler {
    private SkyesightRemoteViewLifecycleHandler() {}

    public static void handle(
            SkyesightRemoteViewLifecyclePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || payload.viewId() == null
                    || payload.targetDimension() == null
                    || payload.generation() <= 0L) {
                return;
            }

            SkyesightRemoteCenterDiagnostics.log("server-lifecycle-received",payload.viewId(),payload.generation(),
                    "player="+player.getUUID()+" active="+payload.active()+" current="+SkyesightServerRemoteViewRegistry.get(player,payload.viewId()));
            if (!payload.active()) {
                SkyesightServerRemoteViewRegistry.unregister(
                        player,
                        payload.viewId(),
                        payload.generation()
                ).ifPresent(registration -> cleanup(player, registration));
                return;
            }

            if (player.server.getLevel(payload.targetDimension()) == null) {
                return;
            }

            SkyesightRemoteViewRegistration previous =
                    SkyesightServerRemoteViewRegistry.get(player, payload.viewId()).orElse(null);
            if (!SkyesightServerRemoteViewRegistry.register(
                    player,
                    payload.viewId(),
                    payload.targetDimension(),
                    payload.generation()
            )) {
                return;
            }

            SkyesightRemoteCenterDiagnostics.log("server-register",payload.viewId(),payload.generation(),"player="+player.getUUID());
            if (previous != null && (previous.generation() != payload.generation()
                    || !previous.targetDimension().equals(payload.targetDimension()))) {
                cleanup(player, previous);
            }

            if (SkyesightDebugConfig.WATCH_DEBUG) {
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CAMERA_REMOTE_REGISTERED: player={} view={} generation={} dim={}",
                        player.getGameProfile().getName(),
                        payload.viewId(),
                        payload.generation(),
                        payload.targetDimension().location()
                );
            }
        });
    }

    public static void removeView(ServerPlayer player, ResourceLocation viewId) {
        SkyesightServerRemoteViewRegistry.get(player, viewId).ifPresent(registration -> {
            SkyesightRemoteCenterDiagnostics.log("server-api-close",viewId,registration.generation(),
                    "player="+player.getUUID()+" alive="+player.isAlive()+" physicalDim="+player.level().dimension().location()
                    +" mainHand="+player.getMainHandItem().getItem());
            SkyesightServerRemoteViewRegistry.unregister(player, viewId, registration.generation())
                    .ifPresent(retired -> cleanup(player, retired));
        });
    }

    public static void removeAll(ServerPlayer player) {
        Collection<SkyesightRemoteViewRegistration> registrations =
                SkyesightServerRemoteViewRegistry.removePlayer(player);
        registrations.forEach(registration -> cleanup(player, registration));
    }
    public static void dimensionChanged(ServerPlayer player) {
        SkyesightServerRemoteViewRegistry.removeNonTraversalViews(player).forEach(registration -> cleanup(player, registration));
    }
    public static void releaseWatch(ServerPlayer player, ResourceLocation id) {
        var registration = SkyesightServerRemoteViewRegistry.resolve(player, id).orElse(null);
        if (registration != null) cleanup(player, registration);
        else {
            SkyesightServerChunkLoader.removeView(player, id, null);
            SkyesightServerViewTracker.removeView(player, id);
            SkyesightSecondaryWatchRegion.removeRegion(player, id);
            SkyesightSecondaryChunkWatchRegion.removeRegion(player, id);
            PortalSimulationCoordinator.remove(player, id);
        }
    }

    private static void cleanup(
            ServerPlayer player,
            SkyesightRemoteViewRegistration registration
    ) {
        SkyesightRemoteCenterDiagnostics.log("server-retire",registration.viewId(),registration.generation(),"player="+player.getUUID());
        ServerLevel level = player.server.getLevel(registration.targetDimension());
        SkyesightServerChunkLoader.removeView(player, registration.viewId(), level);
        SkyesightServerViewTracker.removeView(player, registration.viewId());
        SkyesightSecondaryWatchRegion.removeRegion(player, registration.viewId());
        SkyesightSecondaryChunkWatchRegion.removeRegion(player, registration.viewId());
        PortalSimulationCoordinator.remove(player, registration.viewId());
        if (SkyesightDebugConfig.WATCH_DEBUG) {
            Skyesight.LOGGER.info("[Skyesight] CAMERA_REMOTE_RELEASED player={} view={} generation={} target={} watches=0 ownedChunks=0",
                    player.getUUID(), registration.viewId(), registration.generation(), registration.targetDimension().location());
        }
    }
}
