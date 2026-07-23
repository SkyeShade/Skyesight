package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderPipeline;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerPortalBlockUpdateMixin {
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void skyesight$schedulePortalTerrainBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        DirectStencilPortalRenderPipeline.scheduleSodiumBlockUpdate(packet.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void skyesight$schedulePortalTerrainSectionUpdates(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        LongSet scheduledChunks = new LongOpenHashSet();
        packet.runUpdates((pos, state) -> scheduleChunkOnce(pos, scheduledChunks));
    }

    private static void scheduleChunkOnce(BlockPos pos, LongSet scheduledChunks) {
        if (pos == null || scheduledChunks == null) {
            return;
        }

        long chunk = new ChunkPos(pos).toLong();
        if (scheduledChunks.add(chunk)) {
            DirectStencilPortalRenderPipeline.scheduleSodiumBlockUpdate(pos);
        }
    }
}
