package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/** Authoritative level identity, independent of chunk/watch initialization. */
public record SkyesightDimensionMetadataPayload(
        ResourceKey<Level> dimension, Holder<DimensionType> dimensionType
) implements CustomPacketPayload {
    public static final Type<SkyesightDimensionMetadataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "dimension_metadata"));
    // References resolve to the client's synchronized registry; inline types retain their data.
    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<DimensionType>> TYPE_CODEC =
            ByteBufCodecs.holder(Registries.DIMENSION_TYPE,
                    ByteBufCodecs.fromCodecWithRegistries(DimensionType.DIRECT_CODEC));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightDimensionMetadataPayload> STREAM_CODEC =
            StreamCodec.of(SkyesightDimensionMetadataPayload::write, SkyesightDimensionMetadataPayload::read);

    private static SkyesightDimensionMetadataPayload read(RegistryFriendlyByteBuf buffer) {
        return new SkyesightDimensionMetadataPayload(
                ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()), TYPE_CODEC.decode(buffer));
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightDimensionMetadataPayload payload) {
        buffer.writeResourceLocation(payload.dimension().location());
        TYPE_CODEC.encode(buffer, payload.dimensionType());
    }

    @Override
    public Type<SkyesightDimensionMetadataPayload> type() {
        return TYPE;
    }
}
