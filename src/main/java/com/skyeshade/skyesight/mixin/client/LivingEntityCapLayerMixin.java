package com.skyeshade.skyesight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.client.render.slice.CapContributionScope;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityCapLayerMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void skyesight$featurePolicy(RenderLayer<?,?> layer, PoseStack pose, MultiBufferSource buffers, int light,
                                        Entity entity, float limb, float amount, float partial, float age, float yaw, float pitch,
                                        Operation<Void> original) {
        // Armor is intentionally conservative: it stays clipped, but the burnt fill belongs
        // to the body. Do not blanket-disable solid features such as sheep wool.
        boolean clipOnly=layer instanceof ItemInHandLayer || layer instanceof CapeLayer || layer instanceof ElytraLayer
                || layer instanceof HumanoidArmorLayer || layer instanceof CustomHeadLayer;
        if(!clipOnly) { original.call(layer,pose,buffers,light,entity,limb,amount,partial,age,yaw,pitch);return; }
        try(var scope=CapContributionScope.clipOnly()) {
            original.call(layer,pose,buffers,light,entity,limb,amount,partial,age,yaw,pitch);
        }
    }
}
