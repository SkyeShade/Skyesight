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

public record SkyesightLevelEventPayload(
        ResourceLocation viewId,
        long generation,
        ResourceKey<Level> dimension,
        BlockPos pos,
        int eventId,
        int eventParam,
        long eventSequence,
        boolean vanillaDelivered
) implements CustomPacketPayload {
    public static final Type<SkyesightLevelEventPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "level_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightLevelEventPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightLevelEventPayload::write,
                    SkyesightLevelEventPayload::read
            );

    private static SkyesightLevelEventPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long generation = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        BlockPos pos = buffer.readBlockPos();
        int eventId = buffer.readVarInt();
        int eventParam = buffer.readVarInt();

        return new SkyesightLevelEventPayload(
                viewId,
                generation,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                pos,
                eventId,
                eventParam,
                buffer.readVarLong(), buffer.readBoolean()
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightLevelEventPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarLong(payload.generation());
        buffer.writeResourceLocation(payload.dimension().location());

        buffer.writeBlockPos(payload.pos());
        buffer.writeVarInt(payload.eventId());
        buffer.writeVarInt(payload.eventParam());
        buffer.writeVarLong(payload.eventSequence());
        buffer.writeBoolean(payload.vanillaDelivered());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
