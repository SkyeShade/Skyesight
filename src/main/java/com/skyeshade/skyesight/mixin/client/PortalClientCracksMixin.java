package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.portal.PortalInteractionClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class PortalClientCracksMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true)
    private void skyesight$cracks(int entity, BlockPos pos, int stage, CallbackInfo ci) {
        if (PortalInteractionClient.interactionLevel() != null) {
            // The watched world's native server progress stream owns its crack overlay.
            // Local prediction must not reset it on each client completion/retry.
            ci.cancel();
        }
    }
}
