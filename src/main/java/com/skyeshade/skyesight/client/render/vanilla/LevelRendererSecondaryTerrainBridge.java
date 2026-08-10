package com.skyeshade.skyesight.client.render.vanilla;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

public interface LevelRendererSecondaryTerrainBridge {
    void skyesight$setupSecondaryTerrain(Camera camera, Frustum frustum, boolean spectator);

    void skyesight$compileSecondarySections(Camera camera);

    void skyesight$renderSecondarySectionLayer(
            RenderType renderType,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4f modelView,
            Matrix4f projection
    );
}
