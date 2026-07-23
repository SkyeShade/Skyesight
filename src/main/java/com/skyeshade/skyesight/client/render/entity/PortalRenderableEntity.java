package com.skyeshade.skyesight.client.render.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record PortalRenderableEntity(
        Entity entity,
        ResourceKey<Level> dimension,
        String source,
        Runnable beforeRender,
        Runnable afterRender,
        boolean mainLevelBacked,
        boolean standalonePart,
        int parentEntityId
) {
    public PortalRenderableEntity(
            Entity entity,
            ResourceKey<Level> dimension,
            String source,
            Runnable beforeRender
    ) {
        this(entity, dimension, source, beforeRender, () -> {}, false, false, -1);
    }

    public void prepareForRender() {
        if (this.beforeRender != null) {
            this.beforeRender.run();
        }
    }

    public void finishRender() {
        if (this.afterRender != null) {
            this.afterRender.run();
        }
    }
}
