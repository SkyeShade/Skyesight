package com.skyeshade.skyesight.mixin.server.spawn;

import com.skyeshade.skyesight.mixin.server.chunk.ChunkMapEntityTrackerAccessor;
import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorPortalObserverMixin {
    @Shadow
    @Final
    private ChunkMap chunkMap;

    // 1.21.1 mapped descriptor:
    // canSpawn(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z
    @Inject(
            method = "canSpawn(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void skyesight$portalObserverCountsForLocalMobCap(
            MobCategory category,
            ChunkPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ServerLevel level = ((ChunkMapEntityTrackerAccessor) this.chunkMap).skyesight$getLevel();
        boolean vanillaAllows = cir.getReturnValue();
        boolean portalAllows = PortalVanillaSpawnBridge.shouldAllowLocalMobCap(level, pos, category, vanillaAllows);
        if (portalAllows) {
            cir.setReturnValue(true);
        }
    }
}
