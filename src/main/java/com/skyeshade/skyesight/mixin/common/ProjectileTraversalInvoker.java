package com.skyeshade.skyesight.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Projectile.class)
public interface ProjectileTraversalInvoker {
    @Invoker("hitTargetOrDeflectSelf") ProjectileDeflection skyesight$impact(HitResult hit);
    @Invoker("canHitEntity") boolean skyesight$canHit(Entity entity);
}
