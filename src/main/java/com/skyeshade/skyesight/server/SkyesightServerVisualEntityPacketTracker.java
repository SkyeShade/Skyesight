package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SkyesightServerVisualEntityPacketTracker {
    /*
     * Experimental portal-entity-pool bootstrap. This must never send raw
     * vanilla entity packets to the player connection; all packets are wrapped
     * in SkyesightVisualEntityVanillaPacketPayload for the isolated client pool.
     */
    private static final Map<WatchKey, VisualWatchState> WATCHES = new HashMap<>();
    private static boolean wrappedEntityPacketSendLogged;

    private SkyesightServerVisualEntityPacketTracker() {}

    public static void update(
            ServerPlayer receivingPlayer,
            SkyesightServerViewTracker.ViewWatch watch,
            ServerLevel level
    ) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            return;
        }
        SkyesightNativeVisualEntityRoutingDebug.serverTrackerUpdate(watch.viewId());

        RegisteredPortalView view = SkyesightPortalApi.getPortal(watch.viewId().toString());
        if (view == null || !view.active() || !view.isCrossDimension()
                || !view.target().dimension().equals(level.dimension())) {
            clearWatch(receivingPlayer, watch.viewId());
            return;
        }
        SkyesightNativeVisualEntityRoutingDebug.activeCrossDimView(watch.viewId());

        WatchKey key = new WatchKey(receivingPlayer.getUUID(), watch.viewId());
        VisualWatchState state = WATCHES.computeIfAbsent(key, ignored -> new VisualWatchState(watch.viewId()));
        state.viewGeneration = (int) view.generation();
        state.targetDimension = level.dimension();

        AABB area = watchArea(watch, level);
        Set<Integer> desiredIds = new HashSet<>();

        for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
            if (entity.isRemoved()) {
                continue;
            }
            SkyesightNativeVisualEntityRoutingDebug.serverEntityConsidered(watch.viewId());
            if (entity == receivingPlayer) {
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedReceivingPlayer(watch.viewId());
                continue;
            }
            if (entity instanceof ServerPlayer) {
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedPlayer(watch.viewId());
                continue;
            }
            if (state.shouldSkipNativeTracking(entity)) {
                continue;
            }
            desiredIds.add(entity.getId());
            state.track(level, receivingPlayer, entity);
        }

        state.untrackMissing(receivingPlayer, desiredIds);
    }

    public static void removeView(ResourceLocation viewId) {
        if (viewId == null) {
            return;
        }
        WATCHES.entrySet().removeIf(entry -> {
            if (!entry.getKey().viewId().equals(viewId)) {
                return false;
            }
            entry.getValue().clear(null);
            return true;
        });
    }

    public static void removePlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        WATCHES.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId().equals(playerId)) {
                return false;
            }
            entry.getValue().clear(player);
            return true;
        });
    }

    private static void clearWatch(ServerPlayer player, ResourceLocation viewId) {
        WatchKey key = new WatchKey(player.getUUID(), viewId);
        VisualWatchState state = WATCHES.remove(key);
        if (state != null) {
            state.clear(player);
        }
    }

    private static AABB watchArea(SkyesightServerViewTracker.ViewWatch watch, ServerLevel level) {
        int radiusBlocks = watch.radius() * 16 + 16;
        double centerX = watch.centerChunkX() * 16.0D + 8.0D;
        double centerZ = watch.centerChunkZ() * 16.0D + 8.0D;

        return new AABB(
                centerX - radiusBlocks,
                level.getMinBuildHeight(),
                centerZ - radiusBlocks,
                centerX + radiusBlocks,
                level.getMaxBuildHeight(),
                centerZ + radiusBlocks
        );
    }

    private record WatchKey(UUID playerId, ResourceLocation viewId) {}

    private static final class VisualWatchState {
        private final ResourceLocation viewId;
        private final Map<Integer, TrackedEntityState> trackedEntities = new HashMap<>();
        private final Set<Integer> unsupportedEntityIds = new HashSet<>();
        private int skippedPartEntityCount;
        private int unsupportedEntityCount;
        private int viewGeneration;
        private ResourceKey<net.minecraft.world.level.Level> targetDimension;

        private VisualWatchState(ResourceLocation viewId) {
            this.viewId = viewId;
        }

        private boolean shouldSkipNativeTracking(Entity entity) {
            if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                if (this.unsupportedEntityIds.add(entity.getId())) {
                    this.unsupportedEntityCount++;
                }
                this.skippedPartEntityCount++;
                PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "portal_entity_pool_tracker");
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedPartEntity(this.viewId);
                return true;
            }
            if (this.unsupportedEntityIds.contains(entity.getId())) {
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedUnsupported(this.viewId);
                return true;
            }
            return this.unsupportedEntityIds.contains(entity.getId());
        }

        private void track(ServerLevel level, ServerPlayer receivingPlayer, Entity entity) {
            TrackedEntityState trackedEntity = this.trackedEntities.get(entity.getId());

            if (trackedEntity == null) {
                SkyesightNativeVisualEntityRoutingDebug.initialEntitySendAttempt(this.viewId);
                this.sendWrapped(level, receivingPlayer, entity, new ClientboundAddEntityPacket(entity, 0, entity.blockPosition()));
                this.sendFullUpdate(level, receivingPlayer, entity);
                SkyesightNativeVisualEntityRoutingDebug.initialEntitySendSuccess(this.viewId);
                SkyesightNativeVisualEntityRoutingDebug.serverEntityTracked(this.viewId);
                this.trackedEntities.put(entity.getId(), new TrackedEntityState(entity.getUUID(), entity.getType().toShortString()));
                return;
            }

            this.sendFullUpdate(level, receivingPlayer, entity);
        }

        private void sendFullUpdate(ServerLevel level, ServerPlayer receivingPlayer, Entity entity) {
            this.sendWrapped(level, receivingPlayer, entity, new ClientboundTeleportEntityPacket(entity));
            this.sendWrapped(level, receivingPlayer, entity,
                    new ClientboundSetEntityDataPacket(entity.getId(), SkyesightEntityDataPacker.packAll(entity)));
            this.sendWrapped(level, receivingPlayer, entity, new ClientboundSetEntityMotionPacket(entity));
            if (entity instanceof LivingEntity livingEntity) {
                this.sendEquipment(level, receivingPlayer, livingEntity);
                this.sendWrapped(level, receivingPlayer, livingEntity, new ClientboundRotateHeadPacket(
                        livingEntity,
                        (byte) Mth.floor(livingEntity.getYHeadRot() * 256.0F / 360.0F)
                ));
            }
        }

        private void sendEquipment(ServerLevel level, ServerPlayer receivingPlayer, LivingEntity entity) {
            List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    equipment.add(Pair.of(slot, stack.copy()));
                }
            }
            if (!equipment.isEmpty()) {
                this.sendWrapped(level, receivingPlayer, entity,
                        new ClientboundSetEquipmentPacket(entity.getId(), equipment));
            }
        }

        private void sendWrapped(ServerLevel level, ServerPlayer receivingPlayer, Entity entity, Packet<?> packet) {
            SkyesightVisualEntityPacketWrapper.wrap(
                            this.viewId,
                            this.viewGeneration,
                            this.targetDimension,
                            level.registryAccess(),
                            packet
                    )
                    .ifPresent(payload -> {
                        if (!wrappedEntityPacketSendLogged) {
                            wrappedEntityPacketSendLogged = true;
                            Skyesight.LOGGER.warn(
                                    "[Skyesight] WRAPPED_PORTAL_ENTITY_PACKET_SEND: source=SkyesightServerVisualEntityPacketTracker packet={} entity={} type={} entityDim={} playerDim={} viewId={} rawSendAllowed=false",
                                    packet == null ? "null" : packet.getClass().getSimpleName(),
                                    entity.getId(),
                                    entity.getType().toShortString(),
                                    entity.level().dimension().location(),
                                    receivingPlayer.level().dimension().location(),
                                    this.viewId
                            );
                        }
                        SkyesightNativeVisualEntityRoutingDebug.payloadSent(this.viewId);
                        PacketDistributor.sendToPlayer(receivingPlayer, payload);
                    });
        }

        private void untrackMissing(ServerPlayer player, Set<Integer> desiredIds) {
            var iterator = this.trackedEntities.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Integer, TrackedEntityState> entry = iterator.next();

                if (desiredIds.contains(entry.getKey())) {
                    continue;
                }

                this.sendWrappedRemove(player, entry.getKey());
                iterator.remove();
            }
        }

        private void clear(ServerPlayer player) {
            if (player != null) {
                for (Integer entityId : this.trackedEntities.keySet()) {
                    this.sendWrappedRemove(player, entityId);
                }
            }
            this.trackedEntities.clear();
            this.unsupportedEntityIds.clear();
            this.skippedPartEntityCount = 0;
            this.unsupportedEntityCount = 0;
        }

        private void markUnsupported(int entityId) {
            if (this.unsupportedEntityIds.add(entityId)) {
                this.unsupportedEntityCount++;
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedUnsupported(this.viewId);
            }
        }

        private void sendWrappedRemove(ServerPlayer player, int entityId) {
            if (player == null || this.targetDimension == null) {
                return;
            }
            SkyesightVisualEntityPacketWrapper.wrap(
                            this.viewId,
                            this.viewGeneration,
                            this.targetDimension,
                            player.serverLevel().registryAccess(),
                            new ClientboundRemoveEntitiesPacket(new int[] {entityId})
                    )
                    .ifPresent(payload -> {
                        SkyesightNativeVisualEntityRoutingDebug.payloadSent(this.viewId);
                        PacketDistributor.sendToPlayer(player, payload);
                    });
        }
    }

    private record TrackedEntityState(UUID uuid, String typeName) {}
}
