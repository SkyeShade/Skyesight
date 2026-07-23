package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.skyeshade.skyesight.client.render.SkyesightSecondaryRenderContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMainRenderTargetMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void skyesight$getSecondaryRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        if (SkyesightSecondaryRenderContext.isActive()) {
            RenderTarget target = SkyesightSecondaryRenderContext.currentTarget();

            if (target != null) {
                cir.setReturnValue(target);
            }
        }
    }
}
