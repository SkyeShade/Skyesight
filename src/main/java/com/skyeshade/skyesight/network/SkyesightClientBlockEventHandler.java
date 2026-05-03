package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;

public final class SkyesightClientBlockEventHandler {
    private SkyesightClientBlockEventHandler() {}

    public static void handle(SkyesightBlockEventPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            return;
        }

        world.level().blockEvent(
                payload.pos(),
                world.level().getBlockState(payload.pos()).getBlock(),
                payload.eventId(),
                payload.eventParam()
        );
    }
}