package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleManager;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleWatch;
import com.skyeshade.skyesight.client.world.SkyesightVisualClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;

public final class PortalVisualDisplayTickDriver {
    private static final int SAMPLE_COUNT = 1536;
    private static final int HORIZONTAL_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 16;
    private static final double NEAR_MAIN_CAMERA_SKIP_DISTANCE = 32.0D;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<String, Long> LAST_TICK_BY_VIEW = new HashMap<>();
    private static long lastLogMillis;

    private PortalVisualDisplayTickDriver() {
    }

    public static Result tick(
            ResourceLocation viewId,
            String kind,
            ClientLevel targetLevel,
            SkyesightVisualParticleManager visualParticles,
            Vec3 cameraPos
    ) {
        if (targetLevel == null || cameraPos == null) {
            return Result.skipped("missing-target-level-or-camera");
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 realCameraPos = minecraft.gameRenderer == null || minecraft.gameRenderer.getMainCamera() == null
                ? null
                : minecraft.gameRenderer.getMainCamera().getPosition();
        double realCameraDistance = realCameraPos == null ? Double.NaN : realCameraPos.distanceTo(cameraPos);
        boolean sameMainLevel = minecraft.level == targetLevel;
        boolean skippedNearMainCamera = sameMainLevel
                && realCameraPos != null
                && realCameraDistance <= NEAR_MAIN_CAMERA_SKIP_DISTANCE;

        if (skippedNearMainCamera) {
            Result result = new Result(
                    0,
                    0,
                    0,
                    0,
                    "-",
                    "-",
                    realCameraDistance,
                    true,
                    "near main camera; vanilla display tick already covers this area"
            );
            logIfDue(viewId, kind, targetLevel, cameraPos, result);
            return result;
        }

        String tickKey = tickKey(viewId, kind, targetLevel);
        long gameTime = targetLevel.getGameTime();
        Long lastTick = LAST_TICK_BY_VIEW.get(tickKey);
        if (lastTick != null && lastTick == gameTime) {
            Result result = Result.skipped("already display-ticked this portal view this client tick");
            logIfDue(viewId, kind, targetLevel, cameraPos, result);
            return result;
        }
        LAST_TICK_BY_VIEW.put(tickKey, gameTime);

        Map<String, Integer> typesBefore = visualParticles == null ? null : visualParticles.totalCaptureTypesSnapshot();
        int captureBefore = visualParticles == null ? 0 : visualParticles.totalCaptureCount();
        SkyesightVisualClientLevel visualLevel = targetLevel instanceof SkyesightVisualClientLevel skyesightLevel
                ? skyesightLevel
                : null;
        if (visualLevel != null) {
            visualLevel.setSkyesightViewId(viewId);
            visualLevel.setSkyesightParticleCaptureSource("portal-display-tick");
        }
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int centerX = (int) Math.floor(cameraPos.x());
        int centerY = (int) Math.floor(cameraPos.y());
        int centerZ = (int) Math.floor(cameraPos.z());
        int positionsSampled = 0;
        int blockAnimateTickCalls = 0;
        int fluidAnimateTickCalls = 0;
        Map<String, Integer> sampledBlocks = new LinkedHashMap<>();
        Map<String, Integer> sampledFluids = new LinkedHashMap<>();
        boolean watchInsideRegion = watchedBlockInsideRegion(viewId, cameraPos);
        boolean watchAnimateTickCalled = false;

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            int x = centerX + RANDOM.nextInt(HORIZONTAL_RADIUS * 2 + 1) - HORIZONTAL_RADIUS;
            int y = centerY + RANDOM.nextInt(VERTICAL_RADIUS * 2 + 1) - VERTICAL_RADIUS;
            int z = centerZ + RANDOM.nextInt(HORIZONTAL_RADIUS * 2 + 1) - HORIZONTAL_RADIUS;

            if (y < targetLevel.getMinBuildHeight() || y >= targetLevel.getMaxBuildHeight()) {
                continue;
            }

            mutablePos.set(x, y, z);
            ChunkPos chunkPos = new ChunkPos(mutablePos);
            LevelChunk chunk = targetLevel.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
            if (chunk == null) {
                continue;
            }

            positionsSampled++;
            BlockState state = targetLevel.getBlockState(mutablePos);
            FluidState fluidState = targetLevel.getFluidState(mutablePos);
            if (!state.isAir()) {
                sampledBlocks.merge(blockId(state), 1, Integer::sum);
            }
            if (!fluidState.isEmpty()) {
                sampledFluids.merge(fluidId(fluidState), 1, Integer::sum);
            }

            state.getBlock().animateTick(state, targetLevel, mutablePos, RANDOM);
            blockAnimateTickCalls++;
            if (!fluidState.isEmpty()) {
                fluidState.animateTick(targetLevel, mutablePos, RANDOM);
                fluidAnimateTickCalls++;
            }
            if (SkyesightVisualParticleWatch.matches(viewId, mutablePos)) {
                watchAnimateTickCalled = true;
            }
        }

        WatchResult watchResult = tickWatchedBlock(viewId, targetLevel, visualParticles, watchInsideRegion, watchAnimateTickCalled);
        SkyesightVisualParticleWatch.recordDisplayTick(
                viewId,
                targetLevel,
                cameraPos,
                positionsSampled,
                watchInsideRegion,
                watchAnimateTickCalled
        );
        if (visualLevel != null) {
            visualLevel.setSkyesightParticleCaptureSource("visual-addParticle");
        }
        int captured = visualParticles == null ? 0 : Math.max(0, visualParticles.totalCaptureCount() - captureBefore);
        String particleTypes = visualParticles == null ? "main-client-particle-engine" : visualParticles.captureDeltaSummary(typesBefore);
        String reasonIfNoParticles = visualParticles == null
                ? "main client particle engine handles adds; count unknown"
                : captured == 0
                ? "no particle add captured during portal display tick"
                : "-";
        Result result = new Result(
                positionsSampled,
                blockAnimateTickCalls,
                fluidAnimateTickCalls,
                captured,
                particleTypes,
                "blocks=" + mapSummary(sampledBlocks) + " fluids=" + mapSummary(sampledFluids) + " watch=" + watchResult.summary(),
                realCameraDistance,
                false,
                reasonIfNoParticles
        );
        logIfDue(viewId, kind, targetLevel, cameraPos, result);
        SkyesightVisualParticleWatch.logFullPipelineIfDue(viewId);
        return result;
    }

