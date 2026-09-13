package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;

public final class SkyesightClientBlockEventHandler {
    private SkyesightClientBlockEventHandler() {}

    public static void handle(SkyesightBlockEventPayload payload) {
        if (!SkyesightRemoteViewRegistry.accepts(
                payload.viewId(), payload.generation(), payload.dimension())) {
            return;
        }

        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());

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
