package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.skyeshade.skyesight.client.render.PortalSecondaryWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetBindWriteDebugMixin {
    @Inject(method = "bindWrite", at = @At("HEAD"))
    private void skyesight$recordSecondaryTargetBind(boolean setViewport, CallbackInfo ci) {
        PortalSecondaryWorldRenderer.recordSecondaryContextTargetBind((RenderTarget) (Object) this);
    }
}
