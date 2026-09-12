package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.network.SkyesightPortalMiningPayload;
import com.skyeshade.skyesight.server.SkyesightServerViewTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class PortalDestroyProgressMixin {
    @Inject(method = "destroyBlockProgress", at = @At("TAIL"))
    private void skyesight$mirror(int breaker, BlockPos pos, int stage, CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        for (var watched : SkyesightServerViewTracker.viewsWatching(level.dimension(), new ChunkPos(pos))) {
            var player = level.getServer().getPlayerList().getPlayer(watched.playerId());
            if (player != null) PacketDistributor.sendToPlayer(player, new SkyesightPortalMiningPayload(
                    watched.watch().viewId(), watched.watch().generation(), level.dimension(), breaker, pos, stage));
        }
    }
}
