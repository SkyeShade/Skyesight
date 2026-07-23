package com.skyeshade.skyesight.mixin.server.lifecycle;

import com.skyeshade.skyesight.server.portal.PortalDespawnProtection;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobPortalSimulationMixin {
    @Inject(
            method = "checkDespawn",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void skyesight$portalObserverPreventsDistanceDespawn(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        boolean protectedByPortal = PortalDespawnProtection.shouldProtectPortalMobFromDespawn(mob);
        if (protectedByPortal) {
            mob.setNoActionTime(0);
            ci.cancel();
        }
    }
}

