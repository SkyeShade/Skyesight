package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

public record PortalStencilMask(ResourceLocation texture, boolean alphaBinary, PortalAperture aperture) {
    public PortalStencilMask(ResourceLocation texture, boolean alphaBinary) {
        this(texture, alphaBinary, null);
    }

    public static PortalStencilMask aperture(PortalAperture shape) {
        return new PortalStencilMask(ResourceLocation.parse("skyesight:server_aperture"), true, shape);
    }

    public PortalStencilMask {
        if (texture == null) {
            throw new IllegalArgumentException("Portal stencil mask texture cannot be null");
        }
    }

    public static PortalStencilMask alphaBinary(ResourceLocation texture) {
        return new PortalStencilMask(texture, true);
    }
}
