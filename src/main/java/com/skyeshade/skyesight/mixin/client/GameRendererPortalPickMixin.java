package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class GameRendererPortalPickMixin {
    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void skyesight$pick(float partialTick, CallbackInfo ci) {
        PortalInteractionClient.updatePick(partialTick);
    }
}
