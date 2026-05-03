package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;

public final class SkyesightClientLightDataHandler {
    private SkyesightClientLightDataHandler() {}

    public static void handle(SkyesightLightDataPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            return;
        }

        boolean applied = world.chunkReceiver().applyLightUpdate(
                payload.chunkX(),
                payload.chunkZ(),
                payload.lightData()
        );

        if (applied) {
            world.renderer().scheduleChunkRebuild(
                    payload.chunkX(),
                    payload.chunkZ(),
                    true
            );
        }
    }
}