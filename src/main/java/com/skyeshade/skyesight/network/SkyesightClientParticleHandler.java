package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.world.SecondaryParticleViews;

public final class SkyesightClientParticleHandler {
    private SkyesightClientParticleHandler() {}
    public static void handle(SkyesightParticlePayload payload) {
        SecondaryParticleViews.accept(payload);
    }
}