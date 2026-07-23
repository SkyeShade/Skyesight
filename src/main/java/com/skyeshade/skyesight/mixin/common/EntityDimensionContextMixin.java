package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityDimensionContextMixin implements SkyesightEntityDimensionContext {
    @Unique
    @Nullable
    private ResourceKey<Level> skyesight$explicitDimension;

    @Override
    @Nullable
    public ResourceKey<Level> skyesight$getExplicitDimension() {
        return this.skyesight$explicitDimension;
    }

    @Override
    public void skyesight$setExplicitDimension(@Nullable ResourceKey<Level> dimension) {
        this.skyesight$explicitDimension = dimension;
    }
}
