package com.skyeshade.skyesight.remote;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SkyesightRemoteViewRegistration(
        ResourceLocation viewId,
        ResourceKey<Level> targetDimension,
        long generation
) {
    public boolean accepts(long payloadGeneration, ResourceKey<Level> dimension) {
        return this.generation == payloadGeneration
                && this.targetDimension.equals(dimension);
    }

    public boolean targets(ResourceKey<Level> dimension) {
        return this.targetDimension.equals(dimension);
    }
}
