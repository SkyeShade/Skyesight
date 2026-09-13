package com.skyeshade.skyesight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GameRenderer.class)
public abstract class GameRendererTransitionMixin {
    @Unique private boolean skyesight$physicalRendered;
    @Inject(method = "render", at = @At("HEAD"))
    private void skyesight$frameStart(CallbackInfo ci) {
        TraversalPortalClient.beforePresentation();
        SecondaryTransition.frameStarted();
    }
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void skyesight$worldPresentation(LevelRenderer renderer, DeltaTracker timer, boolean outline,
            Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f model, Matrix4f projection,
            Operation<Void> original) {
        TraversalPortalClient.beforeWorld(camera);
        skyesight$physicalRendered = SecondaryTransition.shouldRenderPhysical();
        if (skyesight$physicalRendered) {
            original.call(renderer, timer, outline, camera, gameRenderer, light, model, projection);
        }
    }
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", shift = At.Shift.AFTER))
    private void skyesight$presentWorld(CallbackInfo ci) {
        if (skyesight$physicalRendered) SecondaryTransition.afterWorldFrame();
        // Replace only world color. GameRenderer still renders the normal hand, overlays and GUI.
        SecondaryTransition.present();
    }
}
