package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Optional;

/**
 * Entry point for persistent camera-to-texture views.
 */
public final class SkyesightCameraApi {
    private SkyesightCameraApi() {}

    public static SkyesightCameraView create(
            ResourceLocation id,
            ResourceKey<Level> dimension,
            Vec3 position,
            Quaternionf rotation,
            int width,
            int height
    ) {
        return create(
                id,
                dimension,
                position,
                rotation,
                width,
                height,
                70.0F,
                8,
                SkyesightViewRenderOptions.defaults()
        );
    }

    public static SkyesightCameraView create(
            ResourceLocation id,
            ResourceKey<Level> dimension,
            Vec3 position,
            Quaternionf rotation,
            int width,
            int height,
            float fov,
            int renderDistanceChunks,
            SkyesightViewRenderOptions options
    ) {
        return Skyesight.api().createView(new SkyesightViewSpec(
                id,
                dimension,
                position,
                rotation,
                renderDistanceChunks,
                width,
                height,
                fov,
                SkyesightRenderMode.WORLD,
                options
        ));
    }

    public static Optional<SkyesightCameraView> get(ResourceLocation id) {
        return Skyesight.api().getView(id).map(view -> view);
    }

    public static boolean destroy(ResourceLocation id) {
        return Skyesight.api().destroyView(id);
    }
}
