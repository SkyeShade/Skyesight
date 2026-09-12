package com.skyeshade.skyesight.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Native server crack event scoped to a watched world generation; -1 clears it.
 */
public record SkyesightPortalMiningPayload(ResourceLocation viewId, long generation, ResourceKey<Level> dimension,
                                           int breakerId, BlockPos pos, int stage)
        implements CustomPacketPayload {
    public static final Type<SkyesightPortalMiningPayload> TYPE = new Type<>(ResourceLocation.parse("skyesight:portal_mining"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightPortalMiningPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeResourceLocation(p.viewId);
                b.writeVarLong(p.generation);
                b.writeResourceLocation(p.dimension.location());
                b.writeVarInt(p.breakerId);
                b.writeBlockPos(p.pos);
                b.writeByte(p.stage);
            },
            b -> new SkyesightPortalMiningPayload(b.readResourceLocation(), b.readVarLong(),
                    ResourceKey.create(Registries.DIMENSION, b.readResourceLocation()), b.readVarInt(), b.readBlockPos(), b.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
