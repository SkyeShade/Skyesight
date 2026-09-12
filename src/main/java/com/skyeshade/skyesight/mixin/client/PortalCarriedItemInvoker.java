package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface PortalCarriedItemInvoker {
    @Invoker("ensureHasSentCarriedItem")
    void skyesight$syncCarriedItem();
}
