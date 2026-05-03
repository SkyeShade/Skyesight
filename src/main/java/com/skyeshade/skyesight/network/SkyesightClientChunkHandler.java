package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SkyesightClientChunkHandler {
    private SkyesightClientChunkHandler() {}

    public static void handleChunkDataOnClient(SkyesightChunkDataPayload payload) {
        Skyesight.LOGGER.info(
                "[Skyesight] Received chunk {}, {} for view {}",
                payload.chunkX(),
                payload.chunkZ(),
                payload.viewId()
        );

        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            Skyesight.LOGGER.debug(
                    "[Skyesight] Dropped chunk {}, {} for missing/closed view {}",
                    payload.chunkX(),
                    payload.chunkZ(),
                    payload.viewId()
            );
            return;
        }

        world.chunkReceiver().setViewCenter(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        world.chunkReceiver().pruneOutside(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        boolean inserted = world.chunkReceiver().receiveChunkWithLight(
                payload.chunkX(),
                payload.chunkZ(),
                payload.chunkData(),
                payload.lightData(),
                world.renderer()::scheduleTerrainUpdate
        );

        if (inserted) {
            SkyesightClientChunkRequester.markChunkReceived(
                    payload.viewId(),
                    payload.dimension(),
                    payload.chunkX(),
                    payload.chunkZ()
            );
        }

        Skyesight.LOGGER.info(
                "[Skyesight] Skyesight view={} loaded chunks={}",
                payload.viewId(),
                world.level().getChunkSource().getLoadedChunksCount()
        );
    }
}