package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPoolLeakTripwire;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import com.skyeshade.skyesight.mixin.common.ClientboundEntityEventPacketAccessor;
import com.skyeshade.skyesight.mixin.common.ClientboundRotateHeadPacketAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class SkyesightPortalEntityPacketApplier {
    /*
     * Experimental client-side applier for the isolated portal entity pool.
     * It must not call ClientPacketListener handlers or insert entities into
     * minecraft.level / ClientLevel entity storage.
     */
    private SkyesightPortalEntityPacketApplier() {}

    public static void handle(SkyesightVisualEntityVanillaPacketPayload payload) {
        ResourceLocation viewId = payload == null ? null : payload.viewId();
        if (payload != null) {
            SkyesightNativeVisualEntityRoutingDebug.payloadReceived(payload.viewId());
        }
        if (payload == null || payload.viewId() == null || payload.targetDimension() == null || payload.kind() == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(viewId, "invalid_payload");
            return;
        }
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "population_disabled");
            return;
        }

        RegisteredPortalView view = SkyesightPortalApi.getPortal(payload.viewId().toString());
        if (view == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "view_missing");
            return;
        }
        if (!view.active()) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "view_inactive");
            return;
        }
        if ((int) view.generation() != payload.viewGeneration()) {
            SkyesightPortalEntityPool.clearView(payload.viewId());
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "generation");
            return;
        }
        if (!view.target().dimension().equals(payload.targetDimension())) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "dimension");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "no_client");
            return;
        }

        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getOrCreateIfCurrent(payload.viewId(), payload.targetDimension());
        if (world == null || world.isClosed() || world.level() == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "no_visual_world");
            return;
        }

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(payload.vanillaPacketData()),
                minecraft.getConnection().registryAccess()
        );

        try {
            switch (payload.kind()) {
                case ADD_ENTITY -> applyAddEntity(
                        world,
                        payload,
                        ClientboundAddEntityPacket.STREAM_CODEC.decode(buffer)
                );
                case REMOVE_ENTITIES -> applyRemoveEntities(
                        payload,
                        ClientboundRemoveEntitiesPacket.STREAM_CODEC.decode(buffer)
                );
                case SET_ENTITY_DATA -> applyEntityData(
                        payload,
                        ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer)
                );
                case SET_EQUIPMENT -> applyEquipment(
                        payload,
                        ClientboundSetEquipmentPacket.STREAM_CODEC.decode(buffer)
                );
                case SET_ENTITY_MOTION -> applyEntityMotion(
                        payload,
                        ClientboundSetEntityMotionPacket.STREAM_CODEC.decode(buffer)
                );
                case TELEPORT_ENTITY -> applyTeleport(
                        payload,
                        ClientboundTeleportEntityPacket.STREAM_CODEC.decode(buffer)
                );
                case ROTATE_HEAD -> applyRotateHead(
                        payload,
                        ClientboundRotateHeadPacket.STREAM_CODEC.decode(buffer)
                );
                case ANIMATE -> applyAnimate(
                        payload,
                        ClientboundAnimatePacket.STREAM_CODEC.decode(buffer)
                );
                case ENTITY_EVENT -> applyEntityEvent(
                        payload,
                        ClientboundEntityEventPacket.STREAM_CODEC.decode(buffer)
                );
            }
        } catch (RuntimeException exception) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "decode:" + exception.getClass().getSimpleName());
        }
    }

    private static SkyesightPortalEntityPoolLeakTripwire.Snapshot beforePacket(
            SkyesightVisualEntityVanillaPacketPayload payload,
            int entityId
    ) {
        return SkyesightPortalEntityPoolLeakTripwire.beforeWrappedPacket(
                payload.viewId(),
                payload.targetDimension(),
                payload.kind().name(),
                entityId
        );
    }

    private static void afterPacket(SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot) {
        SkyesightPortalEntityPoolLeakTripwire.afterWrappedPacket(snapshot);
    }

    private static void applyAddEntity(
            SkyesightVisualWorld world,
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundAddEntityPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.getId());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && (minecraft.player.getId() == packet.getId()
                || minecraft.player.getUUID().equals(packet.getUUID()))) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "self");
            afterPacket(snapshot);
            return;
        }
        if (packet.getType() == EntityType.PLAYER) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "player_entity");
            afterPacket(snapshot);
            return;
        }

        Entity entity = SkyesightPortalEntityPool.get(
                payload.viewId(),
                payload.targetDimension(),
                packet.getId()
        );
        boolean created = entity == null;
        if (entity == null) {
            entity = packet.getType().create(world.level());
            if (entity == null) {
                SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "create_failed");
                afterPacket(snapshot);
                return;
            }
        }
        if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
            PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "portal_entity_pool_packet_apply");
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "multipart_part");
            afterPacket(snapshot);
            return;
        }

        entity.recreateFromPacket(packet);
        setOldPositionToCurrent(entity);
        ((SkyesightEntityDimensionContext) entity).skyesight$setExplicitDimension(payload.targetDimension());
        SkyesightPortalEntityPool.put(payload.viewId(), payload.targetDimension(), packet.getId(), entity);
        SkyesightPortalEntityPool.markAuthoritativePosition(
                payload.viewId(),
                payload.targetDimension(),
                packet.getId(),
                payload.kind().name()
        );
        SkyesightPortalEntityPoolLeakTripwire.pooledEntityStored(
                payload.viewId(),
                payload.targetDimension(),
                payload.kind().name(),
                packet.getId(),
                entity
        );
        if (created) {
            SkyesightNativeVisualEntityRoutingDebug.nativeEntityAdded(payload.viewId());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.nativeEntityUpdated(payload.viewId());
        }
        SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        updatePoolCount(payload);
        afterPacket(snapshot);
    }

    private static void applyRemoveEntities(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundRemoveEntitiesPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(
                payload,
                packet.getEntityIds().isEmpty() ? -1 : packet.getEntityIds().getInt(0)
        );
        packet.getEntityIds().forEach(entityId -> {
            SkyesightPortalEntityPool.remove(payload.viewId(), payload.targetDimension(), entityId);
            SkyesightNativeVisualEntityRoutingDebug.nativeEntityRemoved(payload.viewId());
        });
        SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        updatePoolCount(payload);
        afterPacket(snapshot);
    }

    private static void applyEntityData(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundSetEntityDataPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.id());
        Entity entity = SkyesightPortalEntityPool.get(payload.viewId(), payload.targetDimension(), packet.id());
        if (entity != null) {
            entity.getEntityData().assignValues(packet.packedItems());
            SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), packet.id(), payload.kind().name());
            SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
        }
        afterPacket(snapshot);
    }

    private static void applyEquipment(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundSetEquipmentPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.getEntity());
        if (SkyesightPortalEntityPool.get(payload.viewId(), payload.targetDimension(), packet.getEntity()) instanceof LivingEntity livingEntity) {
            packet.getSlots().forEach(slot -> livingEntity.setItemSlot(slot.getFirst(), slot.getSecond()));
            SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), packet.getEntity(), payload.kind().name());
            SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
        }
        afterPacket(snapshot);
    }

    private static void applyEntityMotion(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundSetEntityMotionPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.getId());
        Entity entity = SkyesightPortalEntityPool.get(payload.viewId(), payload.targetDimension(), packet.getId());
        if (entity != null) {
            entity.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
            SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), packet.getId(), payload.kind().name());
            SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
        }
        afterPacket(snapshot);
    }

    private static void applyTeleport(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundTeleportEntityPacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.getId());
        Entity entity = SkyesightPortalEntityPool.get(payload.viewId(), payload.targetDimension(), packet.getId());
        if (entity == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
            afterPacket(snapshot);
            return;
        }

        applyAuthoritativeTeleport(entity, packet);
        SkyesightPortalEntityPool.markAuthoritativePosition(
                payload.viewId(),
                payload.targetDimension(),
                packet.getId(),
                payload.kind().name()
        );
        SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        afterPacket(snapshot);
    }

    private static void applyRotateHead(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundRotateHeadPacket packet
    ) {
        int entityId = ((ClientboundRotateHeadPacketAccessor) packet).skyesight$getEntityId();
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, entityId);
        Entity entity = SkyesightPortalEntityPool.get(
                payload.viewId(),
                payload.targetDimension(),
                entityId
        );
        if (entity != null) {
            float yHeadRot = net.minecraft.util.Mth.wrapDegrees(packet.getYHeadRot());
            entity.lerpHeadTo(yHeadRot, 3);
            SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), entityId, payload.kind().name());
            SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
        }
        afterPacket(snapshot);
    }

    private static void applyAnimate(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundAnimatePacket packet
    ) {
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, packet.getId());
        Entity entity = SkyesightPortalEntityPool.get(payload.viewId(), payload.targetDimension(), packet.getId());
        if (entity == null) {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
            afterPacket(snapshot);
            return;
        }

        if (packet.getAction() == 0 && entity instanceof LivingEntity livingEntity) {
            livingEntity.swing(InteractionHand.MAIN_HAND);
        } else if (packet.getAction() == 3 && entity instanceof LivingEntity livingEntity) {
            livingEntity.swing(InteractionHand.OFF_HAND);
        } else if (packet.getAction() == 2 && entity instanceof Player player) {
            player.stopSleepInBed(false, false);
        }
        SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), packet.getId(), payload.kind().name());
        SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        afterPacket(snapshot);
    }

    private static void applyEntityEvent(
            SkyesightVisualEntityVanillaPacketPayload payload,
            ClientboundEntityEventPacket packet
    ) {
        int entityId = ((ClientboundEntityEventPacketAccessor) packet).skyesight$getEntityId();
        SkyesightPortalEntityPoolLeakTripwire.Snapshot snapshot = beforePacket(payload, entityId);
        Entity entity = SkyesightPortalEntityPool.get(
                payload.viewId(),
                payload.targetDimension(),
                entityId
        );
        if (entity != null) {
            entity.handleEntityEvent(packet.getEventId());
            SkyesightPortalEntityPool.markPacket(payload.viewId(), payload.targetDimension(), entityId, payload.kind().name());
            SkyesightNativeVisualEntityRoutingDebug.clientApplied(payload.viewId(), payload.kind().name());
        } else {
            SkyesightNativeVisualEntityRoutingDebug.clientDrop(payload.viewId(), "missing_entity");
        }
        afterPacket(snapshot);
    }

    private static void updatePoolCount(SkyesightVisualEntityVanillaPacketPayload payload) {
        SkyesightNativeVisualEntityRoutingDebug.entityCounts(
                payload.viewId(),
                SkyesightPortalEntityPool.count(payload.viewId(), payload.targetDimension()),
                -1
        );
    }

    private static void applyAuthoritativeTeleport(Entity entity, ClientboundTeleportEntityPacket packet) {
        double previousX = entity.getX();
        double previousY = entity.getY();
        double previousZ = entity.getZ();
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        float yRot = net.minecraft.util.Mth.wrapDegrees((float) (packet.getyRot() * 360) / 256.0F);
        float xRot = net.minecraft.util.Mth.wrapDegrees((float) (packet.getxRot() * 360) / 256.0F);
        double distanceSquared = entity.position().distanceToSqr(x, y, z);

        entity.syncPacketPositionCodec(x, y, z);
        entity.setPos(x, y, z);
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yRotO = yRot;
        entity.xRotO = xRot;
        entity.setOnGround(packet.isOnGround());

        if (distanceSquared > 16.0D * 16.0D || !Double.isFinite(previousX + previousY + previousZ)) {
            setOldPositionToCurrent(entity);
            return;
        }
        entity.xo = previousX;
        entity.yo = previousY;
        entity.zo = previousZ;
        entity.xOld = previousX;
        entity.yOld = previousY;
        entity.zOld = previousZ;
    }

    private static void setOldPositionToCurrent(Entity entity) {
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
    }
}
