package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.client.compat.sodium.SkyesightSodiumCompat;
import com.skyeshade.skyesight.client.render.sodium.SodiumSecondaryViewState;
import com.skyeshade.skyesight.client.render.sodium.SodiumTerrainCoverage;
import com.skyeshade.skyesight.mixin.client.LevelRendererAccessor;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Positions only: renderer ownership, meshes and mutable chunks never cross this boundary. */
public final class SecondaryTerrainCoverage {
    private SecondaryTerrainCoverage() {}
    public static Set<BlockPos> visibleVanilla(LevelRenderer renderer) {
        var result = new HashSet<BlockPos>();
        for (var section : ((LevelRendererAccessor) renderer).skyesight$getVisibleSections()) {
            if (section.compiled.get() != SectionRenderDispatcher.CompiledSection.UNCOMPILED
                    && !section.compiled.get().hasNoRenderableLayers()) result.add(section.getOrigin().immutable());
        }
        return result;
    }
    public static Set<BlockPos> visible(SecondarySceneFrame scene) {
        if (scene.visualWorld() != null) return scene.visualWorld().visibleTerrainSections();
        if (SkyesightSodiumCompat.isLoaded()) return SodiumAccess.visible(scene.context());
        return scene.context().vanillaState() instanceof VanillaSecondaryViewState state
                ? visibleVanilla(state.rendererFor(scene.level())) : Set.of();
    }
    public static Set<BlockPos> physicalVisible(LevelRenderer renderer) {
        if (SkyesightSodiumCompat.isLoaded()) return SodiumTerrainCoverage.physicalVisible();
        var result = new HashSet<BlockPos>();
        for (var section : ((LevelRendererAccessor) renderer).skyesight$getVisibleSections()) result.add(section.getOrigin().immutable());
        return result;
    }
    public static Set<BlockPos> physicalDrawn(LevelRenderer renderer) {
        return SkyesightSodiumCompat.isLoaded()
                ? SodiumAccess.physicalDrawn()
                : visibleVanilla(renderer);
    }
    private static final class SodiumAccess {
        static Set<BlockPos> physicalDrawn() {
            return SodiumTerrainCoverage.visible(
                    SodiumWorldRenderer.instanceNullable());
        }
        static Set<BlockPos> visible(SecondaryViewContext context) {
            var state = SodiumSecondaryViewState.get(context);
            return state == null ? Set.of() : SodiumTerrainCoverage.visible(state.renderer());
        }
    }
}
