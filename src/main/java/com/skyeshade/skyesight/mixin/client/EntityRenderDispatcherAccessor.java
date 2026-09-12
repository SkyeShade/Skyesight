package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor("shouldRenderShadow")
    boolean skyesight$getShouldRenderShadow();
    @Accessor("level")
    Level skyesight$getLevel();

    @Accessor("cameraOrientation")
    Quaternionf skyesight$getCameraOrientation();
}
