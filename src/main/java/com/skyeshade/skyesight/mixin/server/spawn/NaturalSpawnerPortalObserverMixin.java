package com.skyeshade.skyesight.mixin.server.spawn;

import com.skyeshade.skyesight.server.portal.PortalVanillaSpawnBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerPortalObserverMixin {
    @Inject(
            method = "spawnForChunk",
            at = @At("HEAD"),
            require = 0
    )
    private static void skyesight$beginPortalSpawnForChunkContext(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean rareSpawn,
            CallbackInfo ci
    ) {
        PortalVanillaSpawnBridge.beginNaturalSpawnerChunk(level, chunk, spawnFriendlies, spawnEnemies, rareSpawn, spawnState);
    }

    @Inject(
            method = "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void skyesight$endSpawnForChunkContext(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            boolean spawnFriendlies,
            boolean spawnEnemies,
            boolean rareSpawn,
            CallbackInfo ci
    ) {
        PortalVanillaSpawnBridge.endNaturalSpawnerChunk(level, chunk);
    }

    @Inject(
            method = "spawnCategoryForChunk",
            at = @At("HEAD"),
            require = 0
    )
    private static void skyesight$beginPortalSpawnCategoryContext(
            MobCategory category,
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback,
            CallbackInfo ci
    ) {
        PortalVanillaSpawnBridge.beginNaturalSpawnerCategory(level, chunk, category);
    }

    @Inject(
            method = "spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("RETURN"),
            require = 0
    )
    private static void skyesight$endSpawnCategoryContext(
            MobCategory category,
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback,
            CallbackInfo ci
    ) {
        PortalVanillaSpawnBridge.endNaturalSpawnerCategory(level, chunk, category);
    }

    @Inject(
            method = "getRandomPosWithin",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void skyesight$maybeOverrideRandomPosWithin(
            Level level,
            LevelChunk chunk,
            CallbackInfoReturnable<BlockPos> cir
    ) {
        BlockPos original = cir.getReturnValue();
        BlockPos replacement = PortalVanillaSpawnBridge.maybeForceValidNaturalSpawnerPosition(level, chunk, original);
        if (PortalVanillaSpawnBridge.hasPendingForcedNaturalSpawnerPosition(level, chunk, original, replacement)) {
            cir.setReturnValue(replacement);
        }
    }

    @ModifyVariable(
            method = "spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "STORE"),
            index = 5,
            require = 0
    )
    private static BlockPos skyesight$overrideRandomPosAtActualSpawnCategoryCallSite(
            BlockPos original,
            MobCategory category,
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback
    ) {
        return original;
    }

    @Redirect(
            method = "spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;"
            ),
            require = 0
    )
    private static BlockPos skyesight$redirectRandomPosWithinAtSpawnCategoryCallSite(
            Level level,
            LevelChunk chunk,
            MobCategory category,
            ServerLevel serverLevel,
            LevelChunk enclosingChunk,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback
    ) {
        BlockPos original = skyesight$vanillaRandomPosWithin(level, chunk);
        return PortalVanillaSpawnBridge.overrideNaturalSpawnerRandomPositionRedirect(
                serverLevel,
                enclosingChunk,
                category,
                original
        );
    }

    private static BlockPos skyesight$vanillaRandomPosWithin(Level level, LevelChunk chunk) {
        int x = chunk.getPos().getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + level.random.nextInt(16);
        int maxY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        int y = Mth.randomBetweenInclusive(level.random, level.getMinBuildHeight(), maxY);
        return new BlockPos(x, y, z);
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;"
            ),
            require = 0
    )
    private static Player skyesight$portalNearestPlayerGateBeforeSpawnList(
            ServerLevel receiver,
            double x,
            double y,
            double z,
            double maxDistance,
            boolean ignoreCreative,
            MobCategory category,
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos basePos,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback
    ) {
        Player player = receiver.getNearestPlayer(x, y, z, maxDistance, ignoreCreative);
        Player returned = PortalVanillaSpawnBridge.portalNearestPlayerOverrideForSpawnListGate(
                receiver,
                chunk,
                category,
                x,
                y,
                z,
                maxDistance,
                player
        );
        return returned;
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(DDD)D"
            ),
            require = 0
    )
    private static double skyesight$portalVirtualDistanceForEntityDistanceToSqr(
            Entity receiver,
            double x,
            double y,
            double z,
            MobCategory category,
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos basePos,
            NaturalSpawner.SpawnPredicate filter,
            NaturalSpawner.AfterSpawnCallback callback
    ) {
        double vanilla = receiver.distanceToSqr(x, y, z);
        return PortalVanillaSpawnBridge.portalDistanceToSqrForPlayerCoordinateRead(receiver, x, y, z, vanilla);
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"),
            require = 0
    )
    private static double skyesight$portalVirtualPlayerX(Entity receiver) {
        return PortalVanillaSpawnBridge.portalPlayerCoordinateForNaturalSpawner(receiver, "x", receiver.getX());
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"),
            require = 0
    )
    private static double skyesight$portalVirtualPlayerY(Entity receiver) {
        return PortalVanillaSpawnBridge.portalPlayerCoordinateForNaturalSpawner(receiver, "y", receiver.getY());
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"),
            require = 0
    )
    private static double skyesight$portalVirtualPlayerZ(Entity receiver) {
        return PortalVanillaSpawnBridge.portalPlayerCoordinateForNaturalSpawner(receiver, "z", receiver.getZ());
    }

    @Inject(
            method = "isValidPositionForMob",
            at = @At("RETURN"),
            require = 0
    )
    private static void skyesight$recordValidPortalSpawnedMob(
            ServerLevel level,
            Mob mob,
            double distance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            PortalVanillaSpawnBridge.recordPortalSpawnedMob(level, mob);
        }
    }

    @Inject(
            method = "isRightDistanceToPlayerAndSpawnPoint",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void skyesight$portalSimulationRegionCountsAsSpawnObserver(
            ServerLevel level,
            ChunkAccess chunk,
            BlockPos.MutableBlockPos pos,
            double distance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!PortalVanillaSpawnBridge.portalNaturalSpawningExperimentEnabled()) {
            return;
        }
        Boolean portalResult = PortalVanillaSpawnBridge.portalDistanceResultForSpawn(level, chunk, pos, distance);
        if (portalResult != null) {
            cir.setReturnValue(portalResult);
            return;
        }
        boolean accepted = PortalVanillaSpawnBridge.isMobSpawnObserverNear(level, pos);
        if (accepted) {
            cir.setReturnValue(true);
        }
    }
}
