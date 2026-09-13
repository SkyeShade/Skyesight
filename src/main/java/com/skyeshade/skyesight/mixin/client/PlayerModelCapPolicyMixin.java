package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.slice.CapModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelCapPolicyMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void skyesight$markCosmetics(ModelPart root, boolean slim, CallbackInfo ci) {
        var model=(PlayerModel<?>)(Object)this;
        for(var part:java.util.List.of(model.hat,model.jacket,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants))
            ((CapModelPart)(Object)part).skyesight$setClipOnly(true);
    }
}
