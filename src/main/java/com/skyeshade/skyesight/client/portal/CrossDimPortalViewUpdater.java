package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.PortalEndpoint;
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

    public static boolean requestInitialTerrainWarmup(
            Minecraft minecraft,
            Camera camera,
            RegisteredPortalView view
    ) {
        if (minecraft == null
                || minecraft.level == null
                || camera == null
                || view == null
                || view.source() == null
                || view.target() == null
                || view.renderSettings() == null
                || !view.active()
                || !view.renderSettings().enabled()
                || !view.renderSettings().rendersView()
                || !view.renderSettings().renderTerrain()
                || !view.isCrossDimension()
                || !view.source().dimension().equals(minecraft.level.dimension())) {
            return false;
        }

        requestChunksForPortalFrames(
                minecraft,
                camera,
                view.id().getPath(),
                view.id(),
                portalFrame(view.source()),
                portalFrame(view.target()),
                view.target().dimension(),
                view.renderSettings().terrainChunkRadius()
        );
        return true;
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

        requestChunksForPortalFrames(
                minecraft,
                camera,
                label,
                regionId,
                entrancePortal,
                exitPortal,
                targetDimension,
                renderView.renderConfig().terrainChunkRadius()
        );
    }

    private static void requestChunksForPortalFrames(
            Minecraft minecraft,
            Camera camera,
            String label,
            ResourceLocation regionId,
            PortalFrame entrancePortal,
            PortalFrame exitPortal,
            ResourceKey<Level> targetDimension,
            int terrainChunkRadius
    ) {
        if (minecraft == null || minecraft.level == null || camera == null || entrancePortal == null || exitPortal == null || regionId == null || targetDimension == null) {
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
                terrainChunkRadius
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

    private static PortalFrame portalFrame(PortalEndpoint endpoint) {
        return new PortalFrame(
                endpoint.center(),
                endpoint.rotation(),
                endpoint.width(),
                endpoint.height()
        );
    }

}
