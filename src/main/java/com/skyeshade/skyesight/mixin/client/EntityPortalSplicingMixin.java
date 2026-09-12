package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.client.render.entity.PortalEntitySplicing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityPortalSplicingMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skyesight$split(Entity entity, double x, double y, double z, float yaw, float tick,
                                 PoseStack pose, MultiBufferSource output, int light, CallbackInfo ci) {
        if (PortalEntitySplicing.source((EntityRenderDispatcher) (Object) this, entity, x, y, z, yaw, tick, pose, output, light))
            ci.cancel();
    }
}
