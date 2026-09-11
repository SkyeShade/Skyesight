package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightParticlePayload(
        ResourceLocation viewId,
        long generation,
        ResourceKey<Level> dimension,
        ParticleOptions particle,
        boolean overrideLimiter,
        double x,
        double y,
        double z,
        double xDist,
        double yDist,
        double zDist,
        double maxSpeed,
        int count
) implements CustomPacketPayload {
    public static final Type<SkyesightParticlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightParticlePayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightParticlePayload::write,
                    SkyesightParticlePayload::read
            );

    private static SkyesightParticlePayload read(RegistryFriendlyByteBuf buffer) {
        ResourceLocation viewId = buffer.readResourceLocation();
        long generation = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();

        ParticleOptions particle = ParticleTypes.STREAM_CODEC.decode(buffer);

        boolean overrideLimiter = buffer.readBoolean();

        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();

        double xDist = buffer.readDouble();
        double yDist = buffer.readDouble();
        double zDist = buffer.readDouble();

        double maxSpeed = buffer.readDouble();

        int count = buffer.readInt();

        return new SkyesightParticlePayload(
                viewId,
                generation,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                particle,
                overrideLimiter,
                x,
                y,
                z,
                xDist,
                yDist,
                zDist,
                maxSpeed,
                count
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightParticlePayload payload) {
        buffer.writeResourceLocation(payload.viewId());
        buffer.writeVarLong(payload.generation());
        buffer.writeResourceLocation(payload.dimension().location());

        ParticleTypes.STREAM_CODEC.encode(buffer, payload.particle());

        buffer.writeBoolean(payload.overrideLimiter());

        buffer.writeDouble(payload.x());
        buffer.writeDouble(payload.y());
        buffer.writeDouble(payload.z());

        buffer.writeDouble(payload.xDist());
        buffer.writeDouble(payload.yDist());
        buffer.writeDouble(payload.zDist());

        buffer.writeDouble(payload.maxSpeed());

        buffer.writeInt(payload.count());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}