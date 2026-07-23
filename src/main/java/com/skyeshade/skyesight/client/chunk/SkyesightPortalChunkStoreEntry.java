package com.skyeshade.skyesight.client.chunk;

import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public record SkyesightPortalChunkStoreEntry(
        ResourceKey<Level> dimension,
        ChunkPos pos,
        ResourceLocation viewId,
        ClientboundLevelChunkPacketData chunkData,
        ClientboundLightUpdatePacketData lightData,
        LevelChunk decodedChunk,
        String decodeException,
        int nonEmptySectionCount,
        int blockEntityTagCount,
        long lastUpdateMillis,
        boolean dirty
) {
    public String summary() {
        return "dim="
                + this.dimension.location()
                + " chunk="
                + this.pos.x
                + ","
                + this.pos.z
                + " view="
                + this.viewId
                + " nonEmptySections="
                + unknownAware(this.nonEmptySectionCount)
                + " blockEntityTags="
                + unknownAware(this.blockEntityTagCount)
                + " decoded="
                + (this.decodedChunk == null ? "no" : "yes")
                + " decodeException="
                + (this.decodeException == null || this.decodeException.isBlank() ? "-" : this.decodeException)
                + " dirty="
                + this.dirty
                + " ageMs="
                + Math.max(0L, System.currentTimeMillis() - this.lastUpdateMillis);
    }

    private static String unknownAware(int value) {
        return value < 0 ? "unknown" : Integer.toString(value);
    }
}
