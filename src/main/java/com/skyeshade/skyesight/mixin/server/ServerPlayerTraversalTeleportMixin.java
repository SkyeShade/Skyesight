package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.portal.TraversalPortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerTraversalTeleportMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void skyesight$acceptedMovement(CallbackInfo ci) {
        // ensureRunningOnSameThread and vanilla movement/collision validation have completed.
        TraversalPortalManager.movementAccepted(player);
    }
    /** Teleports are discontinuities, including short same-level teleports across an aperture. */
    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void skyesight$resetCrossingSample(CallbackInfo ci) {
        TraversalPortalManager.teleported(player);
    }
}
