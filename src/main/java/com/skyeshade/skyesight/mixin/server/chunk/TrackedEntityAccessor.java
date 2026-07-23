package com.skyeshade.skyesight.mixin.server.chunk;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity skyesight$getServerEntity();

    @Accessor("seenBy")
    Set<ServerPlayerConnection> skyesight$getSeenBy();

    @Invoker("removePlayer")
    void skyesight$removePlayer(ServerPlayer player);
}
