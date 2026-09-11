package com.skyeshade.skyesight.client.render;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.skyeshade.skyesight.client.render.SecondarySceneOptions.Pass.*;
import static org.junit.jupiter.api.Assertions.*;

class SecondarySceneOptionsTest {
    @Test void hidingTerrainDoesNotHideIndependentSceneFeatures() {
        var options = new SecondarySceneOptions(4, 4, 4, false, true, true, true, true);
        assertEquals(List.of(BLOCK_ENTITIES, ENTITIES, PARTICLES), options.contentPasses());
        var full = new SecondarySceneOptions(4, 4, 4, true, true, true, true, true);
        assertEquals(List.of(TERRAIN, BLOCK_ENTITIES, ENTITIES, PARTICLES), full.contentPasses());
        assertThrows(UnsupportedOperationException.class, () -> full.contentPasses().clear());
    }

    @Test void framePolicyIsCapturedBeforeLegacyDiagnosticsChange() {
        var frame = new SecondaryViewFrame(null, null, 320, 180, new org.joml.Matrix4f(),
                new org.joml.Matrix4f(), new org.joml.Matrix4f(), null);
        frame.diagnostics().setTerrainChunkRadius(4);
        frame.diagnostics().setEntityChunkRadius(3);
        frame.diagnostics().setBlockEntityChunkRadius(2);
        var first = SecondarySceneOptions.from(frame);
        frame.diagnostics().setTerrainChunkRadius(12);
        frame.diagnostics().setRenderTerrain(false);
        var next = SecondarySceneOptions.from(frame);
        assertEquals(4, first.terrainRadius());
        assertTrue(first.terrain());
        assertEquals(12, next.terrainRadius());
        assertFalse(next.terrain());
        assertEquals(3, next.entityRadius());
        assertEquals(2, next.blockEntityRadius());
    }
}
