package com.skyeshade.skyesight.mixin.server.chunk;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapEntityTrackerAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<?> skyesight$getEntityMap();

    @Accessor("level")
    ServerLevel skyesight$getLevel();

    @Invoker("anyPlayerCloseEnoughForSpawning")
    boolean skyesight$anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos);
}
