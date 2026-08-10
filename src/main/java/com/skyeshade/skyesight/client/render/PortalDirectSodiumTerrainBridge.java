package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.client.render.sodium.SameDimMainSodiumSectionReuse;
import com.skyeshade.skyesight.client.render.sodium.SodiumSecondaryViewState;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;

final class PortalDirectSodiumTerrainBridge {
    private static Field sodiumWorldRendererSectionManagerField;
    private static boolean sodiumRendererReadinessReflectionFailed;

    private PortalDirectSodiumTerrainBridge() {
    }

    static MainTerrainStateSnapshot captureMainTerrainState(Minecraft minecraft) {
        SameDimMainSodiumSectionReuse.MainTerrainState state =
                SameDimMainSodiumSectionReuse.captureMainTerrainState(minecraft);
        return new MainTerrainStateSnapshot(
                state.mainRenderListsIdentity(),
                state.mainRenderListsSize()
        );
    }

    static void close(SecondaryViewContext context) {
        SodiumSecondaryViewState.close(context);
    }

    static boolean scheduleBlockUpdate(
            SecondaryViewContext context,
            BlockPos pos,
            int maxPendingChunks
    ) {
        if (context == null || pos == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(pos);
        String notReadyReason = portalSodiumRendererNotReadyReason(context);
        if (notReadyReason != null) {
            context.enqueuePendingSodiumBlockUpdateChunk(chunk, maxPendingChunks);
            return false;
        }

        SodiumWorldRenderer renderer = SodiumSecondaryViewState.getOrCreate(context).renderer();

        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;

        RenderDevice.enterManagedCode();

        try {
            renderer.scheduleRebuildForChunks(
                    sectionX - 1,
                    sectionY - 1,
                    sectionZ - 1,
                    sectionX + 1,
                    sectionY + 1,
                    sectionZ + 1,
                    true
            );
            renderer.scheduleTerrainUpdate();
        } finally {
            RenderDevice.exitManagedCode();
        }

        return true;
    }

    static boolean scheduleTerrainUpdate(SecondaryViewContext context) {
        if (context == null) {
            return false;
        }
        if (portalSodiumRendererNotReadyReason(context) != null) {
            return false;
        }

        SodiumWorldRenderer renderer = SodiumSecondaryViewState.getOrCreate(context).renderer();

        renderer.scheduleTerrainUpdate();
        return true;
    }

    static boolean flushPendingBlockUpdates(SecondaryViewContext context, int maxUpdates) {
        if (context == null || context.pendingSodiumBlockUpdateChunks().isEmpty()) {
            return false;
        }
        String notReadyReason = portalSodiumRendererNotReadyReason(context);
        if (notReadyReason != null) {
            return false;
        }

        SodiumWorldRenderer renderer = SodiumSecondaryViewState.getOrCreate(context).renderer();
        ClientLevel level = Minecraft.getInstance().level;
        int flushed = 0;
        RenderDevice.enterManagedCode();
        try {
            var iterator = context.pendingSodiumBlockUpdateChunks().iterator();
            while (iterator.hasNext() && flushed < maxUpdates) {
                long packed = iterator.nextLong();
                int chunkX = ChunkPos.getX(packed);
                int chunkZ = ChunkPos.getZ(packed);
                renderer.scheduleRebuildForChunks(
                        chunkX,
                        level == null ? 0 : level.getMinSection(),
                        chunkZ,
                        chunkX,
                        level == null ? 15 : level.getMaxSection() - 1,
                        chunkZ,
                        true
                );
                iterator.remove();
                flushed++;
            }
            if (flushed > 0) {
                renderer.scheduleTerrainUpdate();
            }
        } finally {
            RenderDevice.exitManagedCode();
        }

        return flushed > 0;
    }

    private static String portalSodiumRendererNotReadyReason(SecondaryViewContext context) {
        if (context == null) {
            return "context-disposed";
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return "level-unload";
        }
        SodiumSecondaryViewState state = SodiumSecondaryViewState.get(context);
        SodiumWorldRenderer renderer = state == null ? null : state.renderer();
        if (renderer == null) {
            return "renderer-not-ready";
        }
        if (state.rendererLevel() != null && state.rendererLevel() != level) {
            return "level-mismatch";
        }
        if (!portalSodiumRendererHasSectionManager(renderer)) {
            return "render-section-manager-null";
        }
        return null;
    }

    private static boolean portalSodiumRendererHasSectionManager(SodiumWorldRenderer renderer) {
        if (renderer == null || sodiumRendererReadinessReflectionFailed) {
            return false;
        }
        try {
            initializeSodiumRendererReadinessReflection();
            return sodiumWorldRendererSectionManagerField.get(renderer) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            sodiumRendererReadinessReflectionFailed = true;
            return false;
        }
    }

    private static void initializeSodiumRendererReadinessReflection() throws NoSuchFieldException {
        if (sodiumWorldRendererSectionManagerField != null) {
            return;
        }
        sodiumWorldRendererSectionManagerField = SodiumWorldRenderer.class.getDeclaredField("renderSectionManager");
        sodiumWorldRendererSectionManagerField.setAccessible(true);
    }
}
