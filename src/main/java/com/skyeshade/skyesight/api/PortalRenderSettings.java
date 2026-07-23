package com.skyeshade.skyesight.api;

public record PortalRenderSettings(
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
    /**
     * Registration-time sentinel for automatic stencil reference assignment.
     * Rendered portal views must use a positive final stencil reference.
     */
    public static final int AUTO_STENCIL_REF = 0;
    public static final int DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS = 10;

    public PortalRenderSettings(
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
            boolean renderBlockEntities,
            boolean renderParticles
    ) {
        this(
                enabled,
                rendersView,
                stencilRef,
                terrainChunkRadius,
                terrainChunkRadius,
                DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS,
                true,
                entityChunkRadius,
                blockEntityChunkRadius,
                blockUpdateChunkRadius,
                renderSky,
                renderTerrain,
                renderTranslucent,
                renderEntities,
                renderBlockEntities,
                renderParticles,
                null
        );
    }


    public static PortalRenderSettings defaults() {
        return defaultsWithInternalStencilRef(AUTO_STENCIL_REF);
    }

    public static PortalRenderSettings defaultsAutoStencil() {
        return defaults();
    }

    /**
     * @deprecated Normal portal registrations should use automatic stencil assignment.
     * Explicit refs are retained for internal deterministic debug registrations.
     */
    @Deprecated
    public static PortalRenderSettings defaults(int stencilRef) {
        return defaultsWithInternalStencilRef(stencilRef);
    }

    static PortalRenderSettings defaultsWithInternalStencilRef(int stencilRef) {
        return new PortalRenderSettings(
                true,
                true,
                stencilRef,
                8,
                8,
                DEFAULT_SAME_DIM_PLAYER_LOADED_REUSE_RADIUS_CHUNKS,
                true,
                4,
                8,
                8,
                true,
                true,
                true,
                true,
                true,
                true,
                null
        );
    }

    PortalRenderSettings withInternalStencilRef(int stencilRef) {
        return new PortalRenderSettings(
                this.enabled,
                this.rendersView,
                stencilRef,
                this.terrainChunkRadius,
                this.portalOwnedRenderRadiusChunks,
                this.sameDimPlayerLoadedReuseRadiusChunks,
                this.reusePlayerLoadedChunksForSameDim,
                this.entityChunkRadius,
                this.blockEntityChunkRadius,
                this.blockUpdateChunkRadius,
                this.renderSky,
                this.renderTerrain,
                this.renderTranslucent,
                this.renderEntities,
                this.renderBlockEntities,
                this.renderParticles,
                this.stencilMask
        );
    }

    public PortalRenderSettings withStencilMask(PortalStencilMask stencilMask) {
        return new PortalRenderSettings(
                this.enabled,
                this.rendersView,
                this.stencilRef,
                this.terrainChunkRadius,
                this.portalOwnedRenderRadiusChunks,
                this.sameDimPlayerLoadedReuseRadiusChunks,
                this.reusePlayerLoadedChunksForSameDim,
                this.entityChunkRadius,
                this.blockEntityChunkRadius,
                this.blockUpdateChunkRadius,
                this.renderSky,
                this.renderTerrain,
                this.renderTranslucent,
                this.renderEntities,
                this.renderBlockEntities,
                this.renderParticles,
                stencilMask
        );
    }
}
