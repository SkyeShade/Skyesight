package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightLightDataPayload(
        ResourceLocation viewId,
        long generation,
        ResourceKey<Level> dimension,
        int chunkX,
        int chunkZ,
        ClientboundLightUpdatePacketData lightData
) implements CustomPacketPayload {
    public static final Type<SkyesightLightDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "light_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightLightDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightLightDataPayload::write,
                    SkyesightLightDataPayload::read
            );

    private static SkyesightLightDataPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long generation = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();

        ClientboundLightUpdatePacketData lightData =
                new ClientboundLightUpdatePacketData(buffer, chunkX, chunkZ);

        return new SkyesightLightDataPayload(
                viewId,
                generation,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                chunkX,
                chunkZ,
                lightData
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightLightDataPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarLong(payload.generation());
        buffer.writeResourceLocation(payload.dimension().location());

        buffer.writeInt(payload.chunkX());
        buffer.writeInt(payload.chunkZ());

        payload.lightData().write(buffer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}