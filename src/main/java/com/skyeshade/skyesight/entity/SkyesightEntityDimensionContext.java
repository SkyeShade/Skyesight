package com.skyeshade.skyesight.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface SkyesightEntityDimensionContext {
    @Nullable
    ResourceKey<Level> skyesight$getExplicitDimension();

    void skyesight$setExplicitDimension(@Nullable ResourceKey<Level> dimension);

    default ResourceKey<Level> skyesight$getEffectiveDimension(ResourceKey<Level> fallbackDimension) {
        ResourceKey<Level> explicitDimension = this.skyesight$getExplicitDimension();
        return explicitDimension == null ? fallbackDimension : explicitDimension;
    }

    default boolean skyesight$hasExplicitDimension() {
        return this.skyesight$getExplicitDimension() != null;
    }
}
