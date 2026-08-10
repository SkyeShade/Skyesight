package com.skyeshade.skyesight.client;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.portal.CrossDimPortalTerrainWarmup;
import com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer;
import com.skyeshade.skyesight.client.render.SecondarySodiumTerrainPass;
import com.skyeshade.skyesight.client.render.state.PortalRemoteChunkRuntimeState;
import com.skyeshade.skyesight.client.render.state.PortalSecondaryRenderState;
import com.skyeshade.skyesight.client.view.SkyesightClientApi;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class SkyesightClientCleanupEvents {
    private static ClientLevel lastSeenLevel;
    private static ResourceKey<Level> lastSeenDimension;

    private SkyesightClientCleanupEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Skyesight.api() instanceof SkyesightClientApi clientApi) {
            clientApi.closeAll();
        }
        invalidateLevelBoundCaches("client_logout");
        lastSeenLevel = null;
        lastSeenDimension = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft == null ? null : minecraft.level;
        ResourceKey<Level> currentDimension = currentLevel == null ? null : currentLevel.dimension();

        if (currentLevel == null) {
            if (lastSeenLevel != null) {
                invalidateLevelBoundCaches("client_level_unloaded old=" + describe(lastSeenLevel, lastSeenDimension));
            }
            lastSeenLevel = null;
            lastSeenDimension = null;
            return;
        }

        if (lastSeenLevel == null) {
            lastSeenLevel = currentLevel;
            lastSeenDimension = currentDimension;
            return;
        }

        if (currentLevel != lastSeenLevel || !currentDimension.equals(lastSeenDimension)) {
            String reason = "client_level_changed old="
                    + describe(lastSeenLevel, lastSeenDimension)
                    + " new="
                    + describe(currentLevel, currentDimension);
            invalidateLevelBoundCaches(reason);
            lastSeenLevel = currentLevel;
            lastSeenDimension = currentDimension;
        }
    }

    public static void invalidateLevelBoundCaches(String reason) {
        int viewContexts = PortalDirectStencilRenderer.invalidateLevelBoundCaches(reason);
        int visualWorlds = SkyesightVisualWorldManager.count();

        PortalSecondaryRenderState.SECONDARY_VIEW.close();
        PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_CENTERS.clear();
        PortalSecondaryRenderState.SECONDARY_CHUNK_WATCH_SENT_RADII.clear();
        PortalSecondaryRenderState.renderingSecondaryView = false;
        PortalSecondaryRenderState.backendFailed = false;
        PortalSecondaryRenderState.lastException = "";
        PortalSecondaryRenderState.forceLoadTicketCenter = null;
        PortalSecondaryRenderState.forceLoadTicketSubmitted = false;
        PortalSecondaryRenderState.forceLoadSynchronousLoadQueued = false;
        PortalSecondaryRenderState.remoteClientCacheExpandedLevel = null;
        PortalSecondaryRenderState.remoteClientCacheExpanded = false;
        PortalSecondaryRenderState.remoteClientCacheOriginalRadius = -1;
        PortalSecondaryRenderState.remoteClientCacheExpandedRadius = -1;
        PortalRemoteChunkRuntimeState.reset();

        SkyesightClientChunkRequester.reset();
        CrossDimPortalTerrainWarmup.clear();
        SkyesightPortalChunkStorage.clear();
        SkyesightVisualWorldManager.closeAll();
        SkyesightPortalEntityPool.clearAll();
        SecondarySodiumTerrainPass.clearRendererPool();

        Skyesight.LOGGER.info(
                "[Skyesight] CLIENT_LEVEL_CHANGED_INVALIDATE: reason={} viewContextsCleared={} visualWorldsCleared={} remoteChunkCachesCleared=yes portalRegistryEntriesPreserved={}",
                reason == null || reason.isBlank() ? "-" : reason,
                viewContexts,
                visualWorlds,
                SkyesightPortalApi.getAllPortals().size()
        );
    }

    private static String describe(ClientLevel level, ResourceKey<Level> dimension) {
        if (level == null) {
            return "null";
        }
        return Integer.toHexString(System.identityHashCode(level))
                + "/"
                + (dimension == null ? "unknown" : dimension.location());
    }
}
