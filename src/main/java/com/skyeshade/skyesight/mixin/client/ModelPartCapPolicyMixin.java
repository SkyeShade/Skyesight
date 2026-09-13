package com.skyeshade.skyesight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyesight.client.render.slice.CapContributionScope;
import com.skyeshade.skyesight.client.render.slice.CapModelPart;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ModelPart.class)
public abstract class ModelPartCapPolicyMixin implements CapModelPart {
    @Unique private boolean skyesight$clipOnly;
    @Override public void skyesight$setClipOnly(boolean value) { skyesight$clipOnly=value; }

    @WrapMethod(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    private void skyesight$capPolicy(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, Operation<Void> original) {
        if(!skyesight$clipOnly) { original.call(pose,buffer,light,overlay,color);return; }
        try(var scope=CapContributionScope.clipOnly()) { original.call(pose,buffer,light,overlay,color); }
    }
}
