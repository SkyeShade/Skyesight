package com.skyeshade.skyesight.client;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.PortalCachePolicy;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.portal.CrossDimPortalViewUpdater;
import com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer;
import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.network.SkyesightClientChunkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PortalViewCacheInvalidator {
    private static boolean registered;

    private PortalViewCacheInvalidator() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        SkyesightPortalRegistry.addChangeListener(PortalViewCacheInvalidator::invalidate);
    }

    private static void invalidate(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> invalidateOnClientThread(oldView, newView, reason, cachePolicy));
            return;
        }
        invalidateOnClientThread(oldView, newView, reason, cachePolicy);
    }

    private static void invalidateOnClientThread(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy
    ) {
        RegisteredPortalView view = oldView == null ? newView : oldView;
        if (view == null) {
            return;
        }
        ResourceLocation viewId = view.id();
        if (cachePolicy == PortalCachePolicy.SOFT_REPLACE) {
            PortalDirectStencilRenderer.softReplaceViewCaches(viewId);
            if (SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
                logInvalidation(
                        viewId,
                        oldView,
                        newView,
                        reason,
                        cachePolicy,
                        "transientFrameState"
                );
            }
            return;
        }
        if (cachePolicy == PortalCachePolicy.DISABLE_RETAIN_CACHE) {
            PortalDirectStencilRenderer.softReplaceViewCaches(viewId);
            PortalSecondaryWorldRenderer.invalidateViewCaches(viewId);
            SkyesightPortalEntityPool.clearView(viewId);
            CrossDimPortalViewUpdater.removeEntityWatchRegion(Minecraft.getInstance(), viewId);
            if (SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
                logInvalidation(
                        viewId,
                        oldView,
                        newView,
                        reason,
                        cachePolicy,
                        "transientFrameState,activeWatches"
                );
            }
            return;
        }
        PortalDirectStencilRenderer.invalidateViewCaches(viewId);
        PortalSecondaryWorldRenderer.invalidateViewCaches(viewId);
        SkyesightVisualWorldManager.close(viewId);
        SkyesightPortalEntityPool.clearView(viewId);
        SkyesightClientChunkRequester.reset(viewId);
        SkyesightPortalChunkStorage.clearView(viewId);
        SkyesightClientChunkHandler.invalidateView(viewId);
        CrossDimPortalViewUpdater.removeEntityWatchRegion(Minecraft.getInstance(), viewId);
        if (SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
            logInvalidation(
                    viewId,
                    oldView,
                    newView,
                    reason,
                    cachePolicy,
                    "renderContext,visualWorld,chunkRequester,chunkStorage,chunkReceiver,entityWatch,blockUpdates,particles"
            );
        }
    }

    private static void logInvalidation(
            ResourceLocation viewId,
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy,
            String cleared
    ) {
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VIEW_CACHE_INVALIDATED: viewId={} oldSourceDim={} oldTargetDim={} newSourceDim={} newTargetDim={} oldGeneration={} newGeneration={} cachePolicy={} reason={} cleared={}",
                viewId,
                oldView == null ? "-" : oldView.source().dimension().location(),
                oldView == null ? "-" : oldView.target().dimension().location(),
                newView == null ? "-" : newView.source().dimension().location(),
                newView == null ? "-" : newView.target().dimension().location(),
                oldView == null ? "-" : oldView.generation(),
                newView == null ? "-" : newView.generation(),
                cachePolicyName(cachePolicy),
                reason == null || reason.isBlank() ? "replace" : reason,
                cleared
        );
    }

    private static String cachePolicyName(PortalCachePolicy cachePolicy) {
        if (cachePolicy == null) {
            return "-";
        }
        return switch (cachePolicy) {
            case HARD_INVALIDATE -> "hard_invalidate";
            case SOFT_REPLACE -> "soft_replace";
            case DISABLE_RETAIN_CACHE -> "disable_retain_cache";
            case REMOVE -> "remove";
            case CLEAR -> "clear";
            case STALE_PACKET_DROP -> "stale_packet_drop";
        };
    }
}
