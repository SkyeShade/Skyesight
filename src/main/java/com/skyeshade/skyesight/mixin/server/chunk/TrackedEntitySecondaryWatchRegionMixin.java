package com.skyeshade.skyesight.mixin.server.chunk;

import com.skyeshade.skyesight.server.SkyesightSecondaryWatchRegion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntitySecondaryWatchRegionMixin {
    @Shadow
    @Final
    Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At(value = "STORE"), ordinal = 0)
    private boolean skyesight$includeSecondaryWatchRegion(boolean vanillaShouldTrack, ServerPlayer player) {
        if (vanillaShouldTrack) {
            SkyesightSecondaryWatchRegion.recordVanillaTracked();
            return true;
        }

        return SkyesightSecondaryWatchRegion.shouldTrackAnyRegion(player, this.entity);
    }
}
