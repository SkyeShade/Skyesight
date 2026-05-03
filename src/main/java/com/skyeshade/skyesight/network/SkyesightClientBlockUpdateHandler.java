package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;

public final class SkyesightClientBlockUpdateHandler {
    private SkyesightClientBlockUpdateHandler() {}

    public static void handle(SkyesightBlockUpdatesPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            return;
        }

        for (SkyesightBlockUpdatesPayload.Entry update : payload.updates()) {
            boolean applied = world.chunkReceiver().applyBlockUpdate(
                    update.pos(),
                    update.state()
            );

            if (applied) {
                world.renderer().scheduleBlockUpdate(update.pos());
            }
        }
    }
}