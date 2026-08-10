package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.client.compat.sodium.SkyesightSodiumCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SecondarySodiumTerrainPass {
    private SecondarySodiumTerrainPass() {}

    public static boolean render(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            RenderLevelStageEvent event
    ) {
        return render(frame, context, minecraft, event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    public static boolean render(
            SecondaryViewFrame frame,
            SecondaryViewContext context,
            Minecraft minecraft,
            float partialTick
    ) {
        if (!SkyesightSodiumCompat.isLoaded()) {
            return PortalSecondaryVanillaTerrainRenderer.render(frame, context, minecraft, partialTick);
        }

        return SodiumMethods.render(frame, context, minecraft, partialTick);
    }

    public static void close(SecondaryViewContext context) {
        if (SkyesightSodiumCompat.isLoaded()) {
            SodiumMethods.close(context);
        } else if (context != null) {
            context.setSodiumState(null);
        }
        PortalSecondaryVanillaTerrainRenderer.close(context);
    }

    public static boolean terrainAvailable() {
        return true;
    }

    public static MainTerrainStateSnapshot captureMainTerrainState(Minecraft minecraft) {
        if (!SkyesightSodiumCompat.isLoaded()) {
            return MainTerrainStateSnapshot.unavailable();
        }
        return SodiumMethods.captureMainTerrainState(minecraft);
    }

    public static boolean scheduleBlockUpdate(
            SecondaryViewContext context,
            BlockPos pos,
            int maxPendingChunks
    ) {
        if (!terrainAvailable()) {
            return false;
        }
        if (!SkyesightSodiumCompat.isLoaded()) {
            return PortalSecondaryVanillaTerrainRenderer.scheduleBlockUpdate(context, pos);
        }
        return SodiumMethods.scheduleBlockUpdate(context, pos, maxPendingChunks);
    }

    public static boolean scheduleTerrainUpdate(SecondaryViewContext context) {
        if (!terrainAvailable()) {
            return false;
        }
        if (!SkyesightSodiumCompat.isLoaded()) {
            return PortalSecondaryVanillaTerrainRenderer.scheduleTerrainUpdate(context);
        }
        return SodiumMethods.scheduleTerrainUpdate(context);
    }

    public static boolean flushPendingBlockUpdates(
            SecondaryViewContext context,
            int maxUpdates
    ) {
        if (!terrainAvailable()) {
            return false;
        }
        if (!SkyesightSodiumCompat.isLoaded()) {
            return false;
        }
        return SodiumMethods.flushPendingBlockUpdates(context, maxUpdates);
    }

    public static void prewarmPortalRenderersIfNeeded(Minecraft minecraft) {
        if (SkyesightSodiumCompat.isLoaded()) {
            SodiumMethods.prewarmPortalRenderersIfNeeded(minecraft);
        }
    }

    public static void clearRendererPool() {
        if (SkyesightSodiumCompat.isLoaded()) {
            SodiumMethods.clearRendererPool();
        }
    }

    private static final class SodiumMethods {
        private SodiumMethods() {}

        private static boolean render(
                SecondaryViewFrame frame,
                SecondaryViewContext context,
                Minecraft minecraft,
                float partialTick
        ) {
            return PortalSecondarySodiumTerrainRenderer.render(frame, context, minecraft, partialTick);
        }

        private static void close(SecondaryViewContext context) {
            PortalDirectSodiumTerrainBridge.close(context);
        }

        private static MainTerrainStateSnapshot captureMainTerrainState(Minecraft minecraft) {
            return PortalDirectSodiumTerrainBridge.captureMainTerrainState(minecraft);
        }

        private static boolean scheduleBlockUpdate(
                SecondaryViewContext context,
                BlockPos pos,
                int maxPendingChunks
        ) {
            return PortalDirectSodiumTerrainBridge.scheduleBlockUpdate(context, pos, maxPendingChunks);
        }

        private static boolean scheduleTerrainUpdate(SecondaryViewContext context) {
            return PortalDirectSodiumTerrainBridge.scheduleTerrainUpdate(context);
        }

        private static boolean flushPendingBlockUpdates(
                SecondaryViewContext context,
                int maxUpdates
        ) {
            return PortalDirectSodiumTerrainBridge.flushPendingBlockUpdates(context, maxUpdates);
        }

        private static void prewarmPortalRenderersIfNeeded(Minecraft minecraft) {
            PortalSecondarySodiumTerrainRenderer.prewarmPortalRenderersIfNeeded(minecraft);
        }

        private static void clearRendererPool() {
            PortalSecondarySodiumTerrainRenderer.clearRendererPool();
        }
    }
}
