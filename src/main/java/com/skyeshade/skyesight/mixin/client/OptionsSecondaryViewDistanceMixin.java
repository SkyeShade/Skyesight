package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.SkyesightViewDistanceScope;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsSecondaryViewDistanceMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void skyesight$isolatedViewDistance(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SkyesightViewDistanceScope.resolve(cir.getReturnValue()));
    }
}
