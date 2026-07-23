package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.server.portal.PortalPathProximity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityApparentPlayerPositionMixin {
    @Inject(method = "getX()D", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyesight$apparentPortalPlayerX(CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            boolean hasContext = PortalPathProximity.currentApparentPlayerContext(player) != null;
            double real = cir.getReturnValueD();
            if (hasContext) {
                if (PortalPathProximity.shouldBlockClientRealPlayerCoordinateOverride(player)) {
                    PortalPathProximity.traceRealPlayerCoordinateOverrideAttempt(
                            player,
                            PortalPathProximity.currentApparentPlayerContext(player)
                    );
                    cir.setReturnValue(real);
                    return;
                }
                double returned = PortalPathProximity.apparentOrRealX(player);
                cir.setReturnValue(returned);
                PortalPathProximity.traceEnchantApparentCoordinate("getX", player, real, returned);
            } else {
                PortalPathProximity.traceEnchantApparentCoordinate("getX", player, real, real);
            }
        }
    }

    @Inject(method = "getY()D", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyesight$apparentPortalPlayerY(CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player && PortalPathProximity.currentApparentPlayerContext(player) != null) {
            if (PortalPathProximity.shouldBlockClientRealPlayerCoordinateOverride(player)) {
                PortalPathProximity.traceRealPlayerCoordinateOverrideAttempt(
                        player,
                        PortalPathProximity.currentApparentPlayerContext(player)
                );
                return;
            }
            cir.setReturnValue(PortalPathProximity.apparentOrRealY(player));
        }
    }

    @Inject(method = "getZ()D", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyesight$apparentPortalPlayerZ(CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            boolean hasContext = PortalPathProximity.currentApparentPlayerContext(player) != null;
            double real = cir.getReturnValueD();
            if (hasContext) {
                if (PortalPathProximity.shouldBlockClientRealPlayerCoordinateOverride(player)) {
                    PortalPathProximity.traceRealPlayerCoordinateOverrideAttempt(
                            player,
                            PortalPathProximity.currentApparentPlayerContext(player)
                    );
                    cir.setReturnValue(real);
                    return;
                }
                double returned = PortalPathProximity.apparentOrRealZ(player);
                cir.setReturnValue(returned);
                PortalPathProximity.traceEnchantApparentCoordinate("getZ", player, real, returned);
            } else {
                PortalPathProximity.traceEnchantApparentCoordinate("getZ", player, real, real);
            }
        }
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("RETURN"), cancellable = true, require = 0)
    private void skyesight$apparentPortalPlayerDistance(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            boolean hasContext = PortalPathProximity.currentApparentPlayerContext(player) != null;
            double real = cir.getReturnValueD();
            if (hasContext) {
                if (PortalPathProximity.shouldBlockClientRealPlayerCoordinateOverride(player)) {
                    PortalPathProximity.traceRealPlayerCoordinateOverrideAttempt(
                            player,
                            PortalPathProximity.currentApparentPlayerContext(player)
                    );
                    cir.setReturnValue(real);
                    return;
                }
                double returned = PortalPathProximity.apparentOrRealDistanceToSqr(player, x, y, z);
                cir.setReturnValue(returned);
                PortalPathProximity.traceEnchantApparentCoordinate("distanceToSqr", player, real, returned);
            } else {
                PortalPathProximity.traceEnchantApparentCoordinate("distanceToSqr", player, real, real);
            }
        }
    }
}
