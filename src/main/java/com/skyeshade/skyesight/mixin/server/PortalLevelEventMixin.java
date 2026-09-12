package com.skyeshade.skyesight.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class PortalLevelEventMixin {
    @org.spongepowered.asm.mixin.Unique
    private long skyesight$effectSequence;

    @Inject(method = "levelEvent", at = @At("TAIL"))
    private void skyesight$effect(Player excluded, int id, BlockPos pos, int data, CallbackInfo ci) {
        if (id != 2001) return;
        var level = (ServerLevel) (Object) this;
        long sequence = ++skyesight$effectSequence;
        for (var watched : com.skyeshade.skyesight.server.SkyesightServerViewTracker.viewsWatching(level.dimension(), new net.minecraft.world.level.ChunkPos(pos))) {
            var player = level.getServer().getPlayerList().getPlayer(watched.playerId());
            var context = player == null ? null : com.skyeshade.skyesight.server.portal.PortalInteractionContext.forEntity(player);
            boolean vanillaDelivered = player != null && (player == excluded
                    ? context == null // Ordinary physical mining predicted its own break event.
                    : player.level() == level && player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 64 * 64);
            if (player != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.skyeshade.skyesight.network.SkyesightLevelEventPayload(watched.watch().viewId(), watched.watch().generation(), level.dimension(), pos, id, data, sequence, vanillaDelivered));
        }
    }
}
