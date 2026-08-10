package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * Read-only description of the current texture produced by a Skyesight view.
 *
 * <p>Skyesight owns the backing framebuffer/render target. Consumers may sample
 * its color texture during their own rendering, but must not close, destroy, or
 * resize the backing target. The target may be recreated after a view resize or
 * renderer lifecycle change, so integrations should retrieve the current output
 * each time they render instead of permanently caching the raw target.
 *
 * <p>{@link #colorTextureId()} is the preferred value for sampling the rendered
 * image. {@link #renderTarget()} remains available as an advanced escape hatch
 * for integrations that need to interoperate with Minecraft framebuffer APIs;
 * ownership still remains with Skyesight.
 */
public interface SkyesightViewOutput {
    /**
     * Current output width in pixels.
     */
    int width();

    /**
     * Current output height in pixels.
     */
    int height();

    /**
     * OpenGL texture ID for the current color attachment, or {@code -1} when
     * the backing target has not been created yet.
     */
    int colorTextureId();

    /**
     * OpenGL texture ID for the current depth attachment, or {@code -1} when
     * the backing target has not been created yet.
     */
    int depthTextureId();

    /**
     * Advanced access to the current backing render target. Callers may bind or
     * sample it according to normal Minecraft rendering rules, but must not
     * destroy or resize it.
     */
    RenderTarget renderTarget();
}
