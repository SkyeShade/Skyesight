package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.Skyesight;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class DirectStencilPortalRenderer {
    private DirectStencilPortalRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        DirectStencilPortalRenderPipeline.onRenderLevelStage(event);
    }
}
