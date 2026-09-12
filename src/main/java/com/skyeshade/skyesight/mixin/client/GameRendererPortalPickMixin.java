package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererPortalPickMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void skyesight$pick(float partialTick, CallbackInfo ci) {
        com.skyeshade.skyesight.client.portal.PortalInteractionClient.updatePick(partialTick);
    }
}
