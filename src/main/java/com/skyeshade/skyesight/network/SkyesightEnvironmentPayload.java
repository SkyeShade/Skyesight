package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightEnvironmentPayload(
        ResourceLocation viewId,
        ResourceKey<Level> dimension,
        long generation,
        long gameTime,
        long dayTime,
        boolean daylightCycleRunning,
        float dayTimeFraction,
        float dayTimePerTick,
        boolean raining,
        boolean thundering,
        float rainLevel,
        float thunderLevel
) implements CustomPacketPayload {
    public static final Type<SkyesightEnvironmentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "remote_environment"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightEnvironmentPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightEnvironmentPayload::write,
                    SkyesightEnvironmentPayload::read
            );

    private static SkyesightEnvironmentPayload read(RegistryFriendlyByteBuf buffer) {
        return new SkyesightEnvironmentPayload(
                buffer.readResourceLocation(),
                ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                buffer.readVarLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightEnvironmentPayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeResourceLocation(payload.dimension().location());
        buffer.writeVarLong(payload.generation());
        buffer.writeLong(payload.gameTime());
        buffer.writeLong(payload.dayTime());
        buffer.writeBoolean(payload.daylightCycleRunning());
        buffer.writeFloat(payload.dayTimeFraction());
        buffer.writeFloat(payload.dayTimePerTick());
        buffer.writeBoolean(payload.raining());
        buffer.writeBoolean(payload.thundering());
        buffer.writeFloat(payload.rainLevel());
        buffer.writeFloat(payload.thunderLevel());
    }

    @Override
    public Type<SkyesightEnvironmentPayload> type() {
        return TYPE;
    }
}
