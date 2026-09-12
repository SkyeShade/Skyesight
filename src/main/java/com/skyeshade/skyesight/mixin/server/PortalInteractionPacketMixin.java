package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.portal.PortalInteractionContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Destination updates already travel through the generation-scoped chunk watch, never the main-world packet channel.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PortalInteractionPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void skyesight$blockUpdate(Packet<?> packet, CallbackInfo ci) {
        if (PortalInteractionContext.ownsConnection(this) && packet instanceof ClientboundBlockUpdatePacket)
            ci.cancel();
    }
}
