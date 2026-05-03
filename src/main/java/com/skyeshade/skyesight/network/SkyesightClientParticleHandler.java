package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;

public final class SkyesightClientParticleHandler {
    private SkyesightClientParticleHandler() {}

    public static void handle(SkyesightParticlePayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            return;
        }

        world.particles().addParticle(payload);
    }
}