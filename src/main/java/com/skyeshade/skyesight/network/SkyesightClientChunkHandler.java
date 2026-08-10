package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class SkyesightClientChunkHandler {
    private static long lastChunkRouteSummaryMillis;
    private static int chunkRouteStored;
    private static int chunkRouteRouted;
    private static int chunkRouteCollisions;
    private static int chunkRouteMainClientCacheTouched;
    private static final Map<ResourceLocation, Integer> chunkRouteByView = new LinkedHashMap<>();
    private static final Map<String, Integer> chunkRouteByDimension = new LinkedHashMap<>();
    private static final Set<String> staleChunkWarnings = new HashSet<>();
    private static String chunkRouteSample = "-";

    private SkyesightClientChunkHandler() {}

    public static void invalidateView(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        chunkRouteByView.remove(viewId);
        staleChunkWarnings.removeIf(key -> key.startsWith(viewId.toString() + "|"));
        if (chunkRouteSample.startsWith(viewId.toString() + ":")) {
            chunkRouteSample = "-";
        }
    }

    public static void handleChunkDataOnClient(SkyesightChunkDataPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        SkyesightRemoteViewRegistration registration = currentViewForPayload(payload);
        if (registration == null) {
            return;
        }
        boolean sameAsClientDimension = minecraft.level != null
                && minecraft.level.dimension().equals(payload.dimension());
        boolean mainPlayerChunkStillExistsBefore = playerChunkStillExists(minecraft);
        SkyesightPortalChunkStorage.store(
                payload.dimension(),
                payload.viewId(),
                payload.chunkX(),
                payload.chunkZ(),
                payload.chunkData(),
                payload.lightData()
        );
        boolean storedInPortalStorage = SkyesightPortalChunkStorage.hasChunk(
                payload.dimension(),
                new ChunkPos(payload.chunkX(), payload.chunkZ())
        );

        if (SkyesightDebugConfig.VERBOSE_RENDER || SkyesightDebugConfig.PACKET_DEBUG) {
            Skyesight.LOGGER.info(
                    "[Skyesight] Received chunk {}, {} for view {}",
                    payload.chunkX(),
                    payload.chunkZ(),
                    payload.viewId()
            );
        }
        if (SkyesightDebugConfig.WATCH_DEBUG && !sameAsClientDimension) {
            Skyesight.LOGGER.info(
                    "[Skyesight] SKYESIGHT_CROSS_DIM_WATCH_REGION: viewId={} displayDimension={} cameraDimension={} chunk={},{} snapshotReceived=yes",
                    payload.viewId(),
                    minecraft.level == null ? "-" : minecraft.level.dimension().location(),
                    payload.dimension().location(),
                    payload.chunkX(),
                    payload.chunkZ()
            );
        }

        if (!sameAsClientDimension) {
            VisualWorldRouteResult visualWorldRoute = routeVisualWorldPayload(payload);
            if (visualWorldRoute.inserted()) {
                SkyesightClientChunkRequester.markChunkReceived(
                        payload.viewId(),
                        payload.dimension(),
                        payload.chunkX(),
                        payload.chunkZ()
                );
            }
            SkyesightVisualWorld visualWorld = SkyesightVisualWorldManager.get(payload.viewId());
            if (SkyesightDebugConfig.WATCH_DEBUG && visualWorld != null && !visualWorld.isClosed()) {
                LevelChunk chunk = visualWorld.level().getChunkSource().getChunk(
                        payload.chunkX(),
                        payload.chunkZ(),
                        false
                );
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_INITIAL_BLOCK_ENTITY_RECEIVE: viewId={} cameraDimension={} chunkPos={},{} blockEntityCount={}",
                        payload.viewId(),
                        payload.dimension().location(),
                        payload.chunkX(),
                        payload.chunkZ(),
                        chunk == null ? 0 : chunk.getBlockEntitiesPos().size()
                );
            }
            logPortalChunkRoute(
                    minecraft,
                    payload,
                    sameAsClientDimension,
                    false,
                    storedInPortalStorage,
                    mainPlayerChunkStillExistsBefore,
                    playerChunkStillExists(minecraft),
                    false
            );
            logVisualWorldRoute(payload, visualWorldRoute);
            return;
        }

        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.get(payload.viewId());

        if (world == null || world.isClosed()) {
            Skyesight.LOGGER.debug(
                    "[Skyesight] Dropped chunk {}, {} for missing/closed view {}",
                    payload.chunkX(),
                    payload.chunkZ(),
                    payload.viewId()
            );
            logPortalChunkRoute(
                    minecraft,
                    payload,
                    sameAsClientDimension,
                    false,
                    storedInPortalStorage,
                    mainPlayerChunkStillExistsBefore,
                    playerChunkStillExists(minecraft),
                    false
            );
            return;
        }

        world.chunkReceiver().setViewCenter(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        world.chunkReceiver().pruneOutside(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        boolean inserted = world.chunkReceiver().receiveChunkWithLight(
                payload.chunkX(),
                payload.chunkZ(),
                payload.chunkData(),
                payload.lightData(),
                world::scheduleTerrainUpdate
        );

        if (inserted) {
            SkyesightClientChunkRequester.markChunkReceived(
                    payload.viewId(),
                    payload.dimension(),
                    payload.chunkX(),
                    payload.chunkZ()
            );
        }

        logPortalChunkRoute(
                minecraft,
                payload,
                sameAsClientDimension,
                inserted,
                storedInPortalStorage,
                mainPlayerChunkStillExistsBefore,
                playerChunkStillExists(minecraft),
                inserted
        );

        if (SkyesightDebugConfig.VERBOSE_RENDER || SkyesightDebugConfig.PACKET_DEBUG) {
            Skyesight.LOGGER.info(
                    "[Skyesight] Skyesight view={} loaded chunks={}",
                    payload.viewId(),
                    world.level().getChunkSource().getLoadedChunksCount()
            );
        }
    }

    private static SkyesightRemoteViewRegistration currentViewForPayload(SkyesightChunkDataPayload payload) {
        SkyesightRemoteViewRegistration current =
                SkyesightRemoteViewRegistry.get(payload.viewId()).orElse(null);
        if (current == null) {
            warnDroppedStalePayload(payload, -1L, null, "missing-current-view");
            SkyesightClientChunkRequester.reset(payload.viewId());
            return null;
        }
        if (current.generation() != payload.viewGeneration()) {
            warnDroppedStalePayload(payload, current.generation(), current.targetDimension(), "packet_generation_mismatch");
            SkyesightClientChunkRequester.reset(payload.viewId());
            return null;
        }
        if (!current.targetDimension().equals(payload.dimension())) {
            warnDroppedStalePayload(payload, current.generation(), current.targetDimension(), "target_dimension_mismatch");
            SkyesightClientChunkRequester.reset(payload.viewId());
            SkyesightVisualWorldManager.close(payload.viewId());
            return null;
        }
        SkyesightVisualWorld world = SkyesightVisualWorldManager.get(payload.viewId());
        if (world != null && !world.isClosed() && !world.dimension().equals(payload.dimension())) {
            warnDroppedStalePayload(payload, current.generation(), current.targetDimension(), "visual_world_dim_mismatch");
            SkyesightVisualWorldManager.close(payload.viewId());
            SkyesightClientChunkRequester.reset(payload.viewId());
            return null;
        }
        return current;
    }

    private static void warnDroppedStalePayload(
            SkyesightChunkDataPayload payload,
            long currentGeneration,
            net.minecraft.resources.ResourceKey<Level> currentTargetDimension,
            String reason
    ) {
        String key = payload.viewId() + "|" + reason + "|" + payload.viewGeneration() + "|" + currentGeneration;
        if (!staleChunkWarnings.add(key)) {
            return;
        }
        Skyesight.LOGGER.warn(
                "[Skyesight] DROPPED_STALE_PORTAL_CHUNK: viewId={} payloadGeneration={} currentGeneration={} payloadDim={} currentTargetDim={} chunk={},{} reason={}",
                payload.viewId(),
                payload.viewGeneration(),
                currentGeneration,
                payload.dimension().location(),
                currentTargetDimension == null ? "-" : currentTargetDimension.location(),
                payload.chunkX(),
                payload.chunkZ(),
                reason
        );
    }

    private static boolean playerChunkStillExists(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }

        ChunkPos playerChunk = minecraft.player.chunkPosition();
        return minecraft.level.getChunkSource().getChunk(playerChunk.x, playerChunk.z, false) != null;
    }

    private static void logPortalChunkRoute(
            Minecraft minecraft,
            SkyesightChunkDataPayload payload,
            boolean sameAsClientDimension,
            boolean appliedToVanillaClientCache,
            boolean storedInPortalStorage,
            boolean mainPlayerChunkStillExistsBefore,
            boolean mainPlayerChunkStillExistsAfter,
            boolean mainClientCacheTouched
    ) {
        if (sameAsClientDimension) {
            return;
        }

        recordChunkRouteSummary(payload, storedInPortalStorage, false, mainClientCacheTouched);
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.PACKET_DEBUG) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] Portal chunk route view={} chunk={},{} clientDim={} targetDim={} sameAsClientDimension={} appliedToVanillaClientCache={} storedInPortalStorage={} mainPlayerChunkStillExists={}->{} mainClientCacheTouched={} targetStorageCount={} collisionSummary={} storage='{}' global='{}'",
                payload.viewId(),
                payload.chunkX(),
                payload.chunkZ(),
                minecraft.level == null ? "n/a" : minecraft.level.dimension().location(),
                payload.dimension().location(),
                sameAsClientDimension,
                appliedToVanillaClientCache,
                storedInPortalStorage,
                mainPlayerChunkStillExistsBefore,
                mainPlayerChunkStillExistsAfter,
                mainClientCacheTouched,
                SkyesightPortalChunkStorage.countChunks(payload.dimension()),
                SkyesightPortalChunkStorage.coordinateCollisionsSummary(5),
                SkyesightPortalChunkStorage.summaryForDimension(payload.dimension()),
                SkyesightPortalChunkStorage.globalSummary()
        );
    }

    private static VisualWorldRouteResult routeVisualWorldPayload(SkyesightChunkDataPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getOrCreate(payload.viewId(), payload.dimension());

        if (world == null || world.isClosed()) {
            return VisualWorldRouteResult.skipped("visual world unavailable");
        }

        world.chunkReceiver().setViewCenter(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        world.chunkReceiver().pruneOutside(
                payload.centerChunkX(),
                payload.centerChunkZ(),
                payload.radius() + 3
        );

        boolean inserted = world.chunkReceiver().receiveChunkWithLight(
                payload.chunkX(),
                payload.chunkZ(),
                payload.chunkData(),
                payload.lightData(),
                world::scheduleTerrainUpdate
        );

        return new VisualWorldRouteResult(
                true,
                inserted,
                inserted,
                world.dimension().location().toString(),
                world.level().getChunkSource().getLoadedChunksCount(),
                world.level().getBlockState(new BlockPos(payload.chunkX() << 4, world.level().getMinBuildHeight(), payload.chunkZ() << 4))
                        .getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .location()
                        .toString(),
                inserted ? "-" : "receiveChunkWithLight returned false"
        );
    }

    private static void logVisualWorldRoute(
            SkyesightChunkDataPayload payload,
            VisualWorldRouteResult route
    ) {
        if (!route.attempted()) {
            return;
        }

        recordChunkRouteSummary(payload, true, route.inserted(), false);
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.PACKET_DEBUG) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] Cross-dim visual world route view={} targetDim={} visualWorldCreated={} visualWorldDimension={} payloadsRoutedToVisualWorld={} visualWorldClientChunksLoaded={} visualWorldLightApplied={} visualWorldChunkSample={} mainClientCacheTouched=false skipped='{}'",
                payload.viewId(),
                payload.dimension().location(),
                route.attempted(),
                route.visualWorldDimension(),
                route.inserted(),
                route.loadedChunks(),
                route.lightApplied(),
                route.chunkSample(),
                route.skippedReason()
        );
    }

    private static void recordChunkRouteSummary(
            SkyesightChunkDataPayload payload,
            boolean stored,
            boolean routed,
            boolean mainClientCacheTouched
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        chunkRouteByView.merge(payload.viewId(), 1, Integer::sum);
        chunkRouteByDimension.merge(payload.dimension().location().toString(), 1, Integer::sum);
        if (stored) {
            chunkRouteStored++;
        }
        if (routed) {
            chunkRouteRouted++;
        }
        if (mainClientCacheTouched) {
            chunkRouteMainClientCacheTouched++;
        }
        if (chunkRouteSample.equals("-")) {
            chunkRouteSample = payload.viewId() + ":" + payload.dimension().location() + ":" + payload.chunkX() + "," + payload.chunkZ();
        }
        String collisions = SkyesightPortalChunkStorage.coordinateCollisionsSummary(5);
        chunkRouteCollisions = collisions == null || collisions.isBlank() || "-".equals(collisions) ? 0 : 1;
        long now = System.currentTimeMillis();
        if (now - lastChunkRouteSummaryMillis < SkyesightDebugConfig.DEBUG_PORTAL_SUMMARY_INTERVAL_TICKS * 50L) {
            return;
        }
        lastChunkRouteSummaryMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_CHUNK_ROUTE_SUMMARY: byView={} byDimension={} stored={} routed={} collisions={} mainClientCacheTouched={} sample={}",
                chunkRouteByView,
                chunkRouteByDimension,
                chunkRouteStored,
                chunkRouteRouted,
                chunkRouteCollisions,
                chunkRouteMainClientCacheTouched,
                chunkRouteSample
        );
        chunkRouteByView.clear();
        chunkRouteByDimension.clear();
        chunkRouteStored = 0;
        chunkRouteRouted = 0;
        chunkRouteCollisions = 0;
        chunkRouteMainClientCacheTouched = 0;
        chunkRouteSample = "-";
    }

    private record VisualWorldRouteResult(
            boolean attempted,
            boolean inserted,
            boolean lightApplied,
            String visualWorldDimension,
            int loadedChunks,
            String chunkSample,
            String skippedReason
    ) {
        private static VisualWorldRouteResult skipped(String reason) {
            return new VisualWorldRouteResult(false, false, false, "n/a", 0, "n/a", reason);
        }
    }
}
