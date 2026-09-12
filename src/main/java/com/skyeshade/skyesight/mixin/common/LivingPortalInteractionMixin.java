package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.server.portal.PortalInteractionContext;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingPortalInteractionMixin {
    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void skyesight$look(float partialTick, CallbackInfoReturnable<Float> cir) {
        var context = PortalInteractionContext.forEntity((LivingEntity) (Object) this);
        // LivingEntity overrides Entity's getter and reads yHeadRot directly. Direction.orderedByNearest
        // (observers/pistons/placement contexts) must see the same transformed yaw as getDirection().
        if (context != null) cir.setReturnValue(context.pose.yaw());
    }
}
