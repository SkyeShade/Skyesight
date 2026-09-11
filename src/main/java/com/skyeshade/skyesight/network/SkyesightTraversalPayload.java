package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Server-only origin. Pair revision and per-player sequence fence stale traversal notifications. */
public record SkyesightTraversalPayload(UUID owner, long revision, long sequence, int action,
        boolean fromA, PortalEndpoint a, PortalEndpoint b, Vec3 destinationFeet) implements CustomPacketPayload {
    public SkyesightTraversalPayload(UUID owner, long revision, long sequence, int action,
            boolean fromA, PortalEndpoint a, PortalEndpoint b) {
        this(owner, revision, sequence, action, fromA, a, b, Vec3.ZERO);
    }
    public static final int DEFINE = 0, REMOVE = 1, BEGIN = 2;
    public static final Type<SkyesightTraversalPayload> TYPE = new Type<>(ResourceLocation.parse("skyesight:traversal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightTraversalPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUUID(p.owner); buf.writeVarLong(p.revision); buf.writeVarLong(p.sequence);
            buf.writeVarInt(p.action); buf.writeBoolean(p.fromA);
            if (p.action == BEGIN) { buf.writeDouble(p.destinationFeet.x); buf.writeDouble(p.destinationFeet.y); buf.writeDouble(p.destinationFeet.z); }
            if (p.action == DEFINE) { writeEndpoint(buf, p.a); writeEndpoint(buf, p.b); }
        }, buf -> {
            UUID owner = buf.readUUID(); long revision = buf.readVarLong(), sequence = buf.readVarLong();
            int action = buf.readVarInt(); boolean fromA = buf.readBoolean();
            Vec3 position = action == BEGIN ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : Vec3.ZERO;
            return new SkyesightTraversalPayload(owner, revision, sequence, action, fromA,
                    action == DEFINE ? readEndpoint(buf) : null, action == DEFINE ? readEndpoint(buf) : null, position);
        });
    private static void writeEndpoint(RegistryFriendlyByteBuf buf, PortalEndpoint endpoint) {
        buf.writeResourceLocation(endpoint.dimension().location());
        buf.writeDouble(endpoint.center().x); buf.writeDouble(endpoint.center().y); buf.writeDouble(endpoint.center().z);
        buf.writeEnum(endpoint.facing());
    }
    private static PortalEndpoint readEndpoint(RegistryFriendlyByteBuf buf) {
        var dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return PortalEndpoint.of("traversal", dimension, position, buf.readEnum(Direction.class), 2, 3);
    }
    public static String portalId(UUID owner, boolean a) { return "skyesight:traversal_" + owner + (a ? "_a" : "_b"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
