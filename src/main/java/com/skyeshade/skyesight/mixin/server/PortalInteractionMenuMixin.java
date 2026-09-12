package com.skyeshade.skyesight.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Player.class, ServerPlayer.class})
public abstract class PortalInteractionMenuMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean skyesight$menu(AbstractContainerMenu menu, Player player) {
        return player instanceof ServerPlayer serverPlayer
                ? com.skyeshade.skyesight.server.portal.PortalInteractions.menuValid(serverPlayer, menu) : menu.stillValid(player);
    }
}
