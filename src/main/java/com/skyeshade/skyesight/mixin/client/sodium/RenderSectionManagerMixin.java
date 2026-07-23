package com.skyeshade.skyesight.mixin.client.sodium;

import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumRenderContext;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {
    @Inject(
            method = "shouldUseOcclusionCulling",
            at = @At("RETURN"),
            cancellable = true
    )
    private void skyesight$disableSecondaryOcclusionCulling(
            Camera camera,
            boolean spectator,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SkyesightSodiumRenderContext.shouldDisableOcclusionCulling()) {
            cir.setReturnValue(false);
        }
    }
}
