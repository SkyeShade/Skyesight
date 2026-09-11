package com.skyeshade.skyesight.mixin.client;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(LivingEntity.class)
public interface LivingEntityParticleDataAccessor {
    @Accessor("DATA_EFFECT_PARTICLES")
    static EntityDataAccessor<List<ParticleOptions>> skyesight$effectParticles() { throw new AssertionError(); }
    @Accessor("DATA_EFFECT_AMBIENCE_ID")
    static EntityDataAccessor<Boolean> skyesight$effectAmbient() { throw new AssertionError(); }
}
