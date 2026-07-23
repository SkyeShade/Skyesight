package com.skyeshade.skyesight.client.portal;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class DirectStencilPortalRenderPipeline {
    private DirectStencilPortalRenderPipeline() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        PortalDirectStencilRenderer.onRenderLevelStage(event);
    }

    public static boolean scheduleSodiumBlockUpdate(BlockPos pos) {
        return PortalDirectStencilRenderer.scheduleDirectPortalSodiumBlockUpdate(pos);
    }

    public static boolean scheduleSodiumTerrainUpdate() {
        return PortalDirectStencilRenderer.scheduleDirectPortalSodiumTerrainUpdate();
    }
}
