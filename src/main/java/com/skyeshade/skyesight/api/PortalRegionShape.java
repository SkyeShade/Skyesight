package com.skyeshade.skyesight.api;

import java.util.List;

/** Union of integer-aligned rectangles in surface U/V coordinates, including disconnected strips/holes. */
public record PortalRegionShape(List<Strip> strips) {
    public static final int MAX_STRIPS = 256;
    public PortalRegionShape {
        strips = List.copyOf(strips);
        if (strips.isEmpty() || strips.size() > MAX_STRIPS) throw new IllegalArgumentException("1..256 strips required");
    }
    public record Strip(int minU, int minV, int maxU, int maxV) {
        public Strip {
            if (minU >= maxU || minV >= maxV || Math.abs((long)minU) > 30_000_000
                    || Math.abs((long)maxU) > 30_000_000 || Math.abs((long)minV) > 30_000_000
                    || Math.abs((long)maxV) > 30_000_000) throw new IllegalArgumentException("Invalid region strip");
        }
        public boolean contains(double u, double v) { return u >= minU && u < maxU && v >= minV && v < maxV; }
    }
    public boolean contains(double u, double v) {
        for(var strip:strips) if(strip.contains(u,v)) return true;
        return false;
    }
    public static PortalRegionShape rectangle(int minU, int minV, int maxU, int maxV) {
        return new PortalRegionShape(List.of(new Strip(minU,minV,maxU,maxV)));
    }
}
