package com.skyeshade.skyesight.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class MinecraftLiveTransitionScreenMixin {
    @WrapWithCondition(method = {"updateScreenAndTick", "forceSetScreen"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
    private boolean skyesight$skipConcealedForcedFrame(Minecraft minecraft, boolean advanceGameTime,
            @Local(argsOnly = true) Screen screen) {
        // The forced loading frame repeats the old partial tick inside packet application.
        // Keep the already presented scene until the next normal frame advances the timer.
        return !(screen instanceof ReceivingLevelScreen) || !SecondaryTransition.concealsLoadingScreen();
    }
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void skyesight$liveLoading(Screen screen, CallbackInfo ci) {
        if (screen instanceof ReceivingLevelScreen receiving && SecondaryTransition.suppressLoadingScreen(receiving)) ci.cancel();
    }
}
