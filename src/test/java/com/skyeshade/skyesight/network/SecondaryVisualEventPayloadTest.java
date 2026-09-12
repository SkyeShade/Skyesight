package com.skyeshade.skyesight.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecondaryVisualEventPayloadTest {
    @Test
    void crackWorldGenerationAndNativeBreakerSurviveRoundTrip() {
        var value = new SkyesightPortalMiningPayload(ResourceLocation.parse("test:view"), 37,
                Level.NETHER, 812, new BlockPos(4000, 61, -2), -1);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyesightPortalMiningPayload.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, SkyesightPortalMiningPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void breakDeliveryIdentitySurvivesRoundTrip() {
        var value = new SkyesightLevelEventPayload(ResourceLocation.parse("test:view"), 37,
                Level.OVERWORLD, new BlockPos(20, 61, -2), 2001, 17, 123456L, true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyesightLevelEventPayload.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, SkyesightLevelEventPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
