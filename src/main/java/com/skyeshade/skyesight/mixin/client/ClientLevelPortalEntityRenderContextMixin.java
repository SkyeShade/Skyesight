package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.entity.PortalEntityRenderContextScope;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelPortalEntityRenderContextMixin {
    @Inject(method = "getEntity", at = @At("HEAD"), cancellable = true)
    private void skyesight$getPortalScopedEntity(
            int entityId,
            CallbackInfoReturnable<Entity> callback
    ) {
        Entity scopedEntity = PortalEntityRenderContextScope.lookup(
                entityId,
                "ClientLevel.getEntity"
        );
        if (scopedEntity != null) {
            callback.setReturnValue(scopedEntity);
        }
    }

    @Inject(method = "entitiesForRendering", at = @At("HEAD"))
    private void skyesight$recordPortalScopedEntitiesForRendering(
            CallbackInfoReturnable<Iterable<Entity>> callback
    ) {
        PortalEntityRenderContextScope.recordContextCall("ClientLevel.entitiesForRendering");
    }
}
