package com.skyeshade.skyesight.mixin.server;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerGameMode.class)
public interface PortalInteractionGameModeAccessor {
    @Accessor("level")
    net.minecraft.server.level.ServerLevel skyesight$level();

    @Accessor("isDestroyingBlock")
    void skyesight$destroying(boolean value);

    @Accessor("hasDelayedDestroy")
    boolean skyesight$delayed();

    @Accessor("hasDelayedDestroy")
    void skyesight$delayed(boolean value);

    @Accessor("lastSentState")
    int skyesight$progress();

    @Accessor("isDestroyingBlock")
    boolean skyesight$destroying();
}
