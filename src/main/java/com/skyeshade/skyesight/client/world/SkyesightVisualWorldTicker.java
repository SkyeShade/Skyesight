package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.portal.CrossDimPortalTerrainWarmup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT
)
public final class SkyesightVisualWorldTicker {
    private SkyesightVisualWorldTicker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CrossDimPortalTerrainWarmup.tick();
        SkyesightVisualWorldManager.tickAll();
        SkyesightEntityDimensionContextReporter.tick();
    }
}
