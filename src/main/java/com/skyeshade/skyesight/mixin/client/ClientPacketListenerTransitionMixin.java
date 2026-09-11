package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerTransitionMixin {
    // This invocation is after ensureRunningOnSameThread and before setLevel can render loading UI.
    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;commonPlayerSpawnInfo()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;"))
    private void skyesight$dimension(ClientboundRespawnPacket packet, CallbackInfo ci) {
        SecondaryTransition.incomingDimension(packet.commonPlayerSpawnInfo().dimension());
    }
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void skyesight$position(CallbackInfo ci) {
        SecondaryTransition.authoritativePosition();
        com.skyeshade.skyesight.client.transition.TraversalPortalClient.authoritativePosition();
    }
}
