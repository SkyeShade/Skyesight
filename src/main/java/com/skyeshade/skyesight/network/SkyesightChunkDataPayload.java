package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightChunkDataPayload(
        ResourceLocation viewId,
        long viewGeneration,
        ResourceKey<Level> dimension,
        int centerChunkX,
        int centerChunkZ,
        int radius,
        int chunkX,
        int chunkZ,
        ClientboundLevelChunkPacketData chunkData,
        ClientboundLightUpdatePacketData lightData
) implements CustomPacketPayload {
    public static final Type<SkyesightChunkDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "chunk_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightChunkDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightChunkDataPayload::write,
                    SkyesightChunkDataPayload::read
            );

    private static SkyesightChunkDataPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long viewGeneration = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int radius = buffer.readVarInt();

        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();

        ClientboundLevelChunkPacketData chunkData =
                new ClientboundLevelChunkPacketData(buffer, chunkX, chunkZ);

        ClientboundLightUpdatePacketData lightData =
                new ClientboundLightUpdatePacketData(buffer, chunkX, chunkZ);

        return new SkyesightChunkDataPayload(
                viewId,
                viewGeneration,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                centerChunkX,
                centerChunkZ,
                radius,
                chunkX,
                chunkZ,
                chunkData,
                lightData
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightChunkDataPayload payload) {
        buffer.writeResourceLocation(payload.viewId);
        buffer.writeVarLong(payload.viewGeneration);
        buffer.writeResourceLocation(payload.dimension.location());

        buffer.writeInt(payload.centerChunkX);
        buffer.writeInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);

        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);

        payload.chunkData.write(buffer);
        payload.lightData.write(buffer);
    }

    @Override
    public Type<SkyesightChunkDataPayload> type() {
        return TYPE;
    }
}
