package com.skyeshade.skyesight.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable, server-owned pair description. Callers persist this data and re-register on load. */
public record PortalDefinition(UUID id, PortalEndpoint endpointA, PortalEndpoint endpointB, PortalPairSettings settings) {
    public PortalDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(endpointA); Objects.requireNonNull(endpointB); Objects.requireNonNull(settings);
        endpointA = copy(endpointA); endpointB = copy(endpointB);
    }
    private static PortalEndpoint copy(PortalEndpoint p) {
        return new PortalEndpoint(p.id(), p.dimension(), p.center(), p.facing(), p.rotation(), p.width(), p.height());
    }
    @Override public PortalEndpoint endpointA() { return copy(endpointA); }
    @Override public PortalEndpoint endpointB() { return copy(endpointB); }
    public static Builder builder(UUID id) { return new Builder(id); }
    public Builder toBuilder() { return builder(id).endpointA(endpointA).endpointB(endpointB).settings(settings); }

    public static final class Builder {
        private final UUID id;
        private PortalEndpoint a, b;
        private PortalPairSettings settings = PortalPairSettings.defaults();
        private Builder(UUID id) { this.id = Objects.requireNonNull(id); }
        public Builder endpointA(PortalEndpoint endpoint) { a = copy(endpoint); return this; }
        public Builder endpointB(PortalEndpoint endpoint) { b = copy(endpoint); return this; }
        public Builder behavior(PortalBehavior behavior) { settings = settings.withBehavior(behavior); return this; }
        public Builder sides(PortalSidedness sides) { return sides(sides, sides); }
        public Builder sides(PortalSidedness a, PortalSidedness b) { settings = settings.withSides(a, b); return this; }
        public Builder directionality(PortalDirectionality direction) { settings = settings.withDirectionality(direction); return this; }
        public Builder aperture(PortalAperture aperture) { return apertures(aperture, aperture); }
        public Builder apertures(PortalAperture a, PortalAperture b) { settings = settings.withApertures(a, b); return this; }
        public Builder settings(PortalPairSettings settings) { this.settings = Objects.requireNonNull(settings); return this; }
        public PortalDefinition build() { return new PortalDefinition(id, a, b, settings); }
    }
}
