package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.PortalStencilMask;
import com.skyeshade.skyesight.api.PortalRenderSettings;

/**
 * Per-portal render and loading configuration for the temporary direct-stencil
 * portal harness.
 *
 * <p>{@code enabled} controls whether the portal participates at all.
 * {@code rendersView=false} leaves the portal as an exit-only / one-way
 * destination: it can be targeted by another portal, but it does not render its
 * own view. {@code stencilRef} is the unique stencil reference used for that
 * portal's aperture.</p>
 *
 * <p>The radii belong to the portal that renders the view, not the exit. For
 * example, the C -> D far-view test uses C's radii for loading/rendering D's
 * destination region. {@code terrainChunkRadius} drives remote terrain chunk
 * loading/rendering and diagnostics, {@code entityChunkRadius} drives server
 * entity tracking plus client render filtering, {@code blockEntityChunkRadius}
 * drives block-entity scanning/rendering, and {@code blockUpdateChunkRadius}
 * is configured/reported for remote block update forwarding where that path is
 * implemented.</p>
 *
 * <p>The render flags gate the individual portal subpasses: sky/environment,
 * terrain, translucent terrain/water, entities, and block entities.</p>
 */
public record DebugPortalRenderConfig(
        boolean enabled,
        boolean rendersView,
        int stencilRef,
        int terrainChunkRadius,
        int portalOwnedRenderRadiusChunks,
        int sameDimPlayerLoadedReuseRadiusChunks,
        boolean reusePlayerLoadedChunksForSameDim,
        int entityChunkRadius,
        int blockEntityChunkRadius,
        int blockUpdateChunkRadius,
        boolean renderSky,
        boolean renderTerrain,
        boolean renderTranslucent,
        boolean renderEntities,
        boolean renderBlockEntities,
        boolean renderParticles,
        PortalStencilMask stencilMask
) {
    public DebugPortalRenderConfig(
            boolean enabled,
            boolean rendersView,
            int stencilRef,
            int terrainChunkRadius,
            int entityChunkRadius,
            int blockEntityChunkRadius,
            int blockUpdateChunkRadius,
            boolean renderSky,
            boolean renderTerrain,
            boolean renderTranslucent,
            boolean renderEntities,
            boolean renderBlockEntities
    ) {
        this(
                enabled,
                rendersView,
                stencilRef,
                terrainChunkRadius,
                terrainChunkRadius,
                PortalRenderSettings.DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS,
                true,
                entityChunkRadius,
                blockEntityChunkRadius,
                blockUpdateChunkRadius,
                renderSky,
                renderTerrain,
                renderTranslucent,
                renderEntities,
                renderBlockEntities,
                true,
                null
        );
    }

    public static DebugPortalRenderConfig of(
            int stencilRef,
            int terrainChunkRadius,
            int entityChunkRadius,
            int blockEntityChunkRadius,
            int blockUpdateChunkRadius
    ) {
        return of(
                true,
                true,
                stencilRef,
                terrainChunkRadius,
                entityChunkRadius,
                blockEntityChunkRadius,
                blockUpdateChunkRadius
        );
    }

    public static DebugPortalRenderConfig of(
            boolean enabled,
            boolean rendersView,
            int stencilRef,
            int terrainChunkRadius,
            int entityChunkRadius,
            int blockEntityChunkRadius,
            int blockUpdateChunkRadius
    ) {
        return new DebugPortalRenderConfig(
                enabled,
                rendersView,
                stencilRef,
                terrainChunkRadius,
                entityChunkRadius,
                blockEntityChunkRadius,
                blockUpdateChunkRadius,
                true,
                true,
                true,
                true,
                true
        );
    }
}
