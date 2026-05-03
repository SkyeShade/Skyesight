package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface SkyesightViewOutput {
    int width();

    int height();

    int colorTextureId();

    int depthTextureId();

    RenderTarget renderTarget();
}