package com.skyeshade.skyesight.mixin.server.spawn;

import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPortalObserverMixin {
    @Inject(
            method = "hasNearbyAlivePlayer",
            at = @At("RETURN"),
            cancellable = true
    )
    private void skyesight$portalObserverCountsAsNearbyAlivePlayer(
            double x,
            double y,
            double z,
            double maxDistance,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue()) {
            return;
        }

        boolean portalAccepted = PortalVanillaSpawnBridge.shouldPortalObserverSatisfyNearbyAlivePlayer(
                (ServerLevel) (Object) this,
                x,
                y,
                z,
                maxDistance
        );
        if (portalAccepted) {
            cir.setReturnValue(true);
        }
    }
}
