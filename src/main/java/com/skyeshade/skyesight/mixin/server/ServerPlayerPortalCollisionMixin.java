package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.portal.PortalCollisions;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerPortalCollisionMixin {
    @Redirect(method = "isPlayerCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private Iterable<VoxelShape> skyesight$validate(LevelReader level, Entity entity, AABB box,
                                                    LevelReader argumentLevel, AABB oldBox, double x, double y, double z) {
        var shapes = level.getCollisions(entity, box);
        if (!(level instanceof Level world)) return shapes;
        var reference = new Vec3((oldBox.minX + oldBox.maxX) / 2, oldBox.minY, (oldBox.minZ + oldBox.maxZ) / 2);
        var current = Shapes.create(box);
        return PortalCollisions.collect(world, entity, box.minmax(oldBox), shapes, reference).stream()
                .filter(shape -> Shapes.joinIsNotEmpty(shape, current, BooleanOp.AND)).toList();
    }
}
