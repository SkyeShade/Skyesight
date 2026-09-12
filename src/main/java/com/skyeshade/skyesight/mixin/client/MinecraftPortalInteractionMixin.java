package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public abstract class MinecraftPortalInteractionMixin {
    @Shadow
    private int rightClickDelay;
    @Shadow
    private int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void skyesight$attack(CallbackInfoReturnable<Boolean> cir) {
        if (missTime > 0) return;
        if (PortalInteractionClient.attack(true, true)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void skyesight$mine(boolean held, CallbackInfo ci) {
        if (!held) missTime = 0;
        if (missTime > 0) return;
        if (PortalInteractionClient.attack(false, held)) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void skyesight$use(CallbackInfo ci) {
        if (PortalInteractionClient.use()) {
            rightClickDelay = 4;
            ci.cancel();
        }
    }
}
