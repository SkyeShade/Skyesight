package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.network.SkyesightProxyMarkerPayload;
import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PortalPathProximity {
    private static final double QUERY_DISPLAY_FALLBACK_RADIUS = 8.0D;
    private static final double PLAYER_CAMERA_FALLBACK_RADIUS = 128.0D;
    private static final Map<String, Long> LAST_QUERY_BRIDGE_LOG_MILLIS = new HashMap<>();
    private static final ThreadLocal<ApparentPlayerContextState> APPARENT_PLAYER_CONTEXT = new ThreadLocal<>();
    private static final int APPARENT_CONTEXT_MAX_READS = 24;
    private static long lastRegionSnapshotFailureLogMillis;
    private static long lastSummaryMillis;
    private static int queries;
    private static int directWins;
    private static int portalWins;
    private static int noPlayer;
    private static int activePortalsConsidered;
    private static int multiHopSkipped;
    private static int directWrongDimRejected;
    private static boolean loggedClientRealPlayerCoordinateOverrideBlocked;
    private static final StringBuilder closestPortalSamples = new StringBuilder();
    private static final Map<ResourceLocation, Long> LAST_BRIDGE_MATH_LOG_MILLIS = new HashMap<>();
    private static final Map<String, Long> LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS = new HashMap<>();
    private static long lastProximitySnapshotLogMillis;
    private static final Map<Level, VisualLevelContext> VISUAL_LEVELS = new WeakHashMap<>();
    private static final Map<ClientProxyKey, PortalApparentQueryPlayer> CLIENT_QUERY_PROXIES = new HashMap<>();
    private static final Map<String, ProxyMarker> PROXY_MARKERS = new HashMap<>();

    private PortalPathProximity() {
    }

    public record PortalPlayerPathResult(
            Player player,
            Vec3 apparentPosition,
            double effectiveDistanceSqr,
            double pathDistanceSqr,
            boolean throughPortal,
            ResourceLocation viewId,
            ResourceKey<Level> queryDimension,
            ResourceKey<Level> playerDimension,
            String reason
    ) {
    }

    public record PortalPathCandidate(
            ResourceLocation viewId,
            Player player,
            Vec3 apparentPosition,
            double effectiveDistanceSqr,
            double pathDistanceSqr,
            boolean throughPortal,
            String reason
    ) {
    }

    public record ApparentPlayerContext(
            Player player,
            Vec3 apparentPosition,
            ResourceLocation viewId,
            boolean throughPortal,
            String reason
    ) {
    }

    public record ProxyMarker(
            int queryLevelIdentity,
            UUID realPlayerUuid,
            String playerName,
            ResourceLocation viewId,
            ResourceKey<Level> queryDimension,
            ResourceKey<Level> playerDimension,
            Vec3 apparentPosition,
            Vec3 realPlayerPosition,
            String direction,
            boolean syntheticReverse,
            String variant,
            ResourceKey<Level> displayDimension,
            ResourceKey<Level> cameraDimension,
            long createdGameTime,
            long createdMillis,
            String reason
    ) {
    }

    public record PortalBridge(
            ResourceLocation viewId,
            ResourceKey<Level> displayDimension,
            ResourceKey<Level> cameraDimension,
            @Nullable PortalFrame displayFrame,
            @Nullable PortalFrame cameraFrame,
            @Nullable Vec3 watchedRegionCenter,
            String cameraFrameCenterSource,
            boolean usedWatchCenterAsFrame,
            String source,
            ResourceLocation sourceViewId,
            String direction,
            boolean syntheticReverse
    ) {
        @Nullable
        public Vec3 displayCenter() {
            return displayFrame == null ? null : displayFrame.center();
        }

        @Nullable
        public Vec3 cameraCenter() {
            return cameraFrame == null ? null : cameraFrame.center();
        }
    }

    public record PortalFrame(Vec3 center, Vec3 right, Vec3 up, Vec3 forward) {
        public PortalFrame {
            right = normalizedOr(right, new Vec3(1.0D, 0.0D, 0.0D));
            up = normalizedOr(up, new Vec3(0.0D, 1.0D, 0.0D));
            forward = normalizedOr(forward, new Vec3(0.0D, 0.0D, 1.0D));
        }

        public Vec3 worldToLocal(Vec3 worldOffset) {
            return new Vec3(
                    worldOffset.dot(right),
                    worldOffset.dot(up),
                    worldOffset.dot(forward)
            );
        }

        public Vec3 localToWorld(Vec3 local) {
            return right.scale(local.x).add(up.scale(local.y)).add(forward.scale(local.z));
        }
    }

    private record ApparentAnchor(Vec3 position, String reason) {
    }

    private record PortalFrameMapping(
            PortalBridge bridge,
            ApparentAnchor anchor,
            Vec3 cameraLocal,
            Vec3 displayLocal,
            Vec3 queryOnCameraSide,
            double reverseDistanceSqr,
            String basis,
            String variant
    ) {
    }

    private record PortalCandidateEvaluation(Optional<PortalPathCandidate> candidate, String rejectReason) {
    }

    private record VisualLevelContext(
            ResourceLocation viewId,
            ResourceKey<Level> cameraDimension,
            Supplier<Player> playerSupplier,
            Map<ClientProxyKey, PortalApparentQueryPlayer> proxies
    ) {
    }

    private record ClientProxyKey(
            Level level,
            ResourceKey<Level> queryDimension,
            UUID realPlayerUuid,
            @Nullable ResourceLocation viewId,
            @Nullable ResourceKey<Level> displayDimension,
            @Nullable ResourceKey<Level> cameraDimension
    ) {
    }

    public static void registerVisualLevel(Level level, ResourceLocation viewId, ResourceKey<Level> cameraDimension, Supplier<Player> playerSupplier) {
        if (level == null || viewId == null || cameraDimension == null || playerSupplier == null) {
            return;
        }
        VISUAL_LEVELS.put(level, new VisualLevelContext(viewId, cameraDimension, playerSupplier, new HashMap<>()));
    }

    public static void unregisterVisualLevel(Level level) {
        if (level != null) {
            VISUAL_LEVELS.remove(level);
        }
    }

    public static boolean isSkyesightVisualLevel(Level level) {
        return level != null && VISUAL_LEVELS.containsKey(level);
    }

    public static ResourceKey<Level> queryLogicalDimension(Level queryLevel) {
        return logicalQueryDimension(queryLevel);
    }

    public static boolean isDirectPlayerCandidateForQuery(Level queryLevel, Player player) {
        return isDirectPlayerCandidate(queryLevel, player);
    }

    public static boolean isVanillaReturnValidForQueryLevel(Level queryLevel, Player vanilla) {
        return isDirectPlayerCandidate(queryLevel, vanilla)
                || vanilla instanceof PortalApparentQueryPlayer;
    }

    public static boolean shouldOverridePlayerQueryCompletely(EntityGetter getter, Level queryLevel) {
        if (getter == null || queryLevel == null) {
            return false;
        }
        if (isSkyesightVisualLevel(queryLevel)) {
            return true;
        }
        if (!queryLevel.isClientSide()) {
            return false;
        }
        if (!shouldUsePortalPlayerQueryBridge()) {
            return false;
        }
        if (!logicalQueryDimension(queryLevel).equals(queryLevel.dimension())) {
            return true;
        }
        if (hasWrongDimensionPlayers(queryLevel)) {
            return true;
        }
        return hasActivePortalBridgeForQueryLevel(queryLevel);
    }

    public static Optional<PortalPlayerPathResult> nearestPlayerConsideringPortals(
            Level level,
            Vec3 queryPos,
            double maxDistance,
            Predicate<Entity> predicate
    ) {
        if (queryPos == null) {
            return Optional.empty();
        }
        return nearestPlayerConsideringPortals(level, queryPos.x, queryPos.y, queryPos.z, maxDistance, predicate);
    }

    public static Optional<PortalPlayerPathResult> nearestPlayerConsideringPortals(
            Level level,
            double x,
            double y,
            double z,
            double maxDistance,
            Predicate<Entity> predicate
    ) {
        queries++;
        if (level == null) {
            noPlayer++;
            maybeLogSummary();
            return Optional.empty();
        }
        Vec3 queryPos = new Vec3(x, y, z);
        double maxDistanceSq = maxDistance < 0.0D ? Double.MAX_VALUE : maxDistance * maxDistance;
        PortalPathCandidate best = null;
        PortalPathCandidate bestDirect = null;
        PortalPathCandidate bestPortal = null;
        boolean enchantStack = isEnchantingTableBookStack();
        int enchantRegionsConsidered = 0;
        int enchantBridgesConsidered = 0;
        int enchantCandidateCount = 0;
        Map<String, Integer> enchantRejects = enchantStack ? new HashMap<>() : Map.of();
        int sourceRegionCount = PortalRegionTracker.size();
        List<Region> regionSnapshot = snapshotPortalRegionsSafely(level);
        logProximitySnapshotIfDue(level, regionSnapshot.size(), sourceRegionCount);

        for (Player player : level.players()) {
            if (!isDirectPlayerCandidate(level, player)) {
                directWrongDimRejected++;
                if (enchantStack) {
                    enchantRejects.merge("direct-player-wrong-dimension", 1, Integer::sum);
                }
                continue;
            }
            if (!passes(predicate, player)) {
                continue;
            }
            double distanceSq = directDistanceSqr(queryPos, player);
            if (distanceSq <= maxDistanceSq && (best == null || distanceSq < best.pathDistanceSqr())) {
                best = new PortalPathCandidate(null, player, player.position(), distanceSq, distanceSq, false, "direct-same-dimension");
            }
            if (distanceSq <= maxDistanceSq && (bestDirect == null || distanceSq < bestDirect.pathDistanceSqr())) {
                bestDirect = new PortalPathCandidate(null, player, player.position(), distanceSq, distanceSq, false, "direct-same-dimension");
            }
        }

        for (Region region : regionSnapshot) {
            activePortalsConsidered++;
            if (enchantStack) {
                enchantRegionsConsidered++;
                if (portalBridge(level, findPlayer(level, region), region).isPresent()) {
                    enchantBridgesConsidered++;
                }
            }
            PortalCandidateEvaluation evaluation = evaluatePortalCandidate(level, queryPos, region, predicate, maxDistance, maxDistanceSq);
            Optional<PortalPathCandidate> candidate = evaluation.candidate();
            if (enchantStack && candidate.isEmpty()) {
                enchantRejects.merge(evaluation.rejectReason(), 1, Integer::sum);
            }
            if (candidate.isPresent() && (best == null || portalPathBeatsDirectPath(candidate.get().pathDistanceSqr(), best.pathDistanceSqr()))) {
                best = candidate.get();
                appendSample(best.viewId() + ":effective=" + formatDouble(Math.sqrt(best.effectiveDistanceSqr())) + ":path=" + formatDouble(Math.sqrt(best.pathDistanceSqr())));
            }
            if (candidate.isPresent()) {
                enchantCandidateCount++;
                if (bestPortal == null || candidate.get().pathDistanceSqr() < bestPortal.pathDistanceSqr()) {
                    bestPortal = candidate.get();
                }
            }
        }
        VisualLevelContext visualContext = VISUAL_LEVELS.get(level);
        if (visualContext != null) {
            Player visualPlayer = visualContext.playerSupplier().get();
            if (visualPlayer != null) {
                if (isDirectPlayerCandidate(level, visualPlayer) && passes(predicate, visualPlayer)) {
                    double distanceSq = directDistanceSqr(queryPos, visualPlayer);
                    boolean inRange = distanceSq <= maxDistanceSq;
                    traceVisualPlayerCandidate(
                            visualContext,
                            level,
                            queryPos,
                            maxDistance,
                            "real",
                            visualPlayer,
                            visualPlayer.position(),
                            distanceSq,
                            inRange,
                            true,
                            inRange ? "-" : "direct-out-of-range"
                    );
                    if (inRange && (best == null || distanceSq < best.pathDistanceSqr())) {
                        best = new PortalPathCandidate(null, visualPlayer, visualPlayer.position(), distanceSq, distanceSq, false, "direct-same-dimension");
                    }
                    if (inRange && (bestDirect == null || distanceSq < bestDirect.pathDistanceSqr())) {
                        bestDirect = new PortalPathCandidate(null, visualPlayer, visualPlayer.position(), distanceSq, distanceSq, false, "direct-same-dimension");
                    }
                } else {
                    if (!isDirectPlayerCandidate(level, visualPlayer)) {
                        directWrongDimRejected++;
                    }
                    traceVisualPlayerCandidate(
                            visualContext,
                            level,
                            queryPos,
                            maxDistance,
                            "real",
                            visualPlayer,
                            visualPlayer.position(),
                            directDistanceSqr(queryPos, visualPlayer),
                            false,
                            false,
                            isDirectPlayerCandidate(level, visualPlayer) ? "predicate-failed" : "direct-player-wrong-dimension"
                    );
                }
                for (PortalBridge bridge : allDirectionalBridges()) {
                    activePortalsConsidered++;
                    PortalCandidateEvaluation evaluation = evaluatePortalCandidateForBridge(
                            level,
                            queryPos,
                            visualPlayer,
                            visualContext,
                            bridge,
                            maxDistance,
                            maxDistanceSq,
                            predicate
                    );
                    Optional<PortalPathCandidate> candidate = evaluation.candidate();
                    if (enchantStack && candidate.isEmpty()) {
                        enchantRejects.merge(evaluation.rejectReason(), 1, Integer::sum);
                    }
                    if (candidate.isPresent() && (best == null || portalPathBeatsDirectPath(candidate.get().pathDistanceSqr(), best.pathDistanceSqr()))) {
                        best = candidate.get();
                        appendSample(best.viewId() + ":visual-effective=" + formatDouble(Math.sqrt(best.effectiveDistanceSqr())) + ":path=" + formatDouble(Math.sqrt(best.pathDistanceSqr())));
                    }
                    if (candidate.isPresent()) {
                        enchantCandidateCount++;
                        if (bestPortal == null || candidate.get().pathDistanceSqr() < bestPortal.pathDistanceSqr()) {
                            bestPortal = candidate.get();
                        }
                    }
                }
            }
        }
        if (enchantStack && bestDirect != null && bestPortal != null && bestDirect.pathDistanceSqr() <= bestPortal.pathDistanceSqr()) {
            enchantRejects.merge("portal-distance-not-actually-closer", 1, Integer::sum);
        }

        traceEnchantPortalCandidates(
                level,
                queryPos,
                maxDistance,
                bestDirect,
                bestPortal,
                best,
                enchantRegionsConsidered,
                enchantBridgesConsidered,
                enchantCandidateCount,
                enchantRejects
        );

        if (best == null) {
            noPlayer++;
            maybeLogSummary();
            return Optional.empty();
        }
        if (best.throughPortal()) {
            portalWins++;
        } else {
            directWins++;
        }
        traceVisualPlayerQueryResult(visualContext, level, queryPos, maxDistance, best);
        maybeLogSummary();
        return Optional.of(new PortalPlayerPathResult(
                best.player(),
                best.apparentPosition(),
                best.effectiveDistanceSqr(),
                best.pathDistanceSqr(),
                best.throughPortal(),
                best.viewId(),
                logicalQueryDimension(level),
                best.player().level().dimension(),
                best.reason()
        ));
    }

    public static Optional<PortalPlayerPathResult> apparentPlayerPositionThroughPortal(
            Level queryLevel,
            Vec3 queryPos,
            Player player,
            Region region
    ) {
        if (queryLevel == null || queryPos == null || player == null || region == null) {
            return Optional.empty();
        }
        return apparentPlayerPositionThroughPortal(queryLevel, queryPos, region, entity -> entity == player)
                .map(candidate -> new PortalPlayerPathResult(
                        candidate.player(),
                        candidate.apparentPosition(),
                        candidate.effectiveDistanceSqr(),
                        candidate.pathDistanceSqr(),
                        candidate.throughPortal(),
                        candidate.viewId(),
                        logicalQueryDimension(queryLevel),
                        player.level().dimension(),
                        candidate.reason()
                ));
    }

    public static Optional<PortalPlayerPathResult> apparentPlayerPositionThroughPortal(
            Level queryLevel,
            Vec3 queryPos,
            Player player,
            Object portalData
    ) {
        if (portalData instanceof Region region) {
            return apparentPlayerPositionThroughPortal(queryLevel, queryPos, player, region);
        }
        multiHopSkipped++;
        maybeLogSummary();
        return Optional.empty();
    }

    public static double directDistanceSqr(Vec3 queryPos, Player player) {
        if (queryPos == null || player == null) {
            return Double.MAX_VALUE;
        }
        return queryPos.distanceToSqr(player.position());
    }

    public static boolean portalPathBeatsDirectPath(double portalPathDistanceSqr, double directPathDistanceSqr) {
        return portalPathDistanceSqr < directPathDistanceSqr;
    }

    public static ApparentPlayerContext currentApparentPlayerContext(Player player) {
        if (!SkyesightDebugConfig.PORTAL_AWARE_PLAYER_COORDINATES_FOR_QUERIES || player == null) {
            return null;
        }
        ApparentPlayerContextState state = APPARENT_PLAYER_CONTEXT.get();
        if (state == null || state.remainingReads <= 0 || state.context.player() != player) {
            return null;
        }
        return state.context;
    }

    public static void pushApparentPlayerContext(PortalPlayerPathResult result) {
        if (!SkyesightDebugConfig.PORTAL_AWARE_PLAYER_COORDINATES_FOR_QUERIES
                || result == null
                || !result.throughPortal()
                || result.player() == null
                || result.apparentPosition() == null) {
            return;
        }
        APPARENT_PLAYER_CONTEXT.set(new ApparentPlayerContextState(
                new ApparentPlayerContext(
                        result.player(),
                        result.apparentPosition(),
                        result.viewId(),
                        true,
                        result.reason()
                ),
                APPARENT_CONTEXT_MAX_READS
        ));
    }

    public static void storeShortLivedApparentPlayerContext(PortalPlayerPathResult result) {
        pushApparentPlayerContext(result);
    }

    public static int currentApparentPlayerContextRemainingReads(Player player) {
        ApparentPlayerContextState state = APPARENT_PLAYER_CONTEXT.get();
        if (player == null || state == null || state.context.player() != player) {
            return 0;
        }
        return Math.max(0, state.remainingReads);
    }

    public static boolean shouldBlockClientRealPlayerCoordinateOverride(Player player) {
        return player != null
                && player.level() != null
                && player.level().isClientSide()
                && !(player instanceof PortalApparentQueryPlayer);
    }

    public static void traceRealPlayerCoordinateOverrideAttempt(Player player, ApparentPlayerContext context) {
        if (player == null || context == null || loggedClientRealPlayerCoordinateOverrideBlocked) {
            return;
        }
        loggedClientRealPlayerCoordinateOverrideBlocked = true;
        Skyesight.LOGGER.warn(
                "[Skyesight] PORTAL_REAL_PLAYER_COORD_OVERRIDE_ATTEMPT: player={} side=client blocked=yes contextViewId={} real={} apparent={} reason=use-proxy-instead",
                player.getGameProfile().getName(),
                context.viewId(),
                formatVec(player.position()),
                formatVec(context.apparentPosition())
        );
    }

    public static void popApparentPlayerContext() {
        APPARENT_PLAYER_CONTEXT.remove();
    }

    public static double apparentOrRealX(Player player) {
        ApparentPlayerContext context = consumeContext(player);
        return context == null ? player.position().x : context.apparentPosition().x;
    }

    public static double apparentOrRealY(Player player) {
        ApparentPlayerContext context = consumeContext(player);
        return context == null ? player.position().y : context.apparentPosition().y;
    }

    public static double apparentOrRealZ(Player player) {
        ApparentPlayerContext context = consumeContext(player);
        return context == null ? player.position().z : context.apparentPosition().z;
    }

    public static double apparentOrRealDistanceToSqr(Player player, double x, double y, double z) {
        ApparentPlayerContext context = consumeContext(player);
        return context == null ? player.position().distanceToSqr(x, y, z) : context.apparentPosition().distanceToSqr(x, y, z);
    }

    public static boolean shouldUsePortalPlayerQueryBridge() {
        return SkyesightDebugConfig.PORTAL_AWARE_PLAYER_QUERIES && isAllowedQueryContext();
    }

    public static void tracePlayerQueryPlayersAudit(
            Level level,
            boolean overrideAtHead,
            boolean vanillaAllowedToRun,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || level == null) {
            return;
        }
        if (!isEnchantingTableBookStack() && !isPortalPlayerQueryStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("players-audit/" + level.getClass().getName() + "/" + reason, 1000L)) {
            return;
        }
        Map<String, Integer> playersByDim = new HashMap<>();
        boolean wrongDimPlayersPresent = false;
        for (Player player : level.players()) {
            String dim = player == null || player.level() == null ? "-" : player.level().dimension().location().toString();
            playersByDim.merge(dim, 1, Integer::sum);
            if (!isDirectPlayerCandidate(level, player)) {
                wrongDimPlayersPresent = true;
            }
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_PLAYERS_AUDIT: queryLevelClass={} queryDim={} playersCount={} playersByDim={} wrongDimPlayersPresent={} overrideAtHead={} vanillaAllowedToRun={} reason={}",
                level.getClass().getName(),
                logicalQueryDimension(level).location(),
                level.players().size(),
                playersByDim,
                wrongDimPlayersPresent ? "yes" : "no",
                overrideAtHead ? "yes" : "no",
                vanillaAllowedToRun ? "yes" : "no",
                reason
        );
    }

    public static void tracePlayerQueryFinal(
            String method,
            Level level,
            boolean overrodeAtHead,
            @Nullable Player result,
            boolean blockedWrongDimVanilla,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        if (!isEnchantingTableBookStack() && !isPortalPlayerQueryStack()) {
            return;
        }
        String resultKind = "null";
        if (result instanceof PortalApparentQueryPlayer proxy) {
            resultKind = isDirectPlayerCandidate(level, proxy.realPlayer()) ? "proxy-direct" : "proxy-portal";
        } else if (result != null) {
            resultKind = isDirectPlayerCandidate(level, result) ? "real-direct" : "wrong-dim-real-blocked";
        }
        Player realPlayer = result instanceof PortalApparentQueryPlayer proxy ? proxy.realPlayer() : result;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_FINAL: method={} queryDim={} side={} overrodeAtHead={} resultKind={} resultPlayerDim={} resultPos={} blockedWrongDimVanilla={} reason={}",
                method,
                level == null ? "-" : logicalQueryDimension(level).location(),
                side(level),
                overrodeAtHead ? "yes" : "no",
                resultKind,
                realPlayer == null || realPlayer.level() == null ? "-" : realPlayer.level().dimension().location(),
                result == null ? "-" : formatVec(result.position()),
                blockedWrongDimVanilla ? "yes" : "no",
                reason
        );
    }

    public static boolean isAllowedQueryContext() {
        if (SkyesightDebugConfig.PORTAL_AWARE_MOB_TARGETING) {
            return true;
        }
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if (className.contains("ai.")
                    || className.contains("goal")
                    || className.contains("sensor")
                    || className.contains("targeting")
                    || className.contains("TargetingConditions")
                    || methodName.toLowerCase(Locale.ROOT).contains("attack")
                    || methodName.toLowerCase(Locale.ROOT).contains("anger")
                    || methodName.toLowerCase(Locale.ROOT).contains("hurt")) {
                return false;
            }
        }
        return true;
    }

    public static void tracePlayerQueryBridge(
            String method,
            Level level,
            Vec3 query,
            Player directPlayer,
            double directDistanceSqr,
            PortalPlayerPathResult result,
            Player chosen,
            boolean contextAllowed,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = method + "/" + stackCaller();
        long previous = LAST_QUERY_BRIDGE_LOG_MILLIS.getOrDefault(key, 0L);
        if (now - previous < 2000L) {
            return;
        }
        LAST_QUERY_BRIDGE_LOG_MILLIS.put(key, now);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_BRIDGE: method={} levelDim={} query={} vanillaPlayer={} vanillaDistance={} portalPlayer={} portalEffectiveDistance={} portalPathDistance={} chosen={} viewId={} contextAllowed={} reason={}",
                method,
                level == null ? "-" : level.dimension().location(),
                query == null ? "-" : formatVec(query),
                directPlayer == null ? "-" : directPlayer.getGameProfile().getName(),
                directDistanceSqr < 0.0D ? "-" : formatDouble(Math.sqrt(directDistanceSqr)),
                result == null || !result.throughPortal() ? "-" : result.player().getGameProfile().getName(),
                result == null || !result.throughPortal() ? "-" : formatDouble(Math.sqrt(result.effectiveDistanceSqr())),
                result == null || !result.throughPortal() ? "-" : formatDouble(Math.sqrt(result.pathDistanceSqr())),
                chosen == null ? "none" : (result != null && result.throughPortal() && chosen == result.player() ? "portal" : "vanilla"),
                result == null || result.viewId() == null ? "-" : result.viewId(),
                contextAllowed ? "yes" : "no",
                reason
        );
    }

    public static void tracePlayerQueryReturnPath(
            Level level,
            @Nullable PortalPlayerPathResult result,
            @Nullable Player returned,
            boolean usesApparentContext,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_RETURN_PATH: side={} levelClass={} isSkyesightVisualLevel={} queryDim={} viewId={} throughPortal={} returned={} usesApparentContext={} reason={}",
                side(level),
                level == null ? "-" : level.getClass().getName(),
                isSkyesightVisualLevel(level) ? "yes" : "no",
                level == null ? "-" : logicalQueryDimension(level).location(),
                result == null || result.viewId() == null ? "-" : result.viewId(),
                result != null && result.throughPortal() ? "yes" : "no",
                returned == null ? "none" : returned instanceof PortalApparentQueryPlayer ? "proxy" : "real",
                usesApparentContext ? "yes" : "no",
                reason
        );
    }

    public static void traceCrossDimPlayerQueryGuard(
            Level level,
            @Nullable Player player,
            @Nullable Player vanillaReturned,
            @Nullable PortalPlayerPathResult result,
            boolean directAllowed,
            boolean proxyReturned,
            boolean blockedVanillaWrongDim,
            String returnedKind,
            @Nullable Vec3 returnedPos,
            @Nullable ResourceLocation viewId,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        if (!isEnchantingTableBookStack() && !isPortalPlayerQueryStack()) {
            return;
        }
        Player realPlayer = player instanceof PortalApparentQueryPlayer proxy ? proxy.realPlayer() : player;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_CROSS_DIM_PLAYER_QUERY_GUARD: queryLevelClass={} queryDim={} playerDim={} player={} vanillaReturned={} directAllowed={} portalCandidateExists={} proxyReturned={} blockedVanillaWrongDim={} returnedKind={} returnedPos={} realPlayerPos={} viewId={} reason={}",
                level == null ? "-" : level.getClass().getName(),
                level == null ? "-" : logicalQueryDimension(level).location(),
                realPlayer == null || realPlayer.level() == null ? "-" : realPlayer.level().dimension().location(),
                realPlayer == null ? "-" : realPlayer.getGameProfile().getName(),
                vanillaReturned == null ? "no" : "yes",
                directAllowed ? "yes" : "no",
                result != null && result.throughPortal() ? "yes" : "no",
                proxyReturned ? "yes" : "no",
                blockedVanillaWrongDim ? "yes" : "no",
                returnedKind,
                returnedPos == null ? "-" : formatVec(returnedPos),
                realPlayer == null ? "-" : formatVec(realPlayer.position()),
                viewId == null ? "-" : viewId,
                reason
        );
    }

    private static boolean isPortalPlayerQueryStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if (element.getClassName().contains("EntityGetterPortalPlayerQueryMixin")
                    || element.getMethodName().contains("getNearestPlayer")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnchantingTableBookStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if (element.getClassName().contains("EnchantingTableBlockEntity")
                    || element.getMethodName().contains("bookAnimationTick")) {
                return true;
            }
        }
        return false;
    }

    public static void traceEnchantQueryStage(Level level, double x, double y, double z, double maxDistance, String method, String reason) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("query-stage/" + method, 1000L)) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENCHANT_QUERY_STAGE: side={} levelClass={} dim={} query={} maxDistance={} hookHit=yes method={} playersInLevel={} portalRegionsVisibleToThisSide={} portalViewsVisibleToThisSide={} reason={}",
                side(level),
                level == null ? "-" : level.getClass().getName(),
                level == null ? "-" : level.dimension().location(),
                formatVec(new Vec3(x, y, z)),
                formatDouble(maxDistance),
                method,
                level == null ? 0 : level.players().size(),
                PortalRegionTracker.size(),
                registryBridges().size(),
                level != null && level.isClientSide() && PortalRegionTracker.isEmpty()
                        ? "client-has-no-portal-region-data"
                        : reason
        );
    }

    public static void tracePlayerQueryTargetHit(Object target, String method) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("target-hit/" + method, 1000L)) {
            return;
        }
        Level level = target instanceof Level levelTarget ? levelTarget : null;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_TARGET_HIT: target={} method={} side={} applies=yes stackTop={}",
                target == null ? "-" : target.getClass().getName(),
                method,
                side(level),
                stackCaller()
        );
    }

    public static void traceEnchantApparentCoordinate(String method, Player player, double real, double returned) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("apparent-coordinate/" + method, 250L)) {
            return;
        }
        ApparentPlayerContext context = currentApparentPlayerContext(player);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENCHANT_APPARENT_COORD: method={} player={} hasContext={} real={} apparent={} returned={} remainingReads={} reason={}",
                method,
                player == null ? "-" : player.getGameProfile().getName(),
                context == null ? "no" : "yes",
                formatDouble(real),
                context == null ? "-" : formatVec(context.apparentPosition()),
                formatDouble(returned),
                currentApparentPlayerContextRemainingReads(player),
                context == null ? "no-apparent-context" : context.reason()
        );
    }

    private static Optional<PortalPathCandidate> apparentPlayerPositionThroughPortal(
            Level queryLevel,
            Vec3 queryPos,
            Region region,
            Predicate<Entity> predicate
    ) {
        if (queryLevel == null || queryPos == null || region == null || !region.dimension().equals(logicalQueryDimension(queryLevel))) {
            return Optional.empty();
        }
        return evaluatePortalCandidate(queryLevel, queryPos, region, predicate, Double.POSITIVE_INFINITY, Double.MAX_VALUE).candidate();
    }

    private static PortalCandidateEvaluation evaluatePortalCandidate(
            Level queryLevel,
            Vec3 queryPos,
            Region region,
            Predicate<Entity> predicate,
            double maxDistance,
            double maxDistanceSq
    ) {
        if (queryLevel == null || queryPos == null || region == null) {
            return new PortalCandidateEvaluation(Optional.empty(), "client-data-missing");
        }
        if (!region.dimension().equals(logicalQueryDimension(queryLevel))) {
            return new PortalCandidateEvaluation(Optional.empty(), "wrong-dimension");
        }
        Player owner = findPlayer(queryLevel, region);
        if (owner == null) {
            return new PortalCandidateEvaluation(Optional.empty(), "no-owner-player");
        }
        if (!passes(predicate, owner)) {
            return new PortalCandidateEvaluation(Optional.empty(), "predicate-failed");
        }
        double directDistanceSq = directDistanceSqr(queryPos, owner);
        List<PortalBridge> bridges = portalBridges(queryLevel, owner, region);
        if (bridges.isEmpty()) {
            return new PortalCandidateEvaluation(Optional.empty(), "no-bridge-data");
        }

        PortalFrameMapping mapping = null;
        String firstRejectReason = null;
        for (PortalBridge bridge : bridges) {
            String rejectReason = bridgeRejectReason(queryLevel, queryPos, owner, bridge, maxDistance);
            if (rejectReason != null) {
                traceEnchantBridgeCandidate(queryLevel, queryPos, maxDistance, owner, region, bridge, directDistanceSq, diagnosticMapping(queryPos, owner, bridge), false, false, rejectReason);
                if (firstRejectReason == null) {
                    firstRejectReason = rejectReason;
                }
                continue;
            }
            Optional<PortalFrameMapping> bridgeMapping = apparentPlayerPositionViaBridge(queryLevel, queryPos, owner, region, bridge);
            if (bridgeMapping.isEmpty()) {
                traceEnchantBridgeCandidate(queryLevel, queryPos, maxDistance, owner, region, bridge, directDistanceSq, diagnosticMapping(queryPos, owner, bridge), false, false, "no-frame-basis");
                if (firstRejectReason == null) {
                    firstRejectReason = "no-frame-basis";
                }
                continue;
            }
            PortalFrameMapping candidate = bridgeMapping.get();
            double candidateDistanceSq = queryPos.distanceToSqr(candidate.anchor().position());
            boolean candidateValid = candidateDistanceSq <= maxDistanceSq;
            traceEnchantBridgeCandidate(queryLevel, queryPos, maxDistance, owner, region, bridge, directDistanceSq, candidate, true, candidateValid, candidateValid ? "valid-frame-candidate" : "portal-distance-too-far");
            if (!candidateValid) {
                if (firstRejectReason == null) {
                    firstRejectReason = "portal-distance-too-far";
                }
                continue;
            }
            if (mapping == null || candidateDistanceSq < queryPos.distanceToSqr(mapping.anchor().position())) {
                mapping = candidate;
            }
        }
        if (mapping == null) {
            return new PortalCandidateEvaluation(Optional.empty(), firstRejectReason == null ? "no-valid-candidate" : firstRejectReason);
        }

        ApparentAnchor anchor = mapping.anchor();
        Vec3 apparent = anchor.position();
        if (isCrossDimIdenticalCoordinateGhost(queryLevel, owner, apparent)) {
            return new PortalCandidateEvaluation(Optional.empty(), "cross-dim-identical-coordinate-ghost-blocked");
        }
        double distanceSq = queryPos.distanceToSqr(apparent);
        double pathDistance = queryPos.distanceTo(mapping.bridge().displayCenter())
                + owner.position().distanceTo(mapping.bridge().cameraCenter());
        double pathDistanceSq = pathDistance * pathDistance;
        Player candidatePlayer = queryLevel.isClientSide()
                ? clientProxyPlayer(queryLevel, owner, mapping, apparent)
                : owner;
        return new PortalCandidateEvaluation(Optional.of(new PortalPathCandidate(
                mapping.bridge().viewId(),
                candidatePlayer,
                apparent,
                distanceSq,
                pathDistanceSq,
                true,
                anchor.reason()
        )), "-");
    }

    private static PortalApparentQueryPlayer proxyPlayer(
            Level queryLevel,
            VisualLevelContext visualContext,
            Player realPlayer,
            PortalFrameMapping mapping,
            Vec3 apparentPosition
    ) {
        PortalBridge bridge = mapping == null ? null : mapping.bridge();
        ClientProxyKey key = new ClientProxyKey(
                queryLevel,
                logicalQueryDimension(queryLevel),
                realPlayer.getUUID(),
                visualContext.viewId(),
                bridge == null ? null : bridge.displayDimension(),
                bridge == null ? visualContext.cameraDimension() : bridge.cameraDimension()
        );
        PortalApparentQueryPlayer proxy = visualContext.proxies().computeIfAbsent(
                key,
                ignored -> new PortalApparentQueryPlayer(queryLevel, realPlayer)
        );
        proxy.updateApparentPosition(apparentPosition);
        recordProxyMarker(queryLevel, realPlayer, visualContext.viewId(), mapping, apparentPosition, "visual-level-proxy");
        return proxy;
    }

    private static PortalApparentQueryPlayer clientProxyPlayer(
            Level queryLevel,
            Player realPlayer,
            PortalFrameMapping mapping,
            Vec3 apparentPosition
    ) {
        PortalBridge bridge = mapping == null ? null : mapping.bridge();
        ClientProxyKey key = new ClientProxyKey(
                queryLevel,
                logicalQueryDimension(queryLevel),
                realPlayer.getUUID(),
                bridge == null ? null : bridge.viewId(),
                bridge == null ? null : bridge.displayDimension(),
                bridge == null ? null : bridge.cameraDimension()
        );
        PortalApparentQueryPlayer proxy = CLIENT_QUERY_PROXIES.computeIfAbsent(
                key,
                ignored -> new PortalApparentQueryPlayer(queryLevel, realPlayer)
        );
        proxy.updateApparentPosition(apparentPosition);
        recordProxyMarker(queryLevel, realPlayer, bridge == null ? null : bridge.viewId(), mapping, apparentPosition, "client-level-proxy");
        return proxy;
    }

    public static List<ProxyMarker> proxyMarkersForLevel(Level level) {
        if (level == null || !SkyesightDebugConfig.SHOW_PROXY_MARKERS) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        PROXY_MARKERS.entrySet().removeIf(entry -> now - entry.getValue().createdMillis() > 2000L);
        int levelIdentity = System.identityHashCode(level);
        ResourceKey<Level> dimension = logicalQueryDimension(level);
        List<ProxyMarker> markers = new ArrayList<>();
        for (ProxyMarker marker : PROXY_MARKERS.values()) {
            if (marker.queryLevelIdentity() == levelIdentity || marker.queryDimension().equals(dimension)) {
                markers.add(marker);
            }
        }
        return markers;
    }

    private static void recordProxyMarker(
            Level queryLevel,
            Player realPlayer,
            @Nullable ResourceLocation viewId,
            @Nullable PortalFrameMapping mapping,
            Vec3 apparentPosition,
            String reason
    ) {
        if (queryLevel == null
                || realPlayer == null
                || apparentPosition == null
                || !queryLevel.isClientSide()
                || (!SkyesightDebugConfig.SHOW_PROXY_MARKERS && !SkyesightDebugConfig.SHOW_PROXY_ARMOR_STANDS)) {
            return;
        }
        PortalBridge bridge = mapping == null ? null : mapping.bridge();
        ResourceLocation resolvedViewId = viewId == null ? ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "unknown") : viewId;
        String direction = bridge == null ? "-" : bridge.direction();
        boolean syntheticReverse = bridge != null && bridge.syntheticReverse();
        String variant = mapping == null ? "-" : mapping.variant();
        ResourceKey<Level> displayDimension = bridge == null ? logicalQueryDimension(queryLevel) : bridge.displayDimension();
        ResourceKey<Level> cameraDimension = bridge == null ? realPlayer.level().dimension() : bridge.cameraDimension();
        long gameTime = 0L;
        try {
            gameTime = queryLevel.getGameTime();
        } catch (RuntimeException ignored) {
            // Debug marker only.
        }
        ProxyMarker marker = new ProxyMarker(
                System.identityHashCode(queryLevel),
                realPlayer.getUUID(),
                realPlayer.getGameProfile().getName(),
                viewId,
                logicalQueryDimension(queryLevel),
                realPlayer.level().dimension(),
                apparentPosition,
                realPlayer.position(),
                direction,
                syntheticReverse,
                variant,
                displayDimension,
                cameraDimension,
                gameTime,
                System.currentTimeMillis(),
                reason
        );
        String key = marker.queryLevelIdentity()
                + "/"
                + resolvedViewId
                + "/"
                + direction
                + "/"
                + syntheticReverse
                + "/"
                + variant
                + "/"
                + displayDimension.location()
                + "/"
                + cameraDimension.location()
                + "/"
                + realPlayer.getUUID();
        PROXY_MARKERS.put(key, marker);
        if (SkyesightDebugConfig.SHOW_PROXY_ARMOR_STANDS) {
            PacketDistributor.sendToServer(new SkyesightProxyMarkerPayload(
                    key,
                    armorStandMarkerName(realPlayer, resolvedViewId, direction, variant),
                    marker.queryDimension(),
                    apparentPosition,
                    realPlayer.getUUID(),
                    realPlayer.getGameProfile().getName(),
                    resolvedViewId,
                    direction,
                    syntheticReverse,
                    variant,
                    displayDimension,
                    cameraDimension,
                    2000
            ));
        }
        if (SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_PROXY_MARKER: viewId={} queryLevel={} playerDim={} realPlayer={} realPos={} apparentPos={} markerLevel={} frameSource={} mapping={} cameraFrameCenter={} cameraFrameCenterSource={} watchedRegionCenter={} usedWatchCenterAsFrame={} reason={} ttlMs={}",
                    resolvedViewId,
                    queryLevel.getClass().getName(),
                    realPlayer.level().dimension().location(),
                    realPlayer.getGameProfile().getName(),
                    formatVec(realPlayer.position()),
                    formatVec(apparentPosition),
                    logicalQueryDimension(queryLevel).location(),
                    bridge == null ? "-" : bridge.source(),
                    mapping == null ? "-" : mapping.variant(),
                    bridge == null ? "-" : formatNullableVec(bridge.cameraCenter()),
                    bridge == null ? "-" : bridge.cameraFrameCenterSource(),
                    bridge == null ? "-" : formatNullableVec(bridge.watchedRegionCenter()),
                    bridge != null && bridge.usedWatchCenterAsFrame() ? "yes" : "no",
                    reason,
                    2000
            );
        }
    }

    private static String armorStandMarkerName(Player realPlayer, ResourceLocation viewId, String direction, String variant) {
        String name = realPlayer == null || realPlayer.getGameProfile() == null
                ? "?"
                : realPlayer.getGameProfile().getName();
        return "proxy " + name
                + "\nview=" + viewId.getPath()
                + "\ndir=" + direction
                + "\nvar=" + variant;
    }

    private static boolean isCrossDimIdenticalCoordinateGhost(
            Level queryLevel,
            Player player,
            Vec3 apparentPosition
    ) {
        if (queryLevel == null || player == null || player.level() == null || apparentPosition == null) {
            return false;
        }
        if (logicalQueryDimension(queryLevel).equals(player.level().dimension())) {
            return false;
        }
        return apparentPosition.distanceToSqr(player.position()) < 1.0E-4D;
    }

    private static PortalCandidateEvaluation evaluatePortalCandidateForBridge(
            Level queryLevel,
            Vec3 queryPos,
            Player owner,
            VisualLevelContext visualContext,
            PortalBridge bridge,
            double maxDistance,
            double maxDistanceSq,
            Predicate<Entity> predicate
    ) {
        if (queryLevel == null || queryPos == null || owner == null || bridge == null) {
            return new PortalCandidateEvaluation(Optional.empty(), "client-data-missing");
        }
        double directDistanceSq = directDistanceSqr(queryPos, owner);
        String rejectReason = bridgeRejectReason(queryLevel, queryPos, owner, bridge, maxDistance);
        if (rejectReason != null) {
            traceVisualBlockEntityPlayerQuery(queryLevel, queryPos, maxDistance, visualContext.viewId(), owner, null, directDistanceSq, rejectReason);
            traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, null, directDistanceSq, false, false, rejectReason);
            return new PortalCandidateEvaluation(Optional.empty(), rejectReason);
        }
        Optional<PortalFrameMapping> bridgeMapping = mapThroughBridge(queryPos, owner, bridge);
        if (bridgeMapping.isEmpty()) {
            traceVisualBlockEntityPlayerQuery(queryLevel, queryPos, maxDistance, visualContext.viewId(), owner, null, directDistanceSq, "no-frame-basis");
            traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, null, directDistanceSq, false, false, "no-frame-basis");
            return new PortalCandidateEvaluation(Optional.empty(), "no-frame-basis");
        }
        PortalFrameMapping mapping = bridgeMapping.get();
        Vec3 apparent = mapping.anchor().position();
        if (isCrossDimIdenticalCoordinateGhost(queryLevel, owner, apparent)) {
            traceVisualBlockEntityPlayerQuery(queryLevel, queryPos, maxDistance, visualContext.viewId(), owner, mapping, directDistanceSq, "cross-dim-identical-coordinate-ghost-blocked");
            traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, apparent, queryPos.distanceToSqr(apparent), false, true, "cross-dim-identical-coordinate-ghost-blocked");
            return new PortalCandidateEvaluation(Optional.empty(), "cross-dim-identical-coordinate-ghost-blocked");
        }
        double distanceSq = queryPos.distanceToSqr(apparent);
        if (distanceSq > maxDistanceSq) {
            traceVisualBlockEntityPlayerQuery(queryLevel, queryPos, maxDistance, visualContext.viewId(), owner, mapping, directDistanceSq, "portal-distance-too-far");
            traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, apparent, distanceSq, false, true, "portal-distance-too-far");
            return new PortalCandidateEvaluation(Optional.empty(), "portal-distance-too-far");
        }
        double pathDistance = queryPos.distanceTo(mapping.bridge().displayCenter())
                + owner.position().distanceTo(mapping.bridge().cameraCenter());
        double pathDistanceSq = pathDistance * pathDistance;
        traceVisualBlockEntityPlayerQuery(queryLevel, queryPos, maxDistance, visualContext.viewId(), owner, mapping, directDistanceSq, "-");
        PortalApparentQueryPlayer proxy = proxyPlayer(queryLevel, visualContext, owner, mapping, apparent);
        boolean predicatePassed = passes(predicate, owner) && passes(predicate, proxy);
        if (!predicatePassed) {
            traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, apparent, distanceSq, true, false, "predicate-failed");
            return new PortalCandidateEvaluation(Optional.empty(), "predicate-failed");
        }
        traceVisualPlayerCandidate(visualContext, queryLevel, queryPos, maxDistance, "proxy", owner, apparent, distanceSq, true, true, "-");
        return new PortalCandidateEvaluation(Optional.of(new PortalPathCandidate(
                mapping.bridge().viewId(),
                proxy,
                apparent,
                distanceSq,
                pathDistanceSq,
                true,
                mapping.anchor().reason()
        )), "-");
    }

    private static Player findPlayer(Level queryLevel, Region region) {
        if (queryLevel == null || region == null) {
            return null;
        }
        for (Player player : queryLevel.players()) {
            if (player.getUUID().equals(region.playerId())) {
                return player;
            }
        }
        if (queryLevel.getServer() == null) {
            return null;
        }
        return queryLevel.getServer().getPlayerList().getPlayer(region.playerId());
    }

    private static Vec3 virtualRegionCenter(Region region) {
        return new Vec3(region.centerChunkX() * 16.0D + 8.0D, 80.0D, region.centerChunkZ() * 16.0D + 8.0D);
    }

    private static Optional<PortalBridge> portalBridge(Level queryLevel, Player owner, Region region) {
        if (queryLevel == null || owner == null || region == null) {
            return Optional.empty();
        }
        PortalBridge bridge = registryBridges().get(region.viewId());
        if (bridge == null) {
            return Optional.empty();
        }
        Vec3 runtimeCameraCenter = SkyesightSecondaryWatchRegion.center(owner.getUUID(), region.viewId(), bridge.cameraDimension());
        if (bridge.cameraCenter() == null) {
            return Optional.of(new PortalBridge(
                    bridge.viewId(),
                    bridge.displayDimension(),
                    bridge.cameraDimension(),
                    bridge.displayFrame(),
                    bridge.cameraFrame() == null ? null : new PortalFrame(
                            bridge.displayCenter(),
                            bridge.cameraFrame().right(),
                            bridge.cameraFrame().up(),
                            bridge.cameraFrame().forward()
                    ),
                    runtimeCameraCenter,
                    "portal-plane",
                    false,
                    bridge.source() + "+display-center-fallback",
                    bridge.sourceViewId(),
                    bridge.direction(),
                    bridge.syntheticReverse()
            ));
        }
        return Optional.of(new PortalBridge(
                bridge.viewId(),
                bridge.displayDimension(),
                bridge.cameraDimension(),
                bridge.displayFrame(),
                bridge.cameraFrame(),
                runtimeCameraCenter,
                "portal-plane",
                false,
                runtimeCameraCenter == null ? bridge.source() : bridge.source() + "+secondary-watch-metadata",
                bridge.sourceViewId(),
                bridge.direction(),
                bridge.syntheticReverse()
        ));
    }

    private static List<PortalBridge> portalBridges(Level queryLevel, Player owner, Region region) {
        List<PortalBridge> bridges = new ArrayList<>();
        portalBridge(queryLevel, owner, region).ifPresent(bridges::add);
        if (!bridges.isEmpty()) {
            PortalBridge forward = bridges.get(0);
            bridges.add(reverseBridge(forward));
        }
        return bridges;
    }

    private static List<PortalBridge> allDirectionalBridges() {
        List<PortalBridge> bridges = new ArrayList<>();
        for (PortalBridge bridge : registryBridges().values()) {
            bridges.add(bridge);
            bridges.add(reverseBridge(bridge));
        }
        return bridges;
    }

    private static PortalBridge reverseBridge(PortalBridge bridge) {
        return new PortalBridge(
                bridge.viewId(),
                bridge.cameraDimension(),
                bridge.displayDimension(),
                bridge.cameraFrame(),
                bridge.displayFrame(),
                null,
                "portal-plane",
                false,
                bridge.source() + "+reverse-direction",
                bridge.sourceViewId(),
                "reverse",
                !hasRealReverseBridge(bridge)
        );
    }

    private static boolean hasRealReverseBridge(PortalBridge bridge) {
        if (bridge == null || bridge.displayCenter() == null || bridge.cameraCenter() == null) {
            return false;
        }
        for (PortalBridge candidate : registryBridges().values()) {
            if (candidate.viewId().equals(bridge.viewId())) {
                continue;
            }
            if (!candidate.displayDimension().equals(bridge.cameraDimension())
                    || !candidate.cameraDimension().equals(bridge.displayDimension())
                    || candidate.displayCenter() == null
                    || candidate.cameraCenter() == null) {
                continue;
            }
            if (candidate.displayCenter().distanceToSqr(bridge.cameraCenter()) < 1.0D
                    && candidate.cameraCenter().distanceToSqr(bridge.displayCenter()) < 1.0D) {
                return true;
            }
        }
        return false;
    }

    private static Optional<PortalBridge> portalBridge(Level queryLevel, Player owner, Region region, ResourceLocation viewId) {
        if (queryLevel == null || owner == null || region == null || viewId == null) {
            return Optional.empty();
        }
        PortalBridge bridge = registryBridges().get(viewId);
        if (bridge == null) {
            return Optional.empty();
        }
        Vec3 runtimeCameraCenter = SkyesightSecondaryWatchRegion.center(owner.getUUID(), viewId, bridge.cameraDimension());
        if (bridge.cameraCenter() == null) {
            return Optional.empty();
        }
        return Optional.of(new PortalBridge(
                bridge.viewId(),
                bridge.displayDimension(),
                bridge.cameraDimension(),
                bridge.displayFrame(),
                bridge.cameraFrame(),
                SkyesightSecondaryWatchRegion.center(owner.getUUID(), viewId, bridge.cameraDimension()),
                "portal-plane",
                false,
                bridge.source() + "+portal-plane-camera",
                bridge.sourceViewId(),
                bridge.direction(),
                bridge.syntheticReverse()
        ));
    }

    private static Optional<PortalFrameMapping> apparentPlayerPositionViaBridge(Level queryLevel, Vec3 queryPos, Player owner, Region region, PortalBridge bridge) {
        Optional<PortalFrameMapping> mapping = mapThroughBridge(queryPos, owner, bridge);
        if (mapping.isEmpty()) {
            logBridgePathMath(queryLevel, queryPos, owner, region, bridge, null, Vec3.ZERO, null, Double.NaN, "rejected", false, "no-frame-basis");
            return Optional.empty();
        }
        PortalFrameMapping result = mapping.get();
        logBridgePathMath(queryLevel, queryPos, owner, region, bridge, result, owner.position().subtract(bridge.cameraCenter()), result.anchor().position(), result.reverseDistanceSqr(), result.anchor().reason(), true, "-");
        return Optional.of(result);
    }

    private static String lastBridgeRejectReason(Level queryLevel, Vec3 queryPos, Player owner, Region region, double maxDistance) {
        Optional<PortalBridge> bridge = portalBridge(queryLevel, owner, region);
        if (bridge.isEmpty()) {
            return "no-bridge-data";
        }
        if (bridge.get().displayCenter() == null) {
            return "no-display-center";
        }
        if (bridge.get().cameraCenter() == null) {
            return "no-camera-center";
        }
        if (bridge.get().displayFrame() == null || bridge.get().cameraFrame() == null) {
            return "no-frame-basis";
        }
        String rejectReason = bridgeRejectReason(queryLevel, queryPos, owner, bridge.get(), maxDistance);
        return rejectReason == null ? "no-bridge-data" : rejectReason;
    }

    private static String bridgeRejectReason(Level queryLevel, Vec3 queryPos, Player owner, PortalBridge bridge, double maxDistance) {
        if (queryLevel == null || queryPos == null || owner == null || bridge == null) {
            return "client-data-missing";
        }
        if (bridge.displayCenter() == null) {
            return "no-display-center";
        }
        if (bridge.cameraCenter() == null) {
            return "no-camera-center";
        }
        if (bridge.displayFrame() == null || bridge.cameraFrame() == null) {
            return "no-frame-basis";
        }
        ResourceKey<Level> queryDimension = logicalQueryDimension(queryLevel);
        if (!queryDimension.equals(bridge.displayDimension()) || !owner.level().dimension().equals(bridge.cameraDimension())) {
            return "wrong-dimension";
        }
        double queryDisplayMaxDistance = queryDisplayMaxDistance(maxDistance);
        if (queryPos.distanceToSqr(bridge.displayCenter()) > queryDisplayMaxDistance * queryDisplayMaxDistance) {
            return "query-not-near-display-side";
        }
        if (owner.position().distanceToSqr(bridge.cameraCenter()) > PLAYER_CAMERA_FALLBACK_RADIUS * PLAYER_CAMERA_FALLBACK_RADIUS) {
            return "player-not-on-camera-side";
        }
        return null;
    }

    private static Optional<PortalFrameMapping> mapThroughBridge(Vec3 queryPos, Player owner, PortalBridge bridge) {
        if (queryPos == null || owner == null || bridge == null || bridge.displayFrame() == null || bridge.cameraFrame() == null) {
            return Optional.empty();
        }
        Vec3 cameraOffsetWorld = owner.position().subtract(bridge.cameraFrame().center());
        Vec3 cameraLocal = bridge.cameraFrame().worldToLocal(cameraOffsetWorld);
        Vec3 displayOffsetWorldFromQuery = queryPos.subtract(bridge.displayFrame().center());
        Vec3 displayLocal = bridge.displayFrame().worldToLocal(displayOffsetWorldFromQuery);
        String basis = bridge.source().contains("translation") ? "translation" : "frame";

        if (isRegistryBackedBridge(bridge)) {
            PortalFrameMapping deterministic = mapThroughBridgeVariant(
                    queryPos,
                    owner,
                    bridge,
                    cameraLocal,
                    displayLocal,
                    basis,
                    "renderer-rotateY-pi"
            );
            return Optional.of(deterministic);
        }

        PortalFrameMapping same = mapThroughBridgeVariant(queryPos, owner, bridge, cameraLocal, displayLocal, basis, "same");
        PortalFrameMapping flipForward = mapThroughBridgeVariant(queryPos, owner, bridge, cameraLocal, displayLocal, basis, "flipForward");
        PortalFrameMapping flipRightForward = mapThroughBridgeVariant(queryPos, owner, bridge, cameraLocal, displayLocal, basis, "flipRightForward");
        PortalFrameMapping best = same;
        if (queryPos.distanceToSqr(flipForward.anchor().position()) < queryPos.distanceToSqr(best.anchor().position())) {
            best = flipForward;
        }
        if (queryPos.distanceToSqr(flipRightForward.anchor().position()) < queryPos.distanceToSqr(best.anchor().position())) {
            best = flipRightForward;
        }
        traceEnchantBridgeVariants(queryPos, owner, bridge, same, flipForward, flipRightForward, best);
        return Optional.of(best);
    }

    private static PortalFrameMapping mapThroughBridgeVariant(
            Vec3 queryPos,
            Player owner,
            PortalBridge bridge,
            Vec3 cameraLocal,
            Vec3 displayLocal,
            String basis,
            String variant
    ) {
        Vec3 displayLocalForPlayer = applyVariant(cameraLocal, variant);
        Vec3 displayOffsetWorld = bridge.displayFrame().localToWorld(displayLocalForPlayer);
        Vec3 apparent = bridge.displayFrame().center().add(displayOffsetWorld);

        Vec3 cameraLocalForQuery = applyVariant(displayLocal, variant);
        Vec3 queryOnCameraSide = bridge.cameraFrame().center().add(bridge.cameraFrame().localToWorld(cameraLocalForQuery));
        double reverseDistanceSqr = queryOnCameraSide.distanceToSqr(owner.position());

        String reason = switch (variant) {
            case "flipForward" -> "one-hop-frame-transform-flip-forward";
            case "flipRightForward" -> "one-hop-frame-transform-flip-right-forward";
            case "renderer-rotateY-pi" -> "renderer-deterministic-transform";
            default -> basis.equals("frame") ? "one-hop-frame-transform" : "one-hop-translation-bridge";
        };
        return new PortalFrameMapping(
                bridge,
                new ApparentAnchor(apparent, reason),
                cameraLocal,
                displayLocal,
                queryOnCameraSide,
                reverseDistanceSqr,
                basis,
                variant
        );
    }

    private static Vec3 applyVariant(Vec3 local, String variant) {
        return switch (variant) {
            case "flipForward" -> new Vec3(local.x, local.y, -local.z);
            case "flipRightForward", "renderer-rotateY-pi" -> new Vec3(-local.x, local.y, -local.z);
            default -> local;
        };
    }

    private static boolean isRegistryBackedBridge(PortalBridge bridge) {
        return bridge != null && bridge.source() != null && bridge.source().contains("shared-render-registry");
    }

    private static double queryDisplayMaxDistance(double maxDistance) {
        if (Double.isFinite(maxDistance) && maxDistance >= 0.0D) {
            return maxDistance;
        }
        return QUERY_DISPLAY_FALLBACK_RADIUS;
    }

    private static Map<ResourceLocation, PortalBridge> registryBridges() {
        Map<ResourceLocation, PortalBridge> bridges = new HashMap<>();
        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            putBridge(bridges, view);
        }
        return bridges;
    }

    private static void putBridge(Map<ResourceLocation, PortalBridge> bridges, RegisteredPortalView view) {
        bridges.put(view.id(), new PortalBridge(
                view.id(),
                view.source().dimension(),
                view.target().dimension(),
                frameFromEndpoint(view.source()),
                frameFromEndpoint(view.target()),
                null,
                "portal-plane",
                false,
                "shared-render-registry",
                view.id(),
                "forward",
                false
        ));
    }

    private static PortalFrame frameFromEndpoint(PortalEndpoint endpoint) {
        return frameFromRotation(endpoint.center(), endpoint.rotation());
    }

    private static PortalFrame frameFromRotation(Vec3 center, @Nullable Quaternionf rotation) {
        if (rotation == null) {
            return new PortalFrame(
                    center,
                    new Vec3(1.0D, 0.0D, 0.0D),
                    new Vec3(0.0D, 1.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, 1.0D)
            );
        }
        return new PortalFrame(
                center,
                rotate(rotation, 1.0F, 0.0F, 0.0F),
                rotate(rotation, 0.0F, 1.0F, 0.0F),
                rotate(rotation, 0.0F, 0.0F, 1.0F)
        );
    }

    private static Vec3 rotate(Quaternionf rotation, float x, float y, float z) {
        Vector3f vector = new Vector3f(x, y, z);
        new Quaternionf(rotation).transform(vector);
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static Quaternionf rotationFromYawPitchRoll(float yawDegrees, float pitchDegrees, float rollDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateZ((float) Math.toRadians(rollDegrees));
    }

    private static void logBridgePathMath(
            Level queryLevel,
            Vec3 queryPos,
            Player owner,
            Region region,
            PortalBridge bridge,
            @Nullable PortalFrameMapping mapping,
            Vec3 cameraOffset,
            @Nullable Vec3 apparent,
            double reverseDistanceSqr,
            String reason,
            boolean validDirection,
            String rejectReason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = LAST_BRIDGE_MATH_LOG_MILLIS.getOrDefault(region.viewId(), 0L);
        if (now - previous < 2000L) {
            return;
        }
        LAST_BRIDGE_MATH_LOG_MILLIS.put(region.viewId(), now);
        double distQueryToDisplay = queryPos == null || bridge.displayCenter() == null
                ? Double.NaN
                : queryPos.distanceTo(bridge.displayCenter());
        double distPlayerToDisplay = owner == null || bridge.displayCenter() == null
                ? Double.NaN
                : owner.position().distanceTo(bridge.displayCenter());
        double distPlayerToCamera = owner == null || bridge.cameraCenter() == null
                ? Double.NaN
                : owner.position().distanceTo(bridge.cameraCenter());
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BRIDGE_PATH_MATH: viewId={} displayDim={} cameraDim={} query={} displayCenter={} cameraCenter={} cameraFrameCenterSource={} watchedRegionCenter={} usedWatchCenterAsFrame={} playerReal={} cameraOffset={} cameraLocal={} displayLocal={} apparent={} distance={} reverseDistance={} distQueryToDisplay={} distPlayerToDisplay={} distPlayerToCamera={} validDirection={} rejectReason={} basis={} reason={}",
                region.viewId(),
                bridge.displayDimension().location(),
                bridge.cameraDimension().location(),
                queryPos == null ? queryLevel.dimension().location() : formatVec(queryPos),
                formatNullableVec(bridge.displayCenter()),
                formatNullableVec(bridge.cameraCenter()),
                bridge.cameraFrameCenterSource(),
                formatNullableVec(bridge.watchedRegionCenter()),
                bridge.usedWatchCenterAsFrame() ? "yes" : "no",
                owner == null ? "-" : formatVec(owner.position()),
                formatNullableVec(cameraOffset),
                mapping == null ? "-" : formatVec(mapping.cameraLocal()),
                mapping == null ? "-" : formatVec(mapping.displayLocal()),
                apparent == null ? "-" : formatVec(apparent),
                apparent == null || owner == null ? "-" : formatDouble(owner.position().distanceTo(apparent)),
                Double.isNaN(reverseDistanceSqr) ? "-" : formatSqrtDistance(reverseDistanceSqr),
                formatDouble(distQueryToDisplay),
                formatDouble(distPlayerToDisplay),
                formatDouble(distPlayerToCamera),
                validDirection ? "yes" : "no",
                rejectReason,
                mapping == null ? "-" : mapping.basis(),
                reason
        );
    }

    private static void traceEnchantPortalCandidates(
            Level level,
            Vec3 queryPos,
            double maxDistance,
            PortalPathCandidate bestDirect,
            PortalPathCandidate bestPortal,
            PortalPathCandidate chosen,
            int regionsConsidered,
            int bridgesConsidered,
            int candidateCount,
            Map<String, Integer> rejects
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("portal-candidates", 1000L)) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENCHANT_PORTAL_CANDIDATES: side={} queryDim={} query={} maxDistance={} playersInLevel={} regionsConsidered={} bridgesConsidered={} validPortalCandidates={} bestDirectPlayer={} bestDirectEffectiveDistance={} bestDirectPathDistance={} bestPortalPlayer={} bestPortalEffectiveDistance={} bestPortalPathDistance={} bestPortalViewId={} bestPortalApparent={} chosen={} rejectReason={}",
                side(level),
                level == null ? "-" : level.dimension().location(),
                queryPos == null ? "-" : formatVec(queryPos),
                formatDouble(maxDistance),
                level == null ? 0 : level.players().size(),
                regionsConsidered,
                bridgesConsidered,
                candidateCount,
                bestDirect == null || bestDirect.player() == null ? "-" : bestDirect.player().getGameProfile().getName(),
                bestDirect == null ? "-" : formatDouble(Math.sqrt(bestDirect.effectiveDistanceSqr())),
                bestDirect == null ? "-" : formatDouble(Math.sqrt(bestDirect.pathDistanceSqr())),
                bestPortal == null || bestPortal.player() == null ? "-" : bestPortal.player().getGameProfile().getName(),
                bestPortal == null ? "-" : formatDouble(Math.sqrt(bestPortal.effectiveDistanceSqr())),
                bestPortal == null ? "-" : formatDouble(Math.sqrt(bestPortal.pathDistanceSqr())),
                bestPortal == null || bestPortal.viewId() == null ? "-" : bestPortal.viewId(),
                bestPortal == null || bestPortal.apparentPosition() == null ? "-" : formatVec(bestPortal.apparentPosition()),
                chosen == null ? "none" : (chosen.throughPortal() ? "portal" : "direct"),
                compactRejects(rejects)
        );
    }

    private static void traceVisualBlockEntityPlayerQuery(
            Level level,
            Vec3 queryPos,
            double maxDistance,
            ResourceLocation viewId,
            Player player,
            @Nullable PortalFrameMapping mapping,
            double directDistanceSq,
            String reasonIfNone
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        if (!isEnchantingTableBookStack()) {
            return;
        }
        String key = "visual-be-player-query/" + viewId;
        long now = System.currentTimeMillis();
        Long previous = LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.get(key);
        if (previous != null && now - previous < 1000L) {
            return;
        }
        LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.put(key, now);
        Vec3 apparent = mapping == null ? null : mapping.anchor().position();
        double distanceSq = apparent == null ? Double.NaN : queryPos.distanceToSqr(apparent);
        boolean directAllowed = isDirectPlayerCandidate(level, player);
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_CROSS_DIM_VISUAL_BE_PLAYER_QUERY: viewId={} queryLogicalDimension={} queryLevelDimension={} playerActualDimension={} cameraDimension={} blockPos={} queryPos={} maxDistance={} directCandidateAllowed={} directRejectReason={} portalCandidateAllowed={} returnedPlayer={} apparentPlayerPos={} distance={} chosen={} reasonIfNone={}",
                viewId == null ? "-" : viewId,
                level == null ? "-" : logicalQueryDimension(level).location(),
                level == null ? "-" : level.dimension().location(),
                player == null || player.level() == null ? "-" : player.level().dimension().location(),
                level == null ? "-" : logicalQueryDimension(level).location(),
                queryPos == null ? "-" : BlockPos.containing(queryPos),
                queryPos == null ? "-" : formatVec(queryPos),
                formatDouble(maxDistance),
                directAllowed ? "yes" : "no",
                directAllowed ? "-" : "direct-player-wrong-dimension",
                mapping == null ? "no" : "yes",
                mapping == null ? "no" : (player == null ? "no" : "yes"),
                apparent == null ? "-" : formatVec(apparent),
                Double.isNaN(distanceSq) ? "-" : formatDouble(Math.sqrt(distanceSq)),
                mapping == null ? "none" : "portal",
                mapping == null ? reasonIfNone : "-"
        );
    }

    private static void traceVisualPlayerCandidate(
            VisualLevelContext visualContext,
            Level level,
            Vec3 queryPos,
            double maxDistance,
            String type,
            Player realPlayer,
            @Nullable Vec3 candidatePosition,
            double distanceSq,
            boolean inRange,
            boolean predicatePassed,
            String rejectReason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        if (!isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("visual-player-candidate/" + visualContext.viewId() + "/" + type + "/" + rejectReason, 250L)) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_VISUAL_PLAYER_CANDIDATES: viewId={} queryLogicalDimension={} queryPos={} maxDistance={} type={} realPlayerDimension={} candidatePosition={} distance={} inRange={} predicatePassed={} rejectReason={}",
                visualContext.viewId(),
                level == null ? "-" : logicalQueryDimension(level).location(),
                queryPos == null ? "-" : formatVec(queryPos),
                formatDouble(maxDistance),
                type,
                realPlayer == null || realPlayer.level() == null ? "-" : realPlayer.level().dimension().location(),
                candidatePosition == null ? "-" : formatVec(candidatePosition),
                Double.isNaN(distanceSq) || distanceSq == Double.MAX_VALUE ? "-" : formatDouble(Math.sqrt(distanceSq)),
                inRange ? "yes" : "no",
                predicatePassed ? "yes" : "no",
                rejectReason
        );
    }

    private static void traceVisualPlayerQueryResult(
            @Nullable VisualLevelContext visualContext,
            Level level,
            Vec3 queryPos,
            double maxDistance,
            @Nullable PortalPathCandidate chosen
    ) {
        if (visualContext == null || (!SkyesightDebugConfig.VERBOSE_PROXIMITY && !SkyesightDebugConfig.WATCH_DEBUG)) {
            return;
        }
        if (!isEnchantingTableBookStack()) {
            return;
        }
        if (!allowEnchantDiagnostic("visual-player-query-result/" + visualContext.viewId(), 250L)) {
            return;
        }
        String chosenType = "none";
        UUID realUuid = null;
        Vec3 position = null;
        double distanceSq = Double.NaN;
        if (chosen != null) {
            position = chosen.apparentPosition();
            distanceSq = chosen.effectiveDistanceSqr();
            if (chosen.player() instanceof PortalApparentQueryPlayer proxy) {
                chosenType = "proxy";
                realUuid = proxy.realPlayer().getUUID();
            } else {
                chosenType = "real";
                realUuid = chosen.player() == null ? null : chosen.player().getUUID();
            }
        }
        Skyesight.LOGGER.info(
                "[Skyesight] SKYESIGHT_VISUAL_PLAYER_QUERY_RESULT: viewId={} chosenType={} chosenPosition={} chosenRealPlayerUuid={} chosenDistance={} reason={} queryLogicalDimension={} queryPos={} maxDistance={}",
                visualContext.viewId(),
                chosenType,
                position == null ? "-" : formatVec(position),
                realUuid == null ? "-" : realUuid,
                Double.isNaN(distanceSq) ? "-" : formatDouble(Math.sqrt(distanceSq)),
                chosen == null ? "no-candidate" : chosen.reason(),
                level == null ? "-" : logicalQueryDimension(level).location(),
                queryPos == null ? "-" : formatVec(queryPos),
                formatDouble(maxDistance)
        );
    }

    private static void traceEnchantBridgeVariants(
            Vec3 queryPos,
            Player owner,
            PortalBridge bridge,
            PortalFrameMapping same,
            PortalFrameMapping flipForward,
            PortalFrameMapping flipRightForward,
            PortalFrameMapping chosen
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack() || bridge == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = "enchant-bridge-variants/" + bridge.sourceViewId() + "/" + bridge.direction();
        Long previous = LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.get(key);
        if (previous != null && now - previous < 1000L) {
            return;
        }
        LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.put(key, now);
        double directDistanceSq = directDistanceSqr(queryPos, owner);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENCHANT_BRIDGE_VARIANTS: viewId={} direction={} syntheticReverse={} sourceViewId={} query={} player={} directDistance={} variant=same apparent={} distance={} variant=flipForward apparent={} distance={} variant=flipRightForward apparent={} distance={} chosenVariant={} candidateValid=pending rejectReason=pending displayRight={} displayUp={} displayForward={} cameraRight={} cameraUp={} cameraForward={}",
                bridge.viewId(),
                bridge.direction(),
                bridge.syntheticReverse() ? "yes" : "no",
                bridge.sourceViewId(),
                queryPos == null ? "-" : formatVec(queryPos),
                owner == null ? "-" : formatVec(owner.position()),
                formatSqrtDistance(directDistanceSq),
                formatVec(same.anchor().position()),
                formatSqrtDistance(queryPos.distanceToSqr(same.anchor().position())),
                formatVec(flipForward.anchor().position()),
                formatSqrtDistance(queryPos.distanceToSqr(flipForward.anchor().position())),
                formatVec(flipRightForward.anchor().position()),
                formatSqrtDistance(queryPos.distanceToSqr(flipRightForward.anchor().position())),
                chosen.variant(),
                bridge.displayFrame() == null ? "-" : formatVec(bridge.displayFrame().right()),
                bridge.displayFrame() == null ? "-" : formatVec(bridge.displayFrame().up()),
                bridge.displayFrame() == null ? "-" : formatVec(bridge.displayFrame().forward()),
                bridge.cameraFrame() == null ? "-" : formatVec(bridge.cameraFrame().right()),
                bridge.cameraFrame() == null ? "-" : formatVec(bridge.cameraFrame().up()),
                bridge.cameraFrame() == null ? "-" : formatVec(bridge.cameraFrame().forward())
        );
    }

    private static void traceEnchantBridgeCandidate(
            Level queryLevel,
            Vec3 queryPos,
            double maxDistance,
            Player owner,
            Region region,
            @Nullable PortalBridge bridge,
            double directDistanceSq,
            @Nullable PortalFrameMapping mapping,
            boolean directionValid,
            boolean candidateValid,
            String rejectReason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY || !isEnchantingTableBookStack() || region == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = "enchant-bridge-candidate/" + region.viewId() + "/" + (bridge == null ? "none" : bridge.direction());
        Long previous = LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.get(key);
        if (previous != null && now - previous < 1000L) {
            return;
        }
        LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.put(key, now);

        double maxDistanceSq = maxDistance < 0.0D ? Double.MAX_VALUE : maxDistance * maxDistance;
        Vec3 apparent = mapping == null ? null : mapping.anchor().position();
        double resolvedPortalDistanceSq = queryPos != null && apparent != null
                ? queryPos.distanceToSqr(apparent)
                : Double.NaN;
        double pathDistance = queryPos == null || owner == null || bridge == null || bridge.displayCenter() == null || bridge.cameraCenter() == null
                ? Double.NaN
                : queryPos.distanceTo(bridge.displayCenter()) + owner.position().distanceTo(bridge.cameraCenter());
        double pathDistanceSq = Double.isNaN(pathDistance) ? Double.NaN : pathDistance * pathDistance;
        double distQueryToDisplay = queryPos == null || bridge == null || bridge.displayCenter() == null
                ? Double.NaN
                : queryPos.distanceTo(bridge.displayCenter());
        double distPlayerToDisplay = owner == null || bridge == null || bridge.displayCenter() == null
                ? Double.NaN
                : owner.position().distanceTo(bridge.displayCenter());
        double distPlayerToCamera = owner == null || bridge == null || bridge.cameraCenter() == null
                ? Double.NaN
                : owner.position().distanceTo(bridge.cameraCenter());

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENCHANT_BRIDGE_CANDIDATE: viewId={} direction={} syntheticReverse={} sourceViewId={} frameSource={} mapping={} query={} playerReal={} displayFrameCenter={} cameraFrameCenter={} cameraFrameCenterSource={} watchedRegionCenter={} usedWatchCenterAsFrame={} displayLocal={} cameraLocal={} apparent={} portalEffectiveDistance={} portalPathDistance={} reverseDistance={} basis={} candidateValid={} rejectReason={} maxDistance={} displayDim={} cameraDim={} directDistance={} directInRange={} distQueryToDisplay={} distPlayerToDisplay={} distPlayerToCamera={} directionValid={} portalInRange={}",
                region.viewId(),
                bridge == null ? "-" : bridge.direction(),
                bridge != null && bridge.syntheticReverse() ? "yes" : "no",
                bridge == null ? "-" : bridge.sourceViewId(),
                bridge == null ? "-" : bridge.source(),
                mapping == null ? "-" : mapping.variant(),
                queryPos == null ? "-" : formatVec(queryPos),
                owner == null ? "-" : formatVec(owner.position()),
                bridge == null ? "-" : formatNullableVec(bridge.displayCenter()),
                bridge == null ? "-" : formatNullableVec(bridge.cameraCenter()),
                bridge == null ? "-" : bridge.cameraFrameCenterSource(),
                bridge == null ? "-" : formatNullableVec(bridge.watchedRegionCenter()),
                bridge != null && bridge.usedWatchCenterAsFrame() ? "yes" : "no",
                mapping == null ? "-" : formatVec(mapping.displayLocal()),
                mapping == null ? "-" : formatVec(mapping.cameraLocal()),
                apparent == null ? "-" : formatVec(apparent),
                Double.isNaN(resolvedPortalDistanceSq) ? "-" : formatSqrtDistance(resolvedPortalDistanceSq),
                Double.isNaN(pathDistanceSq) ? "-" : formatSqrtDistance(pathDistanceSq),
                mapping == null ? "-" : formatSqrtDistance(mapping.reverseDistanceSqr()),
                mapping == null ? "-" : mapping.basis(),
                candidateValid ? "yes" : "no",
                rejectReason,
                formatDouble(maxDistance),
                bridge == null ? "-" : bridge.displayDimension().location(),
                bridge == null ? "-" : bridge.cameraDimension().location(),
                formatSqrtDistance(directDistanceSq),
                directDistanceSq <= maxDistanceSq ? "yes" : "no",
                formatDouble(distQueryToDisplay),
                formatDouble(distPlayerToDisplay),
                formatDouble(distPlayerToCamera),
                directionValid ? "yes" : "no",
                !Double.isNaN(resolvedPortalDistanceSq) && resolvedPortalDistanceSq <= maxDistanceSq ? "yes" : "no"
        );
    }

    @Nullable
    private static PortalFrameMapping diagnosticMapping(Vec3 queryPos, Player owner, PortalBridge bridge) {
        if (bridge == null) {
            return null;
        }
        return mapThroughBridge(queryPos, owner, bridge).orElse(null);
    }

    private static boolean passes(Predicate<Entity> predicate, Player player) {
        return player != null && (predicate == null || predicate.test(player));
    }

    private static boolean isDirectPlayerCandidate(Level queryLevel, Player player) {
        if (queryLevel == null || player == null || player.level() == null) {
            return false;
        }
        return logicalQueryDimension(queryLevel).equals(player.level().dimension());
    }

    private static boolean hasWrongDimensionPlayers(Level queryLevel) {
        for (Player player : queryLevel.players()) {
            if (!isDirectPlayerCandidate(queryLevel, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActivePortalBridgeForQueryLevel(Level queryLevel) {
        ResourceKey<Level> queryDimension = logicalQueryDimension(queryLevel);
        for (PortalBridge bridge : allDirectionalBridges()) {
            if (queryDimension.equals(bridge.displayDimension()) || queryDimension.equals(bridge.cameraDimension())) {
                return true;
            }
        }
        for (Region region : PortalRegionTracker.values()) {
            if (queryDimension.equals(region.dimension())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceKey<Level> logicalQueryDimension(Level queryLevel) {
        if (queryLevel == null) {
            return Level.OVERWORLD;
        }
        VisualLevelContext context = VISUAL_LEVELS.get(queryLevel);
        if (context != null && context.cameraDimension() != null) {
            return context.cameraDimension();
        }
        return queryLevel.dimension();
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static List<Region> snapshotPortalRegionsSafely(Level level) {
        try {
            return PortalRegionTracker.snapshotValues();
        } catch (RuntimeException exception) {
            long now = System.currentTimeMillis();
            if (now - lastRegionSnapshotFailureLogMillis >= 30_000L) {
                lastRegionSnapshotFailureLogMillis = now;
                Skyesight.LOGGER.warn(
                        "[Skyesight] Portal region snapshot failed during proximity query; falling back to direct-player lookup. dim={} error={}",
                        level == null ? "-" : level.dimension().location(),
                        exception.toString()
                );
            }
            return List.of();
        }
    }

    private static void logProximitySnapshotIfDue(Level level, int snapshotSize, int sourceMapSize) {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastProximitySnapshotLogMillis < 1000L) {
            return;
        }
        lastProximitySnapshotLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PATH_PROXIMITY_SNAPSHOT: queryDim={} snapshotSize={} sourceMapSize={} reason=nearest-player-query",
                level == null ? "-" : level.dimension().location(),
                snapshotSize,
                sourceMapSize
        );
    }

    private static void maybeLogSummary() {
        if (!SkyesightDebugConfig.VERBOSE_PROXIMITY) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSummaryMillis < 5000L) {
            return;
        }
        lastSummaryMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_PATH_PROXIMITY_SUMMARY: queries={} directWins={} portalWins={} noPlayer={} activePortalsConsidered={} multiHopSkipped={} directWrongDimRejected={} closestPortalSamples={}",
                queries,
                directWins,
                portalWins,
                noPlayer,
                activePortalsConsidered,
                multiHopSkipped,
                directWrongDimRejected,
                closestPortalSamples.length() == 0 ? "-" : closestPortalSamples
        );
        queries = 0;
        directWins = 0;
        portalWins = 0;
        noPlayer = 0;
        activePortalsConsidered = 0;
        multiHopSkipped = 0;
        directWrongDimRejected = 0;
        closestPortalSamples.setLength(0);
    }

    private static void appendSample(String sample) {
        if (sample == null || sample.isBlank() || closestPortalSamples.length() > 180) {
            return;
        }
        if (closestPortalSamples.length() > 0) {
            closestPortalSamples.append(';');
        }
        closestPortalSamples.append(sample);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatSqrtDistance(double distanceSq) {
        if (Double.isNaN(distanceSq) || distanceSq == Double.MAX_VALUE) {
            return "-";
        }
        return formatDouble(Math.sqrt(distanceSq));
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }

    private static String formatNullableVec(@Nullable Vec3 vec) {
        return vec == null ? "-" : formatVec(vec);
    }

    private static Vec3 normalizedOr(@Nullable Vec3 vector, Vec3 fallback) {
        if (vector == null || vector.lengthSqr() < 1.0E-9D) {
            return fallback;
        }
        return vector.normalize();
    }

    private static ApparentPlayerContext consumeContext(Player player) {
        ApparentPlayerContextState state = APPARENT_PLAYER_CONTEXT.get();
        if (state == null || state.remainingReads <= 0 || state.context.player() != player) {
            return null;
        }
        state.remainingReads--;
        ApparentPlayerContext context = state.context;
        if (state.remainingReads <= 0) {
            APPARENT_PLAYER_CONTEXT.remove();
        }
        return context;
    }

    private static String stackCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.startsWith("java.")
                    && !className.startsWith("com.skyeshade.skyesight.server.portal.PortalPathProximity")
                    && !className.startsWith("com.skyeshade.skyesight.mixin.")) {
                return className + "#" + element.getMethodName();
            }
        }
        return "unknown";
    }

    private static boolean allowEnchantDiagnostic(String key, long intervalMillis) {
        long now = System.currentTimeMillis();
        Long previous = LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.get(key);
        if (previous != null && now - previous < intervalMillis) {
            return false;
        }
        LAST_ENCHANT_DIAGNOSTIC_LOG_MILLIS.put(key, now);
        return true;
    }

    private static String side(Level level) {
        if (level == null) {
            return "unknown";
        }
        return level.isClientSide() ? "client" : "server";
    }

    private static String compactRejects(Map<String, Integer> rejects) {
        if (rejects == null || rejects.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        appendReject(builder, rejects, "wrong-dimension");
        appendReject(builder, rejects, "direct-player-wrong-dimension");
        appendReject(builder, rejects, "no-owner-player");
        appendReject(builder, rejects, "predicate-failed");
        appendReject(builder, rejects, "no-bridge-data");
        appendReject(builder, rejects, "no-frame-basis");
        appendReject(builder, rejects, "no-display-center");
        appendReject(builder, rejects, "no-camera-center");
        appendReject(builder, rejects, "query-not-near-display-side");
        appendReject(builder, rejects, "player-not-on-camera-side");
        appendReject(builder, rejects, "direct-out-of-range");
        appendReject(builder, rejects, "portal-distance-too-far");
        appendReject(builder, rejects, "translation-bridge-out-of-range");
        appendReject(builder, rejects, "portal-distance-not-actually-closer");
        appendReject(builder, rejects, "cross-dim-identical-coordinate-ghost-blocked");
        appendReject(builder, rejects, "no-valid-candidate");
        appendReject(builder, rejects, "client-data-missing");
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private static void appendReject(StringBuilder builder, Map<String, Integer> rejects, String key) {
        int count = rejects.getOrDefault(key, 0);
        if (count <= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(key).append('=').append(count);
    }

    private static final class ApparentPlayerContextState {
        private final ApparentPlayerContext context;
        private int remainingReads;

        private ApparentPlayerContextState(ApparentPlayerContext context, int remainingReads) {
            this.context = context;
            this.remainingReads = remainingReads;
        }
    }
}
