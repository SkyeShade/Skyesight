package com.skyeshade.skyesight;

public final class SkyesightDebugConfig {
    public static volatile boolean CORE = true;
    public static volatile boolean SPAWN_SUMMARY = false;
    public static volatile boolean LIFECYCLE_SUMMARY = false;
    public static volatile boolean VERBOSE_SPAWN = false;
    public static volatile boolean VERBOSE_ENTITY = false;
    public static volatile boolean VERBOSE_RENDER = false;
    public static volatile boolean VERBOSE_PROXIMITY = false;
    public static volatile boolean SOURCE_MAP = false;
    public static volatile boolean PACKET_DEBUG = false;
    public static volatile boolean WATCH_DEBUG = false;
    public static volatile boolean TERRAIN_AUDIT = false;
    public static volatile boolean SODIUM_RENDERER_AUDIT = false;
    public static volatile boolean SKY_CAPTURE_AUDIT = false;
    public static volatile boolean RENDER_TARGET_AUDIT = false;
    public static volatile boolean RENDER_CULLING_AUDIT = false;
    public static volatile boolean RENDER_PERF_AUDIT = false;
    public static volatile boolean DEBUG_STICK_AUDIT = false;
    public static volatile boolean PORTAL_API_AUDIT = false;
    public static volatile boolean LIFECYCLE_DEBUG = false;
    public static volatile boolean ENTITY_DIMENSION_CONTEXT = false;
    public static volatile boolean PORTAL_AWARE_PLAYER_QUERIES = true;
    public static volatile boolean PORTAL_AWARE_PLAYER_COORDINATES_FOR_QUERIES = true;
    public static volatile boolean PORTAL_AWARE_MOB_TARGETING = false;
    public static volatile boolean SHOW_PROXY_MARKERS = false;
    public static volatile boolean SHOW_PROXY_ARMOR_STANDS = false;
    public static volatile boolean SHOW_PORTAL_LOOK_MARKERS = false;
    public static final boolean ENABLE_SAME_DIM_MAIN_SECTION_BORROWED_DRAWING_SOLID_CUTOUT = false;
    public static final boolean DEBUG_DISABLE_SAME_DIM_PORTAL_TERRAIN_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_DISABLE_NEW_STICK_PORTAL_RENDER_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_MASK_ONLY_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_MASK_AND_SKY_ONLY_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_NO_TERRAIN_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_NO_SODIUM_RENDERER_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_NO_CHUNK_TRACKER_UPDATE_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_MASK_AND_SKY_NO_CAPTURE_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_MASK_AND_SKY_CAPTURE_NO_COMPOSITE_FOR_FLASH_TEST = false;
    public static final boolean DEBUG_STICK_RENDER_MASK_AND_SKY_COMPOSITE_ONLY_FOR_FLASH_TEST = false;
    public static final int DEBUG_FORCE_SAME_DIM_REUSE_RADIUS_ON_FIRST_ACTIVATION = -1;


    public static final int DEBUG_PORTAL_SUMMARY_INTERVAL_TICKS = 100;


    public static String status() {
        return "CORE=" + CORE
                + " SPAWN_SUMMARY=" + SPAWN_SUMMARY
                + " LIFECYCLE_SUMMARY=" + LIFECYCLE_SUMMARY
                + " VERBOSE_SPAWN=" + VERBOSE_SPAWN
                + " VERBOSE_ENTITY=" + VERBOSE_ENTITY
                + " VERBOSE_RENDER=" + VERBOSE_RENDER
                + " VERBOSE_PROXIMITY=" + VERBOSE_PROXIMITY
                + " SOURCE_MAP=" + SOURCE_MAP
                + " PACKET_DEBUG=" + PACKET_DEBUG
                + " WATCH_DEBUG=" + WATCH_DEBUG
                + " TERRAIN_AUDIT=" + TERRAIN_AUDIT
                + " SODIUM_RENDERER_AUDIT=" + SODIUM_RENDERER_AUDIT
                + " SKY_CAPTURE_AUDIT=" + SKY_CAPTURE_AUDIT
                + " RENDER_TARGET_AUDIT=" + RENDER_TARGET_AUDIT
                + " RENDER_CULLING_AUDIT=" + RENDER_CULLING_AUDIT
                + " RENDER_PERF_AUDIT=" + RENDER_PERF_AUDIT
                + " DEBUG_STICK_AUDIT=" + DEBUG_STICK_AUDIT
                + " PORTAL_API_AUDIT=" + PORTAL_API_AUDIT
                + " LIFECYCLE_DEBUG=" + LIFECYCLE_DEBUG
                + " ENTITY_DIMENSION_CONTEXT=" + ENTITY_DIMENSION_CONTEXT
                + " PORTAL_AWARE_PLAYER_QUERIES=" + PORTAL_AWARE_PLAYER_QUERIES
                + " PORTAL_AWARE_PLAYER_COORDINATES_FOR_QUERIES=" + PORTAL_AWARE_PLAYER_COORDINATES_FOR_QUERIES
                + " PORTAL_AWARE_MOB_TARGETING=" + PORTAL_AWARE_MOB_TARGETING
                + " SHOW_PROXY_MARKERS=" + SHOW_PROXY_MARKERS
                + " SHOW_PROXY_ARMOR_STANDS=" + SHOW_PROXY_ARMOR_STANDS
                + " SHOW_PORTAL_LOOK_MARKERS=" + SHOW_PORTAL_LOOK_MARKERS
                + " CLIENT_CONFIG={" + SkyesightClientConfig.status() + "}";
    }

