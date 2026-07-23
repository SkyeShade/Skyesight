package com.skyeshade.skyesight.client.portal;

final class DirectStencilPortalRenderConfig {
    static final boolean RENDER_SECONDARY_PORTAL_COMPOSITE = true;
    static final boolean RENDER_PORTAL_INSTANCES = true;
    static final boolean ENABLE_FAR_PORTALS = true;
    static final boolean ENABLE_SECOND_LOCAL_PORTAL_PAIR = true;
    static final boolean ENABLE_CROSS_DIM_PORTALS = true;
    static final boolean ENABLE_END_PORTALS = true;

    static final boolean CROSS_DIM_PORTAL_E_RENDER_TERRAIN = true;
    static final boolean CROSS_DIM_PORTAL_E_RENDER_TRANSLUCENT = true;
    static final boolean CROSS_DIM_PORTAL_E_RENDER_ENTITIES = true;
    static final boolean CROSS_DIM_PORTAL_E_FORCE_ENTITY_PASS_CALL = true;
    static final boolean CROSS_DIM_PORTAL_E_RENDER_BLOCK_ENTITIES = true;
    static final int CROSS_DIM_PORTAL_E_ENTITY_RADIUS_CHUNKS = 4;
    static final boolean PORTAL_E_USE_A_ENTITY_DEPTH_SETUP = true;
    static final boolean PORTAL_E_DISABLE_ENTITY_FRUSTUM_CULLING = true;
    static final boolean CROSS_DIM_PORTAL_E_ENTITY_MARKER_DEPTH_TEST = true;
    static final boolean CROSS_DIM_PORTAL_E_FORCE_NETHER_AMBIENT_LIGHT = true;
    static final boolean CROSS_DIM_PORTAL_E_SOFTWARE_FALLBACK_ON_ERROR = true;

    static final boolean CROSS_DIM_PORTAL_G_RENDER_TERRAIN = true;
    static final boolean CROSS_DIM_PORTAL_G_RENDER_TRANSLUCENT = true;
    static final boolean CROSS_DIM_PORTAL_G_RENDER_ENTITIES = true;
    static final boolean CROSS_DIM_PORTAL_G_RENDER_BLOCK_ENTITIES = true;
    static final boolean CROSS_DIM_PORTAL_PARTICLES_ENABLED = true;

    static final boolean FAR_PORTAL_SNAP_TO_SURFACE = false;
    static final boolean FAR_PORTAL_RENDER_ENTITIES = true;
    static final boolean FAR_PORTAL_RENDER_BLOCK_ENTITIES = true;
    static final boolean TEST_SECONDARY_ENTITIES_ONLY_ONE_PORTAL = false;
    static final boolean STENCIL_ONLY_FIRST_PORTAL = false;
    static final boolean STENCIL_MASK_AT_WORLD_STAGE = true;

    static final float PORTAL_APERTURE_EDGE_INSET_BLOCKS = 0.00F;

    static final float PORTAL_CAMERA_EXIT_PUSH_EPSILON_BLOCKS = 0.005F;

    static final boolean DIRECT_STENCIL_DRAW_PROOF_COLOR = false;
    static final boolean DIRECT_STENCIL_RENDER_TERRAIN = true;
    static final boolean DIRECT_RENDER_TERRAIN = true;
    static final boolean DIRECT_RENDER_SKY = false;
    static final boolean DIRECT_RENDER_REAL_SKY = false;
    static final boolean DIRECT_RENDER_SIMPLE_SKY_FILL = true;
    static final boolean DIRECT_DISABLE_PORTAL_SKY_FILL = false;
    static final boolean DIRECT_DISABLE_PORTAL_TERRAIN = false;
    static final boolean DIRECT_DISABLE_PORTAL_TRANSPARENT = false;
    static final boolean DIRECT_DISABLE_PORTAL_BLOCK_ENTITIES = false;
    static final boolean DIRECT_DISABLE_PORTAL_ENTITIES = false;
    static final boolean ENABLE_PORTAL_ENTITY_POOL_RENDERING = false;
    static final boolean PORTAL_MAIN_PARTICLE_OCCLUSION_FIX = true;
    static final boolean DIRECT_DISABLE_PORTAL_DEPTH_CLEAR = false;
    static final boolean USE_STABLE_TERRAIN_INVOCATION_PATH = true;
    static final boolean DIRECT_DISABLE_ALL_PORTAL_SUBPASSES_AFTER_MASK = false;

    static final boolean DIRECT_SKY_DISABLE_SIMPLE_PREFILL = false;
    static final boolean DIRECT_SKY_MASK_ONLY = false;
    static final boolean DIRECT_RENDER_SKY_ONE_PORTAL_ONLY = false;
    static final boolean DIRECT_SKY_DRAW_BACKGROUND = true;
    static final boolean DIRECT_SKY_DRAW_SUN = true;
    static final boolean DIRECT_SKY_DRAW_MOON = true;
    static final boolean DIRECT_SKY_DRAW_STARS = false;
    static final boolean DIRECT_SKY_DRAW_SUNRISE = true;
    static final boolean DIRECT_SKY_CAPTURE_ENABLED = true;
    static final boolean DIRECT_SKY_CAPTURE_COMPOSITE_ENABLED = true;
    static final boolean DIRECT_SKY_CAPTURE_DEBUG_FULLSCREEN = false;
    static final boolean DIRECT_SKY_CAPTURE_BYPASS_STENCIL = false;
    static final boolean DIRECT_SKY_CAPTURE_FALLBACK_SIMPLE_COLOR = true;
    static final boolean PORTAL_SKY_COMPOSITE_OPAQUE = true;
    static final boolean DIRECT_PORTAL_SKY_AT_AFTER_SKY = false;
    static final boolean DIRECT_PORTAL_SKY_AFTER_SKY_ONE_PORTAL_ONLY = false;

    static final boolean DIRECT_RENDER_ENTITIES = true;
    static final boolean DIRECT_RENDER_ENTITIES_ONE_PORTAL_ONLY = false;
    static final boolean FLUSH_MAIN_BUFFERS_BEFORE_PORTAL_MASK = true;

    static final boolean VERBOSE_CROSS_DIM_PORTAL_STORAGE_DIAGNOSTICS = false;

    private DirectStencilPortalRenderConfig() {}
}
