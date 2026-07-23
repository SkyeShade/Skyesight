package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightVisualEntityVanillaPacketPayload(
        ResourceLocation viewId,
        int viewGeneration,
        ResourceKey<Level> targetDimension,
        PacketKind kind,
        byte[] vanillaPacketData
) implements CustomPacketPayload {
    public static final Type<SkyesightVisualEntityVanillaPacketPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "visual_entity_vanilla_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightVisualEntityVanillaPacketPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightVisualEntityVanillaPacketPayload::write,
                    SkyesightVisualEntityVanillaPacketPayload::read
            );

    private static SkyesightVisualEntityVanillaPacketPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        int viewGeneration = buffer.readVarInt();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        PacketKind kind = buffer.readEnum(PacketKind.class);
        byte[] vanillaPacketData = buffer.readByteArray();

        return new SkyesightVisualEntityVanillaPacketPayload(
                viewId,
                viewGeneration,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                kind,
                vanillaPacketData
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightVisualEntityVanillaPacketPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarInt(payload.viewGeneration());
        buffer.writeResourceLocation(payload.targetDimension().location());
        buffer.writeEnum(payload.kind());
        buffer.writeByteArray(payload.vanillaPacketData());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum PacketKind {
        ADD_ENTITY,
        REMOVE_ENTITIES,
        SET_ENTITY_DATA,
        SET_EQUIPMENT,
        SET_ENTITY_MOTION,
        TELEPORT_ENTITY,
        ROTATE_HEAD,
        ANIMATE,
        ENTITY_EVENT
    }
}
