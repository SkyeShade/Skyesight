package com.skyeshade.skyesight.client.render;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;

/** Immutable branch identity. Repeated portals are legal; depth always terminates a tunnel. */
public record PortalRecursionPath(List<ResourceLocation> portals, int limit) {
    public PortalRecursionPath { portals = List.copyOf(portals); limit = Math.clamp(limit, 0, 5); }
    public boolean canDescend() { return portals.size() < limit; }
    public PortalRecursionPath through(ResourceLocation portal) {
        if (!canDescend()) throw new IllegalStateException("Portal recursion limit reached");
        var next = new ArrayList<>(portals); next.add(portal);
        return new PortalRecursionPath(next, limit);
    }
}
