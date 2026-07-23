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

import java.util.UUID;

public record SkyesightProxyMarkerPayload(
        String markerKey,
        String markerName,
        ResourceKey<Level> queryDimension,
        Vec3 apparentPosition,
        UUID realPlayerUuid,
        String realPlayerName,
        ResourceLocation viewId,
        String direction,
        boolean syntheticReverse,
        String variant,
        ResourceKey<Level> displayDimension,
        ResourceKey<Level> cameraDimension,
        int ttlMillis
) implements CustomPacketPayload {
    public static final Type<SkyesightProxyMarkerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "proxy_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightProxyMarkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    SkyesightProxyMarkerPayload::write,
                    SkyesightProxyMarkerPayload::read
            );

    private static SkyesightProxyMarkerPayload read(RegistryFriendlyByteBuf buffer) {
        String markerKey = buffer.readUtf();
        String markerName = buffer.readUtf();
        ResourceKey<Level> queryDimension = readDimension(buffer);
        Vec3 apparentPosition = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        UUID realPlayerUuid = buffer.readUUID();
        String realPlayerName = buffer.readUtf();
        ResourceLocation viewId = buffer.readResourceLocation();
        String direction = buffer.readUtf();
        boolean syntheticReverse = buffer.readBoolean();
        String variant = buffer.readUtf();
        ResourceKey<Level> displayDimension = readDimension(buffer);
        ResourceKey<Level> cameraDimension = readDimension(buffer);
        int ttlMillis = buffer.readVarInt();
        return new SkyesightProxyMarkerPayload(
                markerKey,
                markerName,
                queryDimension,
                apparentPosition,
                realPlayerUuid,
                realPlayerName,
                viewId,
                direction,
                syntheticReverse,
                variant,
                displayDimension,
                cameraDimension,
                ttlMillis
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, SkyesightProxyMarkerPayload payload) {
        buffer.writeUtf(payload.markerKey);
        buffer.writeUtf(payload.markerName);
        writeDimension(buffer, payload.queryDimension);
        buffer.writeDouble(payload.apparentPosition.x);
        buffer.writeDouble(payload.apparentPosition.y);
        buffer.writeDouble(payload.apparentPosition.z);
        buffer.writeUUID(payload.realPlayerUuid);
        buffer.writeUtf(payload.realPlayerName);
        buffer.writeResourceLocation(payload.viewId);
        buffer.writeUtf(payload.direction);
        buffer.writeBoolean(payload.syntheticReverse);
        buffer.writeUtf(payload.variant);
        writeDimension(buffer, payload.displayDimension);
        writeDimension(buffer, payload.cameraDimension);
        buffer.writeVarInt(payload.ttlMillis);
    }

    private static ResourceKey<Level> readDimension(RegistryFriendlyByteBuf buffer) {
        return ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
    }

    private static void writeDimension(RegistryFriendlyByteBuf buffer, ResourceKey<Level> dimension) {
        buffer.writeResourceLocation(dimension.location());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
