package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.api.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Server-only origin. Pair revision and per-player sequence fence stale traversal notifications.
 */
public record SkyesightTraversalPayload(UUID owner, long revision, long sequence, int action,
                                        boolean fromA, PortalEndpoint a, PortalEndpoint b, Vec3 destinationFeet,
                                        PortalPairSettings settings) implements CustomPacketPayload {
    public SkyesightTraversalPayload(UUID owner, long revision, long sequence, int action,
                                     boolean fromA, PortalEndpoint a, PortalEndpoint b) {
        this(owner, revision, sequence, action, fromA, a, b, Vec3.ZERO, PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
    }

    public SkyesightTraversalPayload(UUID owner, long revision, long sequence, int action,
                                     boolean fromA, PortalEndpoint a, PortalEndpoint b, Vec3 feet) {
        this(owner, revision, sequence, action, fromA, a, b, feet, PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
    }

    public static final int DEFINE = 0, REMOVE = 1, BEGIN = 2;
    public static final Type<SkyesightTraversalPayload> TYPE = new Type<>(ResourceLocation.parse("skyesight:traversal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightTraversalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.owner);
                buf.writeVarLong(p.revision);
                buf.writeVarLong(p.sequence);
                buf.writeVarInt(p.action);
                buf.writeBoolean(p.fromA);
                if (p.action == BEGIN) {
                    buf.writeDouble(p.destinationFeet.x);
                    buf.writeDouble(p.destinationFeet.y);
                    buf.writeDouble(p.destinationFeet.z);
                }
                if (p.action == DEFINE) {
                    writeEndpoint(buf, p.a);
                    writeEndpoint(buf, p.b);
                    PortalPairWire.write(buf, p.settings);
                }
            }, buf -> {
                UUID owner = buf.readUUID();
                long revision = buf.readVarLong(), sequence = buf.readVarLong();
                int action = buf.readVarInt();
                boolean fromA = buf.readBoolean();
                Vec3 position = action == BEGIN ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : Vec3.ZERO;
                return new SkyesightTraversalPayload(owner, revision, sequence, action, fromA,
                        action == DEFINE ? readEndpoint(buf) : null, action == DEFINE ? readEndpoint(buf) : null, position, action == DEFINE ? PortalPairWire.read(buf) : PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
            });

    private static void writeEndpoint(RegistryFriendlyByteBuf buf, PortalEndpoint endpoint) {
        buf.writeResourceLocation(endpoint.dimension().location());
        buf.writeDouble(endpoint.center().x);
        buf.writeDouble(endpoint.center().y);
        buf.writeDouble(endpoint.center().z);
        buf.writeEnum(endpoint.facing());
        buf.writeUtf(endpoint.id());
        buf.writeFloat(endpoint.width());
        buf.writeFloat(endpoint.height());
        buf.writeQuaternion(endpoint.rotation());
    }

    private static PortalEndpoint readEndpoint(RegistryFriendlyByteBuf buf) {
        var dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        var facing = buf.readEnum(Direction.class);
        var id = buf.readUtf();
        float width = buf.readFloat(), height = buf.readFloat();
        return new PortalEndpoint(id, dimension, position, facing, buf.readQuaternion(), width, height);
    }

    public static String portalId(UUID owner, boolean a) {
        return "skyesight:traversal_" + owner + (a ? "_a" : "_b");
    }

    /**
     * Pair identity, not coincident endpoint geometry, selects the reverse direction.
     */
    public static ResourceLocation pairedPortalId(ResourceLocation id) {
        String path = id.getPath();
        if (!id.getNamespace().equals("skyesight") || !path.startsWith("traversal_")
                || !(path.endsWith("_a") || path.endsWith("_b"))) return null;
        try {
            UUID pair = UUID.fromString(path.substring("traversal_".length(), path.length() - 2));
            return ResourceLocation.parse(portalId(pair, path.endsWith("_b")));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
