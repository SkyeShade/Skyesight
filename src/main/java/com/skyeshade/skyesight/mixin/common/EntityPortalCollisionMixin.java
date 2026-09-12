package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.portal.PortalCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityPortalCollisionMixin {
    @Inject(method = "collectColliders", at = @At("RETURN"), cancellable = true)
    private static void skyesight$collisions(Entity entity, Level level, List<VoxelShape> collisions, AABB box, CallbackInfoReturnable<List<VoxelShape>> cir) {
        cir.setReturnValue(PortalCollisions.collect(level, entity, box, cir.getReturnValue()));
    }
}
