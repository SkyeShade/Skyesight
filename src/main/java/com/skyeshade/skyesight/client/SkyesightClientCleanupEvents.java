package com.skyeshade.skyesight.client;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.render.sodium.PortalSodiumRendererPool;
import com.skyeshade.skyesight.client.view.SkyesightClientApi;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class SkyesightClientCleanupEvents {
    private SkyesightClientCleanupEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Skyesight.api() instanceof SkyesightClientApi clientApi) {
            clientApi.closeAll();
        }
        SkyesightClientChunkRequester.reset();
        SkyesightPortalChunkStorage.clear();
        SkyesightVisualWorldManager.closeAll();
        PortalSodiumRendererPool.clear();
    }
}
