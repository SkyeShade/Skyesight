package com.skyeshade.skyesight.mixin.server.lifecycle;

import com.skyeshade.skyesight.server.PortalSimulationCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelEntityTickAuditMixin {
    // 1.21.1 NeoForge descriptor:
    // tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V
    @Inject(
            method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void skyesight$recordPortalVanillaTickNonPassenger(Entity entity, CallbackInfo ci) {
        PortalSimulationCoordinator.recordVanillaEntityTick((ServerLevel) (Object) this, entity);
    }
}

