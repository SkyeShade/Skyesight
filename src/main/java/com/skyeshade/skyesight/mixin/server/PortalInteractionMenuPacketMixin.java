package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.portal.PortalInteractions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PortalInteractionMenuPacketMixin {
    @Redirect(method = {"handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe", "handleSetBeaconPacket"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean skyesight$menu(AbstractContainerMenu menu, Player player) {
        return PortalInteractions.menuValid((ServerPlayer) player, menu);
    }
}
