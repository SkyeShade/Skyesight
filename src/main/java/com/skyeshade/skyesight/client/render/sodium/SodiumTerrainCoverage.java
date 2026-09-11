package com.skyeshade.skyesight.client.render.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.minecraft.core.BlockPos;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/** Sodium-specific read-only adapter for the geometry lists actually submitted to rendering. */
public final class SodiumTerrainCoverage {
    private static Field managerField, listsField, sectionsField;
    private static java.lang.reflect.Method searchDistance, withinDistance;
    private SodiumTerrainCoverage() {}
    private static void initialize() throws ReflectiveOperationException {
        if (managerField != null) return;
        var manager = SodiumWorldRenderer.class.getDeclaredField("renderSectionManager"); manager.setAccessible(true);
        listsField = RenderSectionManager.class.getDeclaredField("renderLists"); listsField.setAccessible(true);
        sectionsField = RenderSectionManager.class.getDeclaredField("sectionByPosition"); sectionsField.setAccessible(true);
        searchDistance = RenderSectionManager.class.getDeclaredMethod("getSearchDistance"); searchDistance.setAccessible(true);
        withinDistance = net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller.class.getDeclaredMethod("isWithinRenderDistance",
                net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform.class,
                net.caffeinemc.mods.sodium.client.render.chunk.RenderSection.class, float.class);
        withinDistance.setAccessible(true);
        managerField = manager;
    }
    public static Set<BlockPos> visible(SodiumWorldRenderer renderer) {
        if (renderer == null) return Set.of();
        try {
            initialize();
            var manager = managerField.get(renderer);
            if (manager == null) return Set.of();
            var lists = (SortedRenderLists) listsField.get(manager);
            if (lists == null) return Set.of();
            var result = new HashSet<BlockPos>();
            for (var iterator = lists.iterator(); iterator.hasNext();) {
                var list = iterator.next();
                var sections = list.sectionsWithGeometryIterator(false);
                if (sections == null) continue;
                while (sections.hasNext()) {
                    var section = list.getRegion().getSection(sections.nextByteAsInt());
                    if (section != null && section.isBuilt()) result.add(section.getPosition().origin());
                }
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect Sodium transition coverage", failure);
        }
    }
    @SuppressWarnings("unchecked")
    public static Set<BlockPos> physicalVisible() {
        var renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer == null) return Set.of();
        try {
            initialize();
            var manager = (RenderSectionManager) managerField.get(renderer);
            if (manager == null) return Set.of();
            var sections = (it.unimi.dsi.fastutil.longs.Long2ReferenceMap<net.caffeinemc.mods.sodium.client.render.chunk.RenderSection>) sectionsField.get(manager);
            float distance = (float) searchDistance.invoke(manager);
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            var transform = new net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform(camera.x, camera.y, camera.z);
            var result = new HashSet<BlockPos>();
            for (var section : sections.values()) {
                var pos = section.getPosition();
                // lastVisibleFrame also marks queued neighbors rejected by the fog/distance test.
                // Such sections are deliberately never built. Apply Sodium's own distance test.
                if (manager.isSectionVisible(pos.x(), pos.y(), pos.z())
                        && (boolean) withinDistance.invoke(null, transform, section, distance)) result.add(pos.origin());
            }
            return result;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
