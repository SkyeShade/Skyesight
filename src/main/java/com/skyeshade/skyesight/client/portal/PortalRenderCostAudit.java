package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalRenderCostAudit {
    private static final Map<ResourceLocation, Stats> STATS_BY_VIEW = new ConcurrentHashMap<>();

    private PortalRenderCostAudit() {}

    public static boolean enabled() {
        return SkyesightDebugConfig.RENDER_PERF_AUDIT;
    }

    public static long start() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static void record(ResourceLocation viewId, String bucket, long startNanos) {
        if (!enabled() || viewId == null || bucket == null || startNanos <= 0L) {
            return;
        }
        recordNanos(viewId, bucket, System.nanoTime() - startNanos);
    }

    public static void recordNanos(ResourceLocation viewId, String bucket, long nanos) {
        if (!enabled() || viewId == null || bucket == null || nanos <= 0L) {
            return;
        }
        STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats()).add(bucket, nanos);
    }

    public static void recordFrame(ResourceLocation viewId, boolean rendered, boolean frustumCulled) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        if (rendered) {
            stats.renderedFrames++;
        }
        if (frustumCulled) {
            stats.frustumCulledFrames++;
        }
    }

    public static void recordTarget(ResourceLocation viewId, int width, int height, boolean boundsScaled) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        stats.target = width + "x" + height;
        stats.boundsScaled = boundsScaled;
    }

    public static void recordTargetLifecycle(
            ResourceLocation viewId,
            int previousWidth,
            int previousHeight,
            int requestedWidth,
            int requestedHeight,
            int currentWidth,
            int currentHeight,
            boolean created,
            boolean resized,
            boolean reused,
            boolean requestedSizeChanged,
            String reason
    ) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        stats.targetPreviousSize = previousWidth + "x" + previousHeight;
        stats.targetRequestedSize = requestedWidth + "x" + requestedHeight;
        stats.targetCurrentSize = currentWidth + "x" + currentHeight;
        if (created) {
            stats.targetCreatedTotal++;
        }
        if (resized) {
            stats.targetResizedTotal++;
        }
        if (reused) {
            stats.targetReusedTotal++;
        }
        if (requestedSizeChanged) {
            stats.targetRequestedSizeChangedTotal++;
        }
        stats.targetAcquireReason = blankToDash(reason);
    }

    public static void recordEntityCounts(ResourceLocation viewId, int considered, int rendered, int skipped) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        stats.entityConsidered += Math.max(0, considered);
        stats.entityRendered += Math.max(0, rendered);
        stats.entitySkipped += Math.max(0, skipped);
        stats.entityConsideredMax = Math.max(stats.entityConsideredMax, Math.max(0, considered));
        stats.entityRenderedMax = Math.max(stats.entityRenderedMax, Math.max(0, rendered));
    }

    public static void recordEntityDetails(
            ResourceLocation viewId,
            String source,
            String queryAabb,
            int skippedOutOfRadius,
            int skippedFrustum,
            int skippedDimension,
            int uniqueConsidered,
            int uniqueRendered,
            int duplicateRenderAttempts,
            String duplicateEntityIdsSample,
            String entityTypeHistogram,
            String levelClass,
            String levelDimension,
            boolean sameAsMinecraftLevel,
            boolean visualLevel,
            String collectionClass,
            double queryAabbVolume,
            double queryRadiusBlocks,
            int queryRadiusChunks,
            int queryResultSize,
            int queryUniqueCount,
            int entitiesForRenderingSize,
            int entitiesForRenderingUniqueCount,
            boolean entityFrustumAvailable,
            String entityFrustumSource,
            boolean entityFrustumCullingEnabled,
            String entityFrustumPadding,
            boolean renderBackface,
            String viewPhysicalSide
    ) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        stats.entitySource = source == null || source.isBlank() ? "-" : source;
        stats.entityQueryAabb = queryAabb == null || queryAabb.isBlank() ? "-" : queryAabb;
        stats.entitySkippedOutOfRadius += Math.max(0, skippedOutOfRadius);
        stats.entitySkippedFrustum += Math.max(0, skippedFrustum);
        stats.entitySkippedDimension += Math.max(0, skippedDimension);
        stats.uniqueEntityConsideredTotal += Math.max(0, uniqueConsidered);
        stats.uniqueEntityRenderedTotal += Math.max(0, uniqueRendered);
        stats.uniqueEntityConsideredMax = Math.max(stats.uniqueEntityConsideredMax, Math.max(0, uniqueConsidered));
        stats.uniqueEntityRenderedMax = Math.max(stats.uniqueEntityRenderedMax, Math.max(0, uniqueRendered));
        stats.duplicateRenderAttempts += Math.max(0, duplicateRenderAttempts);
        stats.duplicateEntityIdsSample = blankToDash(duplicateEntityIdsSample);
        stats.entityTypeHistogram = blankToDash(entityTypeHistogram);
        stats.levelClass = blankToDash(levelClass);
        stats.levelDimension = blankToDash(levelDimension);
        stats.sameAsMinecraftLevel = sameAsMinecraftLevel;
        stats.visualLevel = visualLevel;
        stats.entityCollectionClass = blankToDash(collectionClass);
        stats.queryAabbVolume = queryAabbVolume;
        stats.queryRadiusBlocks = queryRadiusBlocks;
        stats.queryRadiusChunks = queryRadiusChunks;
        stats.queryResultSizeTotal += Math.max(0, queryResultSize);
        stats.queryResultSizeMax = Math.max(stats.queryResultSizeMax, Math.max(0, queryResultSize));
        stats.queryUniqueCountTotal += Math.max(0, queryUniqueCount);
        stats.queryUniqueCountMax = Math.max(stats.queryUniqueCountMax, Math.max(0, queryUniqueCount));
        stats.entitiesForRenderingSize = entitiesForRenderingSize;
        stats.entitiesForRenderingUniqueCount = entitiesForRenderingUniqueCount;
        stats.entityFrustumAvailable = entityFrustumAvailable;
        stats.entityFrustumSource = blankToDash(entityFrustumSource);
        stats.entityFrustumCullingEnabled = entityFrustumCullingEnabled;
        stats.entityFrustumPadding = blankToDash(entityFrustumPadding);
        stats.renderBackface = renderBackface;
        stats.viewPhysicalSide = blankToDash(viewPhysicalSide);
        stats.entityPassFrames++;
    }

    public static void recordTerrainSetupDetails(
            ResourceLocation viewId,
            boolean setupCalled,
            boolean setupReused,
            double positionDeltaBlocks,
            double rotationDeltaDegrees,
            int pendingRebuildChunkCountGlobal,
            int pendingRebuildChunkCountForView,
            int pendingBlockUpdateChunkCount,
            int pendingBlockUpdateChunkCountForView,
            boolean reuseBlockedByPendingViewChunks,
            boolean pendingRebuildChanged,
            int pendingRebuildOldCount,
            int pendingRebuildNewCount,
            int pendingRebuildAdded,
            int pendingRebuildRemoved,
            int pendingRebuildStableFrames,
            boolean reuseBlockedByNewPendingChunks,
            boolean readyChunksActualChanged,
            int readyChunksOldCount,
            int readyChunksNewCount,
            int readyChunksAddedThisFrame,
            int readyChunksRemovedThisFrame,
            String reason
    ) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        if (setupCalled) {
            stats.terrainSetupCalls++;
        }
        if (setupReused) {
            stats.terrainSetupSkippedReuse++;
        }
        stats.terrainSetupReuseAvailable = setupReused;
        stats.portalCameraMovedBlocksSinceLastSetup = positionDeltaBlocks;
        stats.portalCameraRotatedDegreesSinceLastSetup = rotationDeltaDegrees;
        stats.pendingRebuildChunkCount = pendingRebuildChunkCountGlobal;
        stats.pendingRebuildChunkCountForView = pendingRebuildChunkCountForView;
        stats.pendingBlockUpdateChunkCount = pendingBlockUpdateChunkCount;
        stats.pendingBlockUpdateChunkCountForView = pendingBlockUpdateChunkCountForView;
        stats.reuseBlockedByPendingViewChunks = reuseBlockedByPendingViewChunks;
        stats.pendingRebuildChanged = pendingRebuildChanged;
        stats.pendingRebuildOldCount = pendingRebuildOldCount;
        stats.pendingRebuildNewCount = pendingRebuildNewCount;
        stats.pendingRebuildAdded = pendingRebuildAdded;
        stats.pendingRebuildRemoved = pendingRebuildRemoved;
        stats.pendingRebuildStableFrames = pendingRebuildStableFrames;
        stats.reuseBlockedByNewPendingChunks = reuseBlockedByNewPendingChunks;
        stats.readyChunksActualChanged = readyChunksActualChanged;
        stats.readyChunksOldCount = readyChunksOldCount;
        stats.readyChunksNewCount = readyChunksNewCount;
        stats.readyChunksAddedThisFrame = readyChunksAddedThisFrame;
        stats.readyChunksRemovedThisFrame = readyChunksRemovedThisFrame;
        stats.terrainSetupReuseReason = blankToDash(reason);
    }

    public static void recordFlags(ResourceLocation viewId, DebugPortalRenderConfig config) {
        if (!enabled() || viewId == null || config == null) {
            return;
        }
        STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats()).flags = flags(config);
    }

    public static void logIfDue(ResourceLocation viewId) {
        if (!enabled() || viewId == null) {
            return;
        }
        Stats stats = STATS_BY_VIEW.computeIfAbsent(viewId, ignored -> new Stats());
        long now = System.currentTimeMillis();
        if (now - stats.lastLogMillis < 1_000L) {
            return;
        }
        stats.lastLogMillis = now;
        int frames = Math.max(1, stats.renderedFrames);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_RENDER_COST: view={} frames={} totalMs={} maskMs={} targetMs={} targetResolveSizeMs={} targetAcquireOrResizeMs={} targetAcquireMs={} targetResizeMs={} targetBindMs={} targetClearMs={} targetViewportMs={} targetRestoreMs={} targetCreatedTotal={} targetResizedTotal={} targetReusedTotal={} targetRequestedSizeChangedTotal={} targetPreviousSize={} targetCurrentSize={} targetRequestedSize={} targetAcquireReason={} skyMs={} terrainSetupMs={} terrainVisibilityCheckMs={} terrainCameraUpdateMs={} terrainChunkReadyUpdateMs={} terrainSectionCollectMs={} terrainSetupTerrainCallMs={} sodiumSetupTerrainMs={} terrainStateRestoreMs={} terrainSetupCallsTotal={} terrainSetupCallsAvgPerPortalFrame={} terrainSetupSkippedReuseTotal={} terrainSetupReuseHitRate={} terrainSetupReuseAvailable={} portalCameraMovedBlocksSinceLastSetup={} portalCameraRotatedDegreesSinceLastSetup={} pendingRebuildChunkCountGlobal={} pendingRebuildChunkCountForView={} pendingBlockUpdateChunkCount={} pendingBlockUpdateChunkCountForView={} reuseBlockedByPendingViewChunks={} pendingRebuildChanged={} pendingRebuildOldCount={} pendingRebuildNewCount={} pendingRebuildAdded={} pendingRebuildRemoved={} pendingRebuildStableFrames={} reuseBlockedByNewPendingChunks={} readyChunksActualChanged={} readyChunksOldCount={} readyChunksNewCount={} readyChunksAddedThisFrame={} readyChunksRemovedThisFrame={} sodiumNeedsUpdate={} mainRendererNeedsUpdate={} sourcePortalMoved={} targetPortalMoved={} terrainSetupReuseReason={} terrainSolidMs={} terrainCutoutMs={} terrainTranslucentMs={} entitiesMs={} entityCollectMs={} entityCullFilterMs={} entityRenderMs={} entityRenderMsAvgPerEntity={} entityPassFrames={} entityRenderCallsTotal={} entityRenderCallsAvgPerPortalFrame={} entityRenderedUniqueAvgPerPortalFrame={} entityRendererTypesTop10={} entitySource={} entityQueryAabb={} entityLevel={} levelDim={} visualLevel={} sameAsMinecraftLevel={} entityCollectionClass={} entityFrustumCullingEnabled={} entityFrustumPadding={} entityFrustumAvailable={} entityFrustumSource={} entityRenderDistanceRadiusUsed={} entityRadiusSetting={} renderBackface={} viewPhysicalSide={} queryRadiusBlocks={} queryRadiusChunks={} queryAabbVolume={} queryResultTotal={} queryResultAvgPerFrame={} queryResultMaxPerFrame={} queryUniqueAvgPerFrame={} queryUniqueMaxPerFrame={} entitiesForRenderingSize={} entitiesForRenderingUnique={} entityConsideredTotal={} entityConsideredAvgPerFrame={} entityConsideredMaxPerFrame={} entityRenderedTotal={} entityRenderedAvgPerFrame={} entityRenderedMaxPerFrame={} uniqueEntityConsideredAvgPerFrame={} uniqueEntityConsideredMaxPerFrame={} uniqueEntityRenderedAvgPerFrame={} uniqueEntityRenderedMaxPerFrame={} duplicateRenderAttempts={} duplicateEntityIdsSample={} entityTypeHistogram={} entityCountSkipped={} entitySkippedOutOfRadius={} entitySkippedFrustum={} entitySkippedDimension={} blockEntitiesMs={} particlesMs={} compositeMs={} sodiumAcquireMs={} readyChunksMs={} sectionPrimerMs={} renderTarget={} boundsScaled={} frustumCulledFrames={} renderedFrames={} flags={}",
                shortViewId(viewId),
                frames,
                ms(stats.totalNanos, frames),
                ms(stats.maskNanos, frames),
                ms(stats.targetNanos, frames),
                ms(stats.targetResolveSizeNanos, frames),
                ms(stats.targetAcquireOrResizeNanos, frames),
                ms(stats.targetAcquireNanos, frames),
                ms(stats.targetResizeNanos, frames),
                ms(stats.targetBindNanos, frames),
                ms(stats.targetClearNanos, frames),
                ms(stats.targetViewportNanos, frames),
                ms(stats.targetRestoreNanos, frames),
                stats.targetCreatedTotal,
                stats.targetResizedTotal,
                stats.targetReusedTotal,
                stats.targetRequestedSizeChangedTotal,
                stats.targetPreviousSize,
                stats.targetCurrentSize,
                stats.targetRequestedSize,
                stats.targetAcquireReason,
                ms(stats.skyNanos, frames),
                ms(stats.terrainSetupNanos, frames),
                ms(stats.terrainVisibilityCheckNanos, frames),
                ms(stats.terrainCameraUpdateNanos, frames),
                ms(stats.terrainChunkReadyUpdateNanos, frames),
                ms(stats.terrainSectionCollectNanos, frames),
                ms(stats.terrainSetupTerrainCallNanos, frames),
                ms(stats.sodiumSetupTerrainNanos, frames),
                ms(stats.terrainStateRestoreNanos, frames),
                stats.terrainSetupCalls,
                oneDecimal(stats.terrainSetupCalls / (double) frames),
                stats.terrainSetupSkippedReuse,
                oneDecimal(stats.terrainSetupSkippedReuse / (double) Math.max(1, stats.terrainSetupCalls + stats.terrainSetupSkippedReuse)),
                stats.terrainSetupReuseAvailable ? "yes" : "no",
                oneDecimal(stats.portalCameraMovedBlocksSinceLastSetup),
                oneDecimal(stats.portalCameraRotatedDegreesSinceLastSetup),
                stats.pendingRebuildChunkCount,
                stats.pendingRebuildChunkCountForView,
                stats.pendingBlockUpdateChunkCount,
                stats.pendingBlockUpdateChunkCountForView,
                stats.reuseBlockedByPendingViewChunks ? "yes" : "no",
                stats.pendingRebuildChanged ? "yes" : "no",
                stats.pendingRebuildOldCount,
                stats.pendingRebuildNewCount,
                stats.pendingRebuildAdded,
                stats.pendingRebuildRemoved,
                stats.pendingRebuildStableFrames,
                stats.reuseBlockedByNewPendingChunks ? "yes" : "no",
                stats.readyChunksActualChanged ? "yes" : "no",
                stats.readyChunksOldCount,
                stats.readyChunksNewCount,
                stats.readyChunksAddedThisFrame,
                stats.readyChunksRemovedThisFrame,
                "unknown",
                "unknown",
                "no",
                "no",
                stats.terrainSetupReuseReason,
                ms(stats.terrainSolidNanos, frames),
                ms(stats.terrainCutoutNanos, frames),
                ms(stats.terrainTranslucentNanos, frames),
                ms(stats.entitiesNanos, frames),
                ms(stats.entityCollectNanos, frames),
                ms(stats.entityCullFilterNanos, frames),
                ms(stats.entityRenderNanos, frames),
                msPerCount(stats.entityRenderNanos, stats.entityRendered),
                stats.entityPassFrames,
                stats.entityRendered,
                oneDecimal(stats.entityRendered / (double) Math.max(1, stats.entityPassFrames)),
                oneDecimal(stats.uniqueEntityRenderedTotal / (double) Math.max(1, stats.entityPassFrames)),
                stats.entityTypeHistogram,
                stats.entitySource,
                stats.entityQueryAabb,
                stats.levelClass,
                stats.levelDimension,
                stats.visualLevel ? "yes" : "no",
                stats.sameAsMinecraftLevel ? "yes" : "no",
                stats.entityCollectionClass,
                stats.entityFrustumCullingEnabled ? "yes" : "no",
                stats.entityFrustumPadding,
                stats.entityFrustumAvailable ? "yes" : "no",
                stats.entityFrustumSource,
                oneDecimal(stats.queryRadiusBlocks),
                stats.queryRadiusChunks,
                stats.renderBackface ? "yes" : "no",
                stats.viewPhysicalSide,
                oneDecimal(stats.queryRadiusBlocks),
                stats.queryRadiusChunks,
                oneDecimal(stats.queryAabbVolume),
                stats.queryResultSizeTotal,
                oneDecimal(stats.queryResultSizeTotal / (double) frames),
                stats.queryResultSizeMax,
                oneDecimal(stats.queryUniqueCountTotal / (double) frames),
                stats.queryUniqueCountMax,
                stats.entitiesForRenderingSize,
                stats.entitiesForRenderingUniqueCount,
                stats.entityConsidered,
                oneDecimal(stats.entityConsidered / (double) frames),
                stats.entityConsideredMax,
                stats.entityRendered,
                oneDecimal(stats.entityRendered / (double) frames),
                stats.entityRenderedMax,
                oneDecimal(stats.uniqueEntityConsideredTotal / (double) frames),
                stats.uniqueEntityConsideredMax,
                oneDecimal(stats.uniqueEntityRenderedTotal / (double) frames),
                stats.uniqueEntityRenderedMax,
                stats.duplicateRenderAttempts,
                stats.duplicateEntityIdsSample,
                stats.entityTypeHistogram,
                stats.entitySkipped,
                stats.entitySkippedOutOfRadius,
                stats.entitySkippedFrustum,
                stats.entitySkippedDimension,
                ms(stats.blockEntitiesNanos, frames),
                ms(stats.particlesNanos, frames),
                ms(stats.compositeNanos, frames),
                ms(stats.sodiumAcquireNanos, frames),
                ms(stats.readyChunksNanos, frames),
                ms(stats.sectionPrimerNanos, frames),
                stats.target,
                stats.boundsScaled ? "yes" : "no",
                stats.frustumCulledFrames,
                stats.renderedFrames,
                stats.flags
        );
        stats.resetSecond();
    }

    private static String flags(DebugPortalRenderConfig config) {
        StringBuilder builder = new StringBuilder();
        appendFlag(builder, "sky", config.renderSky());
        appendFlag(builder, "terrain", config.renderTerrain());
        appendFlag(builder, "translucent", config.renderTranslucent());
        appendFlag(builder, "entities", config.renderEntities());
        appendFlag(builder, "blockEntities", config.renderBlockEntities());
        appendFlag(builder, "particles", config.renderParticles());
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static void appendFlag(StringBuilder builder, String name, boolean enabled) {
        if (!enabled) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(',');
        }
        builder.append(name);
    }

    private static String ms(long nanos, int frames) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D / Math.max(1, frames));
    }

    private static String msPerCount(long nanos, int count) {
        if (count <= 0) {
            return "0.000";
        }
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D / count);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String shortViewId(ResourceLocation viewId) {
        return viewId == null ? "-" : viewId.getPath();
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final class Stats {
        private long lastLogMillis;
        private int renderedFrames;
        private int frustumCulledFrames;
        private long totalNanos;
        private long maskNanos;
        private long targetNanos;
        private long targetResolveSizeNanos;
        private long targetAcquireOrResizeNanos;
        private long targetAcquireNanos;
        private long targetResizeNanos;
        private long targetBindNanos;
        private long targetClearNanos;
        private long targetViewportNanos;
        private long targetRestoreNanos;
        private int targetCreatedTotal;
        private int targetResizedTotal;
        private int targetReusedTotal;
        private int targetRequestedSizeChangedTotal;
        private String targetPreviousSize = "-";
        private String targetCurrentSize = "-";
        private String targetRequestedSize = "-";
        private String targetAcquireReason = "-";
        private long skyNanos;
        private long terrainSetupNanos;
        private long terrainVisibilityCheckNanos;
        private long terrainCameraUpdateNanos;
        private long terrainChunkReadyUpdateNanos;
        private long terrainSectionCollectNanos;
        private long terrainSetupTerrainCallNanos;
        private long sodiumSetupTerrainNanos;
        private long terrainStateRestoreNanos;
        private int terrainSetupCalls;
        private int terrainSetupSkippedReuse;
        private boolean terrainSetupReuseAvailable;
        private double portalCameraMovedBlocksSinceLastSetup;
        private double portalCameraRotatedDegreesSinceLastSetup;
        private int pendingRebuildChunkCount;
        private int pendingRebuildChunkCountForView;
        private int pendingBlockUpdateChunkCount;
        private int pendingBlockUpdateChunkCountForView;
        private boolean reuseBlockedByPendingViewChunks;
        private boolean pendingRebuildChanged;
        private int pendingRebuildOldCount;
        private int pendingRebuildNewCount;
        private int pendingRebuildAdded;
        private int pendingRebuildRemoved;
        private int pendingRebuildStableFrames;
        private boolean reuseBlockedByNewPendingChunks;
        private boolean readyChunksActualChanged;
        private int readyChunksOldCount;
        private int readyChunksNewCount;
        private int readyChunksAddedThisFrame;
        private int readyChunksRemovedThisFrame;
        private String terrainSetupReuseReason = "-";
        private long terrainSolidNanos;
        private long terrainCutoutNanos;
        private long terrainTranslucentNanos;
        private long entitiesNanos;
        private long entityCollectNanos;
        private long entityCullFilterNanos;
        private long entityRenderNanos;
        private String entitySource = "-";
        private String entityQueryAabb = "-";
        private int entityConsidered;
        private int entityConsideredMax;
        private int entityRendered;
        private int entityRenderedMax;
        private int entitySkipped;
        private int entitySkippedOutOfRadius;
        private int entitySkippedFrustum;
        private int entitySkippedDimension;
        private int uniqueEntityConsideredTotal;
        private int uniqueEntityRenderedTotal;
        private int uniqueEntityConsideredMax;
        private int uniqueEntityRenderedMax;
        private int duplicateRenderAttempts;
        private String duplicateEntityIdsSample = "-";
        private String entityTypeHistogram = "-";
        private String levelClass = "-";
        private String levelDimension = "-";
        private boolean sameAsMinecraftLevel;
        private boolean visualLevel;
        private String entityCollectionClass = "-";
        private double queryAabbVolume;
        private double queryRadiusBlocks;
        private int queryRadiusChunks;
        private int queryResultSizeTotal;
        private int queryResultSizeMax;
        private int queryUniqueCountTotal;
        private int queryUniqueCountMax;
        private int entitiesForRenderingSize = -1;
        private int entitiesForRenderingUniqueCount = -1;
        private boolean entityFrustumCullingEnabled;
        private String entityFrustumPadding = "-";
        private boolean entityFrustumAvailable;
        private String entityFrustumSource = "-";
        private boolean renderBackface;
        private String viewPhysicalSide = "unknown";
        private int entityPassFrames;
        private long blockEntitiesNanos;
        private long particlesNanos;
        private long compositeNanos;
        private long sodiumAcquireNanos;
        private long readyChunksNanos;
        private long sectionPrimerNanos;
        private String target = "-";
        private boolean boundsScaled;
        private String flags = "-";

        private void add(String bucket, long nanos) {
            switch (bucket) {
                case "total" -> this.totalNanos += nanos;
                case "mask" -> this.maskNanos += nanos;
                case "target" -> this.targetNanos += nanos;
                case "targetResolveSize" -> this.targetResolveSizeNanos += nanos;
                case "targetAcquireOrResize" -> this.targetAcquireOrResizeNanos += nanos;
                case "targetAcquire" -> this.targetAcquireNanos += nanos;
                case "targetResize" -> this.targetResizeNanos += nanos;
                case "targetBind" -> this.targetBindNanos += nanos;
                case "targetClear" -> this.targetClearNanos += nanos;
                case "targetViewport" -> this.targetViewportNanos += nanos;
                case "targetRestore" -> this.targetRestoreNanos += nanos;
                case "sky" -> this.skyNanos += nanos;
                case "terrainSetup" -> this.terrainSetupNanos += nanos;
                case "terrainVisibilityCheck" -> this.terrainVisibilityCheckNanos += nanos;
                case "terrainCameraUpdate" -> this.terrainCameraUpdateNanos += nanos;
                case "terrainChunkReadyUpdate" -> this.terrainChunkReadyUpdateNanos += nanos;
                case "terrainSectionCollect" -> this.terrainSectionCollectNanos += nanos;
                case "terrainSetupTerrainCall" -> this.terrainSetupTerrainCallNanos += nanos;
                case "sodiumSetupTerrain" -> this.sodiumSetupTerrainNanos += nanos;
                case "terrainStateRestore" -> this.terrainStateRestoreNanos += nanos;
                case "terrainSolid" -> this.terrainSolidNanos += nanos;
                case "terrainCutout" -> this.terrainCutoutNanos += nanos;
                case "terrainTranslucent" -> this.terrainTranslucentNanos += nanos;
                case "entities" -> this.entitiesNanos += nanos;
                case "entityCollect" -> this.entityCollectNanos += nanos;
                case "entityCullFilter" -> this.entityCullFilterNanos += nanos;
                case "entityRender" -> this.entityRenderNanos += nanos;
                case "blockEntities" -> this.blockEntitiesNanos += nanos;
                case "particles" -> this.particlesNanos += nanos;
                case "composite" -> this.compositeNanos += nanos;
                case "sodiumAcquire" -> this.sodiumAcquireNanos += nanos;
                case "readyChunks" -> this.readyChunksNanos += nanos;
                case "sectionPrimer" -> this.sectionPrimerNanos += nanos;
                default -> {
                }
            }
        }

        private void resetSecond() {
            this.renderedFrames = 0;
            this.frustumCulledFrames = 0;
            this.totalNanos = 0L;
            this.maskNanos = 0L;
            this.targetNanos = 0L;
            this.targetResolveSizeNanos = 0L;
            this.targetAcquireOrResizeNanos = 0L;
            this.targetAcquireNanos = 0L;
            this.targetResizeNanos = 0L;
            this.targetBindNanos = 0L;
            this.targetClearNanos = 0L;
            this.targetViewportNanos = 0L;
            this.targetRestoreNanos = 0L;
            this.targetCreatedTotal = 0;
            this.targetResizedTotal = 0;
            this.targetReusedTotal = 0;
            this.targetRequestedSizeChangedTotal = 0;
            this.targetAcquireReason = "-";
            this.skyNanos = 0L;
            this.terrainSetupNanos = 0L;
            this.terrainVisibilityCheckNanos = 0L;
            this.terrainCameraUpdateNanos = 0L;
            this.terrainChunkReadyUpdateNanos = 0L;
            this.terrainSectionCollectNanos = 0L;
            this.terrainSetupTerrainCallNanos = 0L;
            this.sodiumSetupTerrainNanos = 0L;
            this.terrainStateRestoreNanos = 0L;
            this.terrainSetupCalls = 0;
            this.terrainSetupSkippedReuse = 0;
            this.terrainSetupReuseAvailable = false;
            this.portalCameraMovedBlocksSinceLastSetup = 0.0D;
            this.portalCameraRotatedDegreesSinceLastSetup = 0.0D;
            this.pendingRebuildChunkCount = 0;
            this.pendingRebuildChunkCountForView = 0;
            this.pendingBlockUpdateChunkCount = 0;
            this.pendingBlockUpdateChunkCountForView = 0;
            this.reuseBlockedByPendingViewChunks = false;
            this.pendingRebuildChanged = false;
            this.pendingRebuildOldCount = 0;
            this.pendingRebuildNewCount = 0;
            this.pendingRebuildAdded = 0;
            this.pendingRebuildRemoved = 0;
            this.pendingRebuildStableFrames = 0;
            this.reuseBlockedByNewPendingChunks = false;
            this.readyChunksActualChanged = false;
            this.readyChunksOldCount = 0;
            this.readyChunksNewCount = 0;
            this.readyChunksAddedThisFrame = 0;
            this.readyChunksRemovedThisFrame = 0;
            this.terrainSetupReuseReason = "-";
            this.terrainSolidNanos = 0L;
            this.terrainCutoutNanos = 0L;
            this.terrainTranslucentNanos = 0L;
            this.entitiesNanos = 0L;
            this.entityCollectNanos = 0L;
            this.entityCullFilterNanos = 0L;
            this.entityRenderNanos = 0L;
            this.entityConsidered = 0;
            this.entityConsideredMax = 0;
            this.entityRendered = 0;
            this.entityRenderedMax = 0;
            this.entitySkipped = 0;
            this.entitySkippedOutOfRadius = 0;
            this.entitySkippedFrustum = 0;
            this.entitySkippedDimension = 0;
            this.uniqueEntityConsideredTotal = 0;
            this.uniqueEntityRenderedTotal = 0;
            this.uniqueEntityConsideredMax = 0;
            this.uniqueEntityRenderedMax = 0;
            this.duplicateRenderAttempts = 0;
            this.queryResultSizeTotal = 0;
            this.queryResultSizeMax = 0;
            this.queryUniqueCountTotal = 0;
            this.queryUniqueCountMax = 0;
            this.entitiesForRenderingSize = -1;
            this.entitiesForRenderingUniqueCount = -1;
            this.entityFrustumCullingEnabled = false;
            this.entityFrustumPadding = "-";
            this.entityFrustumAvailable = false;
            this.entityFrustumSource = "-";
            this.entityPassFrames = 0;
            this.blockEntitiesNanos = 0L;
            this.particlesNanos = 0L;
            this.compositeNanos = 0L;
            this.sodiumAcquireNanos = 0L;
            this.readyChunksNanos = 0L;
            this.sectionPrimerNanos = 0L;
        }
    }
}
