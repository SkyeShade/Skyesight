package com.skyeshade.skyesight.client.render;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

public final class SecondarySodiumTranslucentTerrainPass {
    private SecondarySodiumTranslucentTerrainPass() {}

    public static void render(
            SecondaryViewFrame frame,
            SodiumWorldRenderer renderer,
            ChunkRenderMatrices matrices
    ) {
        Vec3 cameraPosition = frame.camera().getPosition();

        RenderType.translucent().setupRenderState();

        try {
            renderer.drawChunkLayer(
                    RenderType.translucent(),
                    matrices,
                    cameraPosition.x(),
                    cameraPosition.y(),
                    cameraPosition.z()
            );
        } finally {
            RenderType.translucent().clearRenderState();
        }
    }
}
