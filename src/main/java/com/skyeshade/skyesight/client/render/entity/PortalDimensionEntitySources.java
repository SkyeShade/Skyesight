package com.skyeshade.skyesight.client.render.entity;

import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPool;
import com.skyeshade.skyesight.client.world.SkyesightVisualEntity;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PortalDimensionEntitySources {
    private PortalDimensionEntitySources() {}

    public static List<SkyesightVisualEntity> visualEntitiesForDimension(
            SkyesightVisualWorld visualWorld,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        return visualEntitiesForDimension(
                visualWorld,
                targetDimension,
                bounds,
                frustum,
                false
        );
    }

    public static List<SkyesightVisualEntity> visualEntitiesForDimension(
            SkyesightVisualWorld visualWorld,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum,
            boolean applyInterpolatedBeforeCulling
    ) {
        List<SkyesightVisualEntity> result = new ArrayList<>();

        if (visualWorld == null || visualWorld.isClosed() || targetDimension == null || bounds == null) {
            return result;
        }

        for (SkyesightVisualEntity visualEntity : visualWorld.entityStore().entities()) {
            if (visualEntity == null) {
                continue;
            }

            Entity entity = visualEntity.entity();
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "visual_entity_source");
                continue;
            }

            SkyesightEntityDimensionContext dimensionContext = (SkyesightEntityDimensionContext) entity;
            ResourceKey<Level> effectiveDimension =
                    dimensionContext.skyesight$getEffectiveDimension(visualWorld.dimension());

            if (!targetDimension.equals(effectiveDimension)) {
                continue;
            }

            if (applyInterpolatedBeforeCulling) {
                visualEntity.applyInterpolated();
            }

            AABB cullingBox = entity.getBoundingBoxForCulling();
            if (!bounds.intersects(cullingBox)) {
                continue;
            }

            if (frustum != null && !frustum.isVisible(cullingBox)) {
                continue;
            }

            result.add(visualEntity);
        }

        return result;
    }

    public static List<PortalRenderableEntity> renderableVisualEntitiesForDimension(
            ResourceLocation viewId,
            SkyesightVisualWorld visualWorld,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        List<PortalRenderableEntity> result = new ArrayList<>();

        if (visualWorld == null || visualWorld.isClosed() || targetDimension == null || bounds == null) {
            return result;
        }

        String source = "visual_world:" + viewId;

        for (SkyesightVisualEntity visualEntity : visualWorld.entityStore().entities()) {
            if (visualEntity == null) {
                continue;
            }

            Entity entity = visualEntity.entity();
            if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "renderable_visual_entity_source");
                continue;
            }
            ResourceKey<Level> effectiveDimension = effectiveDimension(
                    entity,
                    visualWorld.dimension()
            );

            visualEntity.applyInterpolated();

            if (!isRenderableInDimension(entity, effectiveDimension, targetDimension, bounds, frustum)) {
                continue;
            }

            result.add(new PortalRenderableEntity(
                    entity,
                    effectiveDimension,
                    source,
                    visualEntity::prepareForRender,
                    () -> {},
                    false,
                    false,
                    -1
            ));
        }

        return result;
    }

    public static List<PortalRenderableEntity> renderableMainLevelEntitiesForDimension(
            ClientLevel level,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        List<PortalRenderableEntity> result = new ArrayList<>();

        if (level == null || targetDimension == null || bounds == null) {
            return result;
        }

        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds, entity -> true)) {
            ResourceKey<Level> effectiveDimension = effectiveDimension(entity, level.dimension());

            if (!isRenderableInDimension(entity, effectiveDimension, targetDimension, bounds, frustum)) {
                continue;
            }

            result.add(new PortalRenderableEntity(
                    entity,
                    effectiveDimension,
                    "main_level",
                    () -> {},
                    () -> {},
                    true,
                    PortalMultipartEntityUtil.isStandalonePartEntity(entity),
                    PortalMultipartEntityUtil.parentOfPart(entity) == null
                            ? -1
                            : PortalMultipartEntityUtil.parentOfPart(entity).getId()
            ));
        }

        return result;
    }

    public static List<PortalRenderableEntity> renderablePortalEntityPoolForDimension(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum,
            @Nullable SkyesightVisualWorld visualWorld
    ) {
        return SkyesightPortalEntityPool.renderablesFor(viewId, targetDimension, bounds, frustum, visualWorld);
    }

    public static List<PortalRenderableEntity> renderablePortalEntityPoolForDimension(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        return renderablePortalEntityPoolForDimension(viewId, targetDimension, bounds, frustum, null);
    }

    private static ResourceKey<Level> effectiveDimension(
            Entity entity,
            ResourceKey<Level> fallbackDimension
    ) {
        if (entity == null) {
            return fallbackDimension;
        }

        SkyesightEntityDimensionContext dimensionContext = (SkyesightEntityDimensionContext) entity;
        return dimensionContext.skyesight$getEffectiveDimension(fallbackDimension);
    }

    private static boolean isRenderableInDimension(
            Entity entity,
            ResourceKey<Level> effectiveDimension,
            ResourceKey<Level> targetDimension,
            AABB bounds,
            @Nullable Frustum frustum
    ) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }

        if (!targetDimension.equals(effectiveDimension)) {
            return false;
        }

        AABB cullingBox = entity.getBoundingBoxForCulling();
        return bounds.intersects(cullingBox)
                && (frustum == null || frustum.isVisible(cullingBox));
    }
}
