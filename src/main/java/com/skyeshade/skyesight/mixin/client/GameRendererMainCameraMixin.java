package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.SkyesightSecondaryRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMainCameraMixin {
    @Inject(method = "getMainCamera", at = @At("HEAD"), cancellable = true)
    private void skyesight$getSecondaryMainCamera(CallbackInfoReturnable<Camera> cir) {
        if (SkyesightSecondaryRenderContext.isActive()) {
            Camera camera = SkyesightSecondaryRenderContext.currentCamera();

            if (camera != null) {
                cir.setReturnValue(camera);
            }
        }
    }
}
