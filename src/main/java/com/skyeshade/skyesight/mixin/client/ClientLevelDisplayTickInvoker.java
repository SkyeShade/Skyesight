package com.skyeshade.skyesight.mixin.client;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ClientLevel.class)
public interface ClientLevelDisplayTickInvoker {
    @Invoker("trySpawnDripParticles")
    void skyesight$trySpawnDripParticles(BlockPos pos, BlockState state, ParticleOptions particle, boolean sturdy);
}
