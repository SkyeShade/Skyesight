package com.skyeshade.skyesight;

public final class SkyesightPortalEntityPoolConfig {
    /*
     * Experimental and intentionally default-off.
     *
     * Future work: replace the snapshot cross-dim entity protocol with this
     * isolated portal entity pool only after render-state parity is solved.
     * That requires packet/state parity for animation, head/body rotation,
     * multipart entities, and custom mod state.
     */
    public static final boolean ENABLE_PORTAL_ENTITY_POOL_POPULATION = false;
    public static final boolean SKYESIGHT_DISABLE_ISOLATED_PORTAL_ENTITY_POOL_TRACKER = false;
    public static final boolean SKYESIGHT_DISABLE_CROSS_DIM_SECONDARY_WATCH_REGION_RAW_TRACKING = false;
    public static final boolean SKYESIGHT_DISABLE_CROSS_DIM_ENTITY_SNAPSHOTS = false;
    public static final boolean SKYESIGHT_DISABLE_ALL_CROSS_DIM_ENTITY_EXTRAS = false;
    private static volatile boolean disabledForSession;

    private SkyesightPortalEntityPoolConfig() {}

    public static boolean portalEntityPoolPopulationEnabled() {
        return ENABLE_PORTAL_ENTITY_POOL_POPULATION
                && !SKYESIGHT_DISABLE_ALL_CROSS_DIM_ENTITY_EXTRAS
                && !SKYESIGHT_DISABLE_ISOLATED_PORTAL_ENTITY_POOL_TRACKER
                && !disabledForSession;
    }

    public static boolean crossDimSecondaryWatchRegionRawTrackingDisabled() {
        return SKYESIGHT_DISABLE_ALL_CROSS_DIM_ENTITY_EXTRAS
                || SKYESIGHT_DISABLE_CROSS_DIM_SECONDARY_WATCH_REGION_RAW_TRACKING;
    }

    public static boolean crossDimEntitySnapshotsDisabled() {
        return SKYESIGHT_DISABLE_ALL_CROSS_DIM_ENTITY_EXTRAS
                || SKYESIGHT_DISABLE_CROSS_DIM_ENTITY_SNAPSHOTS;
    }

    public static void disablePopulationForSession() {
        disabledForSession = true;
    }

    public static boolean populationDisabledForSession() {
        return disabledForSession;
    }
}
