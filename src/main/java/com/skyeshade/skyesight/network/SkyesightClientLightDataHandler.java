package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.client.Minecraft;

public final class SkyesightClientLightDataHandler {
    private SkyesightClientLightDataHandler() {}

    public static void handle(SkyesightLightDataPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());

        if (world == null || world.isClosed()) {
            return;
        }

        boolean applied = world.chunkReceiver().applyLightUpdate(
                payload.chunkX(),
                payload.chunkZ(),
                payload.lightData()
        );

        if (applied) {
            world.scheduleChunkRebuild(
                    payload.chunkX(),
                    payload.chunkZ(),
                    true
            );
        }

        if (SkyesightDebugConfig.WATCH_DEBUG
                && Minecraft.getInstance().level != null
                && !Minecraft.getInstance().level.dimension().equals(payload.dimension())) {
            Skyesight.LOGGER.info(
                    "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_UPDATE: viewId={} displayDimension={} cameraDimension={} chunk={},{} lightUpdateApplied={}",
                    payload.viewId(),
                    Minecraft.getInstance().level.dimension().location(),
                    payload.dimension().location(),
                    payload.chunkX(),
                    payload.chunkZ(),
                    applied
            );
        }
    }
}
