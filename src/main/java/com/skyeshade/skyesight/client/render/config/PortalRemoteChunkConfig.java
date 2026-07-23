package com.skyeshade.skyesight.client.render.config;

public final class PortalRemoteChunkConfig {
    public static final boolean FORCE_LOAD_REMOTE_CHUNKS = true;
    public static final boolean EXPAND_CLIENT_CACHE_FOR_REMOTE_CHUNKS = true;
    public static final boolean DIRECT_DISABLE_REMOTE_CLIENT_CACHE_EXPANSION = false;
    public static final boolean SEND_REMOTE_CHUNKS_TO_LOCAL_CLIENT = true;
    public static final boolean FORCE_SEND_PORTAL_WATCH_CHUNKS_ON_CHANGE = true;
    public static final int FORCE_LOAD_REMOTE_CHUNK_RADIUS = 2;
    public static final int REMOTE_CHUNK_CLIENT_LOAD_RADIUS = FORCE_LOAD_REMOTE_CHUNK_RADIUS + 1;
    public static final int FORCE_LOAD_WAIT_FRAMES = 100;
    public static final int REMOTE_CHUNK_RESEND_FRAMES = 40;

    private PortalRemoteChunkConfig() {}
}
