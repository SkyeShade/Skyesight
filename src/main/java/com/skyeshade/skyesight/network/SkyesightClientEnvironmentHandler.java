package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualClientLevel;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;

public final class SkyesightClientEnvironmentHandler {
    private SkyesightClientEnvironmentHandler() {}

    public static void handle(SkyesightEnvironmentPayload payload) {
        if (payload.viewId() == null
                || payload.dimension() == null
                || !SkyesightRemoteViewRegistry.accepts(
                        payload.viewId(),
                        payload.generation(),
                        payload.dimension()
                )) {
            return;
        }

        SkyesightVisualWorld world = SkyesightVisualWorldManager.get(payload.viewId());
        if (world == null
                || world.isClosed()
                || !world.dimension().equals(payload.dimension())
                || !(world.level() instanceof SkyesightVisualClientLevel level)) {
            return;
        }

        level.applySkyesightEnvironment(payload);

        if (SkyesightDebugConfig.PACKET_DEBUG || SkyesightDebugConfig.WATCH_DEBUG) {
            Skyesight.LOGGER.debug(
                    "[Skyesight] REMOTE_ENVIRONMENT_APPLIED: viewId={} dimension={} generation={} gameTime={} dayTime={} rain={} thunder={} updateMillis={}",
                    payload.viewId(),
                    payload.dimension().location(),
                    payload.generation(),
                    payload.gameTime(),
                    payload.dayTime(),
                    payload.rainLevel(),
                    payload.thunderLevel(),
                    level.skyesightLastEnvironmentUpdateMillis()
            );
        }
    }
}
