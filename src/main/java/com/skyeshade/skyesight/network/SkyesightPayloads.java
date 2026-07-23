package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.server.SkyesightServerChunkSender;
import com.skyeshade.skyesight.server.portal.PortalProxyArmorStandDebugManager;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SkyesightPayloads {
    private SkyesightPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        if (SkyesightDebugConfig.SOURCE_MAP) {
            Skyesight.LOGGER.info("[Skyesight] Registering network payloads");
        }

        PayloadRegistrar registrar = event.registrar(Skyesight.MODID)
                .versioned("1");

        registrar.playToServer(
                SkyesightChunkRequestPayload.TYPE,
                SkyesightChunkRequestPayload.STREAM_CODEC,
                SkyesightServerChunkSender::handleChunkRequest
        );
        registrar.playToServer(
                SkyesightProxyMarkerPayload.TYPE,
                SkyesightProxyMarkerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> {
                            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
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
    }
}
