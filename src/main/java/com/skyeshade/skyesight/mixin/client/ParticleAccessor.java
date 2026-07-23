package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("xo")
    double skyesight$getXo();

    @Accessor("yo")
    double skyesight$getYo();

    @Accessor("zo")
    double skyesight$getZo();

    @Accessor("x")
    double skyesight$getX();

    @Accessor("y")
    double skyesight$getY();

    @Accessor("z")
    double skyesight$getZ();

    @Accessor("age")
    int skyesight$getAge();

    @Accessor("lifetime")
    int skyesight$getLifetime();

    @Accessor("removed")
    boolean skyesight$isRemoved();
}
