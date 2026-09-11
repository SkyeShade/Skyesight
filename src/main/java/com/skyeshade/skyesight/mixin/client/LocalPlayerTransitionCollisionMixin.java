package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.transition.SecondaryTransition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class LocalPlayerTransitionCollisionMixin {
    @Redirect(method = "collectColliders", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private static Iterable<VoxelShape> skyesight$arrivalCollision(Level level, Entity entity, AABB box) {
        return SecondaryTransition.collisionView(level, entity).getBlockCollisions(entity, box);
    }
}
