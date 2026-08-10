package com.skyeshade.skyesight.api;

/**
 * Selects the world features rendered into a secondary camera view.
 *
 * <p>The default options render a normal world view. These options describe
 * visible scene contents only; debug, Sodium, stencil, and portal internals are
 * intentionally not exposed through this API.
 */
public record SkyesightViewRenderOptions(
        boolean sky,
        boolean terrain,
        boolean blockEntities,
        boolean entities,
        boolean particles,
        boolean publishWatchRegion
) {
    public static final SkyesightViewRenderOptions DEFAULT =
            new SkyesightViewRenderOptions(true, true, true, true, true, false);

    public static SkyesightViewRenderOptions defaults() {
        return DEFAULT;
    }

    public SkyesightViewRenderOptions withSky(boolean sky) {
        return new SkyesightViewRenderOptions(
                sky,
                this.terrain,
                this.blockEntities,
                this.entities,
                this.particles,
                this.publishWatchRegion
        );
    }

    public SkyesightViewRenderOptions withTerrain(boolean terrain) {
        return new SkyesightViewRenderOptions(
                this.sky,
                terrain,
                this.blockEntities,
                this.entities,
                this.particles,
                this.publishWatchRegion
        );
    }

    public SkyesightViewRenderOptions withBlockEntities(boolean blockEntities) {
        return new SkyesightViewRenderOptions(
                this.sky,
                this.terrain,
                blockEntities,
                this.entities,
                this.particles,
                this.publishWatchRegion
        );
    }

    public SkyesightViewRenderOptions withEntities(boolean entities) {
        return new SkyesightViewRenderOptions(
                this.sky,
                this.terrain,
                this.blockEntities,
                entities,
                this.particles,
                this.publishWatchRegion
        );
    }

    public SkyesightViewRenderOptions withParticles(boolean particles) {
        return new SkyesightViewRenderOptions(
                this.sky,
                this.terrain,
                this.blockEntities,
                this.entities,
                particles,
                this.publishWatchRegion
        );
    }

    public SkyesightViewRenderOptions withWatchRegion(boolean publishWatchRegion) {
        return new SkyesightViewRenderOptions(
                this.sky,
                this.terrain,
                this.blockEntities,
                this.entities,
                this.particles,
                publishWatchRegion
        );
    }
}
