package com.skyeshade.skyesight.mixin.server.spawn;

import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCachePortalSimulationMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    private boolean spawnFriendlies;

    @Inject(
            method = "tickChunks()V",
            at = @At("HEAD"),
            require = 0
    )
    private void skyesight$beginPortalSpawnGateTick(CallbackInfo ci) {
        PortalVanillaSpawnBridge.beginServerChunkCacheTick(this.level);
    }

    @ModifyArg(
            method = "tickChunks()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"
            ),
            index = 0,
            require = 0
    )
    private int skyesight$includePortalChunksInNaturalSpawnCount(int vanillaSpawningChunkCount) {
        return PortalVanillaSpawnBridge.effectiveNaturalSpawnChunkCount(this.level, vanillaSpawningChunkCount);
    }

    @Redirect(
            method = "tickChunks()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"
            ),
            require = 0
    )
    private boolean skyesight$portalObserverCountsAsPlayerCloseEnoughForSpawning(ChunkMap chunkMap, ChunkPos chunkPos) {
        return PortalVanillaSpawnBridge.allowSpawnChunk(chunkMap, this.level, chunkPos);
    }

    @Redirect(
            method = "tickChunks()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;shouldForceTicks(J)Z"
            ),
            require = 0
    )
    private boolean skyesight$portalForceTickGate(DistanceManager distanceManager, long chunkPos) {
        return PortalVanillaSpawnBridge.allowForceTickGate(distanceManager, this.level, chunkPos);
    }
}
