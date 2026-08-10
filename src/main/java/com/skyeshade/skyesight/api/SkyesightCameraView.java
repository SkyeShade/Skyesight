package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * A persistent secondary camera that renders a world view into a reusable
 * texture target.
 *
 * <p>The view owns its render target and may recreate it on resize. Callers
 * should use {@link #output()} and sample {@link SkyesightViewOutput#colorTextureId()}
 * when possible. Raw target access is retained for advanced Minecraft renderer
 * interoperability, but callers must not destroy or resize it. Rendering must
 * be requested on the Minecraft render thread; asynchronous world rendering is
 * not supported.
 */
public interface SkyesightCameraView extends AutoCloseable {
    ResourceLocation id();

    ResourceKey<Level> dimension();

    SkyesightViewCamera camera();

    default void setCamera(Vec3 position, Quaternionf rotation) {
        camera().setPosition(position);
        camera().setRotation(rotation);
    }

    void setDimension(ResourceKey<Level> dimension);

    int renderDistanceChunks();

    void setRenderDistance(int renderDistanceChunks);

    float fov();

    void setFov(float fov);

    SkyesightViewRenderOptions renderOptions();

    void setRenderOptions(SkyesightViewRenderOptions options);

    void resize(int width, int height);

    int width();

    int height();

    /**
     * Renders the view for the current client frame and returns the current
     * backing target.
     *
     * <p>The returned target is owned by Skyesight and may be recreated after a
     * resize or renderer lifecycle change. Prefer {@link #output()} for stable
     * texture sampling metadata.
     */
    TextureTarget render(float partialTick);

    /**
     * Returns the current backing target, or {@code null} before the view has
     * created one. Skyesight owns this target; callers must not destroy or
     * resize it.
     */
    TextureTarget textureTarget();

    /**
     * Returns a stable read-only view of the current output texture metadata.
     */
    SkyesightViewOutput output();

    boolean isClosed();

    @Override
    void close();
}