    public static String setVerboseSpawn(boolean enabled) {
        VERBOSE_SPAWN = enabled;
        return status();
    }

    public static String setVerboseEntity(boolean enabled) {
        VERBOSE_ENTITY = enabled;
        return status();
    }

    public static String setVerboseRender(boolean enabled) {
        VERBOSE_RENDER = enabled;
        return status();
    }

    public static String setVerboseProximity(boolean enabled) {
        VERBOSE_PROXIMITY = enabled;
        return status();
    }

    public static String setSourceMap(boolean enabled) {
        SOURCE_MAP = enabled;
        return status();
    }

    public static String setPacketDebug(boolean enabled) {
        PACKET_DEBUG = enabled;
        return status();
    }

    public static String setWatchDebug(boolean enabled) {
        WATCH_DEBUG = enabled;
        return status();
    }

    public static String setTerrainAudit(boolean enabled) {
        TERRAIN_AUDIT = enabled;
        return status();
    }

    public static String setSodiumRendererAudit(boolean enabled) {
        SODIUM_RENDERER_AUDIT = enabled;
        return status();
    }

    public static String setSkyCaptureAudit(boolean enabled) {
        SKY_CAPTURE_AUDIT = enabled;
        return status();
    }

    public static String setRenderTargetAudit(boolean enabled) {
        RENDER_TARGET_AUDIT = enabled;
        return status();
    }

    public static String setRenderCullingAudit(boolean enabled) {
        RENDER_CULLING_AUDIT = enabled;
        return status();
    }

    public static String setRenderPerfAudit(boolean enabled) {
        RENDER_PERF_AUDIT = enabled;
        return status();
    }

    public static String setDebugStickAudit(boolean enabled) {
        DEBUG_STICK_AUDIT = enabled;
        return status();
    }

    public static String setPortalApiAudit(boolean enabled) {
        PORTAL_API_AUDIT = enabled;
        return status();
    }

    public static String setLifecycleDebug(boolean enabled) {
        LIFECYCLE_DEBUG = enabled;
        return status();
    }

    public static String setEntityDimensionContext(boolean enabled) {
        ENTITY_DIMENSION_CONTEXT = enabled;
        return status();
    }

    public static String setProxyMarker(boolean enabled) {
        SHOW_PROXY_MARKERS = enabled;
        return status();
    }

    public static String setProxyArmorStands(boolean enabled) {
        SHOW_PROXY_ARMOR_STANDS = enabled;
        return status();
    }

    public static String setPortalLookMarkers(boolean enabled) {
        SHOW_PORTAL_LOOK_MARKERS = enabled;
        return status();
    }

    public static String quiet() {
        SPAWN_SUMMARY = false;
        LIFECYCLE_SUMMARY = false;
        VERBOSE_SPAWN = false;
        VERBOSE_ENTITY = false;
        VERBOSE_RENDER = false;
        VERBOSE_PROXIMITY = false;
        SOURCE_MAP = false;
        PACKET_DEBUG = false;
        WATCH_DEBUG = false;
        TERRAIN_AUDIT = false;
        SODIUM_RENDERER_AUDIT = false;
        SKY_CAPTURE_AUDIT = false;
        RENDER_TARGET_AUDIT = false;
        RENDER_CULLING_AUDIT = false;
        RENDER_PERF_AUDIT = false;
        DEBUG_STICK_AUDIT = false;
        PORTAL_API_AUDIT = false;
        LIFECYCLE_DEBUG = false;
        ENTITY_DIMENSION_CONTEXT = false;
        SHOW_PROXY_MARKERS = false;
        SHOW_PROXY_ARMOR_STANDS = false;
        SHOW_PORTAL_LOOK_MARKERS = false;
        return status();
    }





    public static boolean shouldLogRenderTargetAudit() {
        return RENDER_TARGET_AUDIT;
    }



    public static boolean shouldLogDebugStickAudit() {
        return DEBUG_STICK_AUDIT;
    }

    public static boolean shouldLogPortalApiAudit() {
        return PORTAL_API_AUDIT;
    }


    private SkyesightDebugConfig() {}
}
