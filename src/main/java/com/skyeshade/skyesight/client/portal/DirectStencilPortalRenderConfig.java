package com.skyeshade.skyesight.client.portal;

final class DirectStencilPortalRenderConfig {
    static final boolean RENDER_SECONDARY_PORTAL_COMPOSITE = true;

    static final boolean CROSS_DIM_PORTAL_PARTICLES_ENABLED = true;

    static final boolean FAR_PORTAL_RENDER_BLOCK_ENTITIES = true;
    static final boolean STENCIL_MASK_AT_WORLD_STAGE = true;

    static final float PORTAL_APERTURE_EDGE_INSET_BLOCKS = 0.00F;

    static final float PORTAL_CAMERA_EXIT_PUSH_EPSILON_BLOCKS = 0.005F;

    static final boolean DIRECT_STENCIL_DRAW_PROOF_COLOR = false;
    static final boolean DIRECT_STENCIL_RENDER_TERRAIN = true;
    static final boolean DIRECT_RENDER_TERRAIN = true;
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
    static final boolean DIRECT_SKY_CAPTURE_ENABLED = true;
    static final boolean DIRECT_SKY_CAPTURE_COMPOSITE_ENABLED = true;
    static final boolean DIRECT_SKY_CAPTURE_BYPASS_STENCIL = false;
    static final boolean DIRECT_SKY_CAPTURE_FALLBACK_SIMPLE_COLOR = true;
    static final boolean PORTAL_SKY_COMPOSITE_OPAQUE = true;
    static final boolean DIRECT_PORTAL_SKY_AT_AFTER_SKY = false;

    static final boolean DIRECT_RENDER_ENTITIES = true;
    static final boolean DIRECT_RENDER_ENTITIES_ONE_PORTAL_ONLY = false;
    static final boolean FLUSH_MAIN_BUFFERS_BEFORE_PORTAL_MASK = true;

    private DirectStencilPortalRenderConfig() {}
}
