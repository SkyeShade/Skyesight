package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.portal.PortalInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;

@Mixin(ContainerOpenersCounter.class)
public abstract class PortalContainerOpenersMixin {
    @Shadow
    protected abstract boolean isOwnContainer(Player player);

    @Inject(method = "getPlayersWithContainerOpen", at = @At("RETURN"), cancellable = true)
    private void skyesight$remoteOpeners(Level level, BlockPos pos, CallbackInfoReturnable<List<Player>> cir) {
        var players = new ArrayList<>(cir.getReturnValue());
        for (var player : PortalInteractions.remoteOpeners(level, pos, this::isOwnContainer))
            if (!players.contains(player)) players.add(player);
        cir.setReturnValue(players);
    }
}
