package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MultiPlayerGameMode.class)
public abstract class PortalClientMiningMixin {
    @Redirect(method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private ClientLevel skyesight$level(Minecraft mc) {
        var level = PortalInteractionClient.interactionLevel();
        return level == null ? mc.level : level;
    }

    @Inject(method = "startPrediction", at = @At("HEAD"), cancellable = true)
    private void skyesight$prediction(ClientLevel level, PredictiveAction action, CallbackInfo ci) {
        if (PortalInteractionClient.interactionLevel() != null) {
            PortalInteractionClient.routeAction(action.predict(0));
            ci.cancel();
        }
    }

    @Redirect(method = {"startDestroyBlock", "stopDestroyBlock"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void skyesight$abort(ClientPacketListener connection, Packet<?> packet) {
        if (!PortalInteractionClient.routeAction(packet)) connection.send(packet);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void skyesight$authoritativeBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Keep vanilla's input/progress/cooldown state, but let the watched authoritative block
        // update own visual-world mutation. No prediction entries leak into the main world.
        var level = PortalInteractionClient.interactionLevel();
        if (level != null) {
            // Event 2001 is mirrored from the authoritative destination break, including nearby views.
            cir.setReturnValue(true);
        }
    }
}
