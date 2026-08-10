package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.pipeline.TextureTarget;
import org.joml.Matrix4f;

public interface SkyesightViewHandle extends SkyesightCameraView {

    SkyesightRenderMode renderMode();

    void renderNow(float partialTick);

    TextureTarget outputTarget();

    @Override
    default TextureTarget render(float partialTick) {
        renderNow(partialTick);
        return outputTarget();
    }

    @Override
    default TextureTarget textureTarget() {
        return outputTarget();
    }

    int colorTextureId();
    void setClipPlane(SkyesightClipPlane clipPlane);

    void clearClipPlane();
    void setProjectionOverride(Matrix4f projectionMatrix);

    void clearProjectionOverride();

    Matrix4f projectionOverride();
    SkyesightClipPlane clipPlane();
}
