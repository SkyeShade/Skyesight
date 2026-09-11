package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A short-lived particle subscription; does not load chunks or simulate server worlds. */
public record SkyesightParticleWatchPayload(ResourceLocation viewId, long generation,
        ResourceKey<Level> dimension, Vec3 center, int radius, boolean active) implements CustomPacketPayload {
    public static final Type<SkyesightParticleWatchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "particle_watch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightParticleWatchPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeResourceLocation(value.viewId); buffer.writeVarLong(value.generation);
                buffer.writeResourceLocation(value.dimension.location());
                buffer.writeDouble(value.center.x); buffer.writeDouble(value.center.y); buffer.writeDouble(value.center.z);
                buffer.writeVarInt(value.radius); buffer.writeBoolean(value.active);
            }, buffer -> new SkyesightParticleWatchPayload(buffer.readResourceLocation(), buffer.readVarLong(),
                    ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readVarInt(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
