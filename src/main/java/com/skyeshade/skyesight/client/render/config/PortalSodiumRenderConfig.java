package com.skyeshade.skyesight.client.render.config;

public final class PortalSodiumRenderConfig {
    public static final boolean SODIUM_BUILD_IMMEDIATELY = true;
    public static final boolean SODIUM_SCHEDULE_REMOTE_REBUILDS = true;
    public static final boolean SODIUM_DISABLE_OCCLUSION_CULLING_FOR_SECONDARY = true;
    public static final boolean SODIUM_FORCE_REMOTE_RENDER_LIST = true;
    public static final boolean SODIUM_FORCE_RENDER_LIST_FROM_REMOTE_GEOMETRY = true;
    public static final boolean SODIUM_REBUILD_CENTER_SECTION_ONLY = false;
    public static final boolean USE_MAIN_SODIUM_RENDERER_FOR_SECONDARY_VIEW = false;
    public static final boolean PORTAL_SODIUM_PREWARM_ENABLED = false;
    public static final int DEFAULT_PORTAL_SODIUM_PREWARM_RENDERERS = 2;
    public static final int DEFAULT_PORTAL_SODIUM_PREWARM_DELAY_FRAMES = 40;
    public static final int DEFAULT_PORTAL_SODIUM_PREWARM_PER_FRAME = 1;
    public static final int DEFAULT_MAX_NEW_PORTAL_SODIUM_RENDERERS_PER_FRAME = 1;
    public static final int DEFAULT_NEW_PORTAL_TERRAIN_SKIP_FRAMES = 2;
    public static final int DEFAULT_SAME_DIM_REUSE_INITIAL_RADIUS_CHUNKS = 2;
    public static final int DEFAULT_SAME_DIM_REUSE_RADIUS_GROWTH_INTERVAL_FRAMES = 4;
    public static final int DEFAULT_SAME_DIM_REUSE_RADIUS_GROWTH_STEP_CHUNKS = 1;
    public static final int DEFAULT_SAME_DIM_REUSE_FIRST_ACTIVE_MAX_RADIUS_CHUNKS = 2;
    public static final int DEFAULT_MAX_SAME_DIM_READY_CHUNKS_ADDED_PER_FRAME = 32;
    public static final int DEFAULT_MAX_SAME_DIM_READY_SECTIONS_SCANNED_PER_FRAME = 512;
    public static final int DEFAULT_MAX_NEW_SAME_DIM_PORTAL_TERRAIN_WARMUPS_PER_FRAME = 1;
    public static final int DEFAULT_MAX_PORTAL_SECTION_REBUILDS_SCHEDULED_PER_FRAME = 16;
    public static final double DEFAULT_PORTAL_TURN_THROTTLE_DEGREES = 15.0D;
    public static final int DEFAULT_PORTAL_TURN_THROTTLED_REBUILD_BUDGET = 4;
    public static final int DEFAULT_PORTAL_TURN_THROTTLED_REUSE_RADIUS_CAP = 3;
    public static final double DEFAULT_PORTAL_REUSE_GROWTH_MAX_SETUP_MS = 4.0D;
    public static final boolean DEFAULT_PORTAL_REUSE_SHRINK_ON_SPIKE = true;
    public static final int DEFAULT_SAME_DIM_MAIN_SECTION_PRIMER_RADIUS_CHUNKS = 4;
    public static final int DEFAULT_MAX_MAIN_SECTION_PRIMER_SECTIONS_PER_FRAME = 256;
    public static final int DEFAULT_MAX_MAIN_SECTION_PRIMER_FRAMES = 20;
    public static final int SODIUM_CHUNK_RADIUS = 8;
    public static final int SODIUM_DELAYED_REBUILD_FRAMES = 60;

    private PortalSodiumRenderConfig() {}
}
