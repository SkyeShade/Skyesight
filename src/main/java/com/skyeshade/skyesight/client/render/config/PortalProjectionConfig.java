package com.skyeshade.skyesight.client.render.config;

import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;

public final class PortalProjectionConfig {
    public static final PortalSecondaryWorldRenderer.SecondaryProjectionMode SECONDARY_PROJECTION_MODE =
            PortalSecondaryWorldRenderer.SecondaryProjectionMode.PORTAL_OFF_AXIS;
    public static final boolean PORTAL_OFF_AXIS_CULL_USES_NORMAL_PERSPECTIVE = true;
    public static final PortalSecondaryWorldRenderer.DirectPortalProjectionMode DIRECT_PORTAL_PROJECTION_MODE =
            PortalSecondaryWorldRenderer.DirectPortalProjectionMode.DIRECT_MAIN_PROJECTION_OBLIQUE_CLIP;
    public static final PortalSecondaryWorldRenderer.PortalProjectionHandednessMode DIRECT_PORTAL_PROJECTION_HANDEDNESS =
            PortalSecondaryWorldRenderer.PortalProjectionHandednessMode.ORIGINAL_FLIPPED_RIGHT;
    public static final int VIEW_WIDTH = 300;
    public static final int VIEW_HEIGHT = 600;
    public static final float VIEW_FOV = 70.0F;

    private PortalProjectionConfig() {}
}
