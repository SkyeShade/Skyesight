package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SkyesightEntitySnapshotPayload(
        ResourceLocation viewId,
        ResourceKey<Level> dimension,
        List<Entry> entities
) implements CustomPacketPayload {

    public static final Type<SkyesightEntitySnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "entity_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightEntitySnapshotPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightEntitySnapshotPayload::write,
                    SkyesightEntitySnapshotPayload::read
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, EntityType<?>> ENTITY_TYPE_CODEC =
            ByteBufCodecs.registry(Registries.ENTITY_TYPE);

    private static SkyesightEntitySnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        int count = buffer.readVarInt();
        List<Entry> entities = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            UUID uuid = buffer.readUUID();
            EntityType<?> type = ENTITY_TYPE_CODEC.decode(buffer);
            String profileName = buffer.readUtf();

            Vec3 position = new Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );

            Vec3 deltaMovement = new Vec3(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );

            boolean onGround = buffer.readBoolean();
            float fallDistance = buffer.readFloat();

            float yRot = buffer.readFloat();
            float xRot = buffer.readFloat();

            int tickCount = buffer.readVarInt();

            float yBodyRot = buffer.readFloat();
            float yBodyRotO = buffer.readFloat();
            float yHeadRot = buffer.readFloat();
            float yHeadRotO = buffer.readFloat();

            float walkPosition = buffer.readFloat();
            float walkSpeed = buffer.readFloat();
            float walkSpeedOld = buffer.readFloat();

            int hurtTime = buffer.readVarInt();
            int hurtDuration = buffer.readVarInt();
            int deathTime = buffer.readVarInt();

            float attackAnim = buffer.readFloat();
            float oAttackAnim = buffer.readFloat();

            boolean swinging = buffer.readBoolean();
            InteractionHand swingingArm = buffer.readEnum(InteractionHand.class);
            int swingTime = buffer.readVarInt();

            List<SynchedEntityData.DataValue<?>> entityData = readEntityData(buffer);
            List<EquipmentEntry> equipment = readEquipment(buffer);
            entities.add(new Entry(
                    uuid,
                    type,
                    profileName,
                    position,
                    deltaMovement,
                    onGround,
                    fallDistance,
                    yRot,
                    xRot,
                    tickCount,
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
            ));
        }

        return new SkyesightEntitySnapshotPayload(
                viewId,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                entities
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightEntitySnapshotPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeResourceLocation(payload.dimension().location());

        buffer.writeVarInt(payload.entities().size());

        for (Entry entity : payload.entities()) {
            Vec3 position = entity.position() == null ? Vec3.ZERO : entity.position();
            Vec3 deltaMovement = entity.deltaMovement() == null ? Vec3.ZERO : entity.deltaMovement();
            String profileName = entity.profileName() == null ? "" : entity.profileName();
            InteractionHand swingingArm = entity.swingingArm() == null
                    ? InteractionHand.MAIN_HAND
                    : entity.swingingArm();

            buffer.writeUUID(entity.uuid());
            ENTITY_TYPE_CODEC.encode(buffer, entity.type());
            buffer.writeUtf(profileName);

            buffer.writeDouble(position.x());
            buffer.writeDouble(position.y());
            buffer.writeDouble(position.z());

            buffer.writeDouble(deltaMovement.x());
            buffer.writeDouble(deltaMovement.y());
            buffer.writeDouble(deltaMovement.z());

            buffer.writeBoolean(entity.onGround());
            buffer.writeFloat(entity.fallDistance());

            buffer.writeFloat(entity.yRot());
            buffer.writeFloat(entity.xRot());

            buffer.writeVarInt(entity.tickCount());

            buffer.writeFloat(entity.yBodyRot());
            buffer.writeFloat(entity.yBodyRotO());
            buffer.writeFloat(entity.yHeadRot());
            buffer.writeFloat(entity.yHeadRotO());

            buffer.writeFloat(entity.walkPosition());
            buffer.writeFloat(entity.walkSpeed());
            buffer.writeFloat(entity.walkSpeedOld());

            buffer.writeVarInt(entity.hurtTime());
            buffer.writeVarInt(entity.hurtDuration());
            buffer.writeVarInt(entity.deathTime());

            buffer.writeFloat(entity.attackAnim());
            buffer.writeFloat(entity.oAttackAnim());

            buffer.writeBoolean(entity.swinging());
            buffer.writeEnum(swingingArm);
            buffer.writeVarInt(entity.swingTime());

            writeEntityData(buffer, entity.entityData());

            writeEquipment(buffer, entity.equipment());
        }
    }

    private static List<SynchedEntityData.DataValue<?>> readEntityData(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int id = buffer.readUnsignedByte();
            values.add(SynchedEntityData.DataValue.read(buffer, id));
        }

        return values;
    }

    private static void writeEntityData(
            RegistryFriendlyByteBuf buffer,
            List<SynchedEntityData.DataValue<?>> values
    ) {
        if (values == null || values.isEmpty()) {
            buffer.writeVarInt(0);
            return;
        }

        buffer.writeVarInt(values.size());

        for (SynchedEntityData.DataValue<?> value : values) {
            value.write(buffer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    private static List<EquipmentEntry> readEquipment(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<EquipmentEntry> equipment = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            EquipmentSlot slot = buffer.readEnum(EquipmentSlot.class);
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);

            equipment.add(new EquipmentEntry(slot, stack));
        }

        return equipment;
    }

    private static void writeEquipment(
            RegistryFriendlyByteBuf buffer,
            List<EquipmentEntry> equipment
    ) {
        if (equipment == null || equipment.isEmpty()) {
            buffer.writeVarInt(0);
            return;
        }

        buffer.writeVarInt(equipment.size());

        for (EquipmentEntry entry : equipment) {
            buffer.writeEnum(entry.slot());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
        }
    }
    public record Entry(
            UUID uuid,
            EntityType<?> type,
            String profileName,
            Vec3 position,
            Vec3 deltaMovement,
            boolean onGround,
            float fallDistance,
            float yRot,
            float xRot,
            int tickCount,
            float yBodyRot,
            float yBodyRotO,
            float yHeadRot,
            float yHeadRotO,
            float walkPosition,
            float walkSpeed,
            float walkSpeedOld,
            int hurtTime,
            int hurtDuration,
            int deathTime,
            float attackAnim,
            float oAttackAnim,
            boolean swinging,
            InteractionHand swingingArm,
            int swingTime,
            List<SynchedEntityData.DataValue<?>> entityData,
            List<EquipmentEntry> equipment
    ) {}
    public record EquipmentEntry(
            EquipmentSlot slot,
            ItemStack stack
    ) {}
}
