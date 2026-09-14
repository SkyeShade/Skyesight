package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import com.skyeshade.skyesight.client.world.SkyesightClientDimensionMetadata;
import com.skyeshade.skyesight.server.portal.PortalInteractions;
import com.skyeshade.skyesight.server.portal.PortalProxyArmorStandDebugManager;
import com.skyeshade.skyesight.server.SkyesightParticleWatches;
import com.skyeshade.skyesight.server.SkyesightRemoteViewLifecycleHandler;
import com.skyeshade.skyesight.server.SkyesightServerChunkSender;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SkyesightPayloads {
    private SkyesightPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        if (SkyesightDebugConfig.SOURCE_MAP) {
            Skyesight.LOGGER.info("[Skyesight] Registering network payloads");
        }

        PayloadRegistrar registrar = event.registrar(Skyesight.MODID)
                .versioned("15");

        registrar.playToClient(SkyesightRegionTraversalPayload.TYPE,SkyesightRegionTraversalPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->com.skyeshade.skyesight.client.portal.PortalRegionClient.traverse(payload)));

        registrar.playToClient(SkyesightPortalRegionPayload.TYPE, SkyesightPortalRegionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.skyeshade.skyesight.client.portal.PortalRegionClient.receive(payload)));

        registrar.playToServer(SkyesightPortalInteractionPayload.TYPE, SkyesightPortalInteractionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                        PortalInteractions.handle(player, payload);
                }));
        registrar.playToClient(SkyesightLevelEventPayload.TYPE, SkyesightLevelEventPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SkyesightClientLevelEventHandler.handle(payload)));
        registrar.playToClient(SkyesightPortalMiningPayload.TYPE, SkyesightPortalMiningPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        PortalInteractionClient.receive(payload)));

        registrar.playToClient(SkyesightTraversalPayload.TYPE, SkyesightTraversalPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TraversalPortalClient.receive(payload)));

        registrar.playToServer(SkyesightParticleWatchPayload.TYPE, SkyesightParticleWatchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                        SkyesightParticleWatches.update(player, payload);
                }));

        registrar.playToServer(
                SkyesightChunkRequestPayload.TYPE,
                SkyesightChunkRequestPayload.STREAM_CODEC,
                SkyesightServerChunkSender::handleChunkRequest
        );
        registrar.playToServer(
                SkyesightRemoteViewLifecyclePayload.TYPE,
                SkyesightRemoteViewLifecyclePayload.STREAM_CODEC,
                SkyesightRemoteViewLifecycleHandler::handle
        );
        registrar.playToServer(
                SkyesightProxyMarkerPayload.TYPE,
                SkyesightProxyMarkerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PortalProxyArmorStandDebugManager.handleMarker(payload, player);
                            }
                        }
                )
        );

        registrar.playToClient(
                SkyesightChunkDataPayload.TYPE,
                SkyesightChunkDataPayload.STREAM_CODEC,
                SkyesightClientboundPayloads::handleChunkData
        );
        registrar.playToClient(
                SkyesightBlockUpdatesPayload.TYPE,
                SkyesightBlockUpdatesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientBlockUpdateHandler.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightLightDataPayload.TYPE,
                SkyesightLightDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientLightDataHandler.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightEntitySnapshotPayload.TYPE,
                SkyesightEntitySnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientEntityHandler.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightVisualEntityVanillaPacketPayload.TYPE,
                SkyesightVisualEntityVanillaPacketPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightPortalEntityPacketApplier.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightBlockEventPayload.TYPE,
                SkyesightBlockEventPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientBlockEventHandler.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightParticlePayload.TYPE,
                SkyesightParticlePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientParticleHandler.handle(payload)
                )
        );
        registrar.playToClient(
                SkyesightDimensionMetadataPayload.TYPE,
                SkyesightDimensionMetadataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientDimensionMetadata.accept(payload)
                )
        );
        registrar.playToClient(
                SkyesightEnvironmentPayload.TYPE,
                SkyesightEnvironmentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SkyesightClientEnvironmentHandler.handle(payload)
                )
        );
    }
}
