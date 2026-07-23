package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record SkyesightChunkRequestPayload(
        ResourceLocation viewId,
        long viewGeneration,
        ResourceKey<Level> dimension,
        int centerChunkX,
        int centerChunkZ,
        int radius,
        List<ChunkPos> chunks
) implements CustomPacketPayload {
    public static final Type<SkyesightChunkRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "chunk_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightChunkRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightChunkRequestPayload::write,
                    SkyesightChunkRequestPayload::read
            );

    private static SkyesightChunkRequestPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long viewGeneration = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int radius = buffer.readVarInt();

        int count = buffer.readVarInt();
        List<ChunkPos> chunks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }

        return new SkyesightChunkRequestPayload(
                viewId,
                viewGeneration,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                centerChunkX,
                centerChunkZ,
                radius,
                chunks
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightChunkRequestPayload payload) {
        buffer.writeResourceLocation(payload.viewId);
        buffer.writeVarLong(payload.viewGeneration);
        buffer.writeResourceLocation(payload.dimension.location());

        buffer.writeInt(payload.centerChunkX);
        buffer.writeInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);

        buffer.writeVarInt(payload.chunks.size());

        for (ChunkPos chunk : payload.chunks) {
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
        }
    }

    @Override
    public Type<SkyesightChunkRequestPayload> type() {
        return TYPE;
    }
}
