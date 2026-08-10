package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record SkyesightViewSpec(
        ResourceLocation id,
        ResourceKey<Level> dimension,
        Vec3 position,
        Quaternionf rotation,
        int renderDistanceChunks,
        int width,
        int height,
        float fov,
        SkyesightRenderMode renderMode,
        SkyesightViewRenderOptions renderOptions
) {
    public SkyesightViewSpec(
            ResourceLocation id,
            ResourceKey<Level> dimension,
            Vec3 position,
            Quaternionf rotation,
            int renderDistanceChunks,
            int width,
            int height,
            float fov,
            SkyesightRenderMode renderMode
    ) {
        this(
                id,
                dimension,
                position,
                rotation,
                renderDistanceChunks,
                width,
                height,
                fov,
                renderMode,
                SkyesightViewRenderOptions.defaults()
        );
    }
}
