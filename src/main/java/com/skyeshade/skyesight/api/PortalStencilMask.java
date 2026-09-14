package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

public record PortalStencilMask(ResourceLocation texture, boolean alphaBinary, PortalAperture aperture,
                                java.util.List<PortalAperture.Rectangle> rectangles) {
    public PortalStencilMask(ResourceLocation texture, boolean alphaBinary, PortalAperture aperture) {
        this(texture,alphaBinary,aperture,null);
    }
    public static PortalStencilMask rectangles(java.util.List<PortalAperture.Rectangle> rectangles) {
        return new PortalStencilMask(ResourceLocation.parse("skyesight:region_mask"),true,null,rectangles);
    }
    public PortalStencilMask(ResourceLocation texture, boolean alphaBinary) {
        this(texture, alphaBinary, null);
    }

    public static PortalStencilMask aperture(PortalAperture shape) {
        return new PortalStencilMask(ResourceLocation.parse("skyesight:server_aperture"), true, shape);
    }

    public PortalStencilMask {
        if(rectangles!=null) rectangles=java.util.List.copyOf(rectangles);
        if (texture == null) {
            throw new IllegalArgumentException("Portal stencil mask texture cannot be null");
        }
    }

    public static PortalStencilMask alphaBinary(ResourceLocation texture) {
        return new PortalStencilMask(texture, true);
    }
}
