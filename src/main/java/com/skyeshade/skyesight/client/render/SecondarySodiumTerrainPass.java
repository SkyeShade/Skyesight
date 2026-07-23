package com.skyeshade.skyesight.client.render;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SecondarySodiumTerrainPass {
    private SecondarySodiumTerrainPass() {}

    public static void render(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event
    ) {
        frame.diagnostics().setBackend("SODIUM_TERRAIN_ONLY");
        PortalSecondaryWorldRenderer.renderSodiumTerrainOnly(frame, context, minecraft, event);
    }
}
