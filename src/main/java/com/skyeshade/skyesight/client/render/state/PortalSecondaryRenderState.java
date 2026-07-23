package com.skyeshade.skyesight.client.render.state;

import com.skyeshade.skyesight.client.render.SecondaryViewContext;
import com.skyeshade.skyesight.client.render.config.PortalRemoteChunkConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public final class PortalSecondaryRenderState {
    public static final SecondaryViewContext SECONDARY_VIEW = new SecondaryViewContext();
    public static final Map<ResourceLocation, ChunkPos> SECONDARY_CHUNK_WATCH_SENT_CENTERS = new HashMap<>();
    public static final Map<ResourceLocation, Integer> SECONDARY_CHUNK_WATCH_SENT_RADII = new HashMap<>();

    public static boolean renderingSecondaryView;
    public static int newPortalSodiumRenderersCreatedThisFrame;
    public static int sameDimPortalTerrainWarmupsThisFrame;
    public static boolean backendFailed;
    public static volatile boolean directTerrainRestoreAfterEachPortal = true;
    public static String lastException = "";
    public static ChunkPos forceLoadTicketCenter;
    public static volatile int activeRemoteTerrainChunkRadius = PortalRemoteChunkConfig.FORCE_LOAD_REMOTE_CHUNK_RADIUS;
    public static int sodiumDelayedRebuildStableFrames;
    public static boolean forceLoadTicketSubmitted;
    public static boolean forceLoadSynchronousLoadQueued;
    public static ClientLevel remoteClientCacheExpandedLevel;
    public static boolean remoteClientCacheExpanded;
    public static int remoteClientCacheOriginalRadius = -1;
    public static int remoteClientCacheExpandedRadius = -1;

    private PortalSecondaryRenderState() {}
}