    private static WatchResult tickWatchedBlock(
            ResourceLocation viewId,
            ClientLevel targetLevel,
            SkyesightVisualParticleManager visualParticles,
            boolean insideRegion,
            boolean sampledByRandomDriver
    ) {
        if (!SkyesightVisualParticleWatch.matches(viewId)) {
            return WatchResult.disabled();
        }

        BlockPos pos = SkyesightVisualParticleWatch.pos();
        if (pos == null) {
            return WatchResult.disabled();
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunk = targetLevel.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
        boolean chunkLoaded = chunk != null;
        BlockState state = targetLevel.getBlockState(pos);
        FluidState fluidState = targetLevel.getFluidState(pos);
        BlockEntity blockEntity = targetLevel.getBlockEntity(pos);
        SkyesightVisualParticleWatch.recordWatchedState(viewId, targetLevel, chunkLoaded, state, fluidState, insideRegion);
        int capturedBefore = visualParticles == null ? 0 : visualParticles.totalCaptureCount();
        Map<String, Integer> typesBefore = visualParticles == null ? null : visualParticles.totalCaptureTypesSnapshot();
        boolean calledBlockAnimateTick = false;
        boolean calledFluidAnimateTick = false;
        boolean ranForcedTick = SkyesightVisualParticleWatch.shouldRunForcedTick();

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_BLOCK_STATE: viewId={} targetDim={} pos={},{},{} visualLevel={} chunkLoaded={} block={} fluid={} blockEntity={} isInWatchedRegion={}",
                viewId,
                targetLevel.dimension().location(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                targetLevel.getClass().getName(),
                yesNo(chunkLoaded),
                blockId(state),
                fluidState.isEmpty() ? "-" : fluidId(fluidState),
                blockEntity == null ? "-" : blockEntity.getType().toString(),
                yesNo(chunkLoaded)
        );

        if (ranForcedTick) {
            if (!state.isAir()) {
                state.getBlock().animateTick(state, targetLevel, pos, RANDOM);
                calledBlockAnimateTick = true;
            }
            if (!fluidState.isEmpty()) {
                fluidState.animateTick(targetLevel, pos, RANDOM);
                calledFluidAnimateTick = true;
            }
        }

        int capturedDelta = visualParticles == null ? 0 : Math.max(0, visualParticles.totalCaptureCount() - capturedBefore);
        String capturedTypes = visualParticles == null ? "main-client-particle-engine" : visualParticles.captureDeltaSummary(typesBefore);
        SkyesightVisualParticleWatch.recordForcedTick(viewId, calledBlockAnimateTick, calledFluidAnimateTick, capturedDelta, capturedTypes);
        if (ranForcedTick) {
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_PARTICLE_WATCH_FORCED_TICK: viewId={} pos={},{},{} block={} beforeCaptured={} afterCaptured={} delta={} typesDelta={} result={}",
                    viewId,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    blockId(state),
                    capturedBefore,
                    visualParticles == null ? capturedBefore : visualParticles.totalCaptureCount(),
                    capturedDelta,
                    capturedTypes,
                    capturedDelta > 0 ? "emitted" : calledBlockAnimateTick || calledFluidAnimateTick ? "no-particle-from-block" : "not-run-this-second"
            );
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_PARTICLE_WATCH_ANIMATE_TICK: viewId={} pos={},{},{} block={} calledBlockAnimateTick={} calledFluidAnimateTick={} addParticleCapturedDelta={} typesCapturedDelta={} insidePortalDisplayTickRegion={} sampledByPortalDisplayTickDriver={}",
                    viewId,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    blockId(state),
                    yesNo(calledBlockAnimateTick),
                    yesNo(calledFluidAnimateTick),
                    capturedDelta,
                    capturedTypes,
                    yesNo(insideRegion),
                    yesNo(sampledByRandomDriver)
            );
        }
        return new WatchResult(true, insideRegion, sampledByRandomDriver, capturedDelta, capturedTypes);
    }

    private static boolean watchedBlockInsideRegion(ResourceLocation viewId, Vec3 cameraPos) {
        if (!SkyesightVisualParticleWatch.matches(viewId) || SkyesightVisualParticleWatch.pos() == null) {
            return false;
        }

        BlockPos watched = SkyesightVisualParticleWatch.pos();
        return Math.abs(watched.getX() - cameraPos.x()) <= HORIZONTAL_RADIUS
                && Math.abs(watched.getY() - cameraPos.y()) <= VERTICAL_RADIUS
                && Math.abs(watched.getZ() - cameraPos.z()) <= HORIZONTAL_RADIUS;
    }

    private static void logIfDue(ResourceLocation viewId, String kind, ClientLevel targetLevel, Vec3 cameraPos, Result result) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLogMillis < 3000L) {
            return;
        }

        lastLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_DISPLAY_TICK_DRIVER: viewId={} kind={} targetDim={} targetLevelClass={} cameraPos={} realCameraDistance={} skippedNearMainCamera={} positionsSampled={} blockAnimateTickCalls={} fluidAnimateTickCalls={} particleAddsCaptured={} particleTypes={} reasonIfNoParticles={}",
                viewId == null ? "-" : viewId,
                kind == null || kind.isBlank() ? "unknown" : kind,
                targetLevel.dimension().location(),
                targetLevel.getClass().getName(),
                formatVec(cameraPos),
                Double.isNaN(result.realCameraDistance()) ? "unknown" : String.format(java.util.Locale.ROOT, "%.2f", result.realCameraDistance()),
                yesNo(result.skippedNearMainCamera()),
                result.positionsSampled(),
                result.blockAnimateTickCalls(),
                result.fluidAnimateTickCalls(),
                result.particleAddsCaptured(),
                result.particleTypes(),
                result.reasonIfNoParticles()
        );
    }

    private static String blockId(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockId == null ? "unknown" : blockId.toString();
    }

    private static String tickKey(ResourceLocation viewId, String kind, ClientLevel targetLevel) {
        return (viewId == null ? "-" : viewId.toString())
                + "|"
                + (kind == null ? "unknown" : kind)
                + "|"
                + targetLevel.dimension().location();
    }

    private static String fluidId(FluidState state) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(state.getType());
        return fluidId == null ? "unknown" : fluidId.toString();
    }

    private static String mapSummary(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        int emitted = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (emitted++ >= 12) {
                builder.append(",...");
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", vec.x(), vec.y(), vec.z());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    public record Result(
            int positionsSampled,
            int blockAnimateTickCalls,
            int fluidAnimateTickCalls,
            int particleAddsCaptured,
            String particleTypes,
            String detail,
            double realCameraDistance,
            boolean skippedNearMainCamera,
            String reasonIfNoParticles
    ) {
        private static Result skipped(String reason) {
            return new Result(0, 0, 0, 0, "-", "-", Double.NaN, false, reason);
        }
    }

    private record WatchResult(
            boolean enabled,
            boolean insideRegion,
            boolean sampledByRandomDriver,
            int capturedDelta,
            String capturedTypes
    ) {
        private static WatchResult disabled() {
            return new WatchResult(false, false, false, 0, "-");
        }

        private String summary() {
            if (!this.enabled) {
                return "disabled";
            }
            return "insideRegion=" + yesNo(this.insideRegion)
                    + " sampledByDriver=" + yesNo(this.sampledByRandomDriver)
                    + " capturedDelta=" + this.capturedDelta
                    + " capturedTypes=" + this.capturedTypes;
        }
    }
}
