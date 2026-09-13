package com.skyeshade.skyesight.api;

import java.util.Objects;

/**
 * Server-owned pair policy; the aperture is also the authoritative rendering mask.
 */
public record PortalPairSettings(PortalBehavior behavior, PortalAperture apertureA, PortalAperture apertureB,
                                 PortalRenderSettings renderA, PortalRenderSettings renderB,
                                 boolean renderBackface, String sourceTag,
                                 PortalSidedness sidednessA, PortalSidedness sidednessB,
                                 PortalDirectionality directionality) {
    public PortalPairSettings(PortalBehavior behavior, PortalAperture apertureA, PortalAperture apertureB,
                              PortalRenderSettings renderA, PortalRenderSettings renderB,
                              boolean renderBackface, String sourceTag) {
        this(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag,
                renderBackface ? PortalSidedness.BOTH : PortalSidedness.FRONT_ONLY,
                renderBackface ? PortalSidedness.BOTH : PortalSidedness.FRONT_ONLY, PortalDirectionality.TWO_WAY);
    }
    public PortalPairSettings {
        Objects.requireNonNull(behavior);
        Objects.requireNonNull(apertureA);
        Objects.requireNonNull(apertureB);
        Objects.requireNonNull(renderA);
        Objects.requireNonNull(renderB);
        Objects.requireNonNull(sourceTag);
        Objects.requireNonNull(sidednessA);
        Objects.requireNonNull(sidednessB);
        Objects.requireNonNull(directionality);
        // Compatibility accessor; endpoint sidedness is authoritative.
        renderBackface = sidednessA != PortalSidedness.FRONT_ONLY || sidednessB != PortalSidedness.FRONT_ONLY;
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
        return new PortalPairSettings(behavior, a, b, renderA, renderB, renderBackface, sourceTag, sidednessA, sidednessB, directionality);
    }

    public PortalPairSettings withBehavior(PortalBehavior behavior) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag, sidednessA, sidednessB, directionality);
    }

    public PortalPairSettings withRenderSettings(PortalRenderSettings a, PortalRenderSettings b) {
        return new PortalPairSettings(behavior, apertureA, apertureB, a, b, renderBackface, sourceTag, sidednessA, sidednessB, directionality);
    }

    public PortalPairSettings withSourceTag(String sourceTag) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface, sourceTag, sidednessA, sidednessB, directionality);
    }

    public PortalPairSettings withRenderBackface(boolean renderBackface) {
        return withSides(renderBackface ? PortalSidedness.BOTH : PortalSidedness.FRONT_ONLY,
                renderBackface ? PortalSidedness.BOTH : PortalSidedness.FRONT_ONLY);
    }

    public PortalSidedness sides(boolean fromA) { return fromA ? sidednessA : sidednessB; }

    public PortalPairSettings withSides(PortalSidedness a, PortalSidedness b) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB,
                a != PortalSidedness.FRONT_ONLY || b != PortalSidedness.FRONT_ONLY, sourceTag, a, b, directionality);
    }

    public PortalPairSettings withDirectionality(PortalDirectionality directionality) {
        return new PortalPairSettings(behavior, apertureA, apertureB, renderA, renderB, renderBackface,
                sourceTag, sidednessA, sidednessB, directionality);
    }
}
