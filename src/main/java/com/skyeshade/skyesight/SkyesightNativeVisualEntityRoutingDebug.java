package com.skyeshade.skyesight;

import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightVisualEntity;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkyesightNativeVisualEntityRoutingDebug {
    private static final Map<ResourceLocation, Counters> COUNTERS_BY_VIEW = new LinkedHashMap<>();
    private static volatile boolean enabled;

    private SkyesightNativeVisualEntityRoutingDebug() {}

    public static boolean enabled() {
        return enabled;
    }

    public static String setEnabled(boolean value) {
        enabled = value;
        return "debugStatus=" + (value ? "enabled" : "disabled");
    }

    public static void activeCrossDimView(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).activeCrossDimViewsSeen++;
    }

    public static void serverTrackerUpdate(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverTrackerUpdateCalls++;
    }

    public static void serverTrackerSkipped(ResourceLocation viewId, String reason) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverTrackerSkippedByReason.merge(reason, 1, Integer::sum);
    }

    public static void serverEntityConsidered(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesConsidered++;
    }

    public static void serverEntitySkippedReceivingPlayer(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesSkippedReceivingPlayer++;
    }

    public static void serverEntitySkippedPlayer(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesSkippedPlayer++;
    }

    public static void serverEntitySkippedPartEntity(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesSkippedPartEntity++;
    }

    public static void serverEntitySkippedUnsupported(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesSkippedUnsupported++;
    }

    public static void serverEntityTracked(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverEntitiesTracked++;
    }

    public static void rawVanillaTrackingBlocked(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).rawVanillaTrackingBlocked++;
    }

    public static void rawVanillaTrackingAllowedSameDim(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).rawVanillaTrackingAllowedSameDim++;
    }

    public static void addPairingAttempt(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverAddPairingAttempts++;
    }

    public static void initialEntitySendAttempt(ResourceLocation viewId) {
        addPairingAttempt(viewId);
    }

    public static void addPairingSuccess(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).serverAddPairingSuccess++;
    }

    public static void initialEntitySendSuccess(ResourceLocation viewId) {
        addPairingSuccess(viewId);
    }

    public static void addPairingFailure(ResourceLocation viewId, String reason) {
        if (!enabled) {
            return;
        }
        Counters counters = counters(viewId);
        counters.serverAddPairingFailures++;
        counters.lastFailureReason = reason;
    }

    public static void wrappedPacket(ResourceLocation viewId, String kind) {
        if (!enabled) {
            return;
        }
        counters(viewId).wrappedPacketsByKind.merge(kind, 1, Integer::sum);
    }

    public static void unsupportedPacket(ResourceLocation viewId, String packetClass) {
        if (!enabled) {
            return;
        }
        counters(viewId).unsupportedPacketsByClass.merge(packetClass, 1, Integer::sum);
    }

    public static void encodeFailure(ResourceLocation viewId, String packetClass, String reason) {
        if (!enabled) {
            return;
        }
        Counters counters = counters(viewId);
        counters.unsupportedPacketsByClass.merge(packetClass + ":encode", 1, Integer::sum);
        counters.lastFailureReason = reason;
    }

    public static void payloadSent(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).payloadsSent++;
    }

    public static void payloadReceived(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).payloadsReceived++;
    }

    public static void clientDrop(ResourceLocation viewId, String reason) {
        if (!enabled) {
            return;
        }
        counters(viewId).clientDropsByReason.merge(reason, 1, Integer::sum);
    }

    public static void clientApplied(ResourceLocation viewId, String kind) {
        if (!enabled) {
            return;
        }
        counters(viewId).clientPacketsAppliedByKind.merge(kind, 1, Integer::sum);
    }

    public static void nativeEntityAdded(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).nativeEntitiesAdded++;
    }

    public static void nativeEntityUpdated(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).nativeEntitiesUpdated++;
    }

    public static void nativeEntityRemoved(ResourceLocation viewId) {
        if (!enabled) {
            return;
        }
        counters(viewId).nativeEntitiesRemoved++;
    }

    public static void entityCounts(ResourceLocation viewId, int nativeLevelEntityCount, int snapshotStoreEntityCount) {
        if (!enabled) {
            return;
        }
        Counters counters = counters(viewId);
        if (nativeLevelEntityCount >= 0) {
            counters.nativeLevelEntityCount = nativeLevelEntityCount;
        }
        if (snapshotStoreEntityCount >= 0) {
            counters.snapshotStoreEntityCount = snapshotStoreEntityCount;
        }
    }

    public static String status() {
        List<RegisteredPortalView> views = SkyesightPortalApi.getAllPortals();
        for (RegisteredPortalView view : views) {
            if (view != null && view.isCrossDimension()) {
                counters(view.id());
            }
        }

        if (COUNTERS_BY_VIEW.isEmpty()) {
            return "debugStatus=" + (enabled ? "enabled" : "disabled")
                    + " countersPaused=" + (!enabled)
                    + " populationEnabled=" + SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()
                    + " portalEntityPoolRenderingEnabled=" + PortalDirectStencilRenderer.portalEntityPoolRenderingEnabled()
                    + " renderSource=" + renderSource()
                    + " activeCrossDimViews=" + activeCrossDimViewCount(views)
                    + " firstFailureReason=" + firstFailureReason(null, null, 0, 0)
                    + " no cross-dim views/counters";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("debugStatus=")
                .append(enabled ? "enabled" : "disabled")
                .append(" countersPaused=")
                .append(!enabled)
                .append(" populationEnabled=")
                .append(SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled())
                .append(" populationDisabledForSession=")
                .append(SkyesightPortalEntityPoolConfig.populationDisabledForSession())
                .append(" portalEntityPoolRenderingEnabled=")
                .append(PortalDirectStencilRenderer.portalEntityPoolRenderingEnabled())
                .append(" renderSource=")
                .append(renderSource());
        for (Map.Entry<ResourceLocation, Counters> entry : COUNTERS_BY_VIEW.entrySet()) {
            RegisteredPortalView view = SkyesightPortalApi.getPortal(entry.getKey().toString());
            Counters counters = entry.getValue();
            int livePortalPoolCount = livePortalEntityPoolCount(view);
            int liveSnapshotCount = liveSnapshotStoreCount(entry.getKey());
            builder.append(" | viewId=").append(entry.getKey())
                    .append(" targetDim=").append(view == null ? "-" : view.target().dimension().location())
                    .append(" generation=").append(view == null ? "-" : view.generation())
                    .append(" populationEnabled=").append(SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled())
                    .append(" portalEntityPoolRenderingEnabled=").append(PortalDirectStencilRenderer.portalEntityPoolRenderingEnabled())
                    .append(" firstFailureReason=").append(firstFailureReason(view, counters, livePortalPoolCount, liveSnapshotCount))
                    .append(" activeViewsSeen=").append(counters.activeCrossDimViewsSeen)
                    .append(" serverTrackerUpdateCalls=").append(counters.serverTrackerUpdateCalls)
                    .append(" trackerSkipped=").append(formatMap(counters.serverTrackerSkippedByReason))
                    .append(" serverEntitiesConsidered=").append(counters.serverEntitiesConsidered)
                    .append(" serverEntitiesTracked=").append(counters.serverEntitiesTracked)
                    .append(" rawVanillaTrackingBlocked=").append(counters.rawVanillaTrackingBlocked)
                    .append(" rawVanillaTrackingAllowedSameDim=").append(counters.rawVanillaTrackingAllowedSameDim)
                    .append(" skippedPlayer=").append(counters.serverEntitiesSkippedPlayer)
                    .append(" skippedSelf=").append(counters.serverEntitiesSkippedReceivingPlayer)
                    .append(" skippedPart=").append(counters.serverEntitiesSkippedPartEntity)
                    .append(" skippedUnsupported=").append(counters.serverEntitiesSkippedUnsupported)
                    .append(" initialSends=").append(counters.serverAddPairingSuccess).append("/").append(counters.serverAddPairingAttempts)
                    .append(" initialSendFailures=").append(counters.serverAddPairingFailures)
                    .append(" wrappedPackets=").append(formatMap(counters.wrappedPacketsByKind))
                    .append(" unsupportedPackets=").append(formatMap(counters.unsupportedPacketsByClass))
                    .append(" payloadsSent=").append(counters.payloadsSent)
                    .append(" payloadsReceived=").append(counters.payloadsReceived)
                    .append(" clientApplied=").append(formatMap(counters.clientPacketsAppliedByKind))
                    .append(" drops=").append(formatMap(counters.clientDropsByReason))
                    .append(" portalEntityPoolEntities=").append(livePortalPoolCount)
                    .append(" snapshotStoreEntities=").append(liveSnapshotCount)
                    .append(" added=").append(counters.nativeEntitiesAdded)
                    .append(" updated=").append(counters.nativeEntitiesUpdated)
                    .append(" removed=").append(counters.nativeEntitiesRemoved)
                    .append(" lastFailure=").append(counters.lastFailureReason);
        }
        return builder.toString();
    }

    private static String renderSource() {
        return PortalDirectStencilRenderer.portalEntityPoolRenderingEnabled()
                ? "portal_entity_pool_if_available_else_snapshot"
                : "snapshot";
    }

    private static String firstFailureReason(
            RegisteredPortalView view,
            Counters counters,
            int livePortalPoolCount,
            int liveSnapshotCount
    ) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            return "population_config_disabled";
        }
        if (view != null && !view.active()) {
            return "view_inactive";
        }
        if (view != null && !view.isCrossDimension()) {
            return "view_not_cross_dim";
        }
        if (livePortalPoolCount > 0) {
            return "-";
        }
        if (!enabled) {
            return liveSnapshotCount > 0 ? "counters_paused_snapshot_active" : "counters_paused";
        }
        if (counters == null || counters.serverTrackerUpdateCalls == 0) {
            if (counters != null && counters.rawVanillaTrackingBlocked > 0) {
                return "cross_dim_raw_vanilla_tracking_blocked_tracker_not_invoked";
            }
            if (counters != null && !counters.serverTrackerSkippedByReason.isEmpty()) {
                return "tracker_skipped:" + counters.serverTrackerSkippedByReason.keySet().iterator().next();
            }
            return "tracker_not_invoked";
        }
        if (counters.activeCrossDimViewsSeen == 0) {
            return "server_watch_not_active_cross_dim";
        }
        if (counters.serverEntitiesConsidered == 0) {
            return "no_entities_in_watch_bounds";
        }
        if (counters.serverAddPairingAttempts == 0) {
            return "no_supported_entities_for_tracker";
        }
        if (counters.payloadsSent == 0) {
            return "no_wrapped_payloads_sent";
        }
        if (counters.payloadsReceived == 0) {
            return "payloads_not_received_or_client_debug_disabled";
        }
        if (counters.nativeLevelEntityCount == 0) {
            return "no_portal_pool_entities_added";
        }
        return "-";
    }

    private static int activeCrossDimViewCount(List<RegisteredPortalView> views) {
        int count = 0;
        for (RegisteredPortalView view : views) {
            if (view != null && view.active() && view.isCrossDimension()) {
                count++;
            }
        }
        return count;
    }

    private static int livePortalEntityPoolCount(RegisteredPortalView view) {
        if (view == null) {
            return 0;
        }
        return SkyesightPortalEntityPool.count(view.id(), view.target().dimension());
    }

    private static int liveSnapshotStoreCount(ResourceLocation viewId) {
        SkyesightVisualWorld world = SkyesightVisualWorldManager.get(viewId);
        if (world == null || world.isClosed()) {
            return 0;
        }
        int count = 0;
        for (SkyesightVisualEntity visualEntity : world.entityStore().entities()) {
            if (visualEntity != null && visualEntity.entity() != null && !visualEntity.entity().isRemoved()) {
                count++;
            }
        }
        return count;
    }

    private static Counters counters(ResourceLocation viewId) {
        return COUNTERS_BY_VIEW.computeIfAbsent(
                viewId == null ? ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "unknown") : viewId,
                ignored -> new Counters()
        );
    }

    private static String formatMap(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        int written = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (written++ > 0) {
                builder.append(",");
            }
            if (written > 8) {
                builder.append("+").append(map.size() - written + 1);
                break;
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.append("}").toString();
    }

    private static final class Counters {
        private int activeCrossDimViewsSeen;
        private int serverTrackerUpdateCalls;
        private final Map<String, Integer> serverTrackerSkippedByReason = new LinkedHashMap<>();
        private int serverEntitiesConsidered;
        private int serverEntitiesSkippedReceivingPlayer;
        private int serverEntitiesSkippedPlayer;
        private int serverEntitiesSkippedPartEntity;
        private int serverEntitiesSkippedUnsupported;
        private int serverEntitiesTracked;
        private int rawVanillaTrackingBlocked;
        private int rawVanillaTrackingAllowedSameDim;
        private int serverAddPairingAttempts;
        private int serverAddPairingSuccess;
        private int serverAddPairingFailures;
        private final Map<String, Integer> wrappedPacketsByKind = new LinkedHashMap<>();
        private final Map<String, Integer> unsupportedPacketsByClass = new LinkedHashMap<>();
        private int payloadsSent;
        private int payloadsReceived;
        private final Map<String, Integer> clientDropsByReason = new LinkedHashMap<>();
        private final Map<String, Integer> clientPacketsAppliedByKind = new LinkedHashMap<>();
        private int nativeEntitiesAdded;
        private int nativeEntitiesUpdated;
        private int nativeEntitiesRemoved;
        private int nativeLevelEntityCount;
        private int snapshotStoreEntityCount;
        private String lastFailureReason = "-";
    }
}
