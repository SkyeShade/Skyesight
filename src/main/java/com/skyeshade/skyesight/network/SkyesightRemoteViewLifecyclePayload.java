package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightRemoteViewLifecyclePayload(
        ResourceLocation viewId,
        long generation,
        ResourceKey<Level> targetDimension,
        boolean active
) implements CustomPacketPayload {
    public static final Type<SkyesightRemoteViewLifecyclePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "remote_view_lifecycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightRemoteViewLifecyclePayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightRemoteViewLifecyclePayload::write,
                    SkyesightRemoteViewLifecyclePayload::read
            );

    private static SkyesightRemoteViewLifecyclePayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long generation = buffer.readVarLong();
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                buffer.readResourceLocation()
        );
        return new SkyesightRemoteViewLifecyclePayload(
                viewId,
                generation,
                dimension,
                buffer.readBoolean()
        );
    }

    private static void write(
            RegistryFriendlyByteBuf buffer,
            SkyesightRemoteViewLifecyclePayload payload
    ) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarLong(payload.generation());
        buffer.writeResourceLocation(payload.targetDimension().location());
        buffer.writeBoolean(payload.active());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
