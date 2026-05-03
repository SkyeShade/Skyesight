package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

public interface SkyesightViewHandle {
    ResourceLocation id();

    ResourceKey<Level> dimension();

    SkyesightRenderMode renderMode();

    SkyesightViewCamera camera();

    void setDimension(ResourceKey<Level> dimension);

    void setRenderDistance(int renderDistanceChunks);

    int renderDistanceChunks();
    float fov();

    void setFov(float fov);
    void resize(int width, int height);

    int width();

    int height();

    void renderNow(float partialTick);

    TextureTarget outputTarget();

    int colorTextureId();
    SkyesightViewOutput output();
    boolean isClosed();

    void close();
    void setClipPlane(SkyesightClipPlane clipPlane);

    void clearClipPlane();
    void setProjectionOverride(Matrix4f projectionMatrix);

    void clearProjectionOverride();

    Matrix4f projectionOverride();
    SkyesightClipPlane clipPlane();
}