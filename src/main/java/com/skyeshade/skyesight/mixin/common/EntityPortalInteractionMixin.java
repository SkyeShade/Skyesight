package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.server.portal.PortalInteractionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class EntityPortalInteractionMixin {
    @Inject(method = "getBlockX", at = @At("HEAD"), cancellable = true)
    private void skyesight$blockX(CallbackInfoReturnable<Integer> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(Mth.floor(c.pose.position().x));
    }

    @Inject(method = "getBlockY", at = @At("HEAD"), cancellable = true)
    private void skyesight$blockY(CallbackInfoReturnable<Integer> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(Mth.floor(c.pose.position().y));
    }

    @Inject(method = "getBlockZ", at = @At("HEAD"), cancellable = true)
    private void skyesight$blockZ(CallbackInfoReturnable<Integer> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(Mth.floor(c.pose.position().z));
    }

    @Inject(method = "getX(D)D", at = @At("HEAD"), cancellable = true)
    private void skyesight$scaledX(double scale, CallbackInfoReturnable<Double> cir) {
        var e = (Entity) (Object) this;
        var c = PortalInteractionContext.forEntity(e);
        if (c != null) cir.setReturnValue(c.pose.position().x + e.getBbWidth() * scale);
    }

    @Inject(method = "getY(D)D", at = @At("HEAD"), cancellable = true)
    private void skyesight$scaledY(double scale, CallbackInfoReturnable<Double> cir) {
        var e = (Entity) (Object) this;
        var c = PortalInteractionContext.forEntity(e);
        if (c != null) cir.setReturnValue(c.pose.position().y + e.getBbHeight() * scale);
    }

    @Inject(method = "getZ(D)D", at = @At("HEAD"), cancellable = true)
    private void skyesight$scaledZ(double scale, CallbackInfoReturnable<Double> cir) {
        var e = (Entity) (Object) this;
        var c = PortalInteractionContext.forEntity(e);
        if (c != null) cir.setReturnValue(c.pose.position().z + e.getBbWidth() * scale);
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void skyesight$eyeY(CallbackInfoReturnable<Double> cir) {
        var entity = (Entity) (Object) this;
        var context = PortalInteractionContext.forEntity(entity);
        // Vanilla reads the private position field here, not getY(). Reach and vertical placement
        // otherwise combine destination X/Z with source Y when the endpoints have different heights.
        if (context != null) cir.setReturnValue(context.pose.position().y + entity.getEyeHeight());
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void skyesight$bounds(CallbackInfoReturnable<AABB> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.bounds);
    }

    @Inject(method = "level", at = @At("HEAD"), cancellable = true)
    private void skyesight$level(CallbackInfoReturnable<Level> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.level);
    }

    @Inject(method = "position", at = @At("HEAD"), cancellable = true)
    private void skyesight$position(CallbackInfoReturnable<Vec3> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.position());
    }

    @Inject(method = "blockPosition", at = @At("HEAD"), cancellable = true)
    private void skyesight$block(CallbackInfoReturnable<BlockPos> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(BlockPos.containing(c.pose.position()));
    }

    @Inject(method = "getX()D", at = @At("HEAD"), cancellable = true)
    private void skyesight$x(CallbackInfoReturnable<Double> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.position().x);
    }

    @Inject(method = "getY()D", at = @At("HEAD"), cancellable = true)
    private void skyesight$y(CallbackInfoReturnable<Double> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.position().y);
    }

    @Inject(method = "getZ()D", at = @At("HEAD"), cancellable = true)
    private void skyesight$z(CallbackInfoReturnable<Double> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.position().z);
    }

    @Inject(method = "getYRot", at = @At("HEAD"), cancellable = true)
    private void skyesight$yaw(CallbackInfoReturnable<Float> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.yaw());
    }

    @Inject(method = "getXRot", at = @At("HEAD"), cancellable = true)
    private void skyesight$pitch(CallbackInfoReturnable<Float> cir) {
        var c = PortalInteractionContext.forEntity((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c.pose.pitch());
    }
}
