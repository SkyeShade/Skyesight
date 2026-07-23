package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.mixin.common.LivingEntityAnimationAccessor;
import com.skyeshade.skyesight.mixin.common.LivingEntityWalkAnimationAccessor;
import com.skyeshade.skyesight.mixin.common.WalkAnimationStateAccessor;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.network.SkyesightEntitySnapshotPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class SkyesightServerEntitySnapshotSender {
    private static final int MAX_ENTITIES_PER_SNAPSHOT = 128;

    private SkyesightServerEntitySnapshotSender() {}

    public static void sendSnapshot(
            ServerPlayer receivingPlayer,
            SkyesightServerViewTracker.ViewWatch watch,
            ServerLevel level
    ) {
        updatePortalEntityPoolTracker(receivingPlayer, watch, level);
        if (crossDimSnapshotDisabled(watch, level)) {
            return;
        }

        int radiusBlocks = watch.radius() * 16 + 16;
        double centerX = watch.centerChunkX() * 16.0D + 8.0D;
        double centerZ = watch.centerChunkZ() * 16.0D + 8.0D;

        AABB area = new AABB(
                centerX - radiusBlocks,
                level.getMinBuildHeight(),
                centerZ - radiusBlocks,
                centerX + radiusBlocks,
                level.getMaxBuildHeight(),
                centerZ + radiusBlocks
        );

        List<SkyesightEntitySnapshotPayload.Entry> entries = new ArrayList<>();
        List<Entity> candidates = level.getEntities((Entity) null, area, entity -> true);

        for (Entity entity : candidates) {
            if (entries.size() >= MAX_ENTITIES_PER_SNAPSHOT) {
                break;
            }
            if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "entity_snapshot");
                SkyesightNativeVisualEntityRoutingDebug.serverEntitySkippedPartEntity(watch.viewId());
                continue;
            }

            entries.add(createEntry(entity));
        }

        for (ServerPlayer player : level.players()) {
            if (entries.size() >= MAX_ENTITIES_PER_SNAPSHOT) {
                break;
            }

            if (!area.contains(player.position())) {
                continue;
            }

            entries.add(createEntry(player));
        }

        PacketDistributor.sendToPlayer(
                receivingPlayer,
                new SkyesightEntitySnapshotPayload(
                        watch.viewId(),
                        level.dimension(),
                        entries
                )
        );
    }

    private static boolean crossDimSnapshotDisabled(SkyesightServerViewTracker.ViewWatch watch, ServerLevel level) {
        if (!SkyesightPortalEntityPoolConfig.crossDimEntitySnapshotsDisabled()) {
            return false;
        }
        RegisteredPortalView view = SkyesightPortalApi.getPortal(watch.viewId().toString());
        return view != null
                && view.active()
                && view.isCrossDimension()
                && view.target().dimension().equals(level.dimension());
    }

    private static void updatePortalEntityPoolTracker(
            ServerPlayer receivingPlayer,
            SkyesightServerViewTracker.ViewWatch watch,
            ServerLevel level
    ) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            return;
        }
        RegisteredPortalView view = SkyesightPortalApi.getPortal(watch.viewId().toString());
        if (view == null) {
            SkyesightNativeVisualEntityRoutingDebug.serverTrackerSkipped(watch.viewId(), "view_missing");
            return;
        }
        if (!view.active()) {
            SkyesightNativeVisualEntityRoutingDebug.serverTrackerSkipped(watch.viewId(), "view_inactive");
            return;
        }
        if (!view.isCrossDimension()) {
            SkyesightNativeVisualEntityRoutingDebug.serverTrackerSkipped(watch.viewId(), "same_dim");
            return;
        }
        if (!view.target().dimension().equals(level.dimension())) {
            SkyesightNativeVisualEntityRoutingDebug.serverTrackerSkipped(watch.viewId(), "target_dim_mismatch");
            return;
        }
        SkyesightServerVisualEntityPacketTracker.update(receivingPlayer, watch, level);
    }

    private static SkyesightEntitySnapshotPayload.Entry createEntry(Entity entity) {
        float yBodyRot = entity.getYRot();
        float yBodyRotO = entity.getYRot();
        float yHeadRot = entity.getYRot();
        float yHeadRotO = entity.getYRot();

        float walkPosition = 0.0F;
        float walkSpeed = 0.0F;
        float walkSpeedOld = 0.0F;
        int hurtTime = 0;
        int hurtDuration = 0;
        int deathTime = 0;

        float attackAnim = 0.0F;
        float oAttackAnim = 0.0F;

        boolean swinging = false;
        InteractionHand swingingArm = InteractionHand.MAIN_HAND;
        int swingTime = 0;
        if (entity instanceof LivingEntity livingEntity) {
            yBodyRot = livingEntity.yBodyRot;
            yBodyRotO = livingEntity.yBodyRotO;
            yHeadRot = livingEntity.yHeadRot;
            yHeadRotO = livingEntity.yHeadRotO;

            WalkAnimationState walkAnimation =
                    ((LivingEntityWalkAnimationAccessor) livingEntity).skyesight$getWalkAnimation();

            WalkAnimationStateAccessor walkAccessor =
                    (WalkAnimationStateAccessor) walkAnimation;

            walkPosition = walkAccessor.skyesight$getPosition();
            walkSpeed = walkAccessor.skyesight$getSpeed();
            walkSpeedOld = walkAccessor.skyesight$getSpeedOld();
            LivingEntityAnimationAccessor animationAccessor =
                    (LivingEntityAnimationAccessor) livingEntity;

            hurtTime = livingEntity.hurtTime;
            hurtDuration = livingEntity.hurtDuration;
            deathTime = livingEntity.deathTime;

            attackAnim = animationAccessor.skyesight$getAttackAnim();
            oAttackAnim = animationAccessor.skyesight$getOAttackAnim();

            swinging = animationAccessor.skyesight$isSwinging();
            swingingArm = animationAccessor.skyesight$getSwingingArm();
            swingTime = animationAccessor.skyesight$getSwingTime();
        }

        List<SynchedEntityData.DataValue<?>> entityData =
                SkyesightEntityDataPacker.packAll(entity);
        String profileName = "";

        if (entity instanceof ServerPlayer player) {
            profileName = player.getGameProfile().getName();
        }
        List<SkyesightEntitySnapshotPayload.EquipmentEntry> equipment =
                collectEquipment(entity);
        return new SkyesightEntitySnapshotPayload.Entry(
                entity.getUUID(),
                entity.getType(),
                profileName,
                entity.position(),
                entity.getDeltaMovement(),
                entity.onGround(),
                entity.fallDistance,
                entity.getYRot(),
                entity.getXRot(),
                entity.tickCount,
                yBodyRot,
                yBodyRotO,
                yHeadRot,
                yHeadRotO,
                walkPosition,
                walkSpeed,
                walkSpeedOld,
                hurtTime,
                hurtDuration,
                deathTime,
                attackAnim,
                oAttackAnim,
                swinging,
                swingingArm,
                swingTime,
                entityData,
                equipment
        );
    }
    private static List<SkyesightEntitySnapshotPayload.EquipmentEntry> collectEquipment(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return List.of();
        }

        List<SkyesightEntitySnapshotPayload.EquipmentEntry> equipment = new ArrayList<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = livingEntity.getItemBySlot(slot);

            equipment.add(new SkyesightEntitySnapshotPayload.EquipmentEntry(
                    slot,
                    stack.copy()
            ));
        }

        return equipment;
    }
}
