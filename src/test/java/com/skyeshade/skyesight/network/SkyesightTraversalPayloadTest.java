package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.api.PortalEndpoint;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightTraversalPayloadTest {
    @Test void definitionCarriesBothDimensionsAndEndpointBases() {
        UUID owner = UUID.randomUUID();
        var a = PortalEndpoint.of("traversal", Level.OVERWORLD, new Vec3(.5, 102.5, .5), Direction.NORTH, 2, 3);
        var b = PortalEndpoint.of("traversal", Level.NETHER, new Vec3(1200.5, 102.5, .5), Direction.WEST, 2, 3);
        var packet = new SkyesightTraversalPayload(owner, 41, 0, SkyesightTraversalPayload.DEFINE, true, a, b);
        assertEquals(packet, roundTrip(packet));
    }
    @Test void tokenRetainsRevisionSequenceSideAndAuthoritativePosition() {
        var packet = new SkyesightTraversalPayload(UUID.randomUUID(), 41, 82, SkyesightTraversalPayload.BEGIN,
                false, null, null, new Vec3(-1000.25, 80.5, .005));
        assertEquals(packet, roundTrip(packet));
    }
    private static SkyesightTraversalPayload roundTrip(SkyesightTraversalPayload value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyesightTraversalPayload.STREAM_CODEC.encode(buffer, value);
            var result = SkyesightTraversalPayload.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return result;
        } finally { buffer.release(); }
    }
}
