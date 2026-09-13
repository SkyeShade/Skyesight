package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.entity.Entity;

/** Client-only visual capture. Call on the render thread while the source's state is available. */
public final class SkyesightEntitySliceApi {
    private SkyesightEntitySliceApi() {}

    public static FrozenEntityVisual capture(Entity entity, float partialTick) {
        RenderSystem.assertOnRenderThread();
        java.util.Objects.requireNonNull(entity, "entity");
        if (!entity.level().isClientSide()) throw new IllegalArgumentException("Client entity required");
        if (!Float.isFinite(partialTick) || partialTick < 0 || partialTick > 1)
            throw new IllegalArgumentException("partialTick must be in [0,1]");
        if (entity.isPassenger()) throw new UnsupportedOperationException("Riding poses require a vehicle visual adapter");
        return new FrozenEntityVisual(entity, partialTick);
    }
}
