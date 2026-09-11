package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightBlockEventPayload(
        ResourceLocation viewId,
        long generation,
        ResourceKey<Level> dimension,
        BlockPos pos,
        int eventId,
        int eventParam
) implements CustomPacketPayload {
    public static final Type<SkyesightBlockEventPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "block_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightBlockEventPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightBlockEventPayload::write,
                    SkyesightBlockEventPayload::read
            );

    private static SkyesightBlockEventPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long generation = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        BlockPos pos = buffer.readBlockPos();
        int eventId = buffer.readVarInt();
        int eventParam = buffer.readVarInt();

        return new SkyesightBlockEventPayload(
                viewId,
                generation,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                pos,
                eventId,
                eventParam
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightBlockEventPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarLong(payload.generation());
        buffer.writeResourceLocation(payload.dimension().location());

        buffer.writeBlockPos(payload.pos());
        buffer.writeVarInt(payload.eventId());
        buffer.writeVarInt(payload.eventParam());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}