package com.skyeshade.skyesight.api;

import java.util.Objects;

/**
 * Server-owned pair policy; the aperture is also the authoritative rendering mask.
 */
public record PortalPairSettings(PortalBehavior behavior, PortalAperture apertureA, PortalAperture apertureB,
                                 PortalRenderSettings renderA, PortalRenderSettings renderB,
                                 boolean renderBackface, String sourceTag) {
    public PortalPairSettings {
        Objects.requireNonNull(behavior);
        Objects.requireNonNull(apertureA);
        Objects.requireNonNull(apertureB);
        Objects.requireNonNull(renderA);
        Objects.requireNonNull(renderB);
        Objects.requireNonNull(sourceTag);
        if (sourceTag.length() > 256) throw new IllegalArgumentException("Source tag too long");
        if (renderA.stencilMask() != null || renderB.stencilMask() != null)
            throw new IllegalArgumentException("Server pairs use PortalAperture, not a client-only texture mask");
    }

    public static PortalPairSettings defaults() {
        return of(PortalBehavior.VISUAL_ONLY);
    }

    public static PortalPairSettings of(PortalBehavior behavior) {
        return new PortalPairSettings(behavior, PortalAperture.RECTANGLE, PortalAperture.RECTANGLE,
                PortalRenderSettings.defaults(), PortalRenderSettings.defaults(), true, "api");
    }

    public PortalPairSettings withApertures(PortalAperture a, PortalAperture b) {
        return new PortalPairSettings(behavior, a, b, renderA, renderB, renderBackface, sourceTag);
    }

    public PortalPairSettings withBehavior(PortalBehavior behavior) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag);
    }

    public PortalPairSettings withRenderSettings(PortalRenderSettings a, PortalRenderSettings b) {
        return new PortalPairSettings(behavior, apertureA, apertureB, a, b, renderBackface, sourceTag);
    }

    public PortalPairSettings withSourceTag(String sourceTag) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag);
    }

    public PortalPairSettings withRenderBackface(boolean renderBackface) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag);
    }
}
