package com.skyeshade.skyesight.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightRequestTraceCodecTest {
    @Test void rejectsOversizedOrNegativeCountsBeforeReadingChunkEntries() {
        for (int count : new int[]{-1,257,Integer.MAX_VALUE}) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                buffer.writeResourceLocation(ResourceLocation.parse("test:camera"));
                buffer.writeVarLong(2);
                buffer.writeVarLong(100);
                buffer.writeResourceLocation(Level.NETHER.location());
                buffer.writeInt(0);
                buffer.writeInt(0);
                buffer.writeVarInt(Integer.MAX_VALUE);
                buffer.writeVarInt(count);
                assertThrows(DecoderException.class,
                        () -> SkyesightChunkRequestPayload.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
    @Test void movementAndEmptyDemandPreserveGenerationCenterAndSequenceOnWire() {
        var id = ResourceLocation.parse("test:camera");
        for (var request : List.of(
                new SkyesightChunkRequestPayload(id, 2, 41, Level.NETHER, 69, 44, 5, List.of(new ChunkPos(74,44))),
                new SkyesightChunkRequestPayload(id, 2, 42, Level.NETHER, 70, 44, 5, List.of()))) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                SkyesightChunkRequestPayload.STREAM_CODEC.encode(buffer, request);
                assertEquals(request, SkyesightChunkRequestPayload.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}
