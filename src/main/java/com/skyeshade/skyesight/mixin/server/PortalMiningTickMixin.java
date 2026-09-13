package com.skyeshade.skyesight.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skyeshade.skyesight.server.portal.PortalInteractionContext;
import com.skyeshade.skyesight.server.portal.PortalInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
        PortalInteractions.tickGameMode(player, () -> original.call());
    }

    @WrapMethod(method = "handleBlockBreakAction")
    private void skyesight$physicalOwnership(BlockPos pos,
                                             ServerboundPlayerActionPacket.Action action,
                                             Direction face, int maxHeight, int sequence, Operation<Void> original) {
        if (PortalInteractionContext.forEntity(player) == null)
            PortalInteractions.beforePhysicalAction(player);
        original.call(pos, action, face, maxHeight, sequence);
    }
}
