package com.skyeshade.skyesight.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class PortalMiningTickMixin {
    @Shadow
    protected ServerPlayer player;

    @WrapMethod(method = "tick")
    private void skyesight$destinationTick(Operation<Void> original) {
        com.skyeshade.skyesight.server.portal.PortalInteractions.tickGameMode(player, () -> original.call());
    }

    @WrapMethod(method = "handleBlockBreakAction")
    private void skyesight$physicalOwnership(net.minecraft.core.BlockPos pos,
                                             net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action,
                                             net.minecraft.core.Direction face, int maxHeight, int sequence, Operation<Void> original) {
        if (com.skyeshade.skyesight.server.portal.PortalInteractionContext.forEntity(player) == null)
            com.skyeshade.skyesight.server.portal.PortalInteractions.beforePhysicalAction(player);
        original.call(pos, action, face, maxHeight, sequence);
    }
}
