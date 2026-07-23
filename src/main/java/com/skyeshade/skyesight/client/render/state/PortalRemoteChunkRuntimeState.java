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
}
