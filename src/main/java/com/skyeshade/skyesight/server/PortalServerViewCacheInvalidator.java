package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.api.PortalCachePolicy;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class PortalServerViewCacheInvalidator {
    private static boolean registered;

    private PortalServerViewCacheInvalidator() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        SkyesightPortalRegistry.addChangeListener(PortalServerViewCacheInvalidator::invalidate);
    }

    private static void invalidate(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy
    ) {
        RegisteredPortalView view = oldView == null ? newView : oldView;
        if (view == null) {
            return;
        }
        if (cachePolicy == PortalCachePolicy.SOFT_REPLACE) {
            return;
        }
        ResourceLocation viewId = view.id();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (cachePolicy == PortalCachePolicy.DISABLE_RETAIN_CACHE) {
            SkyesightServerVisualEntityPacketTracker.removeView(viewId);
            SkyesightServerViewTracker.removeView(viewId);
            SkyesightSecondaryWatchRegion.removeRegion(viewId);
            SkyesightSecondaryChunkWatchRegion.removeRegion(viewId);
            return;
        }
        SkyesightServerVisualEntityPacketTracker.removeView(viewId);
        SkyesightServerViewTracker.removeView(viewId);
        SkyesightSecondaryWatchRegion.removeRegion(viewId);
        SkyesightSecondaryChunkWatchRegion.removeRegion(viewId);
        SkyesightServerChunkLoader.removeView(server, viewId);
    }
}
