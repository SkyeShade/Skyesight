package com.skyeshade.skyesight.client.render.config;

public final class PortalSecondaryRenderConfig {
    public static final boolean RENDER_VANILLA_PIPELINE_SECONDARY_VIEW = true;
    public static final boolean RENDER_SODIUM_PIPELINE_SECONDARY_VIEW = true;
    public static final boolean SECONDARY_FEATURE_SKY = true;
    public static final boolean SECONDARY_FEATURE_TRANSLUCENT = true;
    public static final boolean SECONDARY_FEATURE_BLOCK_ENTITIES = false;
    public static final boolean SECONDARY_FEATURE_ENTITIES = true;
    public static final boolean SECONDARY_STREAM_REMOTE_ENTITIES = true;
    public static final boolean PORTAL_PARTICLES_ENABLED = true;
    public static final boolean PORTAL_PARTICLES_SAME_DIM_ONLY = true;
    public static final boolean PORTAL_PARTICLES_ALL_AFTER_ENTITIES = true;
    public static final boolean PORTAL_PARTICLES_AFTER_ENTITIES = false;
    public static final boolean SECONDARY_RENDER_PARTICLES = PORTAL_PARTICLES_ENABLED;
    public static final boolean SECONDARY_RENDER_ENTITIES_AFTER_TRANSLUCENT = true;
    public static final boolean DIRECT_RENDER_TERRAIN_DRAW_SOLID_ONLY = true;
    public static final boolean DIRECT_RENDER_TRANSLUCENT_TERRAIN = true;
    public static final boolean DIRECT_RENDER_BLOCK_ENTITIES = true;
    public static final float CLEAR_RED = 1.0F;
    public static final float CLEAR_GREEN = 0.0F;
    public static final float CLEAR_BLUE = 1.0F;
    public static final float CLEAR_ALPHA = 1.0F;

    private PortalSecondaryRenderConfig() {}
}
