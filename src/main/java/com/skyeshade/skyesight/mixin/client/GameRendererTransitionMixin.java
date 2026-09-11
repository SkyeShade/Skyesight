package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererTransitionMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void skyesight$frameStart(CallbackInfo ci) {
        com.skyeshade.skyesight.client.transition.TraversalPortalClient.beforePresentation();
        SecondaryTransition.frameStarted();
    }
    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void skyesight$physicalProbe(CallbackInfo ci) {
        if (!SecondaryTransition.shouldRenderPhysical()) ci.cancel();
    }
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void skyesight$worldFrame(CallbackInfo ci) { SecondaryTransition.afterWorldFrame(); }
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void skyesight$liveBeforeGui(CallbackInfo ci) { SecondaryTransition.presentBeforeGui(); }
    @Inject(method = "render", at = @At("TAIL"))
    private void skyesight$present(CallbackInfo ci) {
        SecondaryTransition.present();
    }
}
