package com.skyeshade.skyesight.mixin.client.sodium;

import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumRenderContext;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerRenderLayerDebugMixin {
    @Shadow
    private SortedRenderLists renderLists;

    @Shadow
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Inject(
            method = "createTerrainRenderList",
            at = @At("RETURN")
    )
    private void skyesight$recordCreateTerrainRenderListReturn(
            Camera camera,
            Viewport viewport,
            int frame,
            boolean spectator,
            CallbackInfo ci
    ) {
        if (!SkyesightSodiumRenderContext.isActive()) {
            return;
        }

        if (PortalSecondaryWorldRenderer.sodiumForceRemoteRenderListEnabled()) {
            forceRemoteRenderList(viewport, frame);
        }
    }

    private void forceRemoteRenderList(Viewport viewport, int frame) {
        SectionPos center = viewport.getChunkCoord();
        int radius = PortalSecondaryWorldRenderer.sodiumForceRemoteRenderListRadius();
        int forcedFrame = frame + 1_000_000;
        VisibleChunkCollector collector = new VisibleChunkCollector(forcedFrame);

        try (SkyesightSodiumRenderContext.Scope ignored =
                     SkyesightSodiumRenderContext.pushForceRenderListConstruction()) {
            for (RenderSection section : this.sectionByPosition.values()) {
                SectionPos position = section.getPosition();

                if (Math.abs(position.getX() - center.getX()) > radius
                        || Math.abs(position.getZ() - center.getZ()) > radius) {
                    continue;
                }

                if (section.isBuilt() && (section.getFlags() & 1) != 0) {
                    collector.visit(section);
                }
            }
        }

        this.renderLists = collector.createRenderLists(viewport);
    }

}
