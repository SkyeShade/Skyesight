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
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        dispatch(server, () -> server.isStopped() || ServerLifecycleHooks.getCurrentServer() != server,
                () -> invalidateOnServer(server, view, newView, cachePolicy));
    }

    /** MinecraftServer.execute runs inline after stop, even on a foreign thread. Never redispatch recursively. */
    static void dispatch(java.util.concurrent.Executor executor, java.util.function.BooleanSupplier stopped, Runnable action) {
        if (stopped.getAsBoolean()) return;
        executor.execute(() -> { if (!stopped.getAsBoolean()) action.run(); });
    }

    private static void invalidateOnServer(MinecraftServer server, RegisteredPortalView view,
            RegisteredPortalView newView, PortalCachePolicy cachePolicy) {
        ResourceLocation viewId = view.id();
        var current = SkyesightPortalRegistry.get(viewId);
        if (current != null && current.generation() != view.generation()
                && (newView == null || current.generation() != newView.generation())) return;
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
