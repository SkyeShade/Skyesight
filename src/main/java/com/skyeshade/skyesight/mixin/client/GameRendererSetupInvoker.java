package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererSetupInvoker {
    @Invoker("getFov")
    double skyesight$getFov(Camera camera, float partialTick, boolean useFovSetting);

    @Invoker("bobHurt")
    void skyesight$bobHurt(PoseStack poseStack, float partialTick);

    @Invoker("bobView")
    void skyesight$bobView(PoseStack poseStack, float partialTick);

    @Accessor("confusionAnimationTick")
    int skyesight$getConfusionAnimationTick();
}
