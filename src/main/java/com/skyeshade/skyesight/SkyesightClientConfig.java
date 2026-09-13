package com.skyeshade.skyesight;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyesightClientConfig {
    public static final double DEFAULT_PORTAL_RENDER_DISTANCE_BLOCKS = 64.0D;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue REGISTER_STARTUP_DEBUG_PORTALS = BUILDER
            .comment("Registers Skyesight's built-in startup portals for development/demo testing. Disabled by default for library use.")
            .define("registerStartupDebugPortals", false);

    public static final ModConfigSpec.BooleanValue ENABLE_BOUNDS_SCALED_PORTAL_RESOLUTION = BUILDER
            .comment("When true, Skyesight may use screen-space portal bounds for render target sizing. Currently experimental and may be internally disabled for stability.")
            .define("enableBoundsScaledPortalResolution", false);

    public static final ModConfigSpec.BooleanValue ENABLE_PORTAL_FRUSTUM_CULLING = BUILDER
            .comment("When true, Skyesight skips rendering portal views whose portal aperture is outside the camera frustum/screen.")
            .define("enablePortalFrustumCulling", false);

    public static final ModConfigSpec.BooleanValue ENABLE_PORTAL_ENTITY_FRUSTUM_CULLING = BUILDER
            .comment("When true, Skyesight culls entities outside the portal view frustum before rendering them through portals. Improves performance, but can be disabled if entities disappear near portal edges.")
            .define("enablePortalEntityFrustumCulling", true);

    public static final ModConfigSpec.DoubleValue PORTAL_RENDER_DISTANCE_BLOCKS = BUILDER
            .comment("Maximum client-side distance in blocks at which portals render their remote view.")
            .defineInRange("portalRenderDistanceBlocks", DEFAULT_PORTAL_RENDER_DISTANCE_BLOCKS, 1.0D, 2048.0D);

    public static final ModConfigSpec.IntValue MAX_PORTAL_RECURSION_DEPTH = BUILDER
            .comment("Portal scene depth: 1 = no recursive/nested portal draws; 2+ = nested recursion. 0 also renders ordinary portals only. Hard maximum 5. Applies live.")
            .defineInRange("maxPortalRecursionDepth", 1, 0, 5);

    public static int maxPortalRecursionDepth() { return Math.clamp(MAX_PORTAL_RECURSION_DEPTH.get(), 0, 5); }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SkyesightClientConfig() {}

    public static boolean registerStartupDebugPortals() {
        return REGISTER_STARTUP_DEBUG_PORTALS.get();
    }

    public static boolean enableBoundsScaledPortalResolution() {
        return ENABLE_BOUNDS_SCALED_PORTAL_RESOLUTION.get();
    }

    public static boolean enablePortalFrustumCulling() {
        return ENABLE_PORTAL_FRUSTUM_CULLING.get();
    }

    public static boolean enablePortalEntityFrustumCulling() {
        return ENABLE_PORTAL_ENTITY_FRUSTUM_CULLING.get();
    }

    public static double portalRenderDistanceBlocks() {
        return PORTAL_RENDER_DISTANCE_BLOCKS.get();
    }

    public static String status() {
        return "registerStartupDebugPortals=" + registerStartupDebugPortals()
                + " enableBoundsScaledPortalResolution=" + enableBoundsScaledPortalResolution()
                + " enablePortalFrustumCulling=" + enablePortalFrustumCulling()
                + " enablePortalEntityFrustumCulling=" + enablePortalEntityFrustumCulling()
                + " portalRenderDistanceBlocks=" + portalRenderDistanceBlocks();
    }
}
