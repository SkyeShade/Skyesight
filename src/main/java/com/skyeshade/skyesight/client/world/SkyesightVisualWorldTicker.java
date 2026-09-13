package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.client.portal.CrossDimPortalTerrainWarmup;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class SkyesightVisualWorldTicker {
    private SkyesightVisualWorldTicker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        CrossDimPortalTerrainWarmup.tick();
        SkyesightVisualWorldManager.tickAll();
        SecondaryParticleViews.tick();
        SkyesightEntityDimensionContextReporter.tick();
    }
}
