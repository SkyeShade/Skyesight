package com.skyeshade.skyesight.client.render.entity;

import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        }

        if (PortalMultipartEntityUtil.isMultipartParent(entity)) {
            PortalMultipartEntityUtil.refreshMultipartParent(entity, source);
        }
    }

    private static float wrap(float degrees) {
        return Mth.wrapDegrees(degrees);
    }
}
