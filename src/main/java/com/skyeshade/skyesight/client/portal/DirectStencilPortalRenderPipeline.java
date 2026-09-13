package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class DirectStencilPortalRenderPipeline {
    private DirectStencilPortalRenderPipeline() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (SecondaryTransition.warmingPhysicalTerrain()) return;
        PortalDirectStencilRenderer.onRenderLevelStage(event);
    }

    public static boolean scheduleSecondaryBlockUpdate(BlockPos pos) {
        return PortalDirectStencilRenderer.scheduleDirectPortalSecondaryBlockUpdate(pos);
    }

    public static boolean scheduleSecondaryTerrainUpdate() {
        return PortalDirectStencilRenderer.scheduleDirectPortalSecondaryTerrainUpdate();
    }
}
