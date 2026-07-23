package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import com.skyeshade.skyesight.mixin.server.chunk.ChunkMapEntityTrackerAccessor;
import com.skyeshade.skyesight.mixin.server.chunk.TrackedEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SecondaryRemoteEntityTracker {
    private static final boolean DEBUG_ENTITY_REPAIR_REMOVE_BEFORE_ADD = false;
    private static final int CLIENT_MISSING_REPAIR_COOLDOWN_TICKS = 40;
    private static final int CLIENT_MISSING_REPAIR_MAX_ATTEMPTS = 3;
    private static boolean rawSecondaryRemoteEntityTrackingLogged;
    private static boolean rawSecondaryRemoteEntityPacketSendLogged;

    private final Map<Integer, TrackedRemoteEntity> trackedEntities = new HashMap<>();
    private final Map<Integer, Integer> nextClientMissingRepairTick = new HashMap<>();
    private final Map<Integer, Integer> clientMissingRepairAttempts = new HashMap<>();
    private final Map<Integer, LifecycleState> lifecycleStates = new HashMap<>();
    private final Map<Integer, Integer> addPairingCountsById = new HashMap<>();
    private final Map<Integer, Integer> removePairingCountsById = new HashMap<>();
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private volatile Set<Integer> trackedIdSnapshot = Set.of();
    private volatile Set<Integer> forcedRepairIdSnapshot = Set.of();

    private volatile int trackedCount;
    private volatile int pairedThisUpdate;
    private volatile int unpairedThisUpdate;
    private volatile int vanillaTrackerCount;
    private volatile int customTrackerCount;
    private volatile int serverEntitiesFound;
    private volatile String serverEntityIdSummary = "-";
    private volatile String serverEntityPositionSummary = "-";
    private volatile boolean excludesLocalPlayer = true;
    private volatile int hasTrackedEntityCount;
    private volatile int wasAlreadySeenByCount;
    private volatile int addedToSeenByCount;
    private volatile int addPairingCalledCount;
    private volatile int sendChangesCalledCount;
    private volatile int removePairingCalledCount;
    private volatile int seenByContainsAfterUpdateCount;
    private volatile int clientMissingForcedPairCount;
    private volatile int removeBeforeRepairCount;
    private volatile int forcedAddPairCount;
    private volatile boolean repairRemoveBeforeAddEnabled;
    private volatile boolean removeOnlyOutOfRange = true;
    private volatile String lifecycleSummary = "-";
    private volatile String aabbSummary = "n/a";
    private volatile String lastException = "";

    public void update(Minecraft minecraft, SecondaryViewFrame frame, Vec3 center, double radius) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            this.queueClear(minecraft);
            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();
        UUID playerId = minecraft.player.getUUID();
        var dimension = minecraft.level.dimension();
        Set<Integer> clientEntityIds = collectClientEntityIds(minecraft);
        AABB bounds = new AABB(
                center.x() - radius,
                center.y() - radius,
                center.z() - radius,
                center.x() + radius,
                center.y() + radius,
                center.z() + radius
        );
        this.aabbSummary = summarizeAabb(center, radius);

        if (!this.updateQueued.compareAndSet(false, true)) {
            return;
        }

        server.execute(() -> {
            try {
                ServerLevel serverLevel = server.getLevel(dimension);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);

                if (serverLevel == null || player == null || player.isRemoved()) {
                    this.clearOnServer(player);
                    return;
                }

                this.updateOnServer(serverLevel, player, bounds, clientEntityIds);
            } catch (RuntimeException exception) {
                this.lastException = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            } finally {
                this.updateQueued.set(false);
            }
        });
    }

    public void queueClear(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || !this.updateQueued.compareAndSet(false, true)) {
            return;
        }

        UUID playerId = minecraft.player == null ? null : minecraft.player.getUUID();

        server.execute(() -> {
            try {
                ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
                this.clearOnServer(player);
            } finally {
                this.updateQueued.set(false);
            }
        });
    }

    private void updateOnServer(
            ServerLevel serverLevel,
            ServerPlayer player,
            AABB bounds,
            Set<Integer> clientEntityIds
    ) {
        Set<Integer> desiredIds = new HashSet<>();
        Set<Integer> forcedRepairIds = new HashSet<>();
        int paired = 0;
        int vanilla = 0;
        int custom = 0;
        int found = 0;
        int hasTracked = 0;
        int alreadySeen = 0;
        int addedToSeenBy = 0;
        int pairingCalled = 0;
        int changesCalled = 0;
        int removeCalled = 0;
        int containsAfter = 0;
        int clientMissingForcedPair = 0;
        int removeBeforeRepair = 0;
        int forcedAddPair = 0;
        StringBuilder idSummary = new StringBuilder();
        StringBuilder positionSummary = new StringBuilder();
        int currentTick = serverLevel.getServer().getTickCount();

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity == player || entity.isRemoved() || !bounds.intersects(entity.getBoundingBoxForCulling())) {
                continue;
            }

            found++;
            desiredIds.add(entity.getId());
            this.publishTrackedIds(desiredIds);
            appendEntitySummary(idSummary, entity.getId() + ":" + entity.getType().toShortString());
            appendEntitySummary(positionSummary, String.format(
                    Locale.ROOT,
                    "%d@%.1f,%.1f,%.1f",
                    entity.getId(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
            ));

            TrackedRemoteEntity trackedEntity = this.trackedEntities.get(entity.getId());
            boolean clientMissing = !clientEntityIds.contains(entity.getId());
            int repairAttempts = this.clientMissingRepairAttempts.getOrDefault(entity.getId(), 0);
            boolean repairDue = clientMissing
                    && repairAttempts < CLIENT_MISSING_REPAIR_MAX_ATTEMPTS
                    && this.nextClientMissingRepairTick.getOrDefault(entity.getId(), 0) <= currentTick;
            this.lifecycleStates.put(
                    entity.getId(),
                    clientMissing ? LifecycleState.WANTED : LifecycleState.CLIENT_PRESENT
            );

            if (trackedEntity == null) {
                trackedEntity = this.createTrackedEntity(serverLevel, player, entity);
                this.trackedEntities.put(entity.getId(), trackedEntity);
                TrackingOperationResult result = trackedEntity.addPairing(player);
                this.lifecycleStates.put(entity.getId(), LifecycleState.SPAWN_SENT);
                increment(this.addPairingCountsById, entity.getId());
                hasTracked += result.hasTrackedEntity() ? 1 : 0;
                alreadySeen += result.wasAlreadySeenByLocalPlayer() ? 1 : 0;
                addedToSeenBy += result.addedToSeenBy() ? 1 : 0;
                pairingCalled += result.addPairingCalled() ? 1 : 0;
                containsAfter += result.seenByContainsAfterUpdate() ? 1 : 0;
                paired++;
            } else {
                TrackingOperationResult result;

                if (repairDue) {
                    result = trackedEntity.repairMissingClientPairing(
                            player,
                            DEBUG_ENTITY_REPAIR_REMOVE_BEFORE_ADD
                    );
                    this.nextClientMissingRepairTick.put(
                            entity.getId(),
                            currentTick + CLIENT_MISSING_REPAIR_COOLDOWN_TICKS
                    );
                    this.clientMissingRepairAttempts.put(entity.getId(), repairAttempts + 1);
                    this.lifecycleStates.put(entity.getId(), LifecycleState.SPAWN_SENT);
                    increment(this.addPairingCountsById, entity.getId());
                    if (result.removePairingCalled()) {
                        increment(this.removePairingCountsById, entity.getId());
                    }
                    forcedRepairIds.add(entity.getId());
                    clientMissingForcedPair++;
                    removeBeforeRepair += result.removePairingCalled() ? 1 : 0;
                    forcedAddPair += result.addPairingCalled() ? 1 : 0;
                } else {
                    result = trackedEntity.sendChangesAndEnsureBroadcasts(player);
                    if (!clientMissing) {
                        this.clientMissingRepairAttempts.remove(entity.getId());
                        this.nextClientMissingRepairTick.remove(entity.getId());
                    }
                }

                hasTracked += result.hasTrackedEntity() ? 1 : 0;
                alreadySeen += result.wasAlreadySeenByLocalPlayer() ? 1 : 0;
                addedToSeenBy += result.addedToSeenBy() ? 1 : 0;
                pairingCalled += result.addPairingCalled() ? 1 : 0;
                changesCalled += result.sendChangesCalled() ? 1 : 0;
                removeCalled += result.removePairingCalled() ? 1 : 0;
                containsAfter += result.seenByContainsAfterUpdate() ? 1 : 0;
            }

            if (trackedEntity.usesVanillaTracker()) {
                vanilla++;
            } else {
                custom++;
            }
        }

        int unpaired = 0;
        var iterator = this.trackedEntities.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedRemoteEntity> entry = iterator.next();

            if (desiredIds.contains(entry.getKey())) {
                continue;
            }

            entry.getValue().removePairing(player);
            iterator.remove();
            this.nextClientMissingRepairTick.remove(entry.getKey());
            this.clientMissingRepairAttempts.remove(entry.getKey());
            this.lifecycleStates.put(entry.getKey(), LifecycleState.REMOVE_SENT);
            increment(this.removePairingCountsById, entry.getKey());
            unpaired++;
            removeCalled++;
        }

        this.trackedCount = this.trackedEntities.size();
        this.publishTrackedIds(this.trackedEntities.keySet());
        this.forcedRepairIdSnapshot = Set.copyOf(forcedRepairIds);
        this.pairedThisUpdate = paired;
        this.unpairedThisUpdate = unpaired;
        this.vanillaTrackerCount = vanilla;
        this.customTrackerCount = custom;
        this.serverEntitiesFound = found;
        this.serverEntityIdSummary = idSummary.isEmpty() ? "-" : idSummary.toString();
        this.serverEntityPositionSummary = positionSummary.isEmpty() ? "-" : positionSummary.toString();
        this.excludesLocalPlayer = true;
        this.hasTrackedEntityCount = hasTracked;
        this.wasAlreadySeenByCount = alreadySeen;
        this.addedToSeenByCount = addedToSeenBy;
        this.addPairingCalledCount = pairingCalled;
        this.sendChangesCalledCount = changesCalled;
        this.removePairingCalledCount = removeCalled;
        this.seenByContainsAfterUpdateCount = containsAfter;
        this.clientMissingForcedPairCount = clientMissingForcedPair;
        this.removeBeforeRepairCount = removeBeforeRepair;
        this.forcedAddPairCount = forcedAddPair;
        this.repairRemoveBeforeAddEnabled = DEBUG_ENTITY_REPAIR_REMOVE_BEFORE_ADD;
        this.removeOnlyOutOfRange = true;
        this.lifecycleSummary = buildLifecycleSummary(this.lifecycleStates, this.addPairingCountsById, this.removePairingCountsById);
        this.lastException = "";
    }

    private TrackedRemoteEntity createTrackedEntity(
            ServerLevel serverLevel,
            ServerPlayer player,
            Entity entity
    ) {
        Object vanillaTrackedEntity = findVanillaTrackedEntity(serverLevel, entity);

        if (vanillaTrackedEntity instanceof TrackedEntityAccessor accessor) {
            return new TrackedRemoteEntity(accessor, null, entity.getId(), entity);
        }

        ServerEntity customServerEntity = new ServerEntity(
                serverLevel,
                entity,
                1,
                true,
                packet -> this.send(player, packet)
        );

        return new TrackedRemoteEntity(null, customServerEntity, entity.getId(), entity);
    }

    private static Object findVanillaTrackedEntity(ServerLevel serverLevel, Entity entity) {
        ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
        return ((ChunkMapEntityTrackerAccessor) chunkMap)
                .skyesight$getEntityMap()
                .get(entity.getId());
    }

    private void clearOnServer(ServerPlayer player) {
        if (player != null) {
            for (TrackedRemoteEntity trackedEntity : this.trackedEntities.values()) {
                trackedEntity.removePairing(player);
            }
        }

        int unpaired = this.trackedEntities.size();
        this.trackedEntities.clear();
        this.nextClientMissingRepairTick.clear();
        this.clientMissingRepairAttempts.clear();
        this.lifecycleStates.clear();
        this.addPairingCountsById.clear();
        this.removePairingCountsById.clear();
        this.trackedCount = 0;
        this.trackedIdSnapshot = Set.of();
        this.forcedRepairIdSnapshot = Set.of();
        this.pairedThisUpdate = 0;
        this.unpairedThisUpdate = unpaired;
        this.vanillaTrackerCount = 0;
        this.customTrackerCount = 0;
        this.serverEntitiesFound = 0;
        this.serverEntityIdSummary = "-";
        this.serverEntityPositionSummary = "-";
        this.hasTrackedEntityCount = 0;
        this.wasAlreadySeenByCount = 0;
        this.addedToSeenByCount = 0;
        this.addPairingCalledCount = 0;
        this.sendChangesCalledCount = 0;
        this.removePairingCalledCount = unpaired;
        this.seenByContainsAfterUpdateCount = 0;
        this.clientMissingForcedPairCount = 0;
        this.removeBeforeRepairCount = 0;
        this.forcedAddPairCount = 0;
        this.repairRemoveBeforeAddEnabled = DEBUG_ENTITY_REPAIR_REMOVE_BEFORE_ADD;
        this.removeOnlyOutOfRange = true;
        this.lifecycleSummary = "-";
    }

    private void send(ServerPlayer player, Packet<?> packet) {
        if (com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug.enabled()
                && !rawSecondaryRemoteEntityPacketSendLogged) {
            rawSecondaryRemoteEntityPacketSendLogged = true;
            Skyesight.LOGGER.warn(
                    "[Skyesight] RAW_VANILLA_ENTITY_PACKET_SEND: source=SecondaryRemoteEntityTracker packet={} playerDim={} rawSendAllowed=true",
                    packet == null ? "null" : packet.getClass().getSimpleName(),
                    player == null ? "null" : player.level().dimension().location()
            );
        }
        player.connection.send(packet);
    }

    private static void logRawEntityTracking(String operation, ServerPlayer player, Entity entity) {
        if (!com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug.enabled()
                || rawSecondaryRemoteEntityTrackingLogged) {
            return;
        }
        rawSecondaryRemoteEntityTrackingLogged = true;
        Skyesight.LOGGER.warn(
                "[Skyesight] RAW_VANILLA_ENTITY_TRACKING_ALLOWED: source=SecondaryRemoteEntityTracker operation={} entity={} type={} entityDim={} playerDim={} rawSendAllowed=true",
                operation,
                entity == null ? -1 : entity.getId(),
                entity == null ? "null" : entity.getType().toShortString(),
                entity == null ? "null" : entity.level().dimension().location(),
                player == null ? "null" : player.level().dimension().location()
        );
    }



    private void publishTrackedIds(Set<Integer> entityIds) {
        this.trackedIdSnapshot = Set.copyOf(entityIds);
    }

    private static Set<Integer> collectClientEntityIds(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Set.of();
        }

        Set<Integer> entityIds = new HashSet<>();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            entityIds.add(entity.getId());
        }

        return entityIds;
    }

    private static void appendEntitySummary(StringBuilder summary, String value) {
        if (summary.length() > 80) {
            return;
        }

        if (!summary.isEmpty()) {
            summary.append(" ");
        }

        summary.append(value);
    }

    private static String summarizeAabb(Vec3 center, double radius) {
        return String.format(
                Locale.ROOT,
                "%.1f,%.1f,%.1f r%.1f",
                center.x(),
                center.y(),
                center.z(),
                radius
        );
    }

    private static void increment(Map<Integer, Integer> counts, int entityId) {
        counts.merge(entityId, 1, Integer::sum);
    }

    private static String buildLifecycleSummary(
            Map<Integer, LifecycleState> states,
            Map<Integer, Integer> addCounts,
            Map<Integer, Integer> removeCounts
    ) {
        if (states.isEmpty()) {
            return "-";
        }

        StringBuilder summary = new StringBuilder();
        int written = 0;

        for (Map.Entry<Integer, LifecycleState> entry : states.entrySet()) {
            if (written >= 5) {
                summary.append(" +").append(states.size() - written);
                break;
            }

            if (!summary.isEmpty()) {
                summary.append(" ");
            }

            int entityId = entry.getKey();
            summary.append(entityId)
                    .append(":")
                    .append(entry.getValue().shortName)
                    .append(" a")
                    .append(addCounts.getOrDefault(entityId, 0))
                    .append(" r")
                    .append(removeCounts.getOrDefault(entityId, 0));
            written++;
        }

        return summary.toString();
    }

    private enum LifecycleState {
        WANTED("W"),
        SPAWN_SENT("S"),
        CLIENT_PRESENT("C"),
        REMOVE_SENT("R");

        private final String shortName;

        LifecycleState(String shortName) {
            this.shortName = shortName;
        }
    }

    public record TrackingOperationResult(
            boolean hasTrackedEntity,
            boolean wasAlreadySeenByLocalPlayer,
            boolean addedToSeenBy,
            boolean addPairingCalled,
            boolean sendChangesCalled,
            boolean removePairingCalled,
            boolean seenByContainsAfterUpdate
    ) {}

    private record TrackedRemoteEntity(
            TrackedEntityAccessor vanillaTrackedEntity,
            ServerEntity customServerEntity,
            int entityId,
            Entity entity
    ) {
        private TrackingOperationResult addPairing(ServerPlayer player) {
            boolean wasAlreadySeenBy = this.seenByContains(player);

            logRawEntityTracking("addPairing", player, this.entity);
            this.serverEntity().addPairing(player);

            boolean addedToSeenBy = this.ensureReceivesBroadcasts(player);
            return new TrackingOperationResult(
                    this.vanillaTrackedEntity != null,
                    wasAlreadySeenBy,
                    addedToSeenBy,
                    true,
                    false,
                    false,
                    this.seenByContains(player)
            );
        }

        private void removePairing(ServerPlayer player) {
            logRawEntityTracking("removePairing", player, this.entity);
            if (this.vanillaTrackedEntity != null) {
                this.vanillaTrackedEntity.skyesight$removePlayer(player);
            } else {
                this.customServerEntity.removePairing(player);
            }
        }

        private TrackingOperationResult sendChangesAndEnsureBroadcasts(ServerPlayer player) {
            boolean wasAlreadySeenBy = this.seenByContains(player);

            logRawEntityTracking("sendChanges", player, this.entity);
            this.serverEntity().sendChanges();

            boolean addedToSeenBy = this.ensureReceivesBroadcasts(player);
            return new TrackingOperationResult(
                    this.vanillaTrackedEntity != null,
                    wasAlreadySeenBy,
                    addedToSeenBy,
                    false,
                    true,
                    false,
                    this.seenByContains(player)
            );
        }

        private TrackingOperationResult repairMissingClientPairing(
                ServerPlayer player,
                boolean removeBeforeAdd
        ) {
            boolean wasAlreadySeenBy = this.seenByContains(player);
            boolean removed = false;

            if (removeBeforeAdd) {
                if (this.vanillaTrackedEntity != null) {
                    this.vanillaTrackedEntity.skyesight$removePlayer(player);
                } else {
                    this.customServerEntity.removePairing(player);
                }
                removed = true;
            }

            logRawEntityTracking("repairMissingClientPairing", player, this.entity);
            this.serverEntity().addPairing(player);

            this.serverEntity().sendChanges();

            boolean addedToSeenBy = this.ensureReceivesBroadcasts(player);
            return new TrackingOperationResult(
                    this.vanillaTrackedEntity != null,
                    wasAlreadySeenBy,
                    addedToSeenBy,
                    true,
                    true,
                    removed,
                    this.seenByContains(player)
            );
        }

        private boolean ensureReceivesBroadcasts(ServerPlayer player) {
            if (this.vanillaTrackedEntity != null) {
                Set<ServerPlayerConnection> seenBy = this.vanillaTrackedEntity.skyesight$getSeenBy();
                return seenBy.add(player.connection);
            }

            return false;
        }

        private boolean seenByContains(ServerPlayer player) {
            return this.vanillaTrackedEntity != null
                    && this.vanillaTrackedEntity.skyesight$getSeenBy().contains(player.connection);
        }

        private boolean usesVanillaTracker() {
            return this.vanillaTrackedEntity != null;
        }

        private ServerEntity serverEntity() {
            return this.vanillaTrackedEntity == null
                    ? this.customServerEntity
                    : this.vanillaTrackedEntity.skyesight$getServerEntity();
        }
    }
}
