package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.network.SkyesightVisualEntityVanillaPacketPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class SkyesightVisualEntityPacketWrapper {
    private SkyesightVisualEntityPacketWrapper() {}

    public static Optional<SkyesightVisualEntityVanillaPacketPayload> wrap(
            ResourceLocation viewId,
            int viewGeneration,
            ResourceKey<Level> targetDimension,
            RegistryAccess registryAccess,
            Packet<?> packet
    ) {
        if (packet instanceof ClientboundAddEntityPacket addEntityPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.ADD_ENTITY,
                    ClientboundAddEntityPacket.STREAM_CODEC,
                    addEntityPacket);
        }
        if (packet instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.REMOVE_ENTITIES,
                    ClientboundRemoveEntitiesPacket.STREAM_CODEC,
                    removeEntitiesPacket);
        }
        if (packet instanceof ClientboundSetEntityDataPacket setEntityDataPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.SET_ENTITY_DATA,
                    ClientboundSetEntityDataPacket.STREAM_CODEC,
                    setEntityDataPacket);
        }
        if (packet instanceof ClientboundSetEquipmentPacket setEquipmentPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.SET_EQUIPMENT,
                    ClientboundSetEquipmentPacket.STREAM_CODEC,
                    setEquipmentPacket);
        }
        if (packet instanceof ClientboundSetEntityMotionPacket setEntityMotionPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.SET_ENTITY_MOTION,
                    ClientboundSetEntityMotionPacket.STREAM_CODEC,
                    setEntityMotionPacket);
        }
        if (packet instanceof ClientboundTeleportEntityPacket teleportEntityPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.TELEPORT_ENTITY,
                    ClientboundTeleportEntityPacket.STREAM_CODEC,
                    teleportEntityPacket);
        }
        if (packet instanceof ClientboundRotateHeadPacket rotateHeadPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.ROTATE_HEAD,
                    ClientboundRotateHeadPacket.STREAM_CODEC,
                    rotateHeadPacket);
        }
        if (packet instanceof ClientboundAnimatePacket animatePacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.ANIMATE,
                    ClientboundAnimatePacket.STREAM_CODEC,
                    animatePacket);
        }
        if (packet instanceof ClientboundEntityEventPacket entityEventPacket) {
            return wrap(viewId, viewGeneration, targetDimension, registryAccess,
                    SkyesightVisualEntityVanillaPacketPayload.PacketKind.ENTITY_EVENT,
                    ClientboundEntityEventPacket.STREAM_CODEC,
                    entityEventPacket);
        }

        SkyesightNativeVisualEntityRoutingDebug.unsupportedPacket(
                viewId,
                packet == null ? "null" : packet.getClass().getName()
        );
        return Optional.empty();
    }

    private static <T extends Packet<?>> Optional<SkyesightVisualEntityVanillaPacketPayload> wrap(
            ResourceLocation viewId,
            int viewGeneration,
            ResourceKey<Level> targetDimension,
            RegistryAccess registryAccess,
            SkyesightVisualEntityVanillaPacketPayload.PacketKind kind,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            T packet
    ) {
        try {
            ByteBuf rawBuffer = Unpooled.buffer();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(rawBuffer, registryAccess);
            codec.encode(buffer, packet);
            byte[] packetData = new byte[buffer.readableBytes()];
            buffer.readBytes(packetData);
            SkyesightNativeVisualEntityRoutingDebug.wrappedPacket(viewId, kind.name());

            return Optional.of(new SkyesightVisualEntityVanillaPacketPayload(
                    viewId,
                    viewGeneration,
                    targetDimension,
                    kind,
                    packetData
            ));
        } catch (RuntimeException exception) {
            SkyesightNativeVisualEntityRoutingDebug.encodeFailure(
                    viewId,
                    packet == null ? "null" : packet.getClass().getName(),
                    exception.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }
}
