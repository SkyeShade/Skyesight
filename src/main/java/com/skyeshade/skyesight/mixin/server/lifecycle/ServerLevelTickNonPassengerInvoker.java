package com.skyeshade.skyesight.mixin.server.lifecycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelTickNonPassengerInvoker {
    @Invoker("tickNonPassenger")
    void skyesight$invokeTickNonPassenger(Entity entity);
}
