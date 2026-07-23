package com.skyeshade.skyesight.mixin.server.spawn;

import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.SpawnState.class)
public abstract class NaturalSpawnerSpawnStatePortalObserverMixin {
    @Shadow
    @Final
    private int spawnableChunkCount;

    @Shadow
    @Final
    private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;

    @Inject(
            method = "canSpawnForCategory(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void skyesight$portalObserverCategoryEligibility(
            MobCategory category,
            ChunkPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean globalCapAllows = PortalVanillaSpawnBridge.globalCapAllows(
                this.spawnableChunkCount,
                this.mobCategoryCounts.getInt(category),
                category
        );
        boolean portalOverride = PortalVanillaSpawnBridge.shouldForcePortalCategory(category, pos, globalCapAllows, cir.getReturnValue());
        if (portalOverride) {
            cir.setReturnValue(true);
        }
    }
}
