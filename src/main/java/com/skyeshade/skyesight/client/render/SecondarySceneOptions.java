package com.skyeshade.skyesight.client.render;

import java.util.ArrayList;
import java.util.List;

/** Immutable scene policy, independent of the legacy frame diagnostics and output type. */
public record SecondarySceneOptions(int terrainRadius, int entityRadius, int blockEntityRadius,
                                    boolean terrain, boolean translucent, boolean blockEntities,
                                    boolean entities, boolean particles) {
    public enum Pass { TERRAIN, BLOCK_ENTITIES, ENTITIES, PARTICLES }

    public SecondarySceneOptions {
        terrainRadius = Math.max(1, terrainRadius);
        entityRadius = Math.max(0, entityRadius);
        blockEntityRadius = Math.max(0, blockEntityRadius);
    }

    public List<Pass> contentPasses() {
        var passes = new ArrayList<Pass>(4);
        if (terrain) passes.add(Pass.TERRAIN);
        if (blockEntities) passes.add(Pass.BLOCK_ENTITIES);
        if (entities) passes.add(Pass.ENTITIES);
        if (particles) passes.add(Pass.PARTICLES);
        return List.copyOf(passes);
    }

    static SecondarySceneOptions from(SecondaryViewFrame frame) {
        var options = frame.diagnostics();
        return new SecondarySceneOptions(options.terrainChunkRadius(), options.entityChunkRadius(), options.blockEntityChunkRadius(),
                options.renderTerrain(), options.renderTranslucent(), options.renderBlockEntities(), options.renderEntities(), options.renderParticles());
    }
}
