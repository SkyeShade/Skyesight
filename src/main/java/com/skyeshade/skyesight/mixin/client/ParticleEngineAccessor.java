package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("particles")
    Map<ParticleRenderType, Queue<Particle>> skyesight$getParticles();

    @Accessor("textureManager")
    TextureManager skyesight$getTextureManager();

    @Accessor("level")
    ClientLevel skyesight$getLevel();

    @Accessor("level")
    void skyesight$setLevel(ClientLevel level);

    @Invoker("makeParticle")
    <T extends ParticleOptions> Particle skyesight$makeParticle(T particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed);
}
