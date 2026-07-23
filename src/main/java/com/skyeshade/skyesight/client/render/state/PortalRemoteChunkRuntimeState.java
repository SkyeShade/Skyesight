package com.skyeshade.skyesight.client.render.state;

public final class PortalRemoteChunkRuntimeState {
    public static int forceLoadFramesSinceTicketing;
    public static int forceLoadRequestedChunks;
    public static int loadedChunksInRadius;
    public static int requiredLoadedChunksInRadius;
    public static int clientChunkNonAirSamples;
    public static int clientCenterSectionNonAirCount;
    public static boolean loadedAfterWait;

    private PortalRemoteChunkRuntimeState() {}

    public static void reset() {
        forceLoadFramesSinceTicketing = 0;
        forceLoadRequestedChunks = 0;
        loadedChunksInRadius = 0;
        requiredLoadedChunksInRadius = 0;
        clientChunkNonAirSamples = 0;
        clientCenterSectionNonAirCount = 0;
        loadedAfterWait = false;
    }
}
