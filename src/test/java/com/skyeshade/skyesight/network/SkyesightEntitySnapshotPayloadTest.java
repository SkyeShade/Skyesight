package com.skyeshade.skyesight.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightEntitySnapshotPayloadTest {
    @Test void authoritativeSampleClockSurvivesEmptyRosterRoundTrip() {
        var value = new SkyesightEntitySnapshotPayload(ResourceLocation.parse("test:remote"), 42,
                Level.NETHER, 1234567, List.of());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyesightEntitySnapshotPayload.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, SkyesightEntitySnapshotPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
