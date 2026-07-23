package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

public record PortalStencilMask(ResourceLocation texture, boolean alphaBinary) {
    public PortalStencilMask {
        if (texture == null) {
            throw new IllegalArgumentException("Portal stencil mask texture cannot be null");
        }
    }

    public static PortalStencilMask alphaBinary(ResourceLocation texture) {
        return new PortalStencilMask(texture, true);
    }
}
