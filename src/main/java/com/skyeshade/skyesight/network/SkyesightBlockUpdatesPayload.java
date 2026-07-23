package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public record SkyesightBlockUpdatesPayload(
        ResourceLocation viewId,
        ResourceKey<Level> dimension,
        List<Entry> updates
) implements CustomPacketPayload {
    public static final Type<SkyesightBlockUpdatesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "block_updates"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightBlockUpdatesPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightBlockUpdatesPayload::write,
                    SkyesightBlockUpdatesPayload::read
            );

    private static SkyesightBlockUpdatesPayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        int size = buffer.readVarInt();
        List<Entry> updates = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            BlockPos pos = buffer.readBlockPos();
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(buffer.readVarInt());
            CompoundTag blockEntityTag = buffer.readNbt();
            updates.add(new Entry(pos, state, blockEntityTag));
        }

        return new SkyesightBlockUpdatesPayload(
                viewId,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                updates
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightBlockUpdatesPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeResourceLocation(payload.dimension().location());

        buffer.writeVarInt(payload.updates().size());

        for (Entry update : payload.updates()) {
            buffer.writeBlockPos(update.pos());
            buffer.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(update.state()));
            buffer.writeNbt(update.blockEntityTag());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(BlockPos pos, BlockState state, CompoundTag blockEntityTag) {}
}
