package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import com.skyeshade.skyesight.server.SkyesightServerViewTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CrossDimPortalViewUpdater {
    private CrossDimPortalViewUpdater() {}

    public static void updateStorageForView(
            Minecraft minecraft,
            Camera camera,
            RegisteredPortalView view,
            PortalRenderView renderView
    ) {
        if (view == null) {
            return;
        }
        updateStorageForView(
                minecraft,
                camera,
                view.id().getPath(),
                view.id(),
                renderView == null ? null : renderView.entrancePortal(),
                renderView == null ? null : renderView.exitPortal(),
                renderView,
                view.target().dimension()
        );
    }

    private static void updateStorageForView(
            Minecraft minecraft,
            Camera camera,
            String label,
            ResourceLocation regionId,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            PortalRenderView renderView,
            ResourceKey<Level> targetDimension
    ) {
        if (camera == null || entrancePortal == null || exitPortal == null || renderView == null || targetDimension == null || !renderView.renderConfig().enabled()) {
            return;
        }

        DirectStencilPortalMath.PortalCameraPose targetPose =
                DirectStencilPortalMath.transformCamera(camera, entrancePortal, exitPortal);
        BlockPos targetBlock = BlockPos.containing(targetPose.position());
        ChunkPos targetChunk = new ChunkPos(targetBlock);
        PortalSecondaryWorldRenderer.requestCrossDimensionPortalChunks(
                minecraft,
                label,
                regionId,
                minecraft.level == null ? Level.OVERWORLD : minecraft.level.dimension(),
                targetDimension,
                targetBlock,
                targetChunk,
                renderView.renderConfig().terrainChunkRadius()
        );
    }

    public static void updateEntityWatchRegion(
            Minecraft minecraft,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius,
            int radiusChunks
    ) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null || center == null) {
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        BlockPos centerBlock = BlockPos.containing(center);
        ChunkPos centerChunk = new ChunkPos(centerBlock);
        List<ChunkPos> watchedChunks = buildSquareChunkList(centerChunk, radiusChunks);
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player == null) {
                return;
            }

            SkyesightSecondaryWatchRegion.setRegion(
                    player,
                    regionId,
                    dimension,
                    center,
                    radius
            );
            SkyesightServerViewTracker.updateWatch(
                    player,
                    regionId,
                    dimension,
                    centerChunk.x,
                    centerChunk.z,
                    radiusChunks,
                    watchedChunks
            );
        });
    }

    public static void removeEntityWatchRegion(Minecraft minecraft, ResourceLocation regionId) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null) {
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null) {
                SkyesightSecondaryWatchRegion.removeRegion(player, regionId);
            }
        });
    }

    private static List<ChunkPos> buildSquareChunkList(ChunkPos center, int radius) {
        List<ChunkPos> chunks = new ArrayList<>();

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                chunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        return chunks;
    }

}
