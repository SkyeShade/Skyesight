package com.skyeshade.skyesight.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkyesightVisualParticleWatch {
    private static ResourceLocation viewId;
    private static BlockPos pos;
    private static long lastFullPipelineLogMillis;
    private static long lastForcedTickMillis;
    private static String targetDim = "-";
    private static String levelClass = "-";
    private static boolean chunkLoaded;
    private static String block = "-";
    private static String fluid = "-";
    private static boolean insideRenderedViewRadius;
    private static boolean insideDisplayTickSampleRegion;
    private static boolean displayTickDriverRan;
    private static String displayTickCenter = "-";
    private static double distanceToTickCenter = Double.NaN;
    private static int positionsSampledThisSecond;
    private static boolean watchedBlockSampledThisSecond;
    private static boolean watchedBlockAnimateTickCalled;
    private static boolean watchedFluidAnimateTickCalled;
    private static boolean addParticleCapturedNearWatchedBlock;
    private static final Map<String, Integer> capturedTypesNearWatchedBlock = new LinkedHashMap<>();
    private static int visualParticlesStoredNearWatchedBlock;
    private static int particleInstancesCreatedNearWatchedBlock;
    private static int particleInstancesRenderedNearWatchedBlock;

    private SkyesightVisualParticleWatch() {
    }

    public static String set(ResourceLocation watchedViewId, BlockPos watchedPos) {
        viewId = watchedViewId;
        pos = watchedPos == null ? null : watchedPos.immutable();
        resetSecond();
        lastFullPipelineLogMillis = 0L;
        lastForcedTickMillis = 0L;
        return status();
    }

    public static String clear() {
        viewId = null;
        pos = null;
        resetSecond();
        lastFullPipelineLogMillis = 0L;
        lastForcedTickMillis = 0L;
        return status();
    }

    public static boolean enabled() {
        return viewId != null && pos != null;
    }

    public static boolean matches(ResourceLocation candidateViewId) {
        return enabled() && viewId.equals(candidateViewId);
    }

    public static boolean matches(ResourceLocation candidateViewId, BlockPos candidatePos) {
        return matches(candidateViewId) && pos.equals(candidatePos);
    }

    public static boolean near(ResourceLocation candidateViewId, double x, double y, double z, double radius) {
        if (!matches(candidateViewId)) {
            return false;
        }

        double dx = x - pos.getX();
        double dy = y - pos.getY();
        double dz = z - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static boolean near(ResourceLocation candidateViewId, Vec3 position, double radius) {
        return position != null && near(candidateViewId, position.x(), position.y(), position.z(), radius);
    }

    public static boolean nearAnyView(double x, double y, double z, double radius) {
        if (!enabled()) {
            return false;
        }

        double dx = x - pos.getX();
        double dy = y - pos.getY();
        double dz = z - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static ResourceLocation viewId() {
        return viewId;
    }

    public static BlockPos pos() {
        return pos;
    }

    public static boolean shouldRunForcedTick() {
        if (!enabled()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastForcedTickMillis < 1000L) {
            return false;
        }

        lastForcedTickMillis = now;
        return true;
    }

    public static void recordDisplayTick(
            ResourceLocation candidateViewId,
            ClientLevel level,
            Vec3 center,
            int positionsSampled,
            boolean insideSampleRegion,
            boolean watchedBlockSampled
    ) {
        if (!matches(candidateViewId)) {
            return;
        }

        displayTickDriverRan = true;
        displayTickCenter = center == null ? "-" : formatVec(center);
        distanceToTickCenter = center == null || pos == null
                ? Double.NaN
                : center.distanceTo(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
        if (level != null) {
            targetDim = level.dimension().location().toString();
            levelClass = level.getClass().getName();
        }
        insideDisplayTickSampleRegion |= insideSampleRegion;
        positionsSampledThisSecond += positionsSampled;
        watchedBlockSampledThisSecond |= watchedBlockSampled;
    }

    public static void recordWatchedState(
            ResourceLocation candidateViewId,
            ClientLevel level,
            boolean loaded,
            BlockState state,
            FluidState fluidState,
            boolean insideViewRadius
    ) {
        if (!matches(candidateViewId)) {
            return;
        }

        if (level != null) {
            targetDim = level.dimension().location().toString();
            levelClass = level.getClass().getName();
        }
        chunkLoaded = loaded;
        block = state == null ? "-" : state.getBlock().builtInRegistryHolder().key().location().toString();
        fluid = fluidState == null || fluidState.isEmpty()
                ? "-"
                : fluidState.getType().builtInRegistryHolder().key().location().toString();
        insideRenderedViewRadius = insideViewRadius;
    }

    public static void recordForcedTick(ResourceLocation candidateViewId, boolean blockTickCalled, boolean fluidTickCalled, int capturedDelta, String typesDelta) {
        if (!matches(candidateViewId)) {
            return;
        }

        watchedBlockAnimateTickCalled |= blockTickCalled;
        watchedFluidAnimateTickCalled |= fluidTickCalled;
        if (capturedDelta > 0) {
            addParticleCapturedNearWatchedBlock = true;
            mergeTypes(typesDelta);
        }
    }

    public static void recordAddParticle(ResourceLocation candidateViewId, String type, boolean stored) {
        if (!nearTypeEnabled(candidateViewId)) {
            return;
        }

        addParticleCapturedNearWatchedBlock = true;
        capturedTypesNearWatchedBlock.merge(type == null || type.isBlank() ? "unknown" : type, 1, Integer::sum);
        if (stored) {
            visualParticlesStoredNearWatchedBlock++;
        }
    }

    public static void recordStored(ResourceLocation candidateViewId, String type) {
        if (!nearTypeEnabled(candidateViewId)) {
            return;
        }

        visualParticlesStoredNearWatchedBlock++;
        capturedTypesNearWatchedBlock.merge(type == null || type.isBlank() ? "unknown" : type, 1, Integer::sum);
    }

    public static void recordRender(ResourceLocation candidateViewId, boolean providerCreated, boolean rendered) {
        if (!matches(candidateViewId)) {
            return;
        }

        if (providerCreated) {
            particleInstancesCreatedNearWatchedBlock++;
        }
        if (rendered) {
            particleInstancesRenderedNearWatchedBlock++;
        }
    }

    public static void logFullPipelineIfDue(ResourceLocation candidateViewId) {
        if (!matches(candidateViewId)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFullPipelineLogMillis < 1000L) {
            return;
        }

        lastFullPipelineLogMillis = now;
        com.skyeshade.skyesight.Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_FULL_PIPELINE: viewId={} targetDim={} watchedPos={} visualOrMainLevelClass={} chunkLoaded={} block={} fluid={} insideRenderedViewRadius={} insideDisplayTickSampleRegion={} displayTickDriverRan={} displayTickCenter={} distanceToTickCenter={} positionsSampledThisSecond={} watchedBlockSampledThisSecond={} watchedBlockAnimateTickCalled={} watchedFluidAnimateTickCalled={} addParticleCapturedNearWatchedBlock={} capturedTypesNearWatchedBlock={} visualParticlesStoredNearWatchedBlock={} particleInstancesCreatedNearWatchedBlock={} particleInstancesRenderedNearWatchedBlock={} reasonIfFailed={}",
                viewId,
                targetDim,
                pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                levelClass,
                yesNo(chunkLoaded),
                block,
                fluid,
                yesNo(insideRenderedViewRadius),
                yesNo(insideDisplayTickSampleRegion),
                yesNo(displayTickDriverRan),
                displayTickCenter,
                Double.isNaN(distanceToTickCenter) ? "unknown" : String.format(java.util.Locale.ROOT, "%.2f", distanceToTickCenter),
                positionsSampledThisSecond,
                yesNo(watchedBlockSampledThisSecond),
                yesNo(watchedBlockAnimateTickCalled),
                yesNo(watchedFluidAnimateTickCalled),
                yesNo(addParticleCapturedNearWatchedBlock),
                mapSummary(capturedTypesNearWatchedBlock),
                visualParticlesStoredNearWatchedBlock,
                particleInstancesCreatedNearWatchedBlock,
                particleInstancesRenderedNearWatchedBlock,
                reasonIfFailed()
        );
        resetSecond();
    }

    public static String status() {
        if (!enabled()) {
            return "disabled";
        }

        return "viewId=" + viewId + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean nearTypeEnabled(ResourceLocation candidateViewId) {
        return matches(candidateViewId);
    }

    private static void resetSecond() {
        targetDim = "-";
        levelClass = "-";
        chunkLoaded = false;
        block = "-";
        fluid = "-";
        insideRenderedViewRadius = false;
        insideDisplayTickSampleRegion = false;
        displayTickDriverRan = false;
        displayTickCenter = "-";
        distanceToTickCenter = Double.NaN;
        positionsSampledThisSecond = 0;
        watchedBlockSampledThisSecond = false;
        watchedBlockAnimateTickCalled = false;
        watchedFluidAnimateTickCalled = false;
        addParticleCapturedNearWatchedBlock = false;
        capturedTypesNearWatchedBlock.clear();
        visualParticlesStoredNearWatchedBlock = 0;
        particleInstancesCreatedNearWatchedBlock = 0;
        particleInstancesRenderedNearWatchedBlock = 0;
    }

    private static String reasonIfFailed() {
        if (!chunkLoaded) {
            return "watched block chunk not loaded";
        }
        if (!insideDisplayTickSampleRegion) {
            return "watched block outside portal display tick sample region";
        }
        if (!displayTickDriverRan) {
            return "portal display tick driver did not run";
        }
        if (!watchedBlockAnimateTickCalled && !watchedFluidAnimateTickCalled) {
            return "watched block/fluid animateTick not called";
        }
        if (!addParticleCapturedNearWatchedBlock) {
            return "animateTick produced no captured addParticle near watched block";
        }
        if (!levelClass.contains("SkyesightVisualClientLevel")) {
            return particleInstancesRenderedNearWatchedBlock > 0
                    ? "-"
                    : "main particle captured but not rendered near watched block";
        }
        if (visualParticlesStoredNearWatchedBlock == 0 && !"main-client-particle-engine".equals(mapSummary(capturedTypesNearWatchedBlock))) {
            return "particle captured but no visual particle stored";
        }
        if (visualParticlesStoredNearWatchedBlock > 0 && particleInstancesCreatedNearWatchedBlock == 0) {
            return "visual particle stored but provider did not create particle instance";
        }
        if (particleInstancesCreatedNearWatchedBlock > 0 && particleInstancesRenderedNearWatchedBlock == 0) {
            return "particle instance created but not rendered";
        }
        return "-";
    }

    private static void mergeTypes(String summary) {
        if (summary == null || summary.isBlank() || "-".equals(summary)) {
            return;
        }

        for (String part : summary.split(",")) {
            String[] pieces = part.split("=", 2);
            if (pieces.length != 2) {
                continue;
            }
            try {
                capturedTypesNearWatchedBlock.merge(pieces[0], Integer.parseInt(pieces[1]), Integer::sum);
            } catch (NumberFormatException ignored) {
                capturedTypesNearWatchedBlock.merge(pieces[0], 1, Integer::sum);
            }
        }
    }

    private static String mapSummary(Map<String, Integer> values) {
        if (values.isEmpty()) {
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
}
