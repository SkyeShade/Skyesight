package com.skyeshade.skyesight.client.render.entity;

import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.mixin.common.LivingEntityWalkAnimationAccessor;
import com.skyeshade.skyesight.mixin.common.WalkAnimationStateAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;

public final class PortalVisualEntityAnimationUpdater {
    private PortalVisualEntityAnimationUpdater() {}

    public static void updateForRender(Entity entity, float partialTick, String source) {
        if (entity == null) {
            return;
        }

        entity.yRotO = wrap(entity.yRotO);
        entity.xRotO = wrap(entity.xRotO);
        entity.setYRot(wrap(entity.getYRot()));
        entity.setXRot(wrap(entity.getXRot()));

        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.yBodyRot = wrap(livingEntity.yBodyRot);
            livingEntity.yBodyRotO = wrap(livingEntity.yBodyRotO);
            livingEntity.yHeadRot = wrap(livingEntity.yHeadRot);
            livingEntity.yHeadRotO = wrap(livingEntity.yHeadRotO);
            smoothWalkAnimation(livingEntity, partialTick);
        }

        if (PortalMultipartEntityUtil.isMultipartParent(entity)) {
            PortalMultipartEntityUtil.refreshMultipartParent(entity, source);
        }
    }

    private static void smoothWalkAnimation(LivingEntity entity, float partialTick) {
        try {
            WalkAnimationState walkAnimation =
                    ((LivingEntityWalkAnimationAccessor) entity).skyesight$getWalkAnimation();
            WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) walkAnimation;
            float speed = walkAnimation.speed();
            float oldSpeed = accessor.skyesight$getSpeedOld();
            float position = walkAnimation.position();
            accessor.skyesight$setSpeed(Mth.lerp(0.35F, oldSpeed, speed));
            accessor.skyesight$setSpeedOld(oldSpeed);
            accessor.skyesight$setPosition(position + speed * Math.max(0.0F, partialTick));
        } catch (RuntimeException exception) {
            // Render-only best effort. Never let animation smoothing break portal rendering.
        }
    }

    private static float wrap(float degrees) {
        return Mth.wrapDegrees(degrees);
    }
}
