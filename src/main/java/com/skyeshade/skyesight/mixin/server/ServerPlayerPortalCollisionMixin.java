package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.portal.PortalCollisions;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerPortalCollisionMixin {
    @Redirect(method = "isPlayerCollidingWithAnythingNew", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private Iterable<VoxelShape> skyesight$validate(LevelReader level, Entity entity, AABB box,
                                                    LevelReader argumentLevel, AABB oldBox, double x, double y, double z) {
        var shapes = level.getCollisions(entity, box);
        if (!(level instanceof Level world)) return shapes;
        var reference = new net.minecraft.world.phys.Vec3((oldBox.minX + oldBox.maxX) / 2, oldBox.minY, (oldBox.minZ + oldBox.maxZ) / 2);
        var current = net.minecraft.world.phys.shapes.Shapes.create(box);
        return PortalCollisions.collect(world, entity, box.minmax(oldBox), shapes, reference).stream()
                .filter(shape -> net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape, current, net.minecraft.world.phys.shapes.BooleanOp.AND)).toList();
    }
}
