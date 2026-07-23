package com.skyeshade.skyesight.mixin.server.lifecycle;

import com.skyeshade.skyesight.server.portal.PortalDespawnProtection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPortalLifecycleMixin {
    @Inject(
            method = "remove",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void skyesight$handlePortalEntityRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (PortalDespawnProtection.isSkyesightIntentionalDiscard()) {
            return;
        }
        if (PortalDespawnProtection.shouldCancelPortalEntityRemoval(entity, reason)) {
            PortalDespawnProtection.onPortalEntityRemoved(entity, reason, true);
            ci.cancel();
            return;
        }
        PortalDespawnProtection.onPortalEntityRemoved(entity, reason, false);
    }
}

