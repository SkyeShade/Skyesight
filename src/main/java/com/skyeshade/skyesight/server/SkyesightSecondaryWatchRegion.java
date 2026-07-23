package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SkyesightSecondaryWatchRegion {
    private static final ResourceLocation DEFAULT_REGION_ID =
            ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "debug_secondary");

    private static final Map<UUID, Map<ResourceLocation, WatchRegion>> REGIONS = new HashMap<>();

    private static volatile boolean active;
    private static volatile int activeRegionCount;
    private static volatile String dimensionSummary = "n/a";
    private static volatile String centerSummary = "n/a";
    private static volatile String radiusSummary = "n/a";
    private static volatile int skySightTrackDecisions;
    private static volatile int vanillaBypassChecks;
    private static volatile int unionTrackDecisions;
    private static volatile int untrackedChecks;
    private static volatile String lastDecisionSummary = "n/a";
    private static boolean crossDimRawTrackingBlockedLogged;
    private static boolean sameDimRawTrackingAllowedLogged;

    private SkyesightSecondaryWatchRegion() {}

    public static void update(ServerPlayer player, ResourceKey<Level> dimension, Vec3 center, double radius) {
        setRegion(player, DEFAULT_REGION_ID, dimension, center, radius);
    }

    public static void setRegion(
            ServerPlayer player,
            ResourceLocation regionId,
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius
    ) {
        if (player == null || regionId == null || dimension == null || center == null) {
            return;
        }

        REGIONS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(regionId, new WatchRegion(regionId, dimension, center, radius));
        updateSummaries();
    }

    public static void removeRegion(ServerPlayer player, ResourceLocation regionId) {
        if (player == null || regionId == null) {
            return;
        }

        Map<ResourceLocation, WatchRegion> playerRegions = REGIONS.get(player.getUUID());

        if (playerRegions == null) {
            return;
        }

        playerRegions.remove(regionId);

        if (playerRegions.isEmpty()) {
            REGIONS.remove(player.getUUID());
        }

        updateSummaries();
    }

    public static void removeRegion(ResourceLocation regionId) {
        if (regionId == null) {
            return;
        }
        REGIONS.values().forEach(playerRegions -> playerRegions.remove(regionId));
        REGIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        updateSummaries();
    }

    public static void clear(ServerPlayer player) {
        clearAllRegions(player);
    }

    public static void clearAllRegions(ServerPlayer player) {
        if (player != null) {
            REGIONS.remove(player.getUUID());
        }

        updateSummaries();
    }

    public static boolean shouldTrackAnyRegion(ServerPlayer player, Entity entity) {
        if (player == null || entity == null || entity == player || entity.isRemoved()) {
            untrackedChecks++;
            lastDecisionSummary = "invalid";
            return false;
        }

        Map<ResourceLocation, WatchRegion> playerRegions = REGIONS.get(player.getUUID());

        if (playerRegions == null || playerRegions.isEmpty()) {
            untrackedChecks++;
            lastDecisionSummary = entity.getId() + ":" + entity.getType().toShortString() + " no-region";
            return false;
        }

        for (WatchRegion region : playerRegions.values()) {
            if (!entity.level().dimension().equals(region.dimension())) {
                continue;
            }

            if (!region.bounds().intersects(entity.getBoundingBoxForCulling())) {
                continue;
            }

            if (!entity.level().dimension().equals(player.level().dimension())
                    || (SkyesightPortalEntityPoolConfig.crossDimSecondaryWatchRegionRawTrackingDisabled()
                    && !region.dimension().equals(player.level().dimension()))) {
                SkyesightNativeVisualEntityRoutingDebug.rawVanillaTrackingBlocked(region.id());
                if (SkyesightNativeVisualEntityRoutingDebug.enabled() && !crossDimRawTrackingBlockedLogged) {
                    crossDimRawTrackingBlockedLogged = true;
                    Skyesight.LOGGER.warn(
                            "[Skyesight] RAW_VANILLA_ENTITY_TRACKING_BLOCKED: source=SkyesightSecondaryWatchRegionMixin entity={} type={} entityDim={} playerDim={} region={} regionDim={} rawSendAllowed=false",
                            entity.getId(),
                            entity.getType().toShortString(),
                            entity.level().dimension().location(),
                            player.level().dimension().location(),
                            region.id(),
                            region.dimension().location()
                    );
                }
                untrackedChecks++;
                lastDecisionSummary = entity.getId()
                        + ":"
                        + entity.getType().toShortString()
                        + " cross-dim-raw-vanilla-tracking-blocked region="
                        + region.id();
                return false;
            }

            if (!entity.broadcastToPlayer(player)) {
                untrackedChecks++;
                lastDecisionSummary = entity.getId() + ":" + entity.getType().toShortString() + " broadcast=false";
                return false;
            }

            SkyesightNativeVisualEntityRoutingDebug.rawVanillaTrackingAllowedSameDim(region.id());
            if (SkyesightNativeVisualEntityRoutingDebug.enabled() && !sameDimRawTrackingAllowedLogged) {
                sameDimRawTrackingAllowedLogged = true;
                Skyesight.LOGGER.warn(
                        "[Skyesight] RAW_VANILLA_ENTITY_TRACKING_ALLOWED: source=SkyesightSecondaryWatchRegionMixin entity={} type={} entityDim={} playerDim={} region={} regionDim={} rawSendAllowed=true",
                        entity.getId(),
                        entity.getType().toShortString(),
                        entity.level().dimension().location(),
                        player.level().dimension().location(),
                        region.id(),
                        region.dimension().location()
                );
            }
            skySightTrackDecisions++;
            unionTrackDecisions++;
            lastDecisionSummary = entity.getId()
                    + ":"
                    + entity.getType().toShortString()
                    + " secondary=true region="
                    + region.id();
            return true;
        }

        untrackedChecks++;
        lastDecisionSummary = entity.getId() + ":" + entity.getType().toShortString() + " outside";
        return false;
    }

    public static boolean shouldTrack(ServerPlayer player, Entity entity) {
        return shouldTrackAnyRegion(player, entity);
    }

    public static boolean active() {
        return active;
    }

    public static int activeRegionCount() {
        return activeRegionCount;
    }

    public static String dimensionSummary() {
        return dimensionSummary;
    }

    public static String centerSummary() {
        return centerSummary;
    }

    public static Vec3 center(UUID playerId, ResourceLocation regionId, ResourceKey<Level> dimension) {
        if (playerId == null || regionId == null || dimension == null) {
            return null;
        }
        Map<ResourceLocation, WatchRegion> playerRegions = REGIONS.get(playerId);
        if (playerRegions == null) {
            return null;
        }
        WatchRegion region = playerRegions.get(regionId);
        if (region == null || !dimension.equals(region.dimension())) {
            return null;
        }
        return region.center();
    }

    public static String radiusSummary() {
        return radiusSummary;
    }

    public static double regionRadius(ResourceLocation regionId) {
        if (regionId == null) {
            return -1.0D;
        }

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            WatchRegion region = playerRegions.get(regionId);

            if (region != null) {
                return region.radius();
            }
        }

        return -1.0D;
    }

    public static int skySightTrackDecisions() {
        return skySightTrackDecisions;
    }

    public static int vanillaBypassChecks() {
        return vanillaBypassChecks;
    }

    public static int unionTrackDecisions() {
        return unionTrackDecisions;
    }

    public static int untrackedChecks() {
        return untrackedChecks;
    }

    public static void recordVanillaTracked() {
        vanillaBypassChecks++;
        unionTrackDecisions++;
        lastDecisionSummary = "vanilla=true";
    }

    public static String lastDecisionSummary() {
        return lastDecisionSummary;
    }

    private static void updateSummaries() {
        activeRegionCount = 0;
        WatchRegion sample = null;

        for (Map<ResourceLocation, WatchRegion> playerRegions : REGIONS.values()) {
            activeRegionCount += playerRegions.size();

            if (sample == null && !playerRegions.isEmpty()) {
                sample = playerRegions.values().iterator().next();
            }
        }

        active = activeRegionCount > 0;

        if (sample == null) {
            dimensionSummary = "n/a";
            centerSummary = "n/a";
            radiusSummary = "n/a";
            skySightTrackDecisions = 0;
            vanillaBypassChecks = 0;
            unionTrackDecisions = 0;
            untrackedChecks = 0;
            lastDecisionSummary = "n/a";
            return;
        }

        dimensionSummary = sample.dimension().location().toString();
        centerSummary = formatCenter(sample.center());
        radiusSummary = String.format(Locale.ROOT, "%.1f", sample.radius());
    }

    private static String formatCenter(Vec3 center) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", center.x(), center.y(), center.z());
    }

    private record WatchRegion(
            ResourceLocation id,
            ResourceKey<Level> dimension,
            Vec3 center,
            double radius
    ) {
        private AABB bounds() {
            return new AABB(
                    this.center.x() - this.radius,
                    this.center.y() - this.radius,
                    this.center.z() - this.radius,
                    this.center.x() + this.radius,
                    this.center.y() + this.radius,
                    this.center.z() + this.radius
            );
        }
    }
}
